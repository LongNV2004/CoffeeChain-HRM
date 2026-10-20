-- Bổ sung loại nghỉ, người bàn giao, số điện thoại và số ngày đã tính.
-- Không xóa dữ liệu. Đơn cũ được coi là nghỉ phép năm.
-- Chạy khi ddl-auto=none. Không chạy lại phần ALTER nếu cột đã tồn tại.
USE CoffeeHRM;
GO

IF COL_LENGTH(N'dbo.LeaveRequests', N'LeaveType') IS NULL
BEGIN
    ALTER TABLE dbo.LeaveRequests ADD LeaveType NVARCHAR(20) NULL;
END;
GO

UPDATE dbo.LeaveRequests
SET LeaveType = N'Annual'
WHERE LeaveType IS NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.LeaveRequests')
      AND name = N'LeaveType'
      AND is_nullable = 1
)
BEGIN
    ALTER TABLE dbo.LeaveRequests ALTER COLUMN LeaveType NVARCHAR(20) NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.LeaveRequests')
      AND name = N'DF_LeaveRequests_LeaveType'
)
BEGIN
    ALTER TABLE dbo.LeaveRequests
        ADD CONSTRAINT DF_LeaveRequests_LeaveType DEFAULT (N'Annual') FOR LeaveType;
END;
GO

IF COL_LENGTH(N'dbo.LeaveRequests', N'ContactPhone') IS NULL
BEGIN
    ALTER TABLE dbo.LeaveRequests ADD ContactPhone NVARCHAR(15) NULL;
END;
GO

IF COL_LENGTH(N'dbo.LeaveRequests', N'RequestedWorkDays') IS NULL
BEGIN
    ALTER TABLE dbo.LeaveRequests ADD RequestedWorkDays INT NULL;
END;
GO

IF COL_LENGTH(N'dbo.LeaveRequests', N'HandoverEmployeeId') IS NULL
BEGIN
    ALTER TABLE dbo.LeaveRequests ADD HandoverEmployeeId INT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_LeaveRequests_HandoverEmployee'
)
BEGIN
    ALTER TABLE dbo.LeaveRequests
        ADD CONSTRAINT FK_LeaveRequests_HandoverEmployee
            FOREIGN KEY (HandoverEmployeeId) REFERENCES dbo.Employees (EmployeeId);
END;
GO

-- Nếu Status đang bị CHECK chỉ cho Pending/Approved/Rejected thì nới để nhận Cancelled.
DECLARE @dropSql NVARCHAR(MAX) = N'';
SELECT @dropSql = @dropSql + N'ALTER TABLE dbo.LeaveRequests DROP CONSTRAINT '
    + QUOTENAME(cc.name) + N';' + CHAR(10)
FROM sys.check_constraints cc
JOIN sys.columns c
    ON c.object_id = cc.parent_object_id
   AND c.column_id = cc.parent_column_id
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.LeaveRequests')
  AND c.name = N'Status'
  AND cc.definition NOT LIKE N'%Cancelled%';

IF LEN(@dropSql) > 0
BEGIN
    EXEC sp_executesql @dropSql;
    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints WHERE name = N'CK_LeaveRequests_Status'
    )
    BEGIN
        ALTER TABLE dbo.LeaveRequests ADD CONSTRAINT CK_LeaveRequests_Status
            CHECK (Status IN (N'Pending', N'Approved', N'Rejected', N'Cancelled'));
    END;
END;
GO
