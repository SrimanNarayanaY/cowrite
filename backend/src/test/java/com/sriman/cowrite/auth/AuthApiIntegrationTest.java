package com.sriman.cowrite.auth;

import com.jayway.jsonpath.JsonPath;
import com.sriman.cowrite.user.User;
import com.sriman.cowrite.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Test
	void registersUserAndStoresBcryptHash() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"Ravi@Example.com","password":"password123"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("ravi@example.com"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User user = userRepository.findByEmail("ravi@example.com").orElseThrow();

		assertThat(user.getPasswordHash())
				.startsWith("$2a$")
				.isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("wrong-password", user.getPasswordHash())).isFalse();
	}

	@Test
	void samePasswordProducesDifferentHashesBecauseOfSalt() {
		String first = passwordEncoder.encode("123456");
		String second = passwordEncoder.encode("123456");

		assertThat(first).isNotEqualTo(second);
		assertThat(first).startsWith("$2a$");
		assertThat(passwordEncoder.matches("123456", first)).isTrue();
		assertThat(passwordEncoder.matches("123456", second)).isTrue();
	}

	@Test
	void duplicateEmailReturns409() throws Exception {
		register("dup@example.com", "password123");

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"dup@example.com","password":"password456"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Email already registered: dup@example.com"));
	}

	@Test
	void shortPasswordReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"short@example.com","password":"1234567"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password")
						.value("password must be between 8 and 72 characters"));

		assertThat(userRepository.findByEmail("short@example.com")).isEmpty();
	}

	@Test
	void invalidEmailReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"not-an-email","password":"password123"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.email").value("email must be a valid address"));
	}

	@Test
	void loginReturnsJwtThatCanCallProtectedEndpoints() throws Exception {
		register("login@example.com", "password123");

		String token = login("login@example.com", "password123");

		assertThat(token.split("\\.")).hasSize(3);

		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void wrongPasswordAndUnknownEmailReturnTheSame401() throws Exception {
		register("real@example.com", "password123");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"real@example.com","password":"wrong-password"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Invalid credentials"));

		// Same message: no user enumeration
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"ghost@example.com","password":"password123"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid credentials"));
	}

	@Test
	void tokenPayloadContainsOnlyNonSensitiveClaims() throws Exception {
		register("claims@example.com", "password123");
		String token = login("claims@example.com", "password123");

		String payload = new String(java.util.Base64.getUrlDecoder()
				.decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

		assertThat(payload)
				.contains("\"sub\"")
				.contains("\"exp\"")
				.contains("\"iat\"")
				.doesNotContain("password")
				.doesNotContain("$2a$");
	}

	@Test
	void tamperedTokenReturns401() throws Exception {
		register("tamper@example.com", "password123");
		String token = login("tamper@example.com", "password123");

		String tampered = token.substring(0, token.length() - 3) + "abc";

		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + tampered))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void malformedTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenReturns401() throws Exception {
		String expired = Jwts.builder()
				.subject("1")
				.issuedAt(Date.from(Instant.now().minusSeconds(7200)))
				.expiration(Date.from(Instant.now().minusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
				.compact();

		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void documentsEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/documents"))
				.andExpect(status().isUnauthorized());
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isCreated());
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return JsonPath.read(body, "$.token");
	}

}
