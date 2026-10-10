-- Add store GPS and attendance coordinates. Run this script manually.
-- Does not drop tables, rows, or existing columns.
IF COL_LENGTH(N'dbo.Stores', N'Latitude') IS NULL
BEGIN
    ALTER TABLE dbo.Stores ADD Latitude DECIMAL(10, 7) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Stores', N'Longitude') IS NULL
BEGIN
    ALTER TABLE dbo.Stores ADD Longitude DECIMAL(10, 7) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Stores', N'LocationUpdatedAt') IS NULL
BEGIN
    ALTER TABLE dbo.Stores ADD LocationUpdatedAt DATETIME2 NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckInLatitude') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckInLatitude DECIMAL(10, 7) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckInLongitude') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckInLongitude DECIMAL(10, 7) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckOutLatitude') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckOutLatitude DECIMAL(10, 7) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckOutLongitude') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckOutLongitude DECIMAL(10, 7) NULL;
END;
GO

-- One manager attendance row per work day when the row has no shift.
IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UQ_Attendances_ManagerDay'
      AND object_id = OBJECT_ID(N'dbo.Attendances')
)
BEGIN
    CREATE UNIQUE INDEX UQ_Attendances_ManagerDay
        ON dbo.Attendances (EmployeeId, WorkDate)
        WHERE ShiftId IS NULL;
END;
GO
