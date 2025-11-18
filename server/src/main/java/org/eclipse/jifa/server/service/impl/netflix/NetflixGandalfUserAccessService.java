package org.eclipse.jifa.server.service.impl.netflix;

import static org.eclipse.jifa.common.enums.CommonErrorCode.ILLEGAL_ARGUMENT;
import static org.eclipse.jifa.server.enums.ServerErrorCode.ACCESS_DENIED;
import static org.eclipse.jifa.server.enums.ServerErrorCode.FILE_NOT_FOUND;
import static org.eclipse.jifa.server.enums.ServerErrorCode.UNAVAILABLE;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;

import org.eclipse.jifa.common.util.Validate;
import org.eclipse.jifa.server.domain.entity.shared.file.BaseFileEntity;
import org.eclipse.jifa.server.domain.entity.shared.user.UserEntity;
import org.eclipse.jifa.server.enums.ServerErrorCode;
import org.eclipse.jifa.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.netflix.gandalf.agent.AuthorizationClient;
import com.netflix.gandalf.agent.GandalfException;
import com.netflix.gandalf.agent.protogen.AuthorizationResponse;
import com.netflix.infosec.stairmaster.enforcement.client.StepUpEnforcementException;
import com.netflix.infosec.stairmaster.enforcement.client.StepUpEnforcer;
import com.netflix.metatron.ipc.security.MetatronSslContext;

import lombok.extern.slf4j.Slf4j;

@Primary
@Component
@Slf4j
public class NetflixGandalfUserAccessService implements FileAccessService {
    // create a threadlocal to track the Netflix token
    public static final ThreadLocal<String> STEP_UP_TOKEN = ThreadLocal.withInitial(() -> null);

    @Value("${fc.flamecommanderApi}")
    private String flamecommanderApi;

    @Value("${fc.authPolicyPrefix}")
    private String authPolicyPrefix;

    AuthorizationClient authorizationClient;

    @Autowired
    UserService userService;

    @Autowired
    StepUpEnforcer stepUpEnforcer;

    public NetflixGandalfUserAccessService() {
        this.authorizationClient = new AuthorizationClient();
        this.authorizationClient.status();
    }

    @Override
    public void checkAuthorityForCurrentUser(BaseFileEntity file)
    {
        UserEntity user = userService.getCurrentUser();

        log.info("Checking authority for file '{}' and user '{}'",
            file.getUniqueName(),
            user.getName());

        InstanceCommand ic = InstanceCommand.parseParam(file.getUniqueName());
        Validate.notNull(ic, ILLEGAL_ARGUMENT, "Not an s3! formed pathname: " + file.getUniqueName());

        // look up historic information in flamecommander database for instance
        final FcEvent fcEvent;
        try {
            fcEvent = loadFlamecommanderEvent(ic);
            log.debug("flamecommander ic: {} received event: {}", ic, fcEvent);
        } catch (IOException e) {
            log.error("flamecommander api error, sending error", e);
            Validate.error(FILE_NOT_FOUND, "flamecommander api error: " + e.getMessage());
            return;
        }

        Validate.notNull(fcEvent, FILE_NOT_FOUND,
            String.format("No event found in flamecommander for instanceId=%s, commandId=%s",
                ic.instanceId, ic.commandId));

        Validate.notNull(user.getName(), ACCESS_DENIED, "No user identity found");

        JsonObject subject = createGandalfSubject(user.getName());
        String policyName = authPolicyNameFromFcEvent(fcEvent);

        log.debug("found identity, verifying resources against gandalf, fcEvent: {} user: {} subject: {}, policyName: {}",
                        fcEvent, user, subject, policyName);
        try
        {
            AuthorizationResponse authorizationResponse = authorizationClient.isAuthorized(
                            policyName, subject, null);

            log.debug("gandalf response: {}", authorizationResponse);

            if (authorizationResponse.getAllowed()) {
                return;
            }

            if (!user.isAdmin()) {
                Validate.error(ServerErrorCode.ACCESS_DENIED, "Access denied by gandalf");
            }

            // else fall through to step-up enforcement
        }
        catch (GandalfException e)
        {
            Validate.error(UNAVAILABLE, "gandalf error: " + e.getMessage());
        }

        // enforce will throw if it is not successful
        log.debug("found identity, gandalf did not pass, user isAdmin, enforcing step-up");
        try {
            String stepUpToken = STEP_UP_TOKEN.get();
            logStepUpToken("pre-enforce", stepUpToken);
            stepUpEnforcer.requireStepUp()
                    .withScopes("instanceId:" + ic.instanceId)
                    .withMaxLifetime(Duration.ofMinutes(90))
                    .withStepUpToken(stepUpToken)
                    .enforce();
        } catch (StepUpEnforcementException e) {
            log.warn("step up token was rejected: {}", e.getMessage());
            Validate.error(ServerErrorCode.ACCESS_DENIED_STEPUP, e.getMessage());
        }
    }

    public static void logStepUpToken(String context, String stepUpToken) {
        if (stepUpToken == null) {
            log.debug("step up token contents: context={}, token null", context);
            return;
        }

        try {
            String[] parts = stepUpToken.split("\\.");
            if (parts.length != 3) {
                log.warn("step up token is not a jwt token: context={}, token={}", context, stepUpToken);
            }

            String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
            );

            String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            );

            log.debug("step up token contents: context={}, header={}, payload={}", context, headerJson, payloadJson);
        } catch (Exception e) {
            log.warn("step up token could not be displayed: context={}, message={}", context, e.getMessage());
        }
    }

    static class FcEvent {
        @SerializedName("account_id")
        String accountId;
        String application;
        String stack;
        String region;

        @Override
        public String toString() {
            return "FcEvent{" +
                "accountId='" + accountId + '\'' +
                ", application='" + application + '\'' +
                ", stack='" + stack + '\'' +
                ", region='" + region + '\'' +
                '}';
        }
    }

    String getFcProfileLookupUrl(InstanceCommand ic) {
        return flamecommanderApi + "api/jifa/fc-event/" + ic.instanceId + "/" + ic.commandId;
    }

    FcEvent loadFlamecommanderEvent(InstanceCommand ic) throws IOException {
        final String eventUrl = getFcProfileLookupUrl(ic);
        HttpsURLConnection connection = (HttpsURLConnection) new URL(eventUrl).openConnection();
        connection.setSSLSocketFactory(MetatronSslContext.forClient("flamecommander").getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> true);
        FcEvent[] events = new Gson().fromJson(new InputStreamReader(connection.getInputStream()), FcEvent[].class);
        return (events.length == 1) ? events[0] : null;
    }

    JsonObject createGandalfSubject(String email) {
        final JsonObject user = new JsonObject();
        user.addProperty("email", email);
        final JsonObject subject = new JsonObject();
        subject.add("user", user);
        return subject;
    }

    String authPolicyNameFromFcEvent(FcEvent fcEvent) {
        return authPolicyPrefix + fcEvent.application;
    }

}
