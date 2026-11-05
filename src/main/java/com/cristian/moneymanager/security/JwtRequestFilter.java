package com.cristian.moneymanager.security;

import com.cristian.moneymanager.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
       final String authHeader = request.getHeader("Authorization");

       if (authHeader != null && authHeader.startsWith("Bearer ")
               && SecurityContextHolder.getContext().getAuthentication() == null) {
           String jwt = authHeader.substring(7);
           try {
               String email = jwtUtil.extractUsername(jwt);
               if (email != null) {
                   UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);
                   if (jwtUtil.validateToken(jwt, userDetails)) {
                       UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                               userDetails,
                               null,
                               userDetails.getAuthorities()
                       );
                       authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                       SecurityContextHolder.getContext().setAuthentication(authToken);
                   }
               }
           } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
               // Invalid, expired or orphaned token: continue unauthenticated so the
               // security chain answers with 401 instead of crashing with a 500.
               log.debug("Rejected JWT: {}", e.getMessage());
               SecurityContextHolder.clearContext();
           }
       }

       filterChain.doFilter(request, response);
    }
}
