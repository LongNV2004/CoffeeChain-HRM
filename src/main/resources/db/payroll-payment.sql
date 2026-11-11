-- Giao dịch thanh toán lương do Admin ghi nhận. Chạy thủ công trên database CoffeeHRM:
--   sqlcmd -S localhost -U sa -P sa -C -I -d CoffeeHRM -f 65001 -i payroll-payment.sql
-- Chỉ tạo bảng mới. Không sửa bảng lương hay báo cáo đã có.
-- Tiền được chuyển khoản hoặc trả tiền mặt ngoài hệ thống; bảng này chỉ lưu lịch sử xác nhận.
-- Lịch sử không bị ghi đè hay xoá: không có khoá ngoại ON DELETE CASCADE.
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.PayrollPayments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PayrollPayments (
        PaymentId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PayrollPayments PRIMARY KEY,
        ReportId INT NOT NULL,
        PayrollId INT NOT NULL,
        EmployeeId INT NOT NULL,
        PayrollYear INT NOT NULL,
        PayrollMonth INT NOT NULL,
        Amount DECIMAL(18,2) NOT NULL,
        PaidDate DATE NOT NULL,
        Method VARCHAR(20) NOT NULL,
        TransactionCode NVARCHAR(100) NULL,
        ProofFileName NVARCHAR(200) NULL,
        ProofStorageKey NVARCHAR(200) NULL,
        Note NVARCHAR(500) NULL,
        PaidByUserId INT NULL,
        PaidByName NVARCHAR(100) NULL,
        -- Một lần bấm "Xác nhận" chỉ ghi nhận một lần, kể cả khi gửi form hai lần.
        RequestKey VARCHAR(80) NOT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_PayrollPayments_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_PayrollPayments_Report FOREIGN KEY (ReportId) REFERENCES dbo.MonthlyAttendanceReports (ReportId),
        CONSTRAINT FK_PayrollPayments_Payroll FOREIGN KEY (PayrollId) REFERENCES dbo.MonthlyPayrolls (PayrollId),
        CONSTRAINT FK_PayrollPayments_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_PayrollPayments_PaidBy FOREIGN KEY (PaidByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT UX_PayrollPayments_RequestEmployee UNIQUE (RequestKey, EmployeeId),
        CONSTRAINT CK_PayrollPayments_Amount CHECK (Amount > 0),
        CONSTRAINT CK_PayrollPayments_Method CHECK (Method IN ('CHUYEN_KHOAN', 'TIEN_MAT')),
        CONSTRAINT CK_PayrollPayments_Month CHECK (PayrollMonth BETWEEN 1 AND 12)
    );
    CREATE INDEX IX_PayrollPayments_Report ON dbo.PayrollPayments (ReportId, EmployeeId);
    CREATE INDEX IX_PayrollPayments_Employee ON dbo.PayrollPayments (EmployeeId, PayrollYear, PayrollMonth);
END;
GO
