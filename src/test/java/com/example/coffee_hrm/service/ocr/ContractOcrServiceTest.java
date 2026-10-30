package com.example.coffee_hrm.service.ocr;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractOcrServiceTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final ContractOcrService service = new ContractOcrService(employees, stores);

    @Test
    void signedPdfFillsFieldsAndPassesBothSignatures() throws Exception {
        Employee worker = worker("Nhan vien", "079203000123");
        stubStore(worker);
        byte[] pdf = ContractSamples.signedContract("Nhan vien", "079203000123");

        var view = service.inspect(manager(), "hop-dong.pdf", pdf);

        assertEquals("SUCCESS", view.getStatus());
        assertEquals("DAT", view.getOverall());
        assertTrue(view.isAllowsCreate());
        assertEquals(30, view.getSuggestedEmployeeId());
        assertEquals("CO_CHU_KY", view.getManagerSignature().getStatus());
        assertEquals("CO_CHU_KY", view.getEmployeeSignature().getStatus());
        assertEquals(1, view.getManagerSignature().getPage());
        ContractOcrService.Acceptance acceptance = service.requireAccepted("hop-dong.pdf", pdf, worker);
        assertEquals("DAT", acceptance.result());
        assertEquals(64, acceptance.fileHash().length());
    }

    @Test
    void readsSignaturesOnALaterPage() throws Exception {
        Employee worker = worker("Nhan vien", "079203000123");
        stubStore(worker);
        byte[] pdf = ContractSamples.signedContractAcrossPages("Nhan vien", "079203000123");

        var view = service.inspect(manager(), "nhieu-trang.pdf", pdf);

        assertEquals("DAT", view.getOverall());
        assertEquals(2, view.getManagerSignature().getPage());
        assertEquals(2, view.getEmployeeSignature().getPage());
    }

    @Test
    void scannedPdfWithoutTesseractDoesNotInventFields() throws Exception {
        var view = service.inspect(manager(), "quet.pdf", ContractSamples.scannedImagePdf());

        assertEquals("FAILED", view.getStatus());
        assertTrue(view.getFields().isEmpty());
        assertFalse(view.isAllowsCreate());
        assertTrue(view.getMessage().toLowerCase().contains("tesseract") || view.getMessage().contains("không đọc"));
    }

    @Test
    void missingSignatureBlocksCreate() throws Exception {
        Employee worker = worker("Nhan vien", "079203000123");
        byte[] pdf = ContractSamples.contractWithoutSignatures("Nhan vien", "079203000123");

        var view = service.inspect(manager(), "thieu-ky.pdf", pdf);

        assertEquals("KHONG_DAT", view.getOverall());
        assertFalse(view.isAllowsCreate());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireAccepted("thieu-ky.pdf", pdf, worker));
        assertTrue(error.getMessage().contains("chữ ký"));
    }

    @Test
    void mismatchedCitizenIsNotAutoSelected() throws Exception {
        Employee worker = worker("Nhan vien", "079203000123");
        stubStore(worker);
        byte[] pdf = ContractSamples.signedContract("Nguoi khac", "001122334455");

        var view = service.inspect(manager(), "lech.pdf", pdf);

        assertNull(view.getSuggestedEmployeeId());
        assertEquals("KHONG_DAT", view.getOverall());
        assertThrows(BusinessException.class, () -> service.requireAccepted("lech.pdf", pdf, worker));
    }

    @Test
    void plainTextFileIsRejectedWithoutFakeFields() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.inspect(manager(), "ghi-chu.txt", "day khong phai hop dong".getBytes()));
        assertTrue(error.getMessage().contains("PDF"));
    }

    @Test
    void blurryImageDoesNotInventACitizenId() {
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 1, 2, 3};
        var view = service.inspect(manager(), "mo.jpg", jpeg);

        assertEquals("FAILED", view.getStatus());
        assertTrue(view.getFields().isEmpty());
        assertFalse(view.isAllowsCreate());
        assertEquals("CHUA_XAC_DINH", view.getManagerSignature().getStatus());
    }

    @Test
    void replacingTheFileDoesNotReuseThePreviousResult() throws Exception {
        Employee worker = worker("Nhan vien", "079203000123");
        stubStore(worker);
        byte[] signed = ContractSamples.signedContract("Nhan vien", "079203000123");
        byte[] unsigned = ContractSamples.contractWithoutSignatures("Nhan vien", "079203000123");

        assertTrue(service.inspect(manager(), "a.pdf", signed).isAllowsCreate());
        assertFalse(service.inspect(manager(), "b.pdf", unsigned).isAllowsCreate());
    }

    @Test
    void blankLaborContractPdfDoesNotFillOrPassSignatures() throws Exception {
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/ocr/blank-labor-contract.pdf"));

        var view = service.inspect(manager(), "Mau_hop_dong_lao_dong_OCR_CoffeeHRM_de_trong.pdf", pdf);

        assertEquals("SUCCESS", view.getStatus());
        assertEquals("KHONG_DAT", view.getOverall());
        assertFalse(view.isAllowsCreate());
        assertEquals("KHONG_CO_CHU_KY", view.getManagerSignature().getStatus());
        assertEquals("KHONG_CO_CHU_KY", view.getEmployeeSignature().getStatus());
        assertTrue(view.getFields().stream().noneMatch(field -> field.isRecognized() && "citizenId".equals(field.getKey())));
        assertTrue(view.getFields().stream().noneMatch(field -> field.isRecognized() && "salary".equals(field.getKey())));
        assertTrue(view.getFields().stream().filter(field -> "citizenId".equals(field.getKey()))
                .allMatch(field -> "Chưa nhận dạng được".equals(field.getDisplay())));
        assertThrows(BusinessException.class, () -> service.requireAccepted(
                "Mau_hop_dong_lao_dong_OCR_CoffeeHRM_de_trong.pdf", pdf, worker("Nhan vien", "079203000123")));
    }

    @Test
    void staffCannotExtract() {
        AuthenticatedUser staff = AuthenticatedUser.from(User.builder()
                .id(3)
                .username("staff")
                .passwordHash("hash")
                .role(Role.builder().roleName(RoleName.STAFF).build())
                .isActive(true)
                .build());
        assertThrows(BusinessException.class, () -> service.inspect(staff, "a.pdf", new byte[] {1}));
    }

    private void stubStore(Employee worker) {
        Store store = Store.builder().id(1).storeName("Cua hang").build();
        when(stores.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employees.findByStoreIdWithAccount(1)).thenReturn(List.of(worker));
    }

    private static Employee worker(String name, String citizenId) {
        return Employee.builder().id(30).fullName(name).citizenId(citizenId).build();
    }

    private static AuthenticatedUser manager() {
        Employee managerEmployee = Employee.builder().id(10).fullName("Quan ly").build();
        return AuthenticatedUser.from(User.builder()
                .id(2)
                .username("manager")
                .passwordHash("hash")
                .role(Role.builder().roleName(RoleName.MANAGER).build())
                .employee(managerEmployee)
                .isActive(true)
                .build());
    }
}
