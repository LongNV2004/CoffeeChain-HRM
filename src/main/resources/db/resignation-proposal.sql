-- Đề xuất nghỉ việc: một đề xuất chung và nhiều dòng nhân sự xử lý độc lập.
-- Script bổ sung, không xóa dữ liệu hiện có. Chạy khi ddl-auto=none.
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.ResignationProposals', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ResignationProposals (
        ProposalId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ResignationProposals PRIMARY KEY,
        StoreId INT NOT NULL,
        RequestedByEmployeeId INT NOT NULL,
        Title NVARCHAR(150) NOT NULL,
        Note NVARCHAR(500) NULL,
        Status VARCHAR(20) NOT NULL CONSTRAINT DF_ResignationProposals_Status DEFAULT ('Pending'),
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_ResignationProposals_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_ResignationProposals_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_ResignationProposals_RequestedBy FOREIGN KEY (RequestedByEmployeeId) REFERENCES dbo.Employees (EmployeeId)
    );
END;
GO

IF OBJECT_ID(N'dbo.ResignationProposalItems', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ResignationProposalItems (
        ItemId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ResignationProposalItems PRIMARY KEY,
        ProposalId INT NOT NULL,
        SortOrder INT NOT NULL CONSTRAINT DF_ResignationProposalItems_SortOrder DEFAULT (1),
        EmployeeId INT NOT NULL,
        Position NVARCHAR(50) NOT NULL,
        ProposedTerminationDate DATE NOT NULL,
        Reason NVARCHAR(500) NOT NULL,
        Note NVARCHAR(500) NULL,
        Status VARCHAR(20) NOT NULL CONSTRAINT DF_ResignationProposalItems_Status DEFAULT ('Pending'),
        ApprovedTerminationDate DATE NULL,
        RejectReason NVARCHAR(500) NULL,
        ReviewedAt DATETIME2 NULL,
        ReviewedByUserId INT NULL,
        AppliedAt DATETIME2 NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_ResignationProposalItems_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_ResignationProposalItems_Proposal FOREIGN KEY (ProposalId) REFERENCES dbo.ResignationProposals (ProposalId),
        CONSTRAINT FK_ResignationProposalItems_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_ResignationProposalItems_ReviewedBy FOREIGN KEY (ReviewedByUserId) REFERENCES dbo.Users (UserId)
    );
END;
GO

SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_ResignationProposalItems_Proposal_Employee'
      AND object_id = OBJECT_ID(N'dbo.ResignationProposalItems')
)
BEGIN
    CREATE UNIQUE INDEX UX_ResignationProposalItems_Proposal_Employee
        ON dbo.ResignationProposalItems (ProposalId, EmployeeId);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_ResignationProposalItems_PendingEmployee'
      AND object_id = OBJECT_ID(N'dbo.ResignationProposalItems')
)
BEGIN
    CREATE UNIQUE INDEX UX_ResignationProposalItems_PendingEmployee
        ON dbo.ResignationProposalItems (EmployeeId)
        WHERE Status = 'Pending';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_ResignationProposalItems_UnappliedApproval'
      AND object_id = OBJECT_ID(N'dbo.ResignationProposalItems')
)
BEGIN
    CREATE UNIQUE INDEX UX_ResignationProposalItems_UnappliedApproval
        ON dbo.ResignationProposalItems (EmployeeId)
        WHERE Status = 'Approved' AND AppliedAt IS NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_ResignationProposals_Store_Manager_Status'
      AND object_id = OBJECT_ID(N'dbo.ResignationProposals')
)
BEGIN
    CREATE INDEX IX_ResignationProposals_Store_Manager_Status
        ON dbo.ResignationProposals (StoreId, RequestedByEmployeeId, Status, CreatedAt);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_ResignationProposalItems_Proposal_Status'
      AND object_id = OBJECT_ID(N'dbo.ResignationProposalItems')
)
BEGIN
    CREATE INDEX IX_ResignationProposalItems_Proposal_Status
        ON dbo.ResignationProposalItems (ProposalId, Status, SortOrder);
END;
GO

IF COL_LENGTH(N'dbo.ResignationProposalItems', N'DecisionNoticeSentAt') IS NULL
BEGIN
    ALTER TABLE dbo.ResignationProposalItems ADD DecisionNoticeSentAt DATETIME2 NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_ResignationProposalItems_Due'
      AND object_id = OBJECT_ID(N'dbo.ResignationProposalItems')
)
BEGIN
    CREATE INDEX IX_ResignationProposalItems_Due
        ON dbo.ResignationProposalItems (ApprovedTerminationDate, Status)
        WHERE AppliedAt IS NULL;
END;
GO
