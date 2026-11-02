package com.sriman.cowrite.security;

import com.sriman.cowrite.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

	/** HS256 requires at least 256 bits. */
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey signingKey;
	private final long expirationMillis;

	public JwtService(@Value("${jwt.secret}") String secret,
					  @Value("${jwt.expiration}") long expirationMillis) {
		byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

		if (keyBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (" + keyBytes.length + " given)");
		}

		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		this.expirationMillis = expirationMillis;
	}

	/**
	 * Subject = user id, plus iat/exp. Never put passwords or other secrets in a JWT:
	 * the payload is only base64, anyone can decode it.
	 */
	public String generateToken(User user) {
		Instant issuedAt = Instant.now();

		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim("email", user.getEmail())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plusMillis(expirationMillis)))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * Verifies the signature and returns the user id from the subject claim.
	 *
	 * @throws JwtException when the token is tampered with or expired
	 */
	public Long extractUserId(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		return Long.valueOf(claims.getSubject());
	}

	/** Safe check: expired, tampered or malformed tokens return false (never throw). */
	public boolean isValid(String token) {
		try {
			Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

}
