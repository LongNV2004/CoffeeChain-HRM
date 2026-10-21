-- Ảnh check-in / check-out đào tạo. Chạy thủ công trên database CoffeeHRM.
SET QUOTED_IDENTIFIER ON;
IF COL_LENGTH(N'dbo.TrainingAttendances', N'CheckInPhotoName') IS NULL
BEGIN
    ALTER TABLE dbo.TrainingAttendances ADD CheckInPhotoName NVARCHAR(255) NULL;
END;
IF COL_LENGTH(N'dbo.TrainingAttendances', N'CheckOutPhotoName') IS NULL
BEGIN
    ALTER TABLE dbo.TrainingAttendances ADD CheckOutPhotoName NVARCHAR(255) NULL;
END;
