-- Chấm công đào tạo. Chạy thủ công trên database CoffeeHRM.
-- Chỉ tạo bảng mới. Không sửa Attendances, không xóa dữ liệu.
IF OBJECT_ID(N'dbo.TrainingAttendances', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingAttendances (
        TrainingAttendanceId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_TrainingAttendances PRIMARY KEY,
        EmployeeId INT NOT NULL,
        ClassId INT NOT NULL,
        WorkDate DATE NOT NULL,
        StoreId INT NULL,
        ClassName NVARCHAR(150) NULL,
        ScheduledStartTime TIME NULL,
        ScheduledEndTime TIME NULL,
        CheckInTime DATETIME2 NULL,
        CheckOutTime DATETIME2 NULL,
        LateMinutes INT NOT NULL CONSTRAINT DF_TrainingAttendances_LateMinutes DEFAULT (0),
        EarlyLeaveMinutes INT NOT NULL CONSTRAINT DF_TrainingAttendances_EarlyLeaveMinutes DEFAULT (0),
        WorkingMinutes INT NULL,
        Status VARCHAR(20) NOT NULL,
        CheckInLatitude DECIMAL(10, 7) NULL,
        CheckInLongitude DECIMAL(10, 7) NULL,
        CheckOutLatitude DECIMAL(10, 7) NULL,
        CheckOutLongitude DECIMAL(10, 7) NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_TrainingAttendances_CreatedAt DEFAULT (SYSUTCDATETIME()),
        UpdatedAt DATETIME2 NULL,
        CONSTRAINT UQ_TrainingAttendances_EmpClassDate UNIQUE (EmployeeId, ClassId, WorkDate),
        CONSTRAINT FK_TrainingAttendances_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_TrainingAttendances_Class FOREIGN KEY (ClassId) REFERENCES dbo.TrainingClasses (ClassId),
        CONSTRAINT FK_TrainingAttendances_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId)
    );
END;
GO
