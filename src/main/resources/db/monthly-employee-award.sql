-- =============================================================================
-- Danh hiệu Nhân viên xuất sắc tháng (MonthlyEmployeeAwards)
-- Quản lý chốt kết quả tháng đã kết thúc: Hạng 1 "Nhân viên xuất sắc tháng", Hạng 2–3 "Top tháng".
-- Bảng cũng được tạo tự động khi ứng dụng khởi động (ReliabilitySchemaInitializer).
-- Chỉ tạo mới, không sửa/xóa dữ liệu hiện có.
-- =============================================================================

IF OBJECT_ID(N'dbo.MonthlyEmployeeAwards', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MonthlyEmployeeAwards (
        AwardId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        StoreId INT NOT NULL,
        AwardMonth CHAR(7) NOT NULL,
        EmployeeId INT NOT NULL,
        AwardRank INT NOT NULL,
        MonthScore INT NOT NULL,
        OverrideReason NVARCHAR(255) NULL,
        DecidedBy INT NOT NULL,
        DecidedAt DATETIME2 NOT NULL,
        CONSTRAINT FK_MonthlyEmployeeAwards_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores(StoreId),
        CONSTRAINT FK_MonthlyEmployeeAwards_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
        CONSTRAINT FK_MonthlyEmployeeAwards_DecidedBy FOREIGN KEY (DecidedBy) REFERENCES dbo.Users(UserId),
        CONSTRAINT UX_MonthlyEmployeeAwards_Store_Month_Rank UNIQUE (StoreId, AwardMonth, AwardRank)
    );
END
GO

