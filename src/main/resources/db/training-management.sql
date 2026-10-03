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
        TrainingType VARCHAR(30) NOT NULL CONSTRAINT DF_TrainingClasses_TrainingType DEFAULT ('STORE_TRAINING'),
        StoreId INT NULL,
        ClassName NVARCHAR(150) NOT NULL,
        TrainerId INT NULL,
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
        CONSTRAINT FK_TrainingClasses_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId),
        CONSTRAINT FK_TrainingClasses_Trainer FOREIGN KEY (TrainerId) REFERENCES dbo.Users (UserId),
        CONSTRAINT FK_TrainingClasses_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES dbo.Users (UserId),
        CONSTRAINT FK_TrainingClasses_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES dbo.Users (UserId),
        CONSTRAINT CK_TrainingClasses_Status CHECK (Status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
        CONSTRAINT CK_TrainingClasses_TrainingType CHECK (TrainingType IN ('STORE_TRAINING', 'CENTRALIZED_TRAINING')),
        CONSTRAINT CK_TrainingClasses_Dates CHECK (EndDate >= StartDate)
    );
END;
GO

IF OBJECT_ID(N'dbo.TrainingClassEnrollments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingClassEnrollments (
        EnrollmentId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_TrainingClassEnrollments PRIMARY KEY,
        ClassId INT NOT NULL,
        EmployeeId INT NOT NULL,
        Result VARCHAR(10) NULL,
        EvaluationNote NVARCHAR(500) NULL,
        EvaluatedAt DATETIME2 NULL,
        EvaluatedBy INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_TrainingClassEnrollments_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT UQ_TrainingClassEnrollments_ClassEmployee UNIQUE (ClassId, EmployeeId),
        CONSTRAINT FK_TrainingClassEnrollments_Class FOREIGN KEY (ClassId) REFERENCES dbo.TrainingClasses (ClassId) ON DELETE CASCADE,
        CONSTRAINT FK_TrainingClassEnrollments_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees (EmployeeId),
        CONSTRAINT FK_TrainingClassEnrollments_EvaluatedBy FOREIGN KEY (EvaluatedBy) REFERENCES dbo.Users (UserId),
        CONSTRAINT CK_TrainingClassEnrollments_Result CHECK (Result IS NULL OR Result IN ('Pass', 'NotPass'))
    );

    CREATE INDEX IX_TrainingClassEnrollments_ClassId
        ON dbo.TrainingClassEnrollments (ClassId);
END;
GO

