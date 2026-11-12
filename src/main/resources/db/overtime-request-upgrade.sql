-- =============================================================================
-- Nâng cấp bảng OvertimeRequests được tạo theo phiên bản cũ (cột CreatedAssignmentId,
-- ReviewedByUserId, ReviewedAt, ReviewNote, ContractWeeklyQuota) cho khớp entity hiện tại.
-- Lỗi khi thiếu: "Invalid column name 'AssignmentId'".
-- Chỉ THÊM cột còn thiếu và cho phép Reason NULL; không xóa cột/dữ liệu cũ.
-- Chạy lại nhiều lần vẫn an toàn. Chạy: sqlcmd ... -I -i overtime-request-upgrade.sql
-- =============================================================================

IF OBJECT_ID(N'dbo.OvertimeRequests', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH(N'dbo.OvertimeRequests', N'AssignmentId') IS NULL
        ALTER TABLE dbo.OvertimeRequests ADD AssignmentId INT NULL;
    IF COL_LENGTH(N'dbo.OvertimeRequests', N'ResolvedBy') IS NULL
        ALTER TABLE dbo.OvertimeRequests ADD ResolvedBy INT NULL;
    IF COL_LENGTH(N'dbo.OvertimeRequests', N'ResolvedAt') IS NULL
        ALTER TABLE dbo.OvertimeRequests ADD ResolvedAt DATETIME2 NULL;
    IF COL_LENGTH(N'dbo.OvertimeRequests', N'RejectReason') IS NULL
        ALTER TABLE dbo.OvertimeRequests ADD RejectReason NVARCHAR(500) NULL;
    IF COL_LENGTH(N'dbo.OvertimeRequests', N'Reason') IS NOT NULL
        ALTER TABLE dbo.OvertimeRequests ALTER COLUMN Reason NVARCHAR(255) NULL;
END
GO

-- Sao chép dữ liệu từ cột cũ sang cột mới (nếu có), dùng SQL động vì cột cũ có thể không tồn tại.
IF COL_LENGTH(N'dbo.OvertimeRequests', N'CreatedAssignmentId') IS NOT NULL
    EXEC(N'UPDATE dbo.OvertimeRequests SET AssignmentId = CreatedAssignmentId
           WHERE AssignmentId IS NULL AND CreatedAssignmentId IS NOT NULL');
IF COL_LENGTH(N'dbo.OvertimeRequests', N'ReviewedByUserId') IS NOT NULL
    EXEC(N'UPDATE dbo.OvertimeRequests SET ResolvedBy = ReviewedByUserId
           WHERE ResolvedBy IS NULL AND ReviewedByUserId IS NOT NULL');
IF COL_LENGTH(N'dbo.OvertimeRequests', N'ReviewedAt') IS NOT NULL
    EXEC(N'UPDATE dbo.OvertimeRequests SET ResolvedAt = ReviewedAt
           WHERE ResolvedAt IS NULL AND ReviewedAt IS NOT NULL');
IF COL_LENGTH(N'dbo.OvertimeRequests', N'ReviewNote') IS NOT NULL
    EXEC(N'UPDATE dbo.OvertimeRequests SET RejectReason = ReviewNote
           WHERE RejectReason IS NULL AND ReviewNote IS NOT NULL AND Status = N''Rejected''');
GO

IF OBJECT_ID(N'dbo.OvertimeRequests', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.FK_OvertimeRequests_Assignment', N'F') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD CONSTRAINT FK_OvertimeRequests_Assignment
        FOREIGN KEY (AssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId) ON DELETE SET NULL;
IF OBJECT_ID(N'dbo.OvertimeRequests', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.FK_OvertimeRequests_ResolvedBy', N'F') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD CONSTRAINT FK_OvertimeRequests_ResolvedBy
        FOREIGN KEY (ResolvedBy) REFERENCES dbo.Users(UserId);
GO
