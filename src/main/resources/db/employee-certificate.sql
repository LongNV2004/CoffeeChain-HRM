-- Chứng chỉ nhân viên: thêm ngày cấp, không đổi dữ liệu chứng chỉ đang có.
-- Không suy ra CERTIFIED từ chứng chỉ kỹ năng cũ (Pha chế, Phục vụ, Thu ngân).
-- Không xóa TrainingSkills, TrainingClassSkills hay lịch sử lớp đào tạo.

IF COL_LENGTH(N'dbo.Employees', N'CertificateIssuedOn') IS NULL
BEGIN
    ALTER TABLE dbo.Employees ADD CertificateIssuedOn DATE NULL;
END;
GO
