package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.ContractPayPeriod;
import com.example.coffee_hrm.common.enums.ContractPaymentMethod;
import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "EmploymentContracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"employee", "createdBy"})
public class EmploymentContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ContractId")
    private Integer id;

    @Column(name = "ContractCode", nullable = false, unique = true, length = 50)
    private String contractCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "EndDate")
    private LocalDate endDate;

    @Column(name = "Salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal salary;

    @Column(name = "JobTitle", length = 150)
    private String jobTitle;

    @Column(name = "WorkLocation", length = 255)
    private String workLocation;

    @Column(name = "WeeklyShiftCount")
    private Integer weeklyShiftCount;

    @Column(name = "InternalNote", length = 500)
    private String internalNote;

    @Column(name = "Allowance", precision = 18, scale = 2)
    private BigDecimal allowance;

    @Column(name = "PaymentMethod", length = 30)
    private ContractPaymentMethod paymentMethod;

    @Column(name = "PayPeriod", length = 30)
    private ContractPayPeriod payPeriod;

    @Column(name = "BenefitNote", length = 500)
    private String benefitNote;

    @Column(name = "OriginalFileName", length = 255)
    private String originalFileName;

    @Column(name = "FileContentType", length = 100)
    private String fileContentType;

    @Column(name = "FileSizeBytes")
    private Long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedByUserId")
    private User createdBy;

    @Column(name = "SigningStatus", nullable = false, length = 20)
    @Builder.Default
    private ContractSigningStatus signingStatus = ContractSigningStatus.DA_TAI_BAN_KY;

    @Column(name = "SignedDate")
    private LocalDate signedDate;

    @Column(name = "SignedFilePath", length = 500)
    private String signedFilePath;

    @Column(name = "TerminatedDate")
    private LocalDate terminatedDate;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void defaultSigningStatus() {
        if (signingStatus == null) {
            signingStatus = ContractSigningStatus.DA_TAI_BAN_KY;
        }
    }
}
