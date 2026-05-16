package com.mgh.backend.auth.security.config;

import com.mgh.backend.auth.security.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (log.isDebugEnabled()) {
            log.debug("Incoming request: method={} path={}", request.getMethod(), request.getRequestURI());
        }

        // Read Authorization header from the incoming request
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        UserDetails userDetails;

        // ---------------------------------------------------------
        // 1. Allow CORS preflight requests to pass through untouched
        // ---------------------------------------------------------
        // Browsers send OPTIONS requests before actual calls.
        // These requests do not carry authentication information.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.debug("Skipping JWT filter for CORS preflight OPTIONS");
            filterChain.doFilter(request, response);
            return;
        }

        // ---------------------------------------------------------
        // 2. Skip authentication if Authorization header is missing
        //    or does not start with "Bearer "
        // ---------------------------------------------------------
        // This allows public endpoints to work without JWT
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            log.debug("No Bearer token found (Authorization header missing or not Bearer). HeaderPresent={}",
                    authHeader != null);
            filterChain.doFilter(request, response);
            return;
        }

        // ---------------------------------------------------------
        // 3. Extract raw JWT token from the Authorization header
        // ---------------------------------------------------------
        jwt = authHeader.substring(7).trim();
        log.debug("Bearer token detected (len={})", jwt.length());

        try {
            // ---------------------------------------------------------
            // 4. Parse and validate JWT structure & signature
            //    and extract the username (subject)
            // ---------------------------------------------------------
            // Any malformed token, invalid signature, or unsupported
            // JWT will throw an exception here
            username = jwtService.extractUsername(jwt);
            userDetails = userDetailsService.loadUserByUsername(username);
            log.debug("JWT parsed. subject(username)={} authorities={}", username, userDetails.getAuthorities());
        } catch (JwtException | IllegalArgumentException ex) {
            // ---------------------------------------------------------
            // 5. Token is invalid or malformed → reject request
            // ---------------------------------------------------------
            SecurityContextHolder.clearContext();
            log.debug("JWT parsing/validation failed: {}", ex.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or malformed JWT");
            return;
        }

        // ---------------------------------------------------------
        // 6. Prevent re-authentication if user is already authenticated
        // ---------------------------------------------------------
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

        if (username != null && existingAuth == null) {

            try {
                // ---------------------------------------------------------
                // 7. Load user details from database or identity store
                // ---------------------------------------------------------
                // This ensures the user still exists
                userDetails = this.userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException ex) {
                // ---------------------------------------------------------
                // 8. JWT refers to a non-existing user → reject
                // ---------------------------------------------------------
                SecurityContextHolder.clearContext();
                log.debug("User not found for token subject(username)={}", username);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found for JWT");
                return;
            }

            // ---------------------------------------------------------
            // 9. Optional server-side token revocation check
            // ---------------------------------------------------------
            // Useful for logout, forced sign-out, or compromised tokens
            // if (tokenRevocationService.isRevoked(jwt)) {
            //     response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
            //     return;
            // }

            // ---------------------------------------------------------
            // 10. Validate token integrity, expiration, and user match
            // ---------------------------------------------------------
            // Ensures token is not expired and still belongs to this user
            if (!jwtService.isTokenValid(jwt, userDetails)) {
                SecurityContextHolder.clearContext();
                log.debug("JWT not valid for username={} (expired/subject mismatch/signature)", username);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Expired or invalid JWT");
                return;
            }

            // ---------------------------------------------------------
            // 11. Enforce account-level security checks
            // ---------------------------------------------------------
            // Prevent disabled, locked, or expired accounts
            // from authenticating even with a valid token
            if (!userDetails.isEnabled()
                    || !userDetails.isAccountNonLocked()
                    || !userDetails.isAccountNonExpired()
                    || !userDetails.isCredentialsNonExpired()) {

                SecurityContextHolder.clearContext();
                log.debug("User account not in good standing. enabled={} locked={}",
                        userDetails.isEnabled(), userDetails.isAccountNonLocked());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User account is not in good standing");
                return;
            }

            // ---------------------------------------------------------
            // 12. Build authenticated security token
            // ---------------------------------------------------------
            // At this point, the user is fully trusted
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            // Attach request-specific details (IP, session, etc.)
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // ---------------------------------------------------------
            // 13. Store authentication in SecurityContext
            // ---------------------------------------------------------
            // This makes the user available to controllers & security rules
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("SecurityContext authentication set for username={}", username);
        }

        // ---------------------------------------------------------
        // 14. Continue request processing
        // ---------------------------------------------------------
        log.debug("Continuing filter chain");
        filterChain.doFilter(request, response);
    }


}
