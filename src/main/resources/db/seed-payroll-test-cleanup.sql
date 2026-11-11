-- Xoá toàn bộ dữ liệu do seed-payroll-test-data.sql tạo ra, kể cả những gì đã phát sinh khi thử
-- (bảng lương, báo cáo, giao dịch thanh toán, thông báo, đăng ký ca...). Không đụng tới cửa hàng thật.
--   sqlcmd -S localhost -U sa -P sa -C -I -d CoffeeHRM -f 65001 -i seed-payroll-test-cleanup.sql
-- File minh chứng thanh toán đã tải lên nằm trong storage/payroll-proofs, xoá tay nếu cần.
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;

DECLARE @StoreId INT = (SELECT StoreId FROM dbo.Stores WHERE StoreName = N'CoffeeHRM Test - Thanh toán lương');
IF @StoreId IS NULL
BEGIN
    PRINT N'Không có dữ liệu test để xoá.';
    RETURN;
END;

DECLARE @Employees TABLE (Id INT PRIMARY KEY);
INSERT INTO @Employees SELECT EmployeeId FROM dbo.Employees WHERE StoreId = @StoreId;
DECLARE @Users TABLE (Id INT PRIMARY KEY);
INSERT INTO @Users SELECT UserId FROM dbo.Users WHERE EmployeeId IN (SELECT Id FROM @Employees);
DECLARE @Shifts TABLE (Id INT PRIMARY KEY);
INSERT INTO @Shifts SELECT ShiftId FROM dbo.Shifts WHERE StoreId = @StoreId;
DECLARE @Assignments TABLE (Id INT PRIMARY KEY);
INSERT INTO @Assignments SELECT AssignmentId FROM dbo.ShiftAssignments
WHERE EmployeeId IN (SELECT Id FROM @Employees) OR ShiftId IN (SELECT Id FROM @Shifts);

BEGIN TRANSACTION;

-- Lương, báo cáo và thanh toán
DELETE FROM dbo.PayrollPayments
WHERE EmployeeId IN (SELECT Id FROM @Employees)
   OR ReportId IN (SELECT ReportId FROM dbo.MonthlyAttendanceReports WHERE StoreId = @StoreId)
   OR PayrollId IN (SELECT PayrollId FROM dbo.MonthlyPayrolls WHERE StoreId = @StoreId);
DELETE FROM dbo.MonthlyAttendanceReportVersions
WHERE ReportId IN (SELECT ReportId FROM dbo.MonthlyAttendanceReports WHERE StoreId = @StoreId);
DELETE FROM dbo.MonthlyAttendanceReports WHERE StoreId = @StoreId;
DELETE FROM dbo.PayrollEmployeeLines WHERE EmployeeId IN (SELECT Id FROM @Employees); -- dòng ca xoá theo (CASCADE)
DELETE FROM dbo.MonthlyPayrolls WHERE StoreId = @StoreId;

-- Thông báo của tài khoản test và thông báo về báo cáo/thanh toán của cửa hàng test
DELETE FROM dbo.Notifications WHERE UserId IN (SELECT Id FROM @Users);

-- Lịch, chấm công và các yêu cầu liên quan
DELETE FROM dbo.LeaveRequestSlots WHERE ShiftAssignmentId IN (SELECT Id FROM @Assignments);
DELETE FROM dbo.ShiftChangeRequests
WHERE EmployeeId IN (SELECT Id FROM @Employees) OR TargetEmployeeId IN (SELECT Id FROM @Employees)
   OR AssignmentId IN (SELECT Id FROM @Assignments) OR TargetAssignmentId IN (SELECT Id FROM @Assignments);
DELETE FROM dbo.ShiftCoverInvitations WHERE EmployeeId IN (SELECT Id FROM @Employees);
UPDATE dbo.ShiftAssignments SET ReplacesAssignmentId = NULL WHERE ReplacesAssignmentId IN (SELECT Id FROM @Assignments);
DELETE FROM dbo.ShiftAssignments WHERE AssignmentId IN (SELECT Id FROM @Assignments);
DELETE FROM dbo.Attendances WHERE EmployeeId IN (SELECT Id FROM @Employees) OR StoreId = @StoreId;
DELETE FROM dbo.TrainingAttendances WHERE EmployeeId IN (SELECT Id FROM @Employees) OR StoreId = @StoreId;
DELETE FROM dbo.WorkAvailabilities WHERE EmployeeId IN (SELECT Id FROM @Employees) OR ShiftId IN (SELECT Id FROM @Shifts);
DELETE FROM dbo.ShiftSlotLimits WHERE ShiftId IN (SELECT Id FROM @Shifts);
DELETE FROM dbo.LeaveRequests WHERE EmployeeId IN (SELECT Id FROM @Employees);
DELETE FROM dbo.LeaveBalances WHERE EmployeeId IN (SELECT Id FROM @Employees);
DELETE FROM dbo.ReliabilityScoreLogs WHERE EmployeeId IN (SELECT Id FROM @Employees) OR StoreId = @StoreId;
DELETE FROM dbo.MonthlyEmployeeAwards WHERE EmployeeId IN (SELECT Id FROM @Employees) OR StoreId = @StoreId;
DELETE FROM dbo.TrainingClassEnrollments WHERE EmployeeId IN (SELECT Id FROM @Employees);

-- Hợp đồng, tài khoản, nhân viên, ca, cửa hàng
DELETE FROM dbo.EmploymentContracts WHERE EmployeeId IN (SELECT Id FROM @Employees);
UPDATE dbo.Stores SET ManagerId = NULL WHERE StoreId = @StoreId;
DELETE FROM dbo.Users WHERE UserId IN (SELECT Id FROM @Users);
DELETE FROM dbo.Employees WHERE EmployeeId IN (SELECT Id FROM @Employees);
DELETE FROM dbo.Shifts WHERE StoreId = @StoreId;
DELETE FROM dbo.Stores WHERE StoreId = @StoreId;

COMMIT TRANSACTION;
PRINT N'Đã xoá dữ liệu test của cửa hàng #' + CAST(@StoreId AS NVARCHAR(10)) + N'.';
