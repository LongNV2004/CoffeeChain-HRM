-- Báo cáo chấm công hàng tháng. Chạy thủ công trên database CoffeeHRM.
-- Chỉ tạo bảng mới. Không sửa Attendances, TrainingAttendances hay dữ liệu chấm công.
IF OBJECT_ID(N'dbo.MonthlyAttendanceReports', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MonthlyAttendanceReports (
        ReportId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_MonthlyAttendanceReports PRIMARY KEY,
        StoreId INT NOT NULL,
        ReportYear INT NOT NULL,
        ReportMonth INT NOT NULL,
        Status VARCHAR(20) NOT NULL,
        VersionNumber INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_Version DEFAULT (0),
        DraftGeneratedAt DATETIME2 NULL,
        SubmittedAt DATETIME2 NULL,
        SubmittedByUserId INT NULL,
        SubmittedByName NVARCHAR(100) NULL,
        PreparedByName NVARCHAR(100) NULL,
        EmployeeCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_EmployeeCount DEFAULT (0),
        ShiftRecordCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_ShiftCount DEFAULT (0),
        TrainingRecordCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_TrainingCount DEFAULT (0),
        LateCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_LateCount DEFAULT (0),
        AbsentCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_AbsentCount DEFAULT (0),
        OpenCheckoutCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_OpenCount DEFAULT (0),
        ReviewCount INT NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_ReviewCount DEFAULT (0),
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_MonthlyAttendanceReports_CreatedAt DEFAULT (SYSUTCDATETIME()),
        UpdatedAt DATETIME2 NULL,
        CONSTRAINT UQ_MonthlyAttendanceReports_StorePeriod UNIQUE (StoreId, ReportYear, ReportMonth),
        CONSTRAINT FK_MonthlyAttendanceReports_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_MonthlyAttendanceReports_User FOREIGN KEY (SubmittedByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT CK_MonthlyAttendanceReports_Status CHECK (Status IN ('DRAFT', 'SUBMITTED')),
        CONSTRAINT CK_MonthlyAttendanceReports_Month CHECK (ReportMonth BETWEEN 1 AND 12),
        CONSTRAINT CK_MonthlyAttendanceReports_Year CHECK (ReportYear BETWEEN 2000 AND 2100)
    );
END;
GO

IF OBJECT_ID(N'dbo.MonthlyAttendanceReportVersions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MonthlyAttendanceReportVersions (
        VersionId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_MonthlyAttendanceReportVersions PRIMARY KEY,
        ReportId INT NOT NULL,
        VersionNumber INT NOT NULL,
        SubmittedAt DATETIME2 NOT NULL,
        SubmittedByUserId INT NULL,
        SubmittedByName NVARCHAR(100) NULL,
        EmployeeCount INT NOT NULL,
        ShiftRecordCount INT NOT NULL,
        TrainingRecordCount INT NOT NULL,
        LateCount INT NOT NULL,
        AbsentCount INT NOT NULL,
        OpenCheckoutCount INT NOT NULL,
        ReviewCount INT NOT NULL,
        CONSTRAINT UQ_MonthlyAttendanceReportVersions UNIQUE (ReportId, VersionNumber),
        CONSTRAINT FK_MonthlyAttendanceReportVersions_Report FOREIGN KEY (ReportId)
            REFERENCES dbo.MonthlyAttendanceReports (ReportId),
        CONSTRAINT FK_MonthlyAttendanceReportVersions_User FOREIGN KEY (SubmittedByUserId)
            REFERENCES dbo.Users (UserId)
    );
END;
GO

-- Bỏ các cột chỉ dùng cho file Excel. Chạy thêm nếu bảng đã được tạo từ bản script cũ.
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'FileName') IS NOT NULL
    ALTER TABLE dbo.MonthlyAttendanceReports DROP COLUMN FileName;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'StorageKey') IS NOT NULL
    ALTER TABLE dbo.MonthlyAttendanceReports DROP COLUMN StorageKey;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'DraftFileName') IS NOT NULL
    ALTER TABLE dbo.MonthlyAttendanceReports DROP COLUMN DraftFileName;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReports', N'DraftStorageKey') IS NOT NULL
    ALTER TABLE dbo.MonthlyAttendanceReports DROP COLUMN DraftStorageKey;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReportVersions', N'FileName') IS NOT NULL
    ALTER TABLE dbo.MonthlyAttendanceReportVersions DROP COLUMN FileName;
IF COL_LENGTH(N'dbo.MonthlyAttendanceReportVersions', N'StorageKey') IS NOT NULL
    ALTER TABLE dbo.MonthlyAttendanceReportVersions DROP COLUMN StorageKey;
GO
