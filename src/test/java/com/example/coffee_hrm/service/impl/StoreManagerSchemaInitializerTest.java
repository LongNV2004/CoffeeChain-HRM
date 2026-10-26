package com.example.coffee_hrm.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreManagerSchemaInitializerTest {

    @Test
    void uniqueIndexIsCreatedOnlyWhenExistingManagersAreNotDuplicated() {
        assertTrue(StoreManagerSchemaInitializer.shouldCreateManagerIndex(0, false));
        assertFalse(StoreManagerSchemaInitializer.shouldCreateManagerIndex(2, false));
        assertFalse(StoreManagerSchemaInitializer.shouldCreateManagerIndex(0, true));
    }
}
