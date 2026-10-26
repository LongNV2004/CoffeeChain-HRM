-- Nhu cầu nhân sự theo vị trí của một ca trong một thứ.
-- Vị trí là kỹ năng đào tạo đang có (TrainingSkills), không tạo danh mục mới.
-- Ca đã cấu hình trước khi có bảng này không có dòng nhu cầu và vẫn dùng tổng chỉ tiêu.
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.ShiftSlotPositionNeeds', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ShiftSlotPositionNeeds (
        NeedId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ShiftSlotPositionNeeds PRIMARY KEY,
        LimitId INT NOT NULL,
        SkillId INT NOT NULL,
        RequiredCount INT NOT NULL,
        CONSTRAINT UQ_ShiftSlotPositionNeeds_Limit_Skill UNIQUE (LimitId, SkillId),
        CONSTRAINT FK_ShiftSlotPositionNeeds_Limit FOREIGN KEY (LimitId) REFERENCES dbo.ShiftSlotLimits (LimitId),
        CONSTRAINT FK_ShiftSlotPositionNeeds_Skill FOREIGN KEY (SkillId) REFERENCES dbo.TrainingSkills (SkillId),
        CONSTRAINT CK_ShiftSlotPositionNeeds_RequiredCount CHECK (RequiredCount >= 0)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ShiftSlotPositionNeeds_LimitId'
      AND object_id = OBJECT_ID(N'dbo.ShiftSlotPositionNeeds')
)
BEGIN
    CREATE INDEX IX_ShiftSlotPositionNeeds_LimitId
        ON dbo.ShiftSlotPositionNeeds (LimitId);
END;
GO

-- Đăng ký cũ để SkillId trống: vẫn tính vào tổng chỉ tiêu, không tính vào một vị trí.
IF COL_LENGTH(N'dbo.WorkAvailabilities', N'SkillId') IS NULL
BEGIN
    ALTER TABLE dbo.WorkAvailabilities ADD SkillId INT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = N'FK_WorkAvailabilities_Skill'
      AND parent_object_id = OBJECT_ID(N'dbo.WorkAvailabilities')
)
BEGIN
    ALTER TABLE dbo.WorkAvailabilities
        ADD CONSTRAINT FK_WorkAvailabilities_Skill
            FOREIGN KEY (SkillId) REFERENCES dbo.TrainingSkills (SkillId);
END;
GO
