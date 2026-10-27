-- Chứng chỉ quản lý, tách khỏi chứng chỉ nhân viên.
-- Không xóa dữ liệu. Có thể chạy lại.

IF COL_LENGTH('dbo.Employees', 'ManagementCertified') IS NULL
BEGIN
    ALTER TABLE dbo.Employees
        ADD ManagementCertified BIT NOT NULL
            CONSTRAINT DF_Employees_ManagementCertified DEFAULT (0);
END;

IF COL_LENGTH('dbo.Employees', 'ManagementCertifiedAt') IS NULL
BEGIN
    ALTER TABLE dbo.Employees ADD ManagementCertifiedAt DATETIME2 NULL;
END;

IF COL_LENGTH('dbo.Employees', 'ManagementCertifiedBy') IS NULL
BEGIN
    ALTER TABLE dbo.Employees ADD ManagementCertifiedBy INT NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Employees_ManagementCertifiedBy'
)
AND COL_LENGTH('dbo.Employees', 'ManagementCertifiedBy') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Employees
        ADD CONSTRAINT FK_Employees_ManagementCertifiedBy
            FOREIGN KEY (ManagementCertifiedBy) REFERENCES dbo.Users (UserId);
END;

IF EXISTS (
    SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TrainingClasses_TrainingType'
)
BEGIN
    ALTER TABLE dbo.TrainingClasses DROP CONSTRAINT CK_TrainingClasses_TrainingType;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TrainingClasses_TrainingType'
)
BEGIN
    ALTER TABLE dbo.TrainingClasses
        ADD CONSTRAINT CK_TrainingClasses_TrainingType
            CHECK (TrainingType IN ('STORE_TRAINING', 'CENTRALIZED_TRAINING', 'MANAGEMENT_TRAINING'));
END;

IF COL_LENGTH('dbo.TrainingSkills', 'CertificateKind') IS NULL
BEGIN
    ALTER TABLE dbo.TrainingSkills
        ADD CertificateKind VARCHAR(20) NOT NULL
            CONSTRAINT DF_TrainingSkills_CertificateKind DEFAULT ('STAFF');
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TrainingSkills_CertificateKind'
)
BEGIN
    ALTER TABLE dbo.TrainingSkills
        ADD CONSTRAINT CK_TrainingSkills_CertificateKind
            CHECK (CertificateKind IN ('STAFF', 'MANAGEMENT'));
END;
