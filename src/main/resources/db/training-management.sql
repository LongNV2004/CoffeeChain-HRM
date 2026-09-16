IF OBJECT_ID(N'dbo.TrainingSkills', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingSkills (
        SkillId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_TrainingSkills PRIMARY KEY,
        SkillName NVARCHAR(100) NOT NULL,
        Description NVARCHAR(500) NULL,
        Requirements NVARCHAR(500) NULL,
        Status VARCHAR(20) NOT NULL CONSTRAINT DF_TrainingSkills_Status DEFAULT ('Active'),
        CreatedBy INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_TrainingSkills_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT UQ_TrainingSkills_SkillName UNIQUE (SkillName),
        CONSTRAINT FK_TrainingSkills_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES dbo.Users (UserId),
        CONSTRAINT CK_TrainingSkills_Status CHECK (Status IN ('Active', 'Inactive'))
    );
END;
GO

IF OBJECT_ID(N'dbo.TrainingClasses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingClasses (
        ClassId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_TrainingClasses PRIMARY KEY,
        SkillId INT NOT NULL,
        StoreId INT NOT NULL,
        ClassName NVARCHAR(150) NOT NULL,
        Trainer NVARCHAR(100) NULL,
        StartDate DATE NOT NULL,
        EndDate DATE NOT NULL,
        StartTime TIME NULL,
        EndTime TIME NULL,
        Location NVARCHAR(255) NULL,
        MaxParticipants INT NULL,
        Notes NVARCHAR(500) NULL,
        Status VARCHAR(30) NOT NULL CONSTRAINT DF_TrainingClasses_Status DEFAULT ('PENDING_APPROVAL'),
        CreatedBy INT NOT NULL,
        ApprovedBy INT NULL,
        ApprovedAt DATETIME2 NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_TrainingClasses_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_TrainingClasses_Skill FOREIGN KEY (SkillId) REFERENCES dbo.TrainingSkills (SkillId),
        CONSTRAINT FK_TrainingClasses_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_TrainingClasses_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES dbo.Users (UserId),
        CONSTRAINT FK_TrainingClasses_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES dbo.Users (UserId),
        CONSTRAINT CK_TrainingClasses_Status CHECK (Status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
        CONSTRAINT CK_TrainingClasses_Dates CHECK (EndDate >= StartDate)
    );
END;
GO
