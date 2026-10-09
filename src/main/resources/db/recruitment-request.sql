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

SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.RecruitmentCandidates', N'U') IS NULL
BEGIN
    DELETE FROM dbo.RecruitmentRequests
    WHERE FullName IS NULL
       OR Email IS NULL
       OR Phone IS NULL
       OR RequestedByEmployeeId IS NULL;
END;
GO

IF COL_LENGTH('dbo.RecruitmentRequests', 'RequestedNumber') IS NOT NULL
    ALTER TABLE dbo.RecruitmentRequests DROP COLUMN RequestedNumber;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Reason') IS NOT NULL
    ALTER TABLE dbo.RecruitmentRequests DROP COLUMN Reason;
GO

IF OBJECT_ID(N'dbo.RecruitmentCandidates', N'U') IS NULL
BEGIN
    ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN RequestedByEmployeeId INT NOT NULL;
    ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN FullName NVARCHAR(100) NOT NULL;
    ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Email NVARCHAR(100) NOT NULL;
    ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Phone NVARCHAR(15) NOT NULL;
END;
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

IF COL_LENGTH('dbo.RecruitmentRequests', 'DateOfBirth') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD DateOfBirth DATE NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Gender') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD Gender VARCHAR(20) NULL;
GO

SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_RecruitmentRequests_PendingPhone'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentRequests')
)
BEGIN
    CREATE UNIQUE INDEX UX_RecruitmentRequests_PendingPhone
        ON dbo.RecruitmentRequests (Phone)
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

-- Một đề xuất chứa nhiều nhân viên. Dữ liệu cũ được sao chép sang RecruitmentCandidates, cột cũ trên RecruitmentRequests được giữ lại.
IF OBJECT_ID(N'dbo.RecruitmentCandidates', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RecruitmentCandidates (
        CandidateId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_RecruitmentCandidates PRIMARY KEY,
        RequestId INT NOT NULL,
        SortOrder INT NOT NULL CONSTRAINT DF_RecruitmentCandidates_SortOrder DEFAULT (1),
        FullName NVARCHAR(100) NOT NULL,
        DateOfBirth DATE NULL,
        Gender VARCHAR(20) NULL,
        Email NVARCHAR(100) NOT NULL,
        Phone NVARCHAR(15) NOT NULL,
        Address NVARCHAR(255) NULL,
        Status VARCHAR(20) NOT NULL CONSTRAINT DF_RecruitmentCandidates_Status DEFAULT ('Pending'),
        RejectReason NVARCHAR(500) NULL,
        ReviewedAt DATETIME2 NULL,
        ReviewedByUserId INT NULL,
        CreatedEmployeeId INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_RecruitmentCandidates_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_RecruitmentCandidates_Request FOREIGN KEY (RequestId) REFERENCES dbo.RecruitmentRequests (RequestId),
        CONSTRAINT FK_RecruitmentCandidates_ReviewedBy FOREIGN KEY (ReviewedByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT FK_RecruitmentCandidates_CreatedEmployee FOREIGN KEY (CreatedEmployeeId) REFERENCES dbo.Employees (EmployeeId)
    );
END;
GO

IF COL_LENGTH('dbo.RecruitmentRequests', 'DateOfBirth') IS NOT NULL
   AND COL_LENGTH('dbo.RecruitmentRequests', 'Gender') IS NOT NULL
BEGIN
    INSERT INTO dbo.RecruitmentCandidates (
        RequestId, SortOrder, FullName, DateOfBirth, Gender, Email, Phone, Address,
        Status, RejectReason, ReviewedAt, ReviewedByUserId, CreatedEmployeeId, CreatedAt
    )
    SELECT
        r.RequestId,
        1,
        r.FullName,
        r.DateOfBirth,
        r.Gender,
        r.Email,
        r.Phone,
        r.Address,
        CASE WHEN r.Status IN ('Approved', 'Rejected', 'Pending') THEN r.Status ELSE 'Pending' END,
        r.RejectReason,
        r.ReviewedAt,
        r.ReviewedByUserId,
        r.CreatedEmployeeId,
        r.CreatedAt
    FROM dbo.RecruitmentRequests r
    WHERE r.FullName IS NOT NULL
      AND r.Email IS NOT NULL
      AND r.Phone IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM dbo.RecruitmentCandidates c WHERE c.RequestId = r.RequestId
      );
END;
GO

IF COL_LENGTH('dbo.RecruitmentRequests', 'Title') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD Title NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.RecruitmentRequests', 'Note') IS NULL
    ALTER TABLE dbo.RecruitmentRequests ADD Note NVARCHAR(500) NULL;
GO

UPDATE dbo.RecruitmentRequests
SET Title = LEFT(FullName, 150)
WHERE Title IS NULL AND FullName IS NOT NULL;
UPDATE dbo.RecruitmentRequests
SET Title = N'Đề xuất tuyển nhân sự'
WHERE Title IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM dbo.RecruitmentRequests WHERE Title IS NULL)
    ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Title NVARCHAR(150) NOT NULL;
GO

UPDATE dbo.RecruitmentRequests
SET Status = 'Completed'
WHERE Status IN ('Approved', 'Rejected');
GO

SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_RecruitmentCandidates_PendingEmail'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentCandidates')
)
BEGIN
    CREATE UNIQUE INDEX UX_RecruitmentCandidates_PendingEmail
        ON dbo.RecruitmentCandidates (Email)
        WHERE Status = 'Pending';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_RecruitmentCandidates_PendingPhone'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentCandidates')
)
BEGIN
    CREATE UNIQUE INDEX UX_RecruitmentCandidates_PendingPhone
        ON dbo.RecruitmentCandidates (Phone)
        WHERE Status = 'Pending';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_RecruitmentCandidates_Request_Status'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentCandidates')
)
BEGIN
    CREATE INDEX IX_RecruitmentCandidates_Request_Status
        ON dbo.RecruitmentCandidates (RequestId, Status, SortOrder);
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_RecruitmentRequests_PendingEmail'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentRequests')
)
    DROP INDEX UX_RecruitmentRequests_PendingEmail ON dbo.RecruitmentRequests;
GO

IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_RecruitmentRequests_PendingPhone'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentRequests')
)
    DROP INDEX UX_RecruitmentRequests_PendingPhone ON dbo.RecruitmentRequests;
GO

ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN FullName NVARCHAR(100) NULL;
ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Email NVARCHAR(100) NULL;
ALTER TABLE dbo.RecruitmentRequests ALTER COLUMN Phone NVARCHAR(15) NULL;
GO
