-- Đơn xin tăng ca (vượt số ca định mức/tuần theo hợp đồng).
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.OvertimeRequests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.OvertimeRequests (
        OvertimeRequestId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_OvertimeRequests PRIMARY KEY,
        EmployeeId INT NOT NULL,
        StoreId INT NOT NULL,
        ShiftId INT NOT NULL,
        WorkDate DATE NOT NULL,
        ContractWeeklyQuota INT NULL,
        Reason NVARCHAR(255) NOT NULL,
        Status NVARCHAR(20) NOT NULL CONSTRAINT DF_OvertimeRequests_Status DEFAULT (N'Pending'),
        ReviewedByUserId INT NULL,
        ReviewedAt DATETIME2 NULL,
        ReviewNote NVARCHAR(255) NULL,
        CreatedAssignmentId INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_OvertimeRequests_CreatedAt DEFAULT (SYSDATETIME()),
        CONSTRAINT FK_OvertimeRequests_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_OvertimeRequests_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_OvertimeRequests_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts (ShiftId),
        CONSTRAINT FK_OvertimeRequests_ReviewedBy FOREIGN KEY (ReviewedByUserId) REFERENCES dbo.Users (UserId),
        CONSTRAINT CK_OvertimeRequests_Status CHECK (Status IN (N'Pending', N'Approved', N'Rejected', N'Cancelled'))
    );

    CREATE INDEX IX_OvertimeRequests_Store_Status_WorkDate
        ON dbo.OvertimeRequests (StoreId, Status, WorkDate DESC);
    CREATE INDEX IX_OvertimeRequests_Employee_WorkDate
        ON dbo.OvertimeRequests (EmployeeId, WorkDate DESC);
END;
GO

IF COL_LENGTH('dbo.OvertimeRequests', 'ContractWeeklyQuota') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD ContractWeeklyQuota INT NULL;
IF COL_LENGTH('dbo.OvertimeRequests', 'ReviewedByUserId') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD ReviewedByUserId INT NULL;
IF COL_LENGTH('dbo.OvertimeRequests', 'ReviewedAt') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD ReviewedAt DATETIME2 NULL;
IF COL_LENGTH('dbo.OvertimeRequests', 'ReviewNote') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD ReviewNote NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.OvertimeRequests', 'CreatedAssignmentId') IS NULL
    ALTER TABLE dbo.OvertimeRequests ADD CreatedAssignmentId INT NULL;
GO

