package com.example.coffee_hrm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Thêm 3 cột phục vụ "Kêu gọi tăng ca" vào bảng ShiftAssignments.
 * - IsOvertimeBroadcast: đã phát lệnh chưa (BIT)
 * - OvertimeBroadcastAt: thời điểm phát (DATETIME2)
 * - OvertimeBroadcastByUserId: Manager nào phát (FK → Users)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class BroadcastOvertimeSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                IF COL_LENGTH('ShiftAssignments', 'IsOvertimeBroadcast') IS NULL
                    ALTER TABLE ShiftAssignments ADD IsOvertimeBroadcast BIT NOT NULL
                        CONSTRAINT DF_ShiftAssignments_IsOvertimeBroadcast DEFAULT (0)
                """);
        jdbcTemplate.execute("""
                IF COL_LENGTH('ShiftAssignments', 'OvertimeBroadcastAt') IS NULL
                    ALTER TABLE ShiftAssignments ADD OvertimeBroadcastAt DATETIME2 NULL
                """);
        jdbcTemplate.execute("""
                IF COL_LENGTH('ShiftAssignments', 'OvertimeBroadcastByUserId') IS NULL
                    ALTER TABLE ShiftAssignments ADD OvertimeBroadcastByUserId INT NULL
                        CONSTRAINT FK_ShiftAssignments_OvtBroadcastBy
                        FOREIGN KEY (OvertimeBroadcastByUserId) REFERENCES dbo.Users(UserId)
                """);
        log.info("Đã kiểm tra cấu trúc cột Broadcast Overtime trên ShiftAssignments.");
    }
}