SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.MonthlyPayrolls', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MonthlyPayrolls (
        PayrollId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_MonthlyPayrolls PRIMARY KEY,
        StoreId INT NOT NULL,
        PayrollYear INT NOT NULL,
        PayrollMonth INT NOT NULL,
        Status VARCHAR(20) NOT NULL,
        CalculatedAt DATETIME2 NOT NULL CONSTRAINT DF_MonthlyPayrolls_CalculatedAt DEFAULT (SYSUTCDATETIME()),
        CalculatedByUserId INT NULL,
        LockedAt DATETIME2 NULL,
        LockedByUserId INT NULL,
        CONSTRAINT FK_MonthlyPayrolls_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_MonthlyPayrolls_CalculatedBy FOREIGN KEY (CalculatedByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT FK_MonthlyPayrolls_LockedBy FOREIGN KEY (LockedByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT UX_MonthlyPayrolls_StorePeriod UNIQUE (StoreId, PayrollYear, PayrollMonth),
        CONSTRAINT CK_MonthlyPayrolls_Month CHECK (PayrollMonth BETWEEN 1 AND 12),
        CONSTRAINT CK_MonthlyPayrolls_Status CHECK (Status IN ('DA_TINH', 'DA_CHOT'))
    );
END;
GO

IF OBJECT_ID(N'dbo.PayrollEmployeeLines', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PayrollEmployeeLines (
        EmployeeLineId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PayrollEmployeeLines PRIMARY KEY,
        PayrollId INT NOT NULL,
        EmployeeId INT NOT NULL,
        AssignedShiftCount INT NOT NULL,
        ValidShiftCount INT NOT NULL,
        ReviewShiftCount INT NOT NULL,
        PayableHours INT NOT NULL,
        HourlyRate DECIMAL(18,2) NULL,
        ExpectedSalary DECIMAL(18,2) NOT NULL,
        CONSTRAINT FK_PayrollEmployeeLines_Payroll FOREIGN KEY (PayrollId)
            REFERENCES dbo.MonthlyPayrolls (PayrollId) ON DELETE CASCADE,
        CONSTRAINT FK_PayrollEmployeeLines_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT UX_PayrollEmployeeLines_PeriodEmployee UNIQUE (PayrollId, EmployeeId),
        CONSTRAINT CK_PayrollEmployeeLines_Counts CHECK (
            AssignedShiftCount >= 0 AND ValidShiftCount >= 0 AND ReviewShiftCount >= 0 AND PayableHours >= 0
        ),
        CONSTRAINT CK_PayrollEmployeeLines_Salary CHECK (ExpectedSalary >= 0)
    );
END;
GO

IF OBJECT_ID(N'dbo.PayrollShiftLines', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PayrollShiftLines (
        ShiftLineId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PayrollShiftLines PRIMARY KEY,
        EmployeeLineId INT NOT NULL,
        AssignmentId INT NULL,
        AttendanceId INT NULL,
        WorkDate DATE NOT NULL,
        ShiftName NVARCHAR(50) NOT NULL,
        ScheduledStartTime TIME NULL,
        ScheduledEndTime TIME NULL,
        CheckInTime DATETIME2 NULL,
        CheckOutTime DATETIME2 NULL,
        AttendanceStatusLabel NVARCHAR(50) NULL,
        ScheduledHours INT NOT NULL,
        LateDeducted BIT NOT NULL CONSTRAINT DF_PayrollShiftLines_LateDeducted DEFAULT (0),
        EarlyDeducted BIT NOT NULL CONSTRAINT DF_PayrollShiftLines_EarlyDeducted DEFAULT (0),
        PayableHours INT NOT NULL,
        HourlyRate DECIMAL(18,2) NULL,
        ShiftSalary DECIMAL(18,2) NOT NULL,
        Payable BIT NOT NULL,
        UnpaidReason NVARCHAR(200) NULL,
        CONSTRAINT FK_PayrollShiftLines_EmployeeLine FOREIGN KEY (EmployeeLineId)
            REFERENCES dbo.PayrollEmployeeLines (EmployeeLineId) ON DELETE CASCADE,
        CONSTRAINT CK_PayrollShiftLines_Hours CHECK (ScheduledHours >= 0 AND PayableHours >= 0),
        CONSTRAINT CK_PayrollShiftLines_Salary CHECK (ShiftSalary >= 0)
    );
END;
GO
