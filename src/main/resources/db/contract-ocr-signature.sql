-- Lưu kết quả kiểm tra chữ ký gắn với đúng file hợp đồng đã tạo.
-- Script bổ sung cột, không xóa dữ liệu. Chạy khi ddl-auto=none.
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH(N'dbo.EmploymentContracts', N'SignatureFileHash') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD SignatureFileHash VARCHAR(64) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'SignatureCheckedAt') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD SignatureCheckedAt DATETIME2 NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'SignatureCheckResult') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD SignatureCheckResult NVARCHAR(40) NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'SignatureCheckedByUserId') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD SignatureCheckedByUserId INT NULL;
GO
