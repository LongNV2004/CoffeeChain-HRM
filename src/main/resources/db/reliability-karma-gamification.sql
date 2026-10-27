-- =============================================================================
-- Gamification: Điểm tín nhiệm & "Cứu bồ" (Reliability & Karma Score)
-- Khởi tạo cột ReliabilityScore (mặc định 100) trên bảng Employees
-- và bảng ReliabilityScoreLogs lưu lịch sử cộng/trừ điểm uy tín.
-- =============================================================================

IF COL_LENGTH('dbo.Employees', 'ReliabilityScore') IS NULL
BEGIN
    ALTER TABLE dbo.Employees
        ADD ReliabilityScore INT NOT NULL
            CONSTRAINT DF_Employees_ReliabilityScore DEFAULT (100);
END
GO

UPDATE dbo.Employees
SET ReliabilityScore = 100
WHERE ReliabilityScore IS NULL;
GO

IF OBJECT_ID(N'dbo.ReliabilityScoreLogs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ReliabilityScoreLogs (
        LogId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        EmployeeId INT NOT NULL,
        StoreId INT NOT NULL,
        ActionType NVARCHAR(50) NOT NULL,
        PointsDelta INT NOT NULL,
        ScoreAfter INT NOT NULL,
        Reason NVARCHAR(300) NOT NULL,
        ReferenceType NVARCHAR(50) NULL,
        ReferenceId INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_ReliabilityScoreLogs_CreatedAt DEFAULT (SYSDATETIME()),
        CONSTRAINT FK_ReliabilityScoreLogs_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
        CONSTRAINT FK_ReliabilityScoreLogs_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores(StoreId)
    );

    CREATE INDEX IX_ReliabilityScoreLogs_Store_CreatedAt
        ON dbo.ReliabilityScoreLogs (StoreId, CreatedAt DESC);

    CREATE INDEX IX_ReliabilityScoreLogs_Employee_CreatedAt
        ON dbo.ReliabilityScoreLogs (EmployeeId, CreatedAt DESC);
END
GO
