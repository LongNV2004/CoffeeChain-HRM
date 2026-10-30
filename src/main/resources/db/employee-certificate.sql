IF COL_LENGTH(N'dbo.Employees', N'CertificateIssuedOn') IS NULL
BEGIN
    ALTER TABLE dbo.Employees ADD CertificateIssuedOn DATE NULL;
END;
GO
