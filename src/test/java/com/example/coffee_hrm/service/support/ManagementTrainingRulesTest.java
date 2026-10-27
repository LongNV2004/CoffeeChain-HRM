package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementTrainingRulesTest {

    @Test
    void staffCannotJoinManagementTraining() {
        User staff = account(RoleName.STAFF);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ManagementTrainingRules.requireManagerAccount(staff));
        assertEquals(ManagementTrainingRules.MANAGER_ONLY, ex.getMessage());
    }

    @Test
    void passingDoesNotReplaceTheStaffCertificate() {
        Employee employee = employee();
        employee.setHasCertificate(true);
        employee.setCertificationStatus(CertificationStatus.CERTIFIED);
        User recorder = account(RoleName.ADMIN);

        ManagementTrainingRules.applyEvaluation(
                employee, RoleName.MANAGER, TrainingResult.PASS, recorder, false);

        assertEquals(Boolean.TRUE, employee.getManagementCertified());
        assertTrue(employee.getManagementCertifiedAt() != null);
        assertEquals(recorder, employee.getManagementCertifiedBy());
        assertEquals(Boolean.TRUE, employee.getHasCertificate());
        assertEquals(CertificationStatus.CERTIFIED, employee.getCertificationStatus());
    }

    @Test
    void failingLeavesTheManagerUncertifiedAndKeepsTheStaffCertificate() {
        Employee employee = employee();
        employee.setHasCertificate(true);
        employee.setCertificationStatus(CertificationStatus.CERTIFIED);

        ManagementTrainingRules.applyEvaluation(
                employee, RoleName.MANAGER, TrainingResult.NOT_PASS, account(RoleName.ADMIN), false);

        assertEquals(Boolean.FALSE, employee.getManagementCertified());
        assertEquals(Boolean.TRUE, employee.getHasCertificate());
    }

    @Test
    void passingAdminReceivesTheManagementCertificate() {
        Employee employee = employee();

        ManagementTrainingRules.applyEvaluation(
                employee, RoleName.ADMIN, TrainingResult.PASS, account(RoleName.ADMIN), false);

        assertEquals(Boolean.TRUE, employee.getManagementCertified());
    }

    @Test
    void staffRoleDoesNotReceiveTheManagementCertificate() {
        Employee employee = employee();

        ManagementTrainingRules.applyEvaluation(
                employee, RoleName.STAFF, TrainingResult.PASS, account(RoleName.ADMIN), false);

        assertEquals(Boolean.FALSE, employee.getManagementCertified());
    }

    @Test
    void anotherPassKeepsTheCertificateWhenThisAttemptFails() {
        Employee employee = employee();
        employee.setManagementCertified(true);

        ManagementTrainingRules.applyEvaluation(
                employee, RoleName.MANAGER, TrainingResult.NOT_PASS, account(RoleName.ADMIN), true);

        assertEquals(Boolean.TRUE, employee.getManagementCertified());
    }

    @Test
    void changingTheOnlyPassToFailRevokesTheManagementCertificate() {
        Employee employee = employee();
        employee.setManagementCertified(true);

        ManagementTrainingRules.applyEvaluation(
                employee, RoleName.MANAGER, TrainingResult.NOT_PASS, account(RoleName.ADMIN), false);

        assertEquals(Boolean.FALSE, employee.getManagementCertified());
        assertNull(employee.getManagementCertifiedAt());
    }

    private static Employee employee() {
        return Employee.builder().id(1).fullName("Manager").hasCertificate(false)
                .certificationStatus(CertificationStatus.NOTCERTIFIED).build();
    }

    private static User account(RoleName roleName) {
        return User.builder().id(2).username("user").passwordHash("x")
                .role(Role.builder().roleName(roleName).build()).build();
    }
}
