-- Bổ sung Số căn cước công dân vào hồ sơ nhân viên.
-- Cột cho phép NULL để hồ sơ cũ chưa có CCCD vẫn hoạt động.
-- Chỉ tạo unique index khi dữ liệu hiện có không bị trùng.

IF COL_LENGTH(N'dbo.Employees', N'CitizenId') IS NULL
BEGIN
    ALTER TABLE dbo.Employees ADD CitizenId NVARCHAR(12) NULL;
END;
GO

UPDATE dbo.Employees
SET CitizenId = NULL
WHERE CitizenId IS NOT NULL
  AND LTRIM(RTRIM(CitizenId)) = N'';
GO

SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_Employees_CitizenId'
      AND object_id = OBJECT_ID(N'dbo.Employees')
)
AND NOT EXISTS (
    SELECT CitizenId
    FROM dbo.Employees
    WHERE CitizenId IS NOT NULL
    GROUP BY CitizenId
    HAVING COUNT(*) > 1
)
BEGIN
    CREATE UNIQUE INDEX UX_Employees_CitizenId
        ON dbo.Employees (CitizenId)
        WHERE CitizenId IS NOT NULL;
END;
GO

-- CCCD trên ứng viên chỉ phục vụ giai đoạn đề xuất. Sau khi duyệt, giá trị được đồng bộ vào Employees.CitizenId.
IF COL_LENGTH(N'dbo.RecruitmentCandidates', N'CitizenId') IS NULL
BEGIN
    ALTER TABLE dbo.RecruitmentCandidates ADD CitizenId NVARCHAR(12) NULL;
END;
GO

UPDATE dbo.RecruitmentCandidates
SET CitizenId = NULL
WHERE CitizenId IS NOT NULL
  AND LTRIM(RTRIM(CitizenId)) = N'';
GO

SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_RecruitmentCandidates_PendingCitizenId'
      AND object_id = OBJECT_ID(N'dbo.RecruitmentCandidates')
)
AND NOT EXISTS (
    SELECT CitizenId
    FROM dbo.RecruitmentCandidates
    WHERE Status = 'Pending'
      AND CitizenId IS NOT NULL
    GROUP BY CitizenId
    HAVING COUNT(*) > 1
)
BEGIN
    CREATE UNIQUE INDEX UX_RecruitmentCandidates_PendingCitizenId
        ON dbo.RecruitmentCandidates (CitizenId)
        WHERE Status = 'Pending' AND CitizenId IS NOT NULL;
END;
GO
