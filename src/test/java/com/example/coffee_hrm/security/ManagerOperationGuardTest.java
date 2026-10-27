package com.example.coffee_hrm.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerOperationGuardTest {

    @Test
    void uncertifiedManagerCanOpenTrainingProfileAndDashboard() {
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/dashboard/manager"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/profile"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/profile/password"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/training", "GET"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/training/classes/4", "GET"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/notifications"));
    }

    @Test
    void uncertifiedManagerCannotOpenManagementOperations() {
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/manager/employees"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/manager/contract"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/manager/salary"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/schedule/manager"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/availability/manager"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/training", "POST"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/training/classes/1/students", "POST"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/training/skills"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/training/management"));
    }
}
