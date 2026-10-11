-- Run once against the existing CoffeeHRM database. NEVER run the full seed script.
USE CoffeeHRM;
GO
IF COL_LENGTH(N'dbo.LeaveRequests', N'RejectReason') IS NULL
BEGIN
    ALTER TABLE dbo.LeaveRequests ADD RejectReason NVARCHAR(500) NULL;
END;
GO
