IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.Employees') AND name = 'HasCertificate'
)
BEGIN
    ALTER TABLE dbo.Employees
    ADD HasCertificate BIT NOT NULL CONSTRAINT DF_Employees_HasCertificate DEFAULT (0);
END;
GO

-- Một Employee chỉ được làm Manager của tối đa 01 Store.
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.Stores') AND name = 'UX_Stores_ManagerId'
)
BEGIN
    CREATE UNIQUE INDEX UX_Stores_ManagerId
        ON dbo.Stores (ManagerId)
        WHERE ManagerId IS NOT NULL;
END;
GO
