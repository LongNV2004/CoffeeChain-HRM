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

