-- Manager đã nghỉ việc (chấm dứt hợp đồng) không còn giữ chức vụ Manager.
-- Trước đây khi Admin chấm dứt hợp đồng, tài khoản bị khóa nhưng vẫn mang role Manager,
-- khiến cửa hàng không bổ nhiệm được Staff thay thế. Script chuyển các tài khoản đó về Staff.
-- Chạy lại nhiều lần an toàn. Không xóa dữ liệu.
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @staffRoleId INT = (SELECT RoleId FROM dbo.Roles WHERE RoleName = N'Staff');
DECLARE @managerRoleId INT = (SELECT RoleId FROM dbo.Roles WHERE RoleName = N'Manager');

IF @staffRoleId IS NULL OR @managerRoleId IS NULL
    THROW 50001, N'Thiếu role Staff hoặc Manager trong bảng Roles.', 1;

UPDATE u
SET u.RoleId = @staffRoleId
FROM dbo.Users u
JOIN dbo.Employees e ON e.EmployeeId = u.EmployeeId
WHERE u.RoleId = @managerRoleId
  AND e.Status = N'Terminated'
  AND NOT EXISTS (SELECT 1 FROM dbo.Stores s WHERE s.ManagerId = e.EmployeeId);

PRINT CONCAT(N'Đã chuyển ', @@ROWCOUNT, N' tài khoản Manager đã nghỉ việc về Staff.');
GO
