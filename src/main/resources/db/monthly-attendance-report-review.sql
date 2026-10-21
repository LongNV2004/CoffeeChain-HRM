-- Trạng thái duyệt báo cáo. Chạy trên database CoffeeHRM.
SET QUOTED_IDENTIFIER ON;
IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_MonthlyAttendanceReports_Status'
      AND parent_object_id = OBJECT_ID(N'dbo.MonthlyAttendanceReports')
)
BEGIN
    ALTER TABLE dbo.MonthlyAttendanceReports DROP CONSTRAINT CK_MonthlyAttendanceReports_Status;
END;
IF OBJECT_ID(N'dbo.MonthlyAttendanceReports', N'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.check_constraints
       WHERE name = N'CK_MonthlyAttendanceReports_Status'
         AND parent_object_id = OBJECT_ID(N'dbo.MonthlyAttendanceReports')
   )
BEGIN
    ALTER TABLE dbo.MonthlyAttendanceReports ADD CONSTRAINT CK_MonthlyAttendanceReports_Status
        CHECK (Status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REVISION'));
END;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'ReviewedAt') IS NULL
BEGIN
    ALTER TABLE dbo.MonthlyAttendanceReports ADD ReviewedAt DATETIME2 NULL;
END;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'ReviewedByName') IS NULL
BEGIN
    ALTER TABLE dbo.MonthlyAttendanceReports ADD ReviewedByName NVARCHAR(100) NULL;
END;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'ReviewNote') IS NULL
BEGIN
    ALTER TABLE dbo.MonthlyAttendanceReports ADD ReviewNote NVARCHAR(500) NULL;
END;
