-- Dữ liệu thử nghiệm cho chức năng Báo cáo cửa hàng + Duyệt + Thanh toán lương.
-- Tạo MỘT cửa hàng test riêng (không đụng tới cửa hàng thật) với chấm công đầy đủ của tháng 10/2026.
--   sqlcmd -S localhost -U sa -P sa -C -I -d CoffeeHRM -f 65001 -i seed-payroll-test-data.sql
-- Xoá toàn bộ dữ liệu test: seed-payroll-test-cleanup.sql
--
-- Tài khoản (mật khẩu giống tài khoản staff3 hiện có, 123456):
--   qltest  — Quản lý cửa hàng test
--   nvtest1, nvtest2, nvtest3 — Nhân viên
-- Kịch bản: qltest mở "Danh sách chấm công và lương" tháng 10/2026 → Tính lương → "Báo cáo cửa hàng" → Gửi;
--           admin duyệt → thanh toán; nvtest* xem "Quản lý lương".
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;

DECLARE @StoreName NVARCHAR(100) = N'CoffeeHRM Test - Thanh toán lương';
IF EXISTS (SELECT 1 FROM dbo.Stores WHERE StoreName = @StoreName)
BEGIN
    PRINT N'Dữ liệu test đã có. Chạy seed-payroll-test-cleanup.sql trước nếu muốn tạo lại.';
    RETURN;
END;

DECLARE @PasswordHash NVARCHAR(255) = (SELECT PasswordHash FROM dbo.Users WHERE Username = 'staff3');
IF @PasswordHash IS NULL
BEGIN
    PRINT N'Không tìm thấy tài khoản staff3 để lấy mật khẩu mẫu.';
    RETURN;
END;

BEGIN TRANSACTION;

-- Cửa hàng (toạ độ giống cửa hàng 1 để chấm công GPS dùng được nếu cần).
INSERT INTO dbo.Stores (StoreName, Address, IsActive, Status, OpeningDate, Latitude, Longitude, TotalLeaveDays, TotalLeaveSlots)
VALUES (@StoreName, N'Dữ liệu thử nghiệm — không phải cửa hàng thật', 1, 'ACTIVE', '2026-09-01',
        20.9774030, 105.8331085, 12, 10);
DECLARE @StoreId INT = SCOPE_IDENTITY();

INSERT INTO dbo.Shifts (StoreId, ShiftName, StartTime, EndTime) VALUES (@StoreId, N'Ca Sáng', '07:00', '12:00');
DECLARE @Morning INT = SCOPE_IDENTITY();
INSERT INTO dbo.Shifts (StoreId, ShiftName, StartTime, EndTime) VALUES (@StoreId, N'Ca Chiều', '12:00', '17:00');
DECLARE @Afternoon INT = SCOPE_IDENTITY();
INSERT INTO dbo.Shifts (StoreId, ShiftName, StartTime, EndTime) VALUES (@StoreId, N'Ca Tối', '17:00', '22:00');
DECLARE @Evening INT = SCOPE_IDENTITY();

-- Nhân sự: 1 quản lý + 3 nhân viên, đều đã có chứng chỉ.
DECLARE @People TABLE (Idx INT, FullName NVARCHAR(100), Phone VARCHAR(20), Email VARCHAR(100), CitizenId NVARCHAR(20),
                       Username NVARCHAR(50), RoleId INT, Salary DECIMAL(18,2), IsManager BIT, EmployeeId INT NULL);
INSERT INTO @People (Idx, FullName, Phone, Email, CitizenId, Username, RoleId, Salary, IsManager) VALUES
    (0, N'Lê Quản Lý Test', '0900000100', 'qltest@coffeehrm.test', '099990000100', N'qltest', 2, 30000, 1),
    (1, N'Phạm Minh An',    '0900000101', 'nvtest1@coffeehrm.test', '099990000101', N'nvtest1', 3, 25000, 0),
    (2, N'Đỗ Thu Hà',       '0900000102', 'nvtest2@coffeehrm.test', '099990000102', N'nvtest2', 3, 27000, 0),
    (3, N'Hoàng Gia Bảo',   '0900000103', 'nvtest3@coffeehrm.test', '099990000103', N'nvtest3', 3, 28000, 0);

DECLARE @Idx INT = 0;
WHILE @Idx <= 3
BEGIN
    INSERT INTO dbo.Employees (FullName, Phone, Email, Address, StoreId, Status, HireDate, HasCertificate,
                               CertificationStatus, CitizenId, CertificateIssuedOn, ManagementCertified,
                               ManagementCertifiedAt, DateOfBirth)
    SELECT FullName, Phone, Email, N'Hà Nội', @StoreId, N'Active', '2026-09-01', 1,
           'CERTIFIED', CitizenId, '2026-08-25', IsManager,
           CASE WHEN IsManager = 1 THEN SYSUTCDATETIME() END, DATEADD(YEAR, -22 - Idx, '2026-05-15')
    FROM @People WHERE Idx = @Idx;
    UPDATE @People SET EmployeeId = SCOPE_IDENTITY() WHERE Idx = @Idx;

    INSERT INTO dbo.Users (Username, PasswordHash, RoleId, EmployeeId, IsActive, MustChangePassword, LoginBlocked)
    SELECT Username, @PasswordHash, RoleId, EmployeeId, 1, 0, 0 FROM @People WHERE Idx = @Idx;

    -- Hợp đồng đã ký, hiệu lực 01/09/2026 – 28/02/2027 (phủ trọn tháng 10).
    INSERT INTO dbo.EmploymentContracts (ContractCode, EmployeeId, StartDate, EndDate, Salary, SigningStatus, SignedDate,
                                         JobTitle, WorkLocation, WeeklyShiftCount, ContractTerm, Superseded, ActivatedAt,
                                         InternalNote)
    SELECT 'HDTEST-' + RIGHT('000' + CAST(Idx AS VARCHAR(3)), 4), EmployeeId, '2026-09-01', '2027-02-28', Salary,
           'DaTaiBanKy', '2026-08-28',
           CASE WHEN IsManager = 1 THEN N'Quản lý cửa hàng' ELSE N'Nhân viên pha chế' END,
           @StoreName, 4, 'XAC_DINH_THOI_HAN', 0, '2026-09-01', N'Dữ liệu thử nghiệm'
    FROM @People WHERE Idx = @Idx;

    SET @Idx = @Idx + 1;
