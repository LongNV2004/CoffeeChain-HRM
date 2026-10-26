package com.example.coffee_hrm.common.security;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenIdsTest {

    @Test
    void acceptsExactlyTwelveDigitsAndMasksTheMiddle() {
        assertEquals("079203000123", CitizenIds.require("079 203 000 123"));
        assertEquals("079******123", CitizenIds.mask("079203000123"));
        assertFalse(CitizenIds.mask("079203000123").contains("203000"));
        assertNull(CitizenIds.mask(null));
        assertNull(CitizenIds.optional("   "));
    }

    @Test
    void rejectsLettersShortValuesAndDoesNotEchoTheInput() {
        BusinessException invalid = assertThrows(BusinessException.class, () -> CitizenIds.require("07920300012A"));
        assertEquals(CitizenIds.INVALID_MESSAGE, invalid.getMessage());
        assertFalse(invalid.getMessage().contains("07920300012A"));

        BusinessException missing = assertThrows(BusinessException.class, () -> CitizenIds.require(" "));
        assertEquals(CitizenIds.REQUIRED_MESSAGE, missing.getMessage());
        assertEquals("************", CitizenIds.mask("not-a-citizen-id"));
        assertTrue(CitizenIds.isValid("012345678901"));
        assertFalse(CitizenIds.isValid("01234567890"));
    }
}
