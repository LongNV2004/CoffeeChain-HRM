-- Trạng thái nhân viên "Chờ hoàn thiện hợp đồng" dùng cột Employees.Status hiện có.
-- Giá trị lưu: PendingContract. Không đổi hồ sơ cũ, không xóa hợp đồng.
IF COL_LENGTH(N'dbo.Employees', N'Status') IS NULL
    THROW 50000, N'Không tìm thấy cột Employees.Status.', 1;
GO
