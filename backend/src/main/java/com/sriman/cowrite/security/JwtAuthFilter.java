package com.sriman.cowrite.security;

import com.sriman.cowrite.user.User;
import com.sriman.cowrite.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserRepository userRepository;

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
									@NonNull HttpServletResponse response,
									@NonNull FilterChain filterChain) throws ServletException, IOException {

		String header = request.getHeader(AUTHORIZATION_HEADER);

		// No token: continue. SecurityConfig decides whether the endpoint is public (401 later).
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = header.substring(BEARER_PREFIX.length()).trim();

		if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
			Long userId = jwtService.extractUserId(token);

			userRepository.findById(userId).ifPresent(user -> authenticate(user, request));
		}

		// Invalid/expired token -> anonymous -> 401 for protected endpoints (never 500).
		filterChain.doFilter(request, response);
	}

	private void authenticate(User user, HttpServletRequest request) {
		var authentication = new UsernamePasswordAuthenticationToken(
				user,
				null,
				List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}
