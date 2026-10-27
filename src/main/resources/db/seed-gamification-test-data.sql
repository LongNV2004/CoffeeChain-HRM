-- =============================================================================
-- Seed Data: Kiểm thử Gamification Điểm tín nhiệm & Cứu bồ (Tuần 26/10 - 01/11/2026)
-- Cửa hàng: Store 1 (The Coffee House - Cầu Giấy)
-- =============================================================================

USE CoffeeHRM;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- 1. Cập nhật nhân viên: cấp chứng chỉ & điểm tín nhiệm ban đầu
UPDATE dbo.Employees
SET HasCertificate = 1,
    CertificationStatus = 'CERTIFIED',
    ReliabilityScore = CASE 
        WHEN EmployeeId = 3 THEN 115 -- Lê Thị Thu (Top 1 Quán quân)
        WHEN EmployeeId = 2 THEN 110 -- Trần Văn Tuấn (Top 2 Á quân)
        WHEN EmployeeId = 4 THEN 95  -- Phạm Minh Đức (Top 3)
        ELSE 100 
    END
WHERE EmployeeId IN (1, 2, 3, 4);
GO

-- 2. Dọn dữ liệu phân ca và đổi ca tuần hiện tại (26/10 đến 01/11/2026)
DELETE FROM dbo.ShiftChangeRequests WHERE RequestDate >= '2026-10-20';
DELETE FROM dbo.ShiftAssignments WHERE WorkDate BETWEEN '2026-10-26' AND '2026-11-01';
GO

-- 3. Tạo phân ca đầy đủ tuần 26/10/2026 - 01/11/2026
-- Thứ Hai (2026-10-26)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 2, '2026-10-26', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(2, 3, '2026-10-26', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(3, 4, '2026-10-26', 'Assigned', 1, SYSDATETIME(), SYSDATETIME());

-- Thứ Ba (2026-10-27)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 4, '2026-10-27', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()), -- Đức (Ca Sáng - ca cần cứu bồ)
(2, 2, '2026-10-27', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()), -- Tuấn (Ca Chiều)
(3, 3, '2026-10-27', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()); -- Thu (Ca Tối)

-- Thứ Tư (2026-10-28)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 3, '2026-10-28', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(2, 4, '2026-10-28', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(3, 2, '2026-10-28', 'Assigned', 1, SYSDATETIME(), SYSDATETIME());

-- Thứ Năm (2026-10-29)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 2, '2026-10-29', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(2, 3, '2026-10-29', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(3, 4, '2026-10-29', 'Assigned', 1, SYSDATETIME(), SYSDATETIME());

-- Thứ Sáu (2026-10-30)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 4, '2026-10-30', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(2, 2, '2026-10-30', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(3, 3, '2026-10-30', 'Assigned', 1, SYSDATETIME(), SYSDATETIME());

-- Thứ Bảy (2026-10-31)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 3, '2026-10-31', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(2, 4, '2026-10-31', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(3, 2, '2026-10-31', 'Assigned', 1, SYSDATETIME(), SYSDATETIME());

-- Chủ Nhật (2026-11-01)
INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
VALUES 
(1, 2, '2026-11-01', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(2, 3, '2026-11-01', 'Assigned', 1, SYSDATETIME(), SYSDATETIME()),
(3, 4, '2026-11-01', 'Assigned', 1, SYSDATETIME(), SYSDATETIME());
GO

-- 4. Tạo ca cứu bồ khẩn cấp (<24h) của Đức (Ca Sáng 27/10)
DECLARE @DucMorningAssignmentId INT;
SELECT @DucMorningAssignmentId = AssignmentId 
FROM dbo.ShiftAssignments 
WHERE EmployeeId = 4 AND WorkDate = '2026-10-27' AND ShiftId = 1;

INSERT INTO dbo.ShiftChangeRequests (AssignmentId, EmployeeId, TargetAssignmentId, TargetEmployeeId, IsTargetAgreed, Reason, Status, RequestDate)
VALUES (@DucMorningAssignmentId, 4, NULL, NULL, NULL, N'Đức bị sốt cấp tính, nhờ anh em cứu ca sáng mai giúp với ạ!', 'Pending', SYSDATETIME());
GO

-- 5. Seed lịch sử biến động điểm tín nhiệm tháng 10/2026
DELETE FROM dbo.ReliabilityScoreLogs WHERE StoreId = 1;

-- Lê Thị Thu (Emp 3) - 3 lần cứu bồ -> 115 điểm
INSERT INTO dbo.ReliabilityScoreLogs (EmployeeId, StoreId, ActionType, PointsDelta, ScoreAfter, Reason, ReferenceType, ReferenceId, CreatedAt)
VALUES 
(3, 1, 'RESCUE_SHIFT_24H', 5, 105, N'Cứu bồ ca khẩn cấp cho Đức (Ca Chiều 10/10)', 'SHIFT_CHANGE', 101, '2026-10-10 14:30:00'),
(3, 1, 'RESCUE_SHIFT_24H', 5, 110, N'Cứu bồ ca khẩn cấp cho Tuấn (Ca Tối 16/10)', 'SHIFT_CHANGE', 102, '2026-10-16 19:00:00'),
(3, 1, 'RESCUE_SHIFT_24H', 5, 115, N'Cứu bồ ca khẩn cấp cho Đức (Ca Sáng 22/10)', 'SHIFT_CHANGE', 103, '2026-10-22 08:30:00');

-- Trần Văn Tuấn (Emp 2) - 2 lần cứu bồ -> 110 điểm
INSERT INTO dbo.ReliabilityScoreLogs (EmployeeId, StoreId, ActionType, PointsDelta, ScoreAfter, Reason, ReferenceType, ReferenceId, CreatedAt)
VALUES 
(2, 1, 'RESCUE_SHIFT_24H', 5, 105, N'Cứu bồ ca khẩn cấp cho Thu (Ca Sáng 12/10)', 'SHIFT_CHANGE', 104, '2026-10-12 09:15:00'),
(2, 1, 'RESCUE_SHIFT_24H', 5, 110, N'Cứu bồ ca khẩn cấp cho Đức (Ca Chiều 18/10)', 'SHIFT_CHANGE', 105, '2026-10-18 15:45:00');

-- Phạm Minh Đức (Emp 4) - 1 lần hủy sát giờ (-10) + 1 lần cứu bồ (+5) -> 95 điểm
INSERT INTO dbo.ReliabilityScoreLogs (EmployeeId, StoreId, ActionType, PointsDelta, ScoreAfter, Reason, ReferenceType, ReferenceId, CreatedAt)
VALUES 
(4, 1, 'LATE_SHIFT_CANCEL', -10, 90, N'Xin hủy ca sát giờ (<24h) ngày 14/10', 'SHIFT_CHANGE', 106, '2026-10-14 06:30:00'),
(4, 1, 'RESCUE_SHIFT_24H', 5, 95, N'Cứu bồ ca khẩn cấp cho Tuấn (Ca Tối 20/10)', 'SHIFT_CHANGE', 107, '2026-10-20 18:00:00');
GO

PRINT N'Đã hoàn tất nạp seed test data cho Gamification Điểm tín nhiệm & Cứu bồ!';
GO
