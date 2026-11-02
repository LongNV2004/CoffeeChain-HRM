package com.example.coffee_hrm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Khởi tạo cột ReliabilityScore (mặc định 100) trên bảng Employees
 * và bảng ReliabilityScoreLogs phục vụ Gamification Điểm chuyên cần & Hỗ trợ ca gấp.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ReliabilitySchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /** Lời mời hỗ trợ ca gấp (xem db/shift-cover-invitation.sql). */
    static final String SHIFT_COVER_INVITATIONS_DDL = """
            IF OBJECT_ID(N'dbo.ShiftCoverInvitations', N'U') IS NULL
            BEGIN
                CREATE TABLE dbo.ShiftCoverInvitations (
                    InvitationId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
                    RequestId INT NOT NULL,
                    EmployeeId INT NOT NULL,
                    InvitedBy INT NULL,
                    Status NVARCHAR(20) NOT NULL,
                    Urgent BIT NOT NULL CONSTRAINT DF_ShiftCoverInvitations_Urgent DEFAULT (0),
                    DeclineReason NVARCHAR(20) NULL,
                    DeclineNote NVARCHAR(255) NULL,
                    SentAt DATETIME2 NOT NULL,
                    ExpiresAt DATETIME2 NOT NULL,
                    RespondedAt DATETIME2 NULL,
                    CONSTRAINT FK_ShiftCoverInvitations_Request FOREIGN KEY (RequestId) REFERENCES dbo.ShiftChangeRequests(RequestId),
                    CONSTRAINT FK_ShiftCoverInvitations_Employee FOREIGN KEY (EmployeeId) REFERENCES dbo.Employees(EmployeeId),
                    CONSTRAINT FK_ShiftCoverInvitations_InvitedBy FOREIGN KEY (InvitedBy) REFERENCES dbo.Users(UserId),
                    CONSTRAINT UX_ShiftCoverInvitations_Request_Employee UNIQUE (RequestId, EmployeeId)
                );
                CREATE INDEX IX_ShiftCoverInvitations_Employee_Status
                    ON dbo.ShiftCoverInvitations (EmployeeId, Status);
                CREATE INDEX IX_ShiftCoverInvitations_Status_ExpiresAt
                    ON dbo.ShiftCoverInvitations (Status, ExpiresAt);
            END
            """;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                IF COL_LENGTH('Employees', 'ReliabilityScore') IS NULL
                    ALTER TABLE Employees ADD ReliabilityScore INT NOT NULL
                        CONSTRAINT DF_Employees_ReliabilityScore DEFAULT (100)
                """);
        jdbcTemplate.update("""
                UPDATE Employees
                SET ReliabilityScore = 100
                WHERE ReliabilityScore IS NULL
                """);
        jdbcTemplate.execute("""
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
                """);
        jdbcTemplate.execute(SHIFT_COVER_INVITATIONS_DDL);
        log.info("Đã kiểm tra cấu trúc dữ liệu Điểm chuyên cần & Hỗ trợ ca gấp (ReliabilityScore & ReliabilityScoreLogs).");
    }
}
