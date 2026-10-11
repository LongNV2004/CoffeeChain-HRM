SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.ShiftSlotLimits', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ShiftSlotLimits (
        LimitId INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ShiftSlotLimits PRIMARY KEY,
        ShiftId INT NOT NULL,
        DayOfWeek TINYINT NOT NULL,
        MaxEmployees INT NOT NULL,
        UpdatedAt DATETIME2 NOT NULL CONSTRAINT DF_ShiftSlotLimits_UpdatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT UQ_ShiftSlotLimits_Shift_Day UNIQUE (ShiftId, DayOfWeek),
        CONSTRAINT FK_ShiftSlotLimits_Shift FOREIGN KEY (ShiftId) REFERENCES dbo.Shifts (ShiftId),
        CONSTRAINT CK_ShiftSlotLimits_DayOfWeek CHECK (DayOfWeek BETWEEN 1 AND 7),
        CONSTRAINT CK_ShiftSlotLimits_MaxEmployees CHECK (MaxEmployees >= 1)
    );
END;
GO
