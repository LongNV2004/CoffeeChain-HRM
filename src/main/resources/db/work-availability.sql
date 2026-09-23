IF OBJECT_ID(N'dbo.WorkAvailabilities', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.WorkAvailabilities (
        AvailabilityId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_WorkAvailabilities PRIMARY KEY,
        EmployeeId INT NOT NULL,
        ShiftId INT NOT NULL,
        WorkDate DATE NOT NULL,
        Note NVARCHAR(255) NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_WorkAvailabilities_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT UQ_WorkAvailabilities_Employee_Shift_Date UNIQUE (EmployeeId, ShiftId, WorkDate),
        CONSTRAINT FK_WorkAvailabilities_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_WorkAvailabilities_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts (ShiftId)
    );
END;
GO
