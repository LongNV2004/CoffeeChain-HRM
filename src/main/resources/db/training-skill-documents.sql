-- Tài liệu đính kèm theo từng kỹ năng đào tạo. Không xóa dữ liệu hiện có.
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.TrainingSkillDocuments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TrainingSkillDocuments (
        DocumentId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        SkillId INT NOT NULL,
        StoredName VARCHAR(80) NOT NULL,
        OriginalName NVARCHAR(255) NOT NULL,
        ContentType VARCHAR(120) NOT NULL,
        SizeBytes BIGINT NOT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_TrainingSkillDocuments_CreatedAt DEFAULT (SYSDATETIME()),
        CONSTRAINT FK_TrainingSkillDocuments_Skill FOREIGN KEY (SkillId) REFERENCES dbo.TrainingSkills(SkillId)
    );
    CREATE INDEX IX_TrainingSkillDocuments_SkillId ON dbo.TrainingSkillDocuments (SkillId, DocumentId);
END
GO
