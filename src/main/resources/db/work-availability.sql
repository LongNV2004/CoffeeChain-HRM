SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.WorkAvailabilities', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.WorkAvailabilities (
        AvailabilityId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_WorkAvailabilities PRIMARY KEY,
        EmployeeId INT NOT NULL,
        ShiftId INT NOT NULL,
        WorkDate DATE NOT NULL,
        Note NVARCHAR(255) NULL,
        Status NVARCHAR(20) NOT NULL CONSTRAINT DF_WorkAvailabilities_Status DEFAULT (N'Pending'),
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_WorkAvailabilities_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT UQ_WorkAvailabilities_Employee_Shift_Date UNIQUE (EmployeeId, ShiftId, WorkDate),
        CONSTRAINT FK_WorkAvailabilities_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_WorkAvailabilities_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts (ShiftId),
        CONSTRAINT CK_WorkAvailabilities_Status CHECK (Status IN (N'Pending', N'Approved', N'Rejected'))
    );
END;
GO
IF COL_LENGTH(N'dbo.WorkAvailabilities', N'Status') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities
        ADD Status NVARCHAR(20) NOT NULL
            CONSTRAINT DF_WorkAvailabilities_Status DEFAULT (N'Pending');
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'CK_WorkAvailabilities_Status'
      AND parent_object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    ALTER TABLE dbo.WorkAvailabilities
        ADD CONSTRAINT CK_WorkAvailabilities_Status
            CHECK (Status IN (N'Pending', N'Approved', N'Rejected'));
END;
GO

IF COL_LENGTH(N'dbo.WorkAvailabilities', N'DayOfWeek') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ADD DayOfWeek TINYINT NULL;
END;
GO

IF COL_LENGTH(N'dbo.WorkAvailabilities', N'ValidFrom') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ADD ValidFrom DATE NULL;
END;
GO

IF COL_LENGTH(N'dbo.WorkAvailabilities', N'ValidTo') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ADD ValidTo DATE NULL;
END;
GO

IF COL_LENGTH(N'dbo.WorkAvailabilities', N'DurationCode') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ADD DurationCode NVARCHAR(20) NULL;
END;
GO

IF COL_LENGTH(N'dbo.WorkAvailabilities', N'RegistrationKey') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ADD RegistrationKey NVARCHAR(36) NULL;
END;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
      AND name = N'WorkDate'
      AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ALTER COLUMN WorkDate DATE NULL;
END;
GO

IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE name = N'UQ_WorkAvailabilities_Employee_Shift_Date'
      AND parent_object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    ALTER TABLE dbo.WorkAvailabilities DROP CONSTRAINT UQ_WorkAvailabilities_Employee_Shift_Date;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_WorkAvailabilities_Legacy_Employee_Shift_Date'
      AND object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    CREATE UNIQUE INDEX UX_WorkAvailabilities_Legacy_Employee_Shift_Date
        ON dbo.WorkAvailabilities (EmployeeId, ShiftId, WorkDate)
        WHERE DayOfWeek IS NULL AND WorkDate IS NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_WorkAvailabilities_Pattern'
      AND object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    CREATE UNIQUE INDEX UX_WorkAvailabilities_Pattern
        ON dbo.WorkAvailabilities (EmployeeId, ShiftId, DayOfWeek, ValidFrom)
        WHERE DayOfWeek IS NOT NULL AND Status IN (N'Pending', N'Approved');
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_WorkAvailabilities_RegistrationKey'
      AND object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    CREATE INDEX IX_WorkAvailabilities_RegistrationKey
        ON dbo.WorkAvailabilities (RegistrationKey);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'CK_WorkAvailabilities_DayOfWeek'
      AND parent_object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    ALTER TABLE dbo.WorkAvailabilities
        ADD CONSTRAINT CK_WorkAvailabilities_DayOfWeek
            CHECK (DayOfWeek IS NULL OR DayOfWeek BETWEEN 1 AND 7);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'CK_WorkAvailabilities_Period'
      AND parent_object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    ALTER TABLE dbo.WorkAvailabilities
        ADD CONSTRAINT CK_WorkAvailabilities_Period
            CHECK (
                (ValidFrom IS NULL AND ValidTo IS NULL)
                OR (ValidFrom IS NOT NULL AND ValidTo IS NOT NULL AND ValidTo >= ValidFrom)
            );
END;
GO

