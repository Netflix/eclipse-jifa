package org.eclipse.jifa.server.netflix.test;

import com.netflix.authorizable.context.identity.AuthorizableIdentity;
import com.netflix.springboot.sso.caller.CallerIdentity;
import com.netflix.springboot.sso.caller.SsoCaller;
import com.netflix.springboot.sso.caller.UserAttributes;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test-only SSO identity.
 *
 * <p>Tests send {@link #USER_HEADER} and optionally {@link #GROUPS_HEADER}.
 * {@link TestIdentitySecurityConfiguration} turns them into a {@link SsoCaller}. It stands in for the
 * Meechum, Metatron, and proxyd authentication that SBN performs in deployments.</p>
 */
public final class TestIdentity {

    public static final String USER_HEADER = "X-Test-User";

    public static final String GROUPS_HEADER = "X-Test-Groups";

    public static final String DEFAULT_USER = "alice@example.com";

    private TestIdentity() {
    }

    public static WebSocketHttpHeaders handshakeHeaders(String email, String... groups) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(USER_HEADER, email);
        if (groups.length > 0) {
            headers.add(GROUPS_HEADER, String.join(",", groups));
        }
        return headers;
    }

    public static WebSocketHttpHeaders handshakeHeaders() {
        return handshakeHeaders(DEFAULT_USER);
    }

    static SsoCaller caller(String email, String groupsHeader) {
        Set<String> groups = groupsHeader == null
                             ? Set.of()
                             : Arrays.stream(groupsHeader.split(","))
                                     .map(String::trim)
                                     .filter(s -> !s.isEmpty())
                                     .collect(Collectors.toUnmodifiableSet());
        UserCallerIdentity identity = new UserCallerIdentity(new User(email, groups));
        return new Caller(identity);
    }

    private record Caller(CallerIdentity identity) implements SsoCaller {

        @Override
        public CallerIdentity getInitial() {
            return identity;
        }

        @Override
        public CallerIdentity getDirect() {
            return identity;
        }

        @Override
        public String toString() {
            return "TestSsoCaller[" + identity.getName() + "]";
        }
    }

    private record UserCallerIdentity(User user) implements CallerIdentity {

        @Override
        public AuthorizableIdentity.IdentityType getAuthorizableIdentityType() {
            return AuthorizableIdentity.IdentityType.USER;
        }

        @Override
        public String getAuthSource() {
            return "test";
        }

        @Override
        public String getName() {
            return user.email;
        }

        @Override
        public String getId() {
            return user.email;
        }

        @Override
        public Optional<UserAttributes> getUser() {
            return Optional.of(user);
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }
    }

    private record User(String email, Set<String> groups) implements UserAttributes {

        @Override
        public String getDomain() {
            return "example.com";
        }

        @Override
        public String getUsername() {
            return email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));
        }

        @Override
        public Set<String> getGroups() {
            return groups;
        }

        @Override
        public Optional<String> getEmail() {
            return Optional.of(email);
        }

        @Override
        public OrgAttributes getOrg() {
            return new OrgAttributes() {
            };
        }
    }
}
