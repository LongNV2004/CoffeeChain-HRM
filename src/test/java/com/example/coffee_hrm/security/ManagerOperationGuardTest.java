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

    @Test
    void uncertifiedManagerKeepsPersonalAttendanceAndCanAttendTraining() {
        // Chấm công cá nhân (chỉ của mình) và chấm công buổi học đào tạo.
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/history", "GET", true));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/history", "GET", false));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/training", "GET"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/training/check-in", "POST"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/training/check-out/preview", "POST"));
        assertTrue(ManagerOperationGuard.allowsWithoutCertificate("/training/classes/4/documents/2", "GET"));
        // Nghiệp vụ cửa hàng vẫn cần chứng chỉ.
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/day", "GET"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/attendance/manager/check-in", "POST"));
        assertFalse(ManagerOperationGuard.allowsWithoutCertificate("/manager/leaves", "GET"));
    }
}
