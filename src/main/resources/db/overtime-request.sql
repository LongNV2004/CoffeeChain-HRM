-- =============================================================================
-- Đơn tăng ca (OvertimeRequests)
-- Nhân viên gửi đơn làm thêm một ca vào một ngày cụ thể. Bình thường mỗi ngày tối đa 2 ca;
-- khi có đơn tăng ca được Quản lý duyệt, nhân viên được làm tối đa 3 ca/ngày.
-- Khi duyệt, hệ thống tạo ShiftAssignment (đã công bố) và lưu vào AssignmentId.
-- Status: Pending / Approved / Rejected / Cancelled (ApprovalStatus).
-- Chỉ tạo mới, không sửa/xóa dữ liệu hiện có. Chạy: sqlcmd ... -I -i overtime-request.sql
-- =============================================================================

IF OBJECT_ID(N'dbo.OvertimeRequests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.OvertimeRequests (
        OvertimeRequestId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        EmployeeId INT NOT NULL,
        StoreId INT NOT NULL,
        ShiftId INT NOT NULL,
        WorkDate DATE NOT NULL,
        Reason NVARCHAR(255) NULL,
        Status NVARCHAR(20) NOT NULL CONSTRAINT DF_OvertimeRequests_Status DEFAULT (N'Pending'),
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_OvertimeRequests_CreatedAt DEFAULT (SYSDATETIME()),
        ResolvedBy INT NULL,
        ResolvedAt DATETIME2 NULL,
        RejectReason NVARCHAR(500) NULL,
        AssignmentId INT NULL,
        CONSTRAINT FK_OvertimeRequests_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
        CONSTRAINT FK_OvertimeRequests_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores(StoreId),
        CONSTRAINT FK_OvertimeRequests_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts(ShiftId),
        CONSTRAINT FK_OvertimeRequests_ResolvedBy FOREIGN KEY (ResolvedBy) REFERENCES dbo.Users(UserId),
        CONSTRAINT FK_OvertimeRequests_Assignment FOREIGN KEY (AssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId)
            ON DELETE SET NULL
    );

    CREATE INDEX IX_OvertimeRequests_Store_Status
        ON dbo.OvertimeRequests (StoreId, Status, WorkDate);

    CREATE INDEX IX_OvertimeRequests_Employee_Date
        ON dbo.OvertimeRequests (EmployeeId, WorkDate, Status);
END
GO
