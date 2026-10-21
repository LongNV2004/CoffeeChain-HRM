USE CoffeeHRM;
GO

/*
    Slot leave is a new counter. TotalLeaveDays / UsedDays / RemainingDays stay as they are.
    TotalLeaveSlots is nullable on purpose: 12 leave days must not be copied into 12 slots.
*/
IF COL_LENGTH('dbo.Stores', 'TotalLeaveSlots') IS NULL
    ALTER TABLE dbo.Stores ADD TotalLeaveSlots INT NULL;
GO

IF COL_LENGTH('dbo.LeaveBalances', 'TotalSlots') IS NULL
    ALTER TABLE dbo.LeaveBalances ADD TotalSlots INT NULL;
GO

IF COL_LENGTH('dbo.LeaveBalances', 'UsedSlots') IS NULL
    ALTER TABLE dbo.LeaveBalances ADD UsedSlots INT NOT NULL
        CONSTRAINT DF_LeaveBalances_UsedSlots DEFAULT 0;
GO

IF COL_LENGTH('dbo.LeaveBalances', 'RemainingSlots') IS NULL
    ALTER TABLE dbo.LeaveBalances ADD RemainingSlots INT NULL;
GO

IF OBJECT_ID('dbo.LeaveRequestSlots', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.LeaveRequestSlots (
        LeaveRequestSlotId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        LeaveRequestId INT NOT NULL,
        ShiftAssignmentId INT NOT NULL,
        CONSTRAINT UQ_LeaveRequestSlots_Request_Assignment UNIQUE (LeaveRequestId, ShiftAssignmentId),
        CONSTRAINT FK_LeaveRequestSlots_Request FOREIGN KEY (LeaveRequestId)
            REFERENCES dbo.LeaveRequests (LeaveRequestId),
        CONSTRAINT FK_LeaveRequestSlots_Assignment FOREIGN KEY (ShiftAssignmentId)
            REFERENCES dbo.ShiftAssignments (AssignmentId)
    );
    CREATE INDEX IX_LeaveRequestSlots_Assignment ON dbo.LeaveRequestSlots (ShiftAssignmentId);
END
GO
