-- Stage 4: preserve cancellation provenance and replacement linkage in EXISTING table.
-- Idempotent. Does not DROP/CREATE CoffeeHRM or change LeaveBalances.
IF OBJECT_ID(N'dbo.ShiftAssignments', N'U') IS NULL
    THROW 50001, 'Missing dbo.ShiftAssignments', 1;
IF COL_LENGTH('dbo.ShiftAssignments', 'CancelledByLeaveRequestId') IS NULL
    ALTER TABLE dbo.ShiftAssignments ADD CancelledByLeaveRequestId INT NULL;
IF COL_LENGTH('dbo.ShiftAssignments', 'ReplacesAssignmentId') IS NULL
    ALTER TABLE dbo.ShiftAssignments ADD ReplacesAssignmentId INT NULL;
IF COL_LENGTH('dbo.ShiftAssignments', 'ReplacementDeferred') IS NULL
    ALTER TABLE dbo.ShiftAssignments ADD ReplacementDeferred BIT NOT NULL
        CONSTRAINT DF_ShiftAssignments_ReplacementDeferred DEFAULT (0);
IF COL_LENGTH('dbo.ShiftAssignments', 'ReplacementConfirmedBy') IS NULL
    ALTER TABLE dbo.ShiftAssignments ADD ReplacementConfirmedBy INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_ShiftAssignments_CancelledByLeave')
    ALTER TABLE dbo.ShiftAssignments ADD CONSTRAINT FK_ShiftAssignments_CancelledByLeave
        FOREIGN KEY (CancelledByLeaveRequestId) REFERENCES dbo.LeaveRequests(LeaveRequestId);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_ShiftAssignments_ReplacesAssignment')
    ALTER TABLE dbo.ShiftAssignments ADD CONSTRAINT FK_ShiftAssignments_ReplacesAssignment
        FOREIGN KEY (ReplacesAssignmentId) REFERENCES dbo.ShiftAssignments(AssignmentId);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_ShiftAssignments_ReplacementConfirmedBy')
    ALTER TABLE dbo.ShiftAssignments ADD CONSTRAINT FK_ShiftAssignments_ReplacementConfirmedBy
        FOREIGN KEY (ReplacementConfirmedBy) REFERENCES dbo.Users(UserId);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ShiftAssignments')
                 AND name='UX_ShiftAssignments_OneReplacementPerOriginal')
    CREATE UNIQUE INDEX UX_ShiftAssignments_OneReplacementPerOriginal
        ON dbo.ShiftAssignments(ReplacesAssignmentId) WHERE ReplacesAssignmentId IS NOT NULL;