END;

UPDATE dbo.Stores SET ManagerId = (SELECT EmployeeId FROM @People WHERE Idx = 0) WHERE StoreId = @StoreId;

-- Lịch làm tháng 10/2026 (đã công bố) và chấm công đầy đủ check-in/check-out cho từng ca.
-- Mỗi nhân viên làm cách ngày (trừ Chủ nhật), xoay vòng 3 ca. Có lẫn các trường hợp:
--   đi muộn 25 phút (bị trừ 1 giờ), đi muộn 10 phút (trong 20 phút cho phép, không trừ), về sớm 30 phút (trừ 1 giờ).
DECLARE @Day DATE, @Employee INT, @Shift INT, @Start TIME, @End TIME, @Late INT, @Early INT, @Name NVARCHAR(50),
        @In DATETIME2, @Out DATETIME2, @Status NVARCHAR(20), @Assignment INT, @D INT;
SET @Idx = 1;
WHILE @Idx <= 3
BEGIN
    SET @Employee = (SELECT EmployeeId FROM @People WHERE Idx = @Idx);
    SET @Day = '2026-10-01';
    WHILE @Day <= '2026-10-31'
    BEGIN
        SET @D = DAY(@Day);
        -- DATEDIFF tính từ 01/01/1900 (Thứ Hai) nên không phụ thuộc DATEFIRST; 6 = Chủ nhật.
        IF (@D + @Idx) % 2 = 0 AND DATEDIFF(DAY, '1900-01-01', @Day) % 7 <> 6
        BEGIN
            SET @Shift = CASE (@D + @Idx) % 3 WHEN 0 THEN @Morning WHEN 1 THEN @Afternoon ELSE @Evening END;
            SELECT @Start = StartTime, @End = EndTime, @Name = ShiftName FROM dbo.Shifts WHERE ShiftId = @Shift;
            SET @Late = CASE WHEN @D % 9 = @Idx THEN 25 WHEN @D % 11 = @Idx THEN 10 ELSE 0 END;
            SET @Early = CASE WHEN @D % 13 = @Idx THEN 30 ELSE 0 END;
            SET @In = DATEADD(MINUTE, CASE WHEN @Late > 0 THEN @Late ELSE -5 END, CAST(CAST(@Day AS DATETIME) + CAST(@Start AS DATETIME) AS DATETIME2));
            SET @Out = DATEADD(MINUTE, CASE WHEN @Early > 0 THEN -@Early ELSE 3 END, CAST(CAST(@Day AS DATETIME) + CAST(@End AS DATETIME) AS DATETIME2));
            SET @Status = CASE WHEN @Late > 0 AND @Early > 0 THEN N'LateAndEarly'
                               WHEN @Late > 0 THEN N'Late'
                               WHEN @Early > 0 THEN N'EarlyLeave'
                               ELSE N'OnTime' END;

            INSERT INTO dbo.ShiftAssignments (ShiftId, EmployeeId, WorkDate, Status, IsPublished, PublishedAt, CreatedAt)
            VALUES (@Shift, @Employee, @Day, N'Assigned', 1, '2026-09-28T09:00:00', '2026-09-28T09:00:00');

            INSERT INTO dbo.Attendances (EmployeeId, WorkDate, CheckInTime, CheckOutTime, TotalHours, Status, StoreId, ShiftId,
                                         ScheduledShiftName, ScheduledStartTime, ScheduledEndTime, WorkingMinutes,
                                         LateMinutes, EarlyLeaveMinutes, CreatedAt, UpdatedAt)
            VALUES (@Employee, @Day, @In, @Out, CAST(DATEDIFF(MINUTE, @In, @Out) / 60.0 AS DECIMAL(5,2)), @Status,
                    @StoreId, @Shift, @Name, @Start, @End, DATEDIFF(MINUTE, @In, @Out), @Late, @Early, @In, @Out);
        END;
        SET @Day = DATEADD(DAY, 1, @Day);
    END;
    SET @Idx = @Idx + 1;
END;

COMMIT TRANSACTION;

SELECT p.Username AS TaiKhoan, p.FullName AS HoTen, p.EmployeeId,
       (SELECT COUNT(*) FROM dbo.ShiftAssignments sa WHERE sa.EmployeeId = p.EmployeeId) AS SoCa,
       (SELECT SUM(CASE WHEN a.LateMinutes > 20 OR a.EarlyLeaveMinutes > 20 THEN 1 ELSE 0 END)
        FROM dbo.Attendances a WHERE a.EmployeeId = p.EmployeeId) AS CaBiTruGio
FROM @People p ORDER BY p.Idx;
PRINT N'Đã tạo cửa hàng test #' + CAST(@StoreId AS NVARCHAR(10)) + N' với chấm công tháng 10/2026.';
