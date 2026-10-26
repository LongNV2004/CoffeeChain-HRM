-- Ảnh check-in và check-out của chấm công ca. Không xóa dữ liệu cũ.
IF COL_LENGTH(N'dbo.Attendances', N'CheckInPhotoName') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckInPhotoName NVARCHAR(255) NULL;
END;
GO

IF COL_LENGTH(N'dbo.Attendances', N'CheckOutPhotoName') IS NULL
BEGIN
    ALTER TABLE dbo.Attendances ADD CheckOutPhotoName NVARCHAR(255) NULL;
END;
GO
