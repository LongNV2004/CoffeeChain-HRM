package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ContractPayPeriod;
import com.example.coffee_hrm.common.enums.ContractPaymentMethod;
import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractTermType;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class EmploymentContractResponse {

    private final Integer id;
    private final String contractCode;
    private final Integer employeeId;
    private final String employeeName;
    /** CCCD hiện đọc từ hồ sơ nhân viên. Null ở danh sách. File hợp đồng đã ký không lưu và không bị ghi lại số này. */
    private final String citizenId;
    private final LocalDate dateOfBirth;
    private final String positionLabel;
    private final LocalDate hireDate;
    private final Integer storeId;
    private final String storeName;
    private final LocalDate signedDate;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ContractTermType contractTerm;
    private final String contractTermLabel;
    private final String jobTitle;
    private final String workLocation;
    private final Integer weeklyShiftCount;
    private final String internalNote;
    private final BigDecimal salary;
    private final BigDecimal allowance;
    private final ContractPaymentMethod paymentMethod;
    private final String paymentMethodLabel;
    private final ContractPayPeriod payPeriod;
    private final String payPeriodLabel;
    private final String benefitNote;
    private final String originalFileName;
    private final Long fileSizeBytes;
    private final boolean hasFile;
    private final String createdByName;
    private final LocalDateTime createdAt;
    private final ContractSigningStatus signingStatus;
    private final String signingStatusLabel;
    private final ContractValidityStatus validityStatus;
    private final String validityStatusLabel;
    private final LocalDate terminatedDate;

    /** Mức lương đúng như trong hợp đồng: phân cách hàng nghìn bằng dấu chấm, không hiện phần thập phân ,00. */
    public String getSalaryLabel() {
        return formatMoney(salary);
    }

    public String getAllowanceLabel() {
        return formatMoney(allowance);
    }

    public static String formatMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", symbols);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return format.format(value);
    }
}
