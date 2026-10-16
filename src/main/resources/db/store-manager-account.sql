-- Cờ đổi mật khẩu lần đầu. Tài khoản hiện có nhận giá trị 0, không bị buộc đổi mật khẩu.
IF COL_LENGTH('dbo.Users', 'MustChangePassword') IS NULL
BEGIN
    ALTER TABLE dbo.Users
        ADD MustChangePassword BIT NOT NULL
            CONSTRAINT DF_Users_MustChangePassword DEFAULT (0);
END;
GO

-- Mỗi nhân sự chỉ là Manager của một cửa hàng.
-- Chỉ tạo khi chưa có ManagerId bị gán trùng. Không xóa dữ liệu.
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.Stores') AND name = 'UX_Stores_ManagerId'
)
AND NOT EXISTS (
    SELECT ManagerId
    FROM dbo.Stores
    WHERE ManagerId IS NOT NULL
    GROUP BY ManagerId
    HAVING COUNT(*) > 1
)
BEGIN
    CREATE UNIQUE INDEX UX_Stores_ManagerId
        ON dbo.Stores (ManagerId)
        WHERE ManagerId IS NOT NULL;
END;
GO
