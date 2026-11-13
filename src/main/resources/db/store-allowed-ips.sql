-- Chấm công xác thực theo IP mạng: danh sách IP được phép của từng cửa hàng.
-- Ứng dụng tự chạy nội dung này khi khởi động (StoreAllowedIpSchemaInitializer); có thể chạy tay.
-- Không xóa bảng, dòng hay cột cũ: Latitude/Longitude của Stores và Attendances được giữ nguyên.
IF OBJECT_ID(N'dbo.StoreAllowedIps', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.StoreAllowedIps (
        StoreId   INT         NOT NULL,
        IpAddress VARCHAR(45) NOT NULL,
        CONSTRAINT PK_StoreAllowedIps PRIMARY KEY (StoreId, IpAddress),
        CONSTRAINT FK_StoreAllowedIps_Stores FOREIGN KEY (StoreId)
            REFERENCES dbo.Stores (StoreId) ON DELETE CASCADE
    );
END;
GO
