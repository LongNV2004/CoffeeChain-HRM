package com.example.coffee_hrm.common.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generatedPasswordsMeetPolicyAndAreNotFixed() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String password = generator.generate();
            generated.add(password);
            assertTrue(password.length() >= 8 && password.length() <= 12);
            assertTrue(password.chars().anyMatch(Character::isUpperCase));
            assertTrue(password.chars().anyMatch(Character::isLowerCase));
            assertTrue(password.chars().anyMatch(Character::isDigit));
            assertTrue(password.chars().anyMatch(ch -> "!@#$%&*?".indexOf(ch) >= 0));
        }
        assertTrue(generated.size() > 1);
    }
}
