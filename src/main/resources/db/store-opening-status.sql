-- Ngày khai trương và ba trạng thái cửa hàng.
-- Cửa hàng đang bật giữ ACTIVE; cửa hàng đã tắt giữ INACTIVE (không tự suy từ ngày tạo).
-- Chạy một lần trên SQL Server. Ứng dụng cũng tự bổ sung cột này lúc khởi động.

IF COL_LENGTH('Stores', 'OpeningDate') IS NULL
    ALTER TABLE Stores ADD OpeningDate DATE NULL;
GO

IF COL_LENGTH('Stores', 'Status') IS NULL
    ALTER TABLE Stores ADD Status NVARCHAR(20) NULL;
GO

UPDATE Stores
SET OpeningDate = CAST(CreatedAt AS DATE)
WHERE OpeningDate IS NULL AND CreatedAt IS NOT NULL;
GO

UPDATE Stores
SET Status = CASE WHEN IsActive = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END
WHERE Status IS NULL OR LTRIM(RTRIM(Status)) = '';
GO
