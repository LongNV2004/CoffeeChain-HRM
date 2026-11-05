-- Phân loại hợp đồng: không xác định thời hạn không có ngày kết thúc.
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH(N'dbo.EmploymentContracts', N'ContractTerm') IS NULL
    ALTER TABLE dbo.EmploymentContracts ADD ContractTerm NVARCHAR(40) NULL;
GO

UPDATE dbo.EmploymentContracts
SET ContractTerm = CASE
        WHEN EndDate IS NULL THEN N'KHONG_XAC_DINH_THOI_HAN'
        ELSE N'XAC_DINH_THOI_HAN'
    END
WHERE ContractTerm IS NULL;
GO
