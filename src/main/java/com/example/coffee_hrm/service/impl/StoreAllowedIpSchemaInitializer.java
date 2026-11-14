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
 * Tạo bảng địa chỉ IP được phép chấm công của cửa hàng (chấm công xác thực theo IP mạng).
 * Chỉ thêm bảng mới; không xóa hay sửa cột Latitude/Longitude cũ của Stores và Attendances.
 * Cùng nội dung với {@code db/store-allowed-ips.sql}.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class StoreAllowedIpSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.StoreAllowedIps', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.StoreAllowedIps (
                        StoreId   INT         NOT NULL,
                        IpAddress VARCHAR(45) NOT NULL,
                        CONSTRAINT PK_StoreAllowedIps PRIMARY KEY (StoreId, IpAddress),
                        CONSTRAINT FK_StoreAllowedIps_Stores FOREIGN KEY (StoreId)
                            REFERENCES dbo.Stores (StoreId) ON DELETE CASCADE
                    );
                END
                """);
        log.info("Đã kiểm tra bảng StoreAllowedIps (IP mạng được phép chấm công).");
    }
}