IF OBJECT_ID(N'dbo.TrainingClassSkills', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingClassSkills (
        ClassId INT NOT NULL,
        SkillId INT NOT NULL,
        CONSTRAINT PK_TrainingClassSkills PRIMARY KEY (ClassId, SkillId),
        CONSTRAINT FK_TrainingClassSkills_Class FOREIGN KEY (ClassId) REFERENCES dbo.TrainingClasses (ClassId) ON DELETE CASCADE,
        CONSTRAINT FK_TrainingClassSkills_Skill FOREIGN KEY (SkillId) REFERENCES dbo.TrainingSkills (SkillId)
    );
END;
GO

IF OBJECT_ID(N'dbo.TrainingClassStores', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingClassStores (
        ClassId INT NOT NULL,
        StoreId INT NOT NULL,
        CONSTRAINT PK_TrainingClassStores PRIMARY KEY (ClassId, StoreId),
        CONSTRAINT FK_TrainingClassStores_Class FOREIGN KEY (ClassId) REFERENCES dbo.TrainingClasses (ClassId) ON DELETE CASCADE,
        CONSTRAINT FK_TrainingClassStores_Store FOREIGN KEY (StoreId) REFERENCES dbo.Stores (StoreId)
    );
END;
GO

IF COL_LENGTH('dbo.TrainingClasses', 'TrainingType') IS NULL
BEGIN
    ALTER TABLE dbo.TrainingClasses
        ADD TrainingType VARCHAR(30) NOT NULL
            CONSTRAINT DF_TrainingClasses_TrainingType DEFAULT ('STORE_TRAINING');
END;
GO

IF COL_LENGTH('dbo.TrainingClasses', 'TrainerId') IS NULL
BEGIN
    ALTER TABLE dbo.TrainingClasses ADD TrainerId INT NULL;
    ALTER TABLE dbo.TrainingClasses
        ADD CONSTRAINT FK_TrainingClasses_Trainer FOREIGN KEY (TrainerId) REFERENCES dbo.Users (UserId);
END;
GO

IF COL_LENGTH('dbo.TrainingClasses', 'SkillId') IS NOT NULL
BEGIN
    INSERT INTO dbo.TrainingClassSkills (ClassId, SkillId)
    SELECT c.ClassId, c.SkillId
    FROM dbo.TrainingClasses c
    WHERE c.SkillId IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.TrainingClassSkills existing
          WHERE existing.ClassId = c.ClassId
            AND existing.SkillId = c.SkillId
      );

    DECLARE @skillFk SYSNAME;
    SELECT @skillFk = fk.name
    FROM sys.foreign_keys fk
    INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
    INNER JOIN sys.columns col ON fkc.parent_object_id = col.object_id AND fkc.parent_column_id = col.column_id
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.TrainingClasses')
      AND col.name = N'SkillId';
    IF @skillFk IS NOT NULL
        EXEC(N'ALTER TABLE dbo.TrainingClasses DROP CONSTRAINT ' + @skillFk);

    ALTER TABLE dbo.TrainingClasses DROP COLUMN SkillId;
END;
GO

IF COL_LENGTH('dbo.TrainingClasses', 'StoreId') IS NOT NULL
   AND EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.TrainingClasses')
          AND name = N'StoreId'
          AND is_nullable = 0
   )
BEGIN
    ALTER TABLE dbo.TrainingClasses ALTER COLUMN StoreId INT NULL;
END;
GO

INSERT INTO dbo.TrainingClassStores (ClassId, StoreId)
SELECT c.ClassId, c.StoreId
FROM dbo.TrainingClasses c
WHERE c.StoreId IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.TrainingClassStores existing
      WHERE existing.ClassId = c.ClassId
        AND existing.StoreId = c.StoreId
  );
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TrainingClasses_TrainingType')
BEGIN
    ALTER TABLE dbo.TrainingClasses
        ADD CONSTRAINT CK_TrainingClasses_TrainingType
            CHECK (TrainingType IN ('STORE_TRAINING', 'CENTRALIZED_TRAINING'));
END;
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TrainingClassEnrollments_Result')
    ALTER TABLE dbo.TrainingClassEnrollments DROP CONSTRAINT CK_TrainingClassEnrollments_Result;
GO

UPDATE dbo.TrainingClassEnrollments
SET Result = 'NotPass'
WHERE Result = 'Fail';
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TrainingClassEnrollments_Result')
BEGIN
    ALTER TABLE dbo.TrainingClassEnrollments
        ADD CONSTRAINT CK_TrainingClassEnrollments_Result
            CHECK (Result IS NULL OR Result IN ('Pass', 'NotPass'));
END;
GO

UPDATE dbo.TrainingClasses
SET TrainerId = CreatedBy
WHERE TrainerId IS NULL
  AND TrainingType = 'STORE_TRAINING'
  AND CreatedBy IS NOT NULL;
GO

IF COL_LENGTH('dbo.TrainingClasses', 'Trainer') IS NOT NULL
BEGIN
    ALTER TABLE dbo.TrainingClasses DROP COLUMN Trainer;
END;
GO

IF COL_LENGTH('dbo.Employees', 'CertificationStatus') IS NULL
BEGIN
    ALTER TABLE dbo.Employees
        ADD CertificationStatus VARCHAR(20) NOT NULL
            CONSTRAINT DF_Employees_CertificationStatus DEFAULT ('NOTCERTIFIED');
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Employees_CertificationStatus')
   AND COL_LENGTH('dbo.Employees', 'CertificationStatus') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Employees
        ADD CONSTRAINT CK_Employees_CertificationStatus
            CHECK (CertificationStatus IN ('CERTIFIED', 'NOTCERTIFIED'));
END;
GO
