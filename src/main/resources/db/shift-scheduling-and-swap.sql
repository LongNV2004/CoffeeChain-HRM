SET QUOTED_IDENTIFIER ON;
GO

-- =========================================================================
-- Migration: Phân công ca và Đổi chéo ca (Shift Scheduling & Shift Swap)
-- Đảm bảo an toàn, idempotent (chạy nhiều lần không lỗi), hỗ trợ cả DB mới và DB cũ.
-- =========================================================================

-- 1. Bảng Shifts (Ca làm việc)
IF OBJECT_ID(N'dbo.Shifts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Shifts (
        ShiftId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Shifts PRIMARY KEY,
        StoreId INT NOT NULL,
        ShiftName NVARCHAR(50) NOT NULL,
        StartTime TIME NOT NULL,
        EndTime TIME NOT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_Shifts_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_Shifts_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores(StoreId),
        CONSTRAINT CK_Shifts_TimeRange CHECK (EndTime > StartTime)
    );
    PRINT N'Đã tạo bảng Shifts.';
END;
GO

-- 2. Bảng ShiftAssignments (Phân công ca làm việc)
IF OBJECT_ID(N'dbo.ShiftAssignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ShiftAssignments (
        AssignmentId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ShiftAssignments PRIMARY KEY,
        ShiftId INT NOT NULL,
        EmployeeId INT NOT NULL,
        WorkDate DATE NOT NULL,
        Status NVARCHAR(20) NOT NULL CONSTRAINT DF_ShiftAssignments_Status DEFAULT (N'Assigned'),
        IsPublished BIT NOT NULL CONSTRAINT DF_ShiftAssignments_IsPublished DEFAULT (1),
        PublishedAt DATETIME2 NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_ShiftAssignments_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_ShiftAssignments_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts(ShiftId),
        CONSTRAINT FK_ShiftAssignments_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
        CONSTRAINT UQ_Assignment_EmpShiftDate UNIQUE (EmployeeId, ShiftId, WorkDate)
    );
    PRINT N'Đã tạo bảng ShiftAssignments.';
END;
GO

-- Đảm bảo cột IsPublished và PublishedAt tồn tại nếu bảng đã được tạo từ trước
IF COL_LENGTH(N'dbo.ShiftAssignments', N'IsPublished') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftAssignments ADD IsPublished BIT NOT NULL CONSTRAINT DF_ShiftAssignments_IsPublished DEFAULT (1);
    PRINT N'Đã thêm cột IsPublished vào ShiftAssignments.';
END;
GO

IF COL_LENGTH(N'dbo.ShiftAssignments', N'PublishedAt') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftAssignments ADD PublishedAt DATETIME2 NULL;
    PRINT N'Đã thêm cột PublishedAt vào ShiftAssignments.';
END;
GO

-- Đảm bảo Unique Constraint UQ_Assignment_EmpShiftDate tồn tại
IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints 
    WHERE name = N'UQ_Assignment_EmpShiftDate' 
      AND parent_object_id = OBJECT_ID(N'dbo.ShiftAssignments')
)
BEGIN
    ALTER TABLE dbo.ShiftAssignments
    ADD CONSTRAINT UQ_Assignment_EmpShiftDate UNIQUE (EmployeeId, ShiftId, WorkDate);
    PRINT N'Đã thêm ràng buộc UQ_Assignment_EmpShiftDate vào ShiftAssignments.';
END;
GO

-- Các index hỗ trợ truy vấn lịch tuần
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ShiftAssignments_Store' AND object_id = OBJECT_ID(N'dbo.ShiftAssignments'))
BEGIN
    CREATE INDEX IX_ShiftAssignments_Store ON dbo.ShiftAssignments(ShiftId, WorkDate);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ShiftAssignments_Date' AND object_id = OBJECT_ID(N'dbo.ShiftAssignments'))
BEGIN
    CREATE INDEX IX_ShiftAssignments_Date ON dbo.ShiftAssignments(WorkDate);
END;
GO

