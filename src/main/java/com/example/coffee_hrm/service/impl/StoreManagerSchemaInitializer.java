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
 * Thêm cờ đổi mật khẩu lần đầu và, nếu dữ liệu cho phép, unique index một Manager cho một cửa hàng.
 * Không xóa hoặc sửa dòng cửa hàng hiện có.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class StoreManagerSchemaInitializer implements ApplicationRunner {

    static final String MANAGER_INDEX = "UX_Stores_ManagerId";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                IF COL_LENGTH('Users', 'MustChangePassword') IS NULL
                    ALTER TABLE Users ADD MustChangePassword BIT NOT NULL
                        CONSTRAINT DF_Users_MustChangePassword DEFAULT (0)
                """);
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys.indexes
                WHERE name = ?
                  AND object_id = OBJECT_ID(N'dbo.Stores')
                """, Integer.class, MANAGER_INDEX);
        if (indexCount != null && indexCount > 0) {
            log.info("Ràng buộc {} đã có trên Stores.ManagerId.", MANAGER_INDEX);
            return;
        }
        Integer duplicates = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT ManagerId
                    FROM dbo.Stores
                    WHERE ManagerId IS NOT NULL
                    GROUP BY ManagerId
                    HAVING COUNT(*) > 1
                ) duplicated
                """, Integer.class);
        if (!shouldCreateManagerIndex(duplicates == null ? 0 : duplicates, false)) {
            log.error("Không tạo {} vì có Manager đang được gán cho nhiều cửa hàng. Dữ liệu được giữ nguyên.",
                    MANAGER_INDEX);
            return;
        }
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX UX_Stores_ManagerId
                    ON dbo.Stores (ManagerId)
                    WHERE ManagerId IS NOT NULL
                """);
        log.info("Đã tạo {} để mỗi nhân sự chỉ quản lý một cửa hàng.", MANAGER_INDEX);
    }

    static boolean shouldCreateManagerIndex(int duplicateManagers, boolean indexExists) {
        return !indexExists && duplicateManagers == 0;
    }
}
