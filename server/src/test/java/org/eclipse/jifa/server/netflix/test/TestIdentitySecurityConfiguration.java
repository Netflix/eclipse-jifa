package org.eclipse.jifa.server.netflix.test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jifa.server.Constant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Test-only security chain for full-context tests.
 *
 * <p>SBN's app chain matches every request and requires a Meechum, Metatron, or proxyd identity.
 * Tests can not produce one. This chain runs first for the JIFA API and STOMP endpoint. It
 * authenticates the {@link TestIdentity} headers as an {@code SsoCaller}, so the rest of the
 * application sees the same identity type as in deployments.</p>
 *
 * <p>Requests without the user header get 401, like SBN's chain.</p>
 */
@Configuration
public class TestIdentitySecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain testIdentitySecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/" + Constant.STOMP_ENDPOINT, "/" + Constant.STOMP_ENDPOINT + "/**",
                             Constant.HTTP_API_PREFIX + "/**")
            // SBN's app chain has no CsrfFilter. Mirror it.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new TestIdentityFilter(), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(r -> r
                    .requestMatchers(Constant.HTTP_API_PREFIX + Constant.HTTP_HEALTH_CHECK_MAPPING).permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    static class TestIdentityFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String user = request.getHeader(TestIdentity.USER_HEADER);
            if (user != null && !user.isBlank()) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(TestIdentity.caller(user.trim(), request.getHeader(TestIdentity.GROUPS_HEADER)));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        }
    }
}
