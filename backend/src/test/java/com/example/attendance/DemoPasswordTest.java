package com.example.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPasswordTest {
    @Test
    void demoPasswordHashMatchesDocumentedPassword() {
        String hash = "$2a$10$WOSQJNROOOUASR0e0Sbrhu0871Dj8IxEcgxxmQpGWb7KR442A2I0u";
        assertTrue(new BCryptPasswordEncoder().matches("123456", hash));
    }
}
