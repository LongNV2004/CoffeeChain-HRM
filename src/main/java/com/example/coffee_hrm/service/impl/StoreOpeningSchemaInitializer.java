package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.time.VietnamTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;

/**
 * Bổ sung cột ngày khai trương và trạng thái trước các runner khác đọc bảng Stores.
 * Cửa hàng đang hoạt động giữ ACTIVE; cửa hàng đã tắt giữ INACTIVE, không suy trạng thái từ ngày tạo.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class StoreOpeningSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final StoreStatusTransition storeStatusTransition;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                IF COL_LENGTH('Stores', 'OpeningDate') IS NULL
                    ALTER TABLE Stores ADD OpeningDate DATE NULL
                """);
        jdbcTemplate.execute("""
                IF COL_LENGTH('Stores', 'Status') IS NULL
                    ALTER TABLE Stores ADD Status NVARCHAR(20) NULL
                """);
        jdbcTemplate.update("""
                UPDATE Stores
                SET OpeningDate = CAST(CreatedAt AS DATE)
                WHERE OpeningDate IS NULL AND CreatedAt IS NOT NULL
                """);
        jdbcTemplate.update("""
                UPDATE Stores
                SET OpeningDate = ?
                WHERE OpeningDate IS NULL
                """, Date.valueOf(VietnamTime.today()));
        jdbcTemplate.update("""
                UPDATE Stores
                SET Status = CASE WHEN IsActive = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END
                WHERE Status IS NULL OR LTRIM(RTRIM(Status)) = ''
                """);
        int promoted = storeStatusTransition.promoteDueStores(VietnamTime.today());
        log.info("Đã kiểm tra cột trạng thái cửa hàng. Số cửa hàng đến ngày khai trương được mở: {}.", promoted);
    }
}
