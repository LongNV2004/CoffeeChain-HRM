-- Check-in / Check-out: store network IP and one attendance row per shift.
IF COL_LENGTH(N'dbo.Stores', N'CurrentIp') IS NULL
BEGIN
    ALTER TABLE dbo.Stores ADD CurrentIp NVARCHAR(45) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'StoreId') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD StoreId INT NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'ShiftId') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD ShiftId INT NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'ScheduledShiftName') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD ScheduledShiftName NVARCHAR(50) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'ScheduledStartTime') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD ScheduledStartTime TIME NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'ScheduledEndTime') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD ScheduledEndTime TIME NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckInIp') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckInIp NVARCHAR(45) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckOutIp') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckOutIp NVARCHAR(45) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'WorkingMinutes') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD WorkingMinutes INT NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'LateMinutes') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD LateMinutes INT NOT NULL
        CONSTRAINT DF_Attendances_LateMinutes DEFAULT (0);
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'EarlyLeaveMinutes') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD EarlyLeaveMinutes INT NOT NULL
        CONSTRAINT DF_Attendances_EarlyLeaveMinutes DEFAULT (0);
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CreatedAt') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CreatedAt DATETIME2 NOT NULL
        CONSTRAINT DF_Attendances_CreatedAt DEFAULT (SYSUTCDATETIME());
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'UpdatedAt') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD UpdatedAt DATETIME2 NULL;
END;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.Attendances')
      AND name = N'CheckInTime'
      AND system_type_id = TYPE_ID(N'time')
)
BEGIN
    ALTER TABLE dbo.Attendances ALTER COLUMN CheckInTime DATETIME2 NULL;
END;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.Attendances')
      AND name = N'CheckOutTime'
      AND system_type_id = TYPE_ID(N'time')
)
BEGIN
    ALTER TABLE dbo.Attendances ALTER COLUMN CheckOutTime DATETIME2 NULL;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE name = N'UQ_Attendances_EmpDate'
      AND parent_object_id = OBJECT_ID(N'dbo.Attendances')
)
BEGIN
    ALTER TABLE dbo.Attendances DROP CONSTRAINT UQ_Attendances_EmpDate;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UQ_Attendances_EmpShiftDate'
      AND object_id = OBJECT_ID(N'dbo.Attendances')
)
BEGIN
    CREATE UNIQUE INDEX UQ_Attendances_EmpShiftDate
        ON dbo.Attendances (EmployeeId, ShiftId, WorkDate);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_Attendances_Store'
)
BEGIN
    ALTER TABLE dbo.Attendances
        ADD CONSTRAINT FK_Attendances_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_Attendances_Shift'
)
BEGIN
    ALTER TABLE dbo.Attendances
        ADD CONSTRAINT FK_Attendances_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts (ShiftId);
END;
GO
