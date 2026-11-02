-- =============================================================================
-- Lời mời hỗ trợ ca gấp (ShiftCoverInvitations)
-- Quản lý mời nhân viên nhận ca đang cần người thay; mỗi lời mời có trạng thái
-- PENDING / ACCEPTED / DECLINED / EXPIRED / CANCELLED làm căn cứ cộng/trừ điểm chuyên cần.
-- Bảng cũng được tạo tự động khi ứng dụng khởi động (ReliabilitySchemaInitializer).
-- Chỉ tạo mới, không sửa/xóa dữ liệu hiện có.
-- =============================================================================

IF OBJECT_ID(N'dbo.ShiftCoverInvitations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ShiftCoverInvitations (
        InvitationId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RequestId INT NOT NULL,
        EmployeeId INT NOT NULL,
        InvitedBy INT NULL,
        Status NVARCHAR(20) NOT NULL,
        Urgent BIT NOT NULL CONSTRAINT DF_ShiftCoverInvitations_Urgent DEFAULT (0),
        DeclineReason NVARCHAR(20) NULL,
        DeclineNote NVARCHAR(255) NULL,
        SentAt DATETIME2 NOT NULL,
        ExpiresAt DATETIME2 NOT NULL,
        RespondedAt DATETIME2 NULL,
        CONSTRAINT FK_ShiftCoverInvitations_Request FOREIGN KEY (RequestId) REFERENCES dbo.ShiftChangeRequests(RequestId),
        CONSTRAINT FK_ShiftCoverInvitations_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
        CONSTRAINT FK_ShiftCoverInvitations_InvitedBy FOREIGN KEY (InvitedBy) REFERENCES dbo.Users(UserId),
        CONSTRAINT UX_ShiftCoverInvitations_Request_Employee UNIQUE (RequestId, EmployeeId)
    );

    CREATE INDEX IX_ShiftCoverInvitations_Employee_Status
        ON dbo.ShiftCoverInvitations (EmployeeId, Status);

    CREATE INDEX IX_ShiftCoverInvitations_Status_ExpiresAt
        ON dbo.ShiftCoverInvitations (Status, ExpiresAt);
END
GO
