-- Drop attendance IP columns after the application no longer maps them.
-- Run this script manually. It does not drop tables or any other column.
SET NOCOUNT ON;

DECLARE @dropStoreDefault NVARCHAR(MAX) = N'';
SELECT @dropStoreDefault = @dropStoreDefault
        + N'ALTER TABLE dbo.Stores DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
FROM sys.default_constraints dc
JOIN sys.columns c
    ON c.object_id = dc.parent_object_id
   AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID(N'dbo.Stores')
  AND c.name = N'CurrentIp';
IF @dropStoreDefault <> N''
BEGIN
    EXEC sp_executesql @dropStoreDefault;
END;

DECLARE @dropAttendanceDefault NVARCHAR(MAX) = N'';
SELECT @dropAttendanceDefault = @dropAttendanceDefault
        + N'ALTER TABLE dbo.Attendances DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
FROM sys.default_constraints dc
JOIN sys.columns c
    ON c.object_id = dc.parent_object_id
   AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID(N'dbo.Attendances')
  AND c.name IN (N'CheckInIp', N'CheckOutIp');
IF @dropAttendanceDefault <> N''
BEGIN
    EXEC sp_executesql @dropAttendanceDefault;
END;
GO

IF COL_LENGTH(N'dbo.Stores', N'CurrentIp') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Stores DROP COLUMN CurrentIp;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckInIp') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Attendances DROP COLUMN CheckInIp;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckOutIp') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Attendances DROP COLUMN CheckOutIp;
END;
GO
