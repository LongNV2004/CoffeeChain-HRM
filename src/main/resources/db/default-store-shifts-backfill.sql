-- Ca chuẩn cho cửa hàng chưa có ca. Cùng khung với cửa hàng đã vận hành.
-- Ngày thường tối đa 3 người, thứ Bảy và Chủ nhật tối đa 4.
SET NOCOUNT ON;

INSERT INTO dbo.Shifts (StoreId, ShiftName, StartTime, EndTime, CreatedAt)
SELECT s.StoreId, v.ShiftName, v.StartTime, v.EndTime, SYSDATETIME()
FROM dbo.Stores s
CROSS APPLY (VALUES
    (N'Ca Sáng',  CAST('07:00' AS time), CAST('12:00' AS time)),
    (N'Ca Chiều', CAST('12:00' AS time), CAST('17:00' AS time)),
    (N'Ca Tối',   CAST('17:00' AS time), CAST('22:00' AS time))
) v(ShiftName, StartTime, EndTime)
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.Shifts existing WHERE existing.StoreId = s.StoreId
);

INSERT INTO dbo.ShiftSlotLimits (ShiftId, DayOfWeek, MaxEmployees, UpdatedAt)
SELECT sh.ShiftId, d.DayOfWeek,
       CASE WHEN d.DayOfWeek >= 6 THEN 4 ELSE 3 END,
       SYSDATETIME()
FROM dbo.Shifts sh
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) d(DayOfWeek)
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.ShiftSlotLimits lim
    WHERE lim.ShiftId = sh.ShiftId AND lim.DayOfWeek = d.DayOfWeek
);
