-- Ngừng gia hạn: ghi nhận quyết định, không xóa hợp đồng và không cắt ngày làm trước ngày kết thúc.
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH(N'dbo.EmploymentContracts', N'RenewalDeclinedAt') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD RenewalDeclinedAt DATETIME2 NULL;
GO

IF COL_LENGTH(N'dbo.EmploymentContracts', N'RenewalDeclineReason') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD RenewalDeclineReason NVARCHAR(500) NULL;
GO
