package com.example.coffee_hrm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingSkillDocumentSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
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
                """);
        log.info("Đã kiểm tra bảng tài liệu kỹ năng đào tạo (TrainingSkillDocuments).");
    }
}
