IF COL_LENGTH('dbo.Users', 'Username') IS NOT NULL
BEGIN
    DECLARE @usernameLength INT;
    SELECT @usernameLength = c.max_length
    FROM sys.columns c
    WHERE c.object_id = OBJECT_ID(N'dbo.Users') AND c.name = 'Username';

    IF @usernameLength IS NOT NULL AND @usernameLength < 200
        ALTER TABLE dbo.Users ALTER COLUMN Username NVARCHAR(100) NOT NULL;
END;
GO

IF OBJECT_ID(N'dbo.RecruitmentRequests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RecruitmentRequests (
        RequestId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_RecruitmentRequests PRIMARY KEY,
        StoreId INT NOT NULL,
        RequestedByEmployeeId INT NOT NULL,
        FullName NVARCHAR(100) NOT NULL,
        Email NVARCHAR(100) NOT NULL,
        Phone NVARCHAR(15) NOT NULL,
        Address NVARCHAR(255) NULL,
        Status VARCHAR(20) NOT NULL CONSTRAINT DF_RecruitmentRequests_Status DEFAULT ('Pending'),
        RejectReason NVARCHAR(500) NULL,
        ReviewedAt DATETIME2 NULL,
        ReviewedByUserId INT NULL,
        CreatedEmployeeId INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_RecruitmentRequests_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_RecruitmentRequests_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_RecruitmentRequests_RequestedBy FOREIGN KEY (RequestedByEmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_RecruitmentRequests_ReviewedBy FOREIGN KEY (ReviewedByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT FK_RecruitmentRequests_CreatedEmployee FOREIGN KEY (CreatedEmployeeId) REFERENCES dbo.Employees (EmployeeId)
    );
END;
GO

IF COL_LENGTH('dbo.RecruitmentRequests', 'RequestedByEmployeeId') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD RequestedByEmployeeId INT NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'FullName') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD FullName NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Email') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD Email NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Phone') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD Phone NVARCHAR(15) NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Address') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD Address NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'RejectReason') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD RejectReason NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'ReviewedAt') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD ReviewedAt DATETIME2 NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'ReviewedByUserId') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD ReviewedByUserId INT NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'CreatedEmployeeId') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD CreatedEmployeeId INT NULL;
GO

DELETE FROM dbo.RecruitmentRequests
WHERE FullName IS NULL
   OR Email IS NULL
   OR Phone IS NULL
   OR RequestedByEmployeeId IS NULL;
GO

IF COL_LENGTH('dbo.RecruitmentRequests', 'RequestedNumber') IS NOT NULL
    ALTER TABLE dbo.RecruitmentRequests DROP COLUMN RequestedNumber;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Reason') IS NOT NULL
    ALTER TABLE dbo.RecruitmentRequests DROP COLUMN Reason;
GO

ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN RequestedByEmployeeId INT NOT NULL;
ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN FullName NVARCHAR(100) NOT NULL;
ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Email NVARCHAR(100) NOT NULL;
ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Phone NVARCHAR(15) NOT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_RecruitmentRequests_RequestedBy')
    ALTER TABLE dbo.RecruitmentRequests
        ADD CONSTRAINT FK_RecruitmentRequests_RequestedBy
        FOREIGN KEY (RequestedByEmployeeId) REFERENCES dbo.Employees (EmployeeId);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_RecruitmentRequests_ReviewedBy')
    ALTER TABLE dbo.RecruitmentRequests
        ADD CONSTRAINT FK_RecruitmentRequests_ReviewedBy
        FOREIGN KEY (ReviewedByUserId) REFERENCES dbo.Users (UserId);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_RecruitmentRequests_CreatedEmployee')
    ALTER TABLE dbo.RecruitmentRequests
        ADD CONSTRAINT FK_RecruitmentRequests_CreatedEmployee
        FOREIGN KEY (CreatedEmployeeId) REFERENCES dbo.Employees (EmployeeId);
GO

SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_RecruitmentRequests_PendingEmail'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentRequests')
)
BEGIN
    CREATE UNIQUE INDEX UX_RecruitmentRequests_PendingEmail
        ON dbo.RecruitmentRequests (Email)
        WHERE Status = 'Pending';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_RecruitmentRequests_Store_Manager_Status'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentRequests')
)
BEGIN
    CREATE INDEX IX_RecruitmentRequests_Store_Manager_Status
        ON dbo.RecruitmentRequests (StoreId, RequestedByEmployeeId, Status, CreatedAt);
END;
GO
