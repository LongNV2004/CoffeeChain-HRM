-- Hợp đồng lao động. Script bổ sung, không xóa dữ liệu hiện có. Chạy khi ddl-auto=none.
-- Không chạy DROP TABLE. Chưa chặn hợp đồng trùng thời gian hiệu lực vì quy tắc này chưa được chốt.
-- Trạng thái hiệu lực không lưu cột riêng: tính từ ngày hiệu lực và ngày chấm dứt.
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