-- 3. Bảng ShiftChangeRequests (Yêu cầu đổi / chuyển ca)
IF OBJECT_ID(N'dbo.ShiftChangeRequests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ShiftChangeRequests (
        RequestId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ShiftChangeRequests PRIMARY KEY,
        AssignmentId INT NOT NULL,
        EmployeeId INT NOT NULL,
        TargetAssignmentId INT NULL,
        TargetEmployeeId INT NULL,
        IsTargetAgreed BIT NULL,
        TargetAgreedAt DATETIME2 NULL,
        Reason NVARCHAR(255) NOT NULL,
        Status NVARCHAR(20) NOT NULL CONSTRAINT DF_ShiftChangeRequests_Status DEFAULT (N'Pending'),
        RequestDate DATETIME2 NOT NULL CONSTRAINT DF_ShiftChangeRequests_RequestDate DEFAULT (SYSUTCDATETIME()),
        ResolvedBy INT NULL,
        ResolvedDate DATETIME2 NULL,
        CONSTRAINT FK_ShiftChangeRequests_Assignment FOREIGN KEY (AssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId),
        CONSTRAINT FK_ShiftChangeRequests_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
        CONSTRAINT FK_ShiftChangeRequests_ResolvedBy FOREIGN KEY (ResolvedBy) REFERENCES dbo.Users(UserId),
        CONSTRAINT FK_ChangeReq_TargetAssignment FOREIGN KEY (TargetAssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId),
        CONSTRAINT FK_ChangeReq_TargetEmployee FOREIGN KEY (TargetEmployeeId) REFERENCES dbo.Employees(EmployeeId)
    );
    PRINT N'Đã tạo bảng ShiftChangeRequests.';
END;
GO

-- Thêm các cột phục vụ Đổi chéo ca nếu bảng đã tồn tại từ phiên bản cũ
IF COL_LENGTH(N'dbo.ShiftChangeRequests', N'TargetAssignmentId') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests ADD TargetAssignmentId INT NULL;
    PRINT N'Đã thêm cột TargetAssignmentId vào ShiftChangeRequests.';
END;
GO

IF COL_LENGTH(N'dbo.ShiftChangeRequests', N'TargetEmployeeId') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests ADD TargetEmployeeId INT NULL;
    PRINT N'Đã thêm cột TargetEmployeeId vào ShiftChangeRequests.';
END;
GO

IF COL_LENGTH(N'dbo.ShiftChangeRequests', N'IsTargetAgreed') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests ADD IsTargetAgreed BIT NULL;
    PRINT N'Đã thêm cột IsTargetAgreed vào ShiftChangeRequests.';
END;
GO

IF COL_LENGTH(N'dbo.ShiftChangeRequests', N'TargetAgreedAt') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests ADD TargetAgreedAt DATETIME2 NULL;
    PRINT N'Đã thêm cột TargetAgreedAt vào ShiftChangeRequests.';
END;
GO

IF COL_LENGTH(N'dbo.ShiftChangeRequests', N'ResolvedBy') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests ADD ResolvedBy INT NULL;
    PRINT N'Đã thêm cột ResolvedBy vào ShiftChangeRequests.';
END;
GO

IF COL_LENGTH(N'dbo.ShiftChangeRequests', N'ResolvedDate') IS NULL
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests ADD ResolvedDate DATETIME2 NULL;
    PRINT N'Đã thêm cột ResolvedDate vào ShiftChangeRequests.';
END;
GO

-- Đảm bảo các khóa ngoại của ShiftChangeRequests tồn tại
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ShiftChangeRequests_Assignment')
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests
    ADD CONSTRAINT FK_ShiftChangeRequests_Assignment FOREIGN KEY (AssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ShiftChangeRequests_Employee')
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests
    ADD CONSTRAINT FK_ShiftChangeRequests_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ShiftChangeRequests_ResolvedBy')
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests
    ADD CONSTRAINT FK_ShiftChangeRequests_ResolvedBy FOREIGN KEY (ResolvedBy) REFERENCES dbo.Users(UserId);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ChangeReq_TargetAssignment')
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests
    ADD CONSTRAINT FK_ChangeReq_TargetAssignment FOREIGN KEY (TargetAssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ChangeReq_TargetEmployee')
BEGIN
    ALTER TABLE dbo.ShiftChangeRequests
    ADD CONSTRAINT FK_ChangeReq_TargetEmployee FOREIGN KEY (TargetEmployeeId) REFERENCES dbo.Employees(EmployeeId);
END;
GO
