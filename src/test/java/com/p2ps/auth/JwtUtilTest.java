package com.p2ps.auth;



import com.p2ps.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-care-trebuie-sa-fie-foarte-lunga-32-chars",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "v8yB2p5x8A/D?G-KaPdSgVkYp3s6v9y$B&E)H+MbQeThWmZq4t7w!z%C*F-JaNcR";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKeyString", secret);
        jwtUtil.init();
    }

    @Test
    void generateAndExtractEmail() {
        String email = "user@test.com";
        String token = jwtUtil.generateToken(email);

        assertNotNull(token);
        assertEquals(email, jwtUtil.extractEmail(token));
    }

    @Test
    void validateToken_Success() {
        String email = "user@test.com";
        String token = jwtUtil.generateToken(email);
        assertTrue(jwtUtil.isTokenValid(token, email));
    }

    @Test
    void validateToken_Fail_WrongUser() {
        String token = jwtUtil.generateToken("user1@test.com");
        assertFalse(jwtUtil.isTokenValid(token, "user2@test.com"));
    }
}