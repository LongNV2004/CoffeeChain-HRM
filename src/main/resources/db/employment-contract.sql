-- Hợp đồng lao động. Script bổ sung, không xóa dữ liệu hiện có. Chạy khi ddl-auto=none.
-- Không chạy DROP TABLE. Trạng thái hiệu lực không lưu cột riêng: tính từ ngày hiệu lực và ngày chấm dứt.
-- Việc chặn hợp đồng trùng thời gian đang hiệu lực được kiểm tra trong ứng dụng, không thêm ràng buộc loại trừ trên bảng.
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.EmploymentContracts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.EmploymentContracts (
        ContractId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_EmploymentContracts PRIMARY KEY,
        ContractCode NVARCHAR(50) NOT NULL,
        EmployeeId INT NOT NULL,
        StartDate DATE NOT NULL,
        EndDate DATE NULL,
        Salary DECIMAL(18,2) NOT NULL,
        SigningStatus VARCHAR(20) NOT NULL CONSTRAINT DF_EmploymentContracts_SigningStatus DEFAULT ('ChuaCoBanKy'),
        SignedDate DATE NULL,
        SignedFilePath NVARCHAR(500) NULL,
        TerminatedDate DATE NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_EmploymentContracts_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_EmploymentContracts_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT CK_EmploymentContracts_DateRange CHECK (EndDate IS NULL OR EndDate >= StartDate),
        CONSTRAINT CK_EmploymentContracts_Salary CHECK (Salary > 0),
        CONSTRAINT CK_EmploymentContracts_SigningStatus CHECK (SigningStatus IN ('ChuaCoBanKy', 'DaTaiBanKy'))
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_EmploymentContracts_ContractCode'
      AND object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
BEGIN
    CREATE UNIQUE INDEX UX_EmploymentContracts_ContractCode
        ON dbo.EmploymentContracts (ContractCode);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_EmploymentContracts_EmployeeId'
      AND object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
BEGIN
    CREATE INDEX IX_EmploymentContracts_EmployeeId
        ON dbo.EmploymentContracts (EmployeeId);
END;
GO

IF COL_LENGTH(N'dbo.EmploymentContracts', N'JobTitle') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD JobTitle NVARCHAR(150) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'WorkLocation') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD WorkLocation NVARCHAR(255) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'WeeklyShiftCount') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD WeeklyShiftCount INT NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'InternalNote') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD InternalNote NVARCHAR(500) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'Allowance') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD Allowance DECIMAL(18,2) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'PaymentMethod') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD PaymentMethod VARCHAR(30) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'PayPeriod') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD PayPeriod VARCHAR(30) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'BenefitNote') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD BenefitNote NVARCHAR(500) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'OriginalFileName') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD OriginalFileName NVARCHAR(255) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'FileContentType') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD FileContentType VARCHAR(100) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'FileSizeBytes') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD FileSizeBytes BIGINT NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'CreatedByUserId') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD CreatedByUserId INT NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_EmploymentContracts_Allowance'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT CK_EmploymentContracts_Allowance CHECK (Allowance IS NULL OR Allowance >= 0);
GO
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_EmploymentContracts_WeeklyShiftCount'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT CK_EmploymentContracts_WeeklyShiftCount
        CHECK (WeeklyShiftCount IS NULL OR (WeeklyShiftCount >= 1 AND WeeklyShiftCount <= 14));
GO
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_EmploymentContracts_PaymentMethod'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT CK_EmploymentContracts_PaymentMethod
        CHECK (PaymentMethod IS NULL OR PaymentMethod IN ('TienMat', 'ChuyenKhoan'));
GO
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_EmploymentContracts_PayPeriod'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT CK_EmploymentContracts_PayPeriod
        CHECK (PayPeriod IS NULL OR PayPeriod IN ('HangThang', 'HaiLanThang'));
GO
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_EmploymentContracts_CreatedBy'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT FK_EmploymentContracts_CreatedBy
        FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (UserId);
GO
