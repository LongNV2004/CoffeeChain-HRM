-- Ngày sinh trong hồ sơ nhân viên.
-- Trước đây ngày sinh chỉ lưu ở hồ sơ ứng viên tuyển dụng (RecruitmentCandidates).
-- Script thêm cột Employees.DateOfBirth và lấy lại ngày sinh từ hồ sơ tuyển dụng đã liên kết.
-- Chạy lại nhiều lần an toàn. Không xóa dữ liệu.
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH(N'dbo.Employees', N'DateOfBirth') IS NULL
    ALTER TABLE dbo.Employees ADD DateOfBirth DATE NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_Employees_DateOfBirth' AND parent_object_id = OBJECT_ID(N'dbo.Employees')
)
    ALTER TABLE dbo.Employees WITH CHECK
        ADD CONSTRAINT CK_Employees_DateOfBirth CHECK (DateOfBirth IS NULL OR DateOfBirth >= '1900-01-01');
GO

-- Lấy ngày sinh từ hồ sơ tuyển dụng gần nhất (tạo mới hoặc tuyển dụng lại) của nhân viên.
UPDATE e
SET e.DateOfBirth = src.DateOfBirth
FROM dbo.Employees e
CROSS APPLY (
    SELECT TOP 1 c.DateOfBirth
    FROM dbo.RecruitmentCandidates c
    WHERE (c.CreatedEmployeeId = e.EmployeeId OR c.RehireEmployeeId = e.EmployeeId)
      AND c.DateOfBirth IS NOT NULL
    ORDER BY c.CandidateId DESC
) src
WHERE e.DateOfBirth IS NULL;

PRINT CONCAT(N'Đã điền ngày sinh cho ', @@ROWCOUNT, N' nhân viên từ hồ sơ tuyển dụng.');
GO
