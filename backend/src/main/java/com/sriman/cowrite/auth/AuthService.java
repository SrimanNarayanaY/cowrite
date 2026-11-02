package com.sriman.cowrite.auth;

import com.sriman.cowrite.auth.dto.LoginRequest;
import com.sriman.cowrite.auth.dto.LoginResponse;
import com.sriman.cowrite.auth.dto.RegisterRequest;
import com.sriman.cowrite.auth.dto.RegisterResponse;
import com.sriman.cowrite.auth.exception.EmailAlreadyRegisteredException;
import com.sriman.cowrite.auth.exception.InvalidCredentialsException;
import com.sriman.cowrite.security.JwtService;
import com.sriman.cowrite.user.User;
import com.sriman.cowrite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());

		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException(email);
		}

		User user = new User();
		user.setEmail(email);
		// Never store raw passwords; BCrypt salts every hash automatically
		user.setPasswordHash(passwordEncoder.encode(request.password()));

		return RegisterResponse.from(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());

		// Unknown email and wrong password must look identical to the client
		User user = userRepository.findByEmail(email)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		return new LoginResponse(jwtService.generateToken(user));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
