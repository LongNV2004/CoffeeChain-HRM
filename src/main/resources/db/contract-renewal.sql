-- Gia hạn hợp đồng: liên kết hợp đồng mới với hợp đồng nguồn. Không xóa dữ liệu cũ.
-- Một hợp đồng nguồn chỉ có một hợp đồng gia hạn trực tiếp.
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH(N'dbo.EmploymentContracts', N'PreviousContractId') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD PreviousContractId INT NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'RenewedByUserId') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD RenewedByUserId INT NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'RenewedAt') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD RenewedAt DATETIME2 NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'ActivatedAt') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD ActivatedAt DATETIME2 NULL;
GO
IF COL_LENGTH(N'dbo.EmploymentContracts', N'SupersededDate') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD SupersededDate DATE NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_EmploymentContracts_PreviousContract'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT FK_EmploymentContracts_PreviousContract
        FOREIGN KEY (PreviousContractId) REFERENCES dbo.EmploymentContracts (ContractId);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_EmploymentContracts_RenewedBy'
      AND parent_object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    ALTER TABLE dbo.EmploymentContracts
        ADD CONSTRAINT FK_EmploymentContracts_RenewedBy
        FOREIGN KEY (RenewedByUserId) REFERENCES dbo.Users (UserId);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_EmploymentContracts_PreviousContractId'
      AND object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    CREATE UNIQUE INDEX UX_EmploymentContracts_PreviousContractId
        ON dbo.EmploymentContracts (PreviousContractId)
        WHERE PreviousContractId IS NOT NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_EmploymentContracts_Activation'
      AND object_id = OBJECT_ID(N'dbo.EmploymentContracts')
)
    CREATE INDEX IX_EmploymentContracts_Activation
        ON dbo.EmploymentContracts (StartDate, ActivatedAt)
        WHERE PreviousContractId IS NOT NULL AND ActivatedAt IS NULL;
GO
