# TÀI LIỆU CHI TIẾT VỀ TÍNH NĂNG QUẢN LÝ LỊCH LÀM VIỆC & QUY TRÌNH ĐỔI CA
**Nhánh Git:** `tuanth` | **Tác giả:** `TuanTran2404` (`tuanthhe171692@fpt.edu.vn`)  
**Dự án:** CoffeeChain-HRM (Hệ thống Quản trị Nhân sự Chuỗi Cà phê)

---

## MỤC LỤC
1. [Tổng quan tính năng (Overview)](#1-tổng-quan-tính-năng)
2. [Hướng dẫn Clone & Chạy dự án (Clone & Run Guide)](#2-hướng-dẫn-clone--chạy-dự-án)
3. [Danh sách tài khoản kiểm thử (Test Accounts)](#3-danh-sách-tài-khoản-kiểm-thử)
4. [Chi tiết các file thêm mới & thay đổi (Files Added & Modified)](#4-chi-tiết-các-file-thêm-mới--thay-đổi)
5. [Giải thích chi tiết các hàm, vị trí code & Logic nghiệp vụ](#5-giải-thích-chi-tiết-các-hàm-vị-trí-code--logic-nghiệp-vụ)
6. [Kịch bản Demo luồng hoàn chỉnh từng bước (Demo Walkthrough)](#6-kịch-bản-demo-luồng-hoàn-chỉnh-từng-bước)

---

## 1. TỔNG QUAN TÍNH NĂNG

Tính năng này giải quyết trọn vẹn nghiệp vụ **Lập lịch - Phân ca - Công bố lịch** của Quản lý cửa hàng (Store Manager) và **Xem lịch - Đổi ca linh hoạt** của Nhân viên (Staff):

1. **Ma trận Lịch tuần trực quan:**
   - Hiển thị đầy đủ các ngày trong tuần (Thứ 2 đến Chủ nhật) theo từng ca làm việc (`Ca Sáng: 07:00 – 12:00`, `Ca Chiều: 12:00 – 17:00`, `Ca Tối: 17:00 – 22:00`).
   - Hỗ trợ chuyển đổi tuần trước, tuần sau, tuần hiện tại mượt mà (`?date=yyyy-MM-dd`).
2. **Phân ca & Công bố lịch tuần (Publishing Flow):**
   - Quản lý có thể gán nhân viên vào từng ca làm việc dạng **bản nháp (Draft)**.
   - Khi hoàn thiện lịch cả tuần, Quản lý nhấn **"Công bố lịch tuần"**, trạng thái ca chuyển sang `Published`. Chỉ các ca đã công bố mới được nhân viên nhìn thấy và cho phép nhân viên thao tác xin đổi ca.
3. **Quy trình Đổi ca 2 giai đoạn (Two-Phase Shift Change Approval):**
   - Tránh tình trạng nhân viên gửi đơn lên quản lý nhưng đồng nghiệp được nhờ lại không biết hoặc không đồng ý.
   - Hệ thống hỗ trợ 3 hình thức đổi ca:
     - **Đổi chéo ca (SWAP):** Nhân viên A hoán đổi ca của mình với một ca cụ thể của đồng nghiệp B trong tuần.
     - **Chuyển ca / Nhường ca 1 chiều (TRANSFER):** Nhân viên A bận việc nhờ đồng nghiệp B làm thay ca đó giúp mình.
     - **Nhờ Quản lý tự sắp xếp (MANAGER_ASSIGN):** Nhân viên bận đột xuất và nhờ Quản lý tự tìm người thay thế.
   - **Cơ chế xác nhận:** Với hình thức SWAP và TRANSFER, đơn sẽ được gửi đến **Đồng nghiệp B** trước. Chỉ khi đồng nghiệp B bấm **"Đồng ý hỗ trợ"**, đơn mới được chuyển tiếp lên bàn làm việc của **Quản lý cửa hàng** để duyệt.
   - **Xử lý cập nhật lịch tự động:** Khi Quản lý duyệt, hệ thống tự động hoán đổi hoặc cập nhật nhân viên trên bảng phân công `ShiftAssignments`. Nhân viên cũ được giải phóng khỏi ca, nhân viên mới xuất hiện ngay trên lịch làm việc.

---

## 2. HƯỚNG DẪN CLONE & CHẠY DỰ ÁN

### Bước 1: Clone Repository & Chuyển sang nhánh `tuanth`
Mở Terminal hoặc Command Prompt:
```bash
git clone https://github.com/LongNV2004/CoffeeChain-HRM.git
cd CoffeeChain-HRM
git checkout tuanth
```

### Bước 2: Khởi tạo Cơ sở Dữ liệu (Database Setup)
1. Đảm bảo Microsoft SQL Server đang chạy (Cổng mặc định: `1433`).
2. Mở công cụ quản lý CSDL (**SQL Server Management Studio (SSMS)** hoặc **Azure Data Studio**).
3. Mở file script tổng hợp đã được chuẩn bị sẵn:
   ```
   coffee_hrm_full_setup.sql
   ```
4. Bấm **Execute (F5)** để chạy toàn bộ file.
   - Script sẽ tự tạo Database `CoffeeHRM` (nếu chưa có).
   - Tự tạo toàn bộ bảng, khóa chính, khóa ngoại, các cột migration mới.
   - Nạp sẵn dữ liệu mẫu (Cửa hàng, Ca làm việc, Nhân viên, Tài khoản người dùng và Phân ca tuần mẫu).

### Bước 3: Kiểm tra file `application.properties`
Đảm bảo file `src/main/resources/application.properties` khớp với tài khoản SQL Server của bạn:
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=CoffeeHRM;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=sa
server.port=8081
```
*(Nếu SQL Server của bạn dùng mật khẩu khác `sa`, hãy sửa `spring.datasource.password` tương ứng)*.

### Bước 4: Chạy ứng dụng Spring Boot
Chạy bằng Maven Wrapper có sẵn trong project:
- **Trên macOS / Linux:**
  ```bash
  ./mvnw spring-boot:run
  ```
- **Trên Windows:**
  ```cmd
  mvnw.cmd spring-boot:run
  ```
- Hoặc mở project bằng **IntelliJ IDEA** / **Eclipse** và chạy class `CoffeeHrmApplication.java`.

### Bước 5: Mở ứng dụng trên trình duyệt
Truy cập: [http://localhost:8081/login](http://localhost:8081/login)

---

## 3. DANH SÁCH TÀI KHOẢN KIỂM THỬ

Toàn bộ tài khoản mẫu được cấp mật khẩu mặc định là: **`123456`**

| Vai trò | Tên tài khoản (Username) | Mật khẩu | Họ và Tên | Ghi chú |
| :--- | :--- | :--- | :--- | :--- |
| **Admin** | `admin` | `123456` | Quản trị viên | Quản trị toàn hệ thống |
| **Quản lý** | `manager1` | `123456` | Nguyễn Văn Quản Lý | Quản lý cửa hàng The Coffee House Cầu Giấy |
| **Nhân viên 1** | `tuanth` | `123456` | Trần Văn Tuấn | Nhân viên pha chế / phục vụ tại cửa hàng 1 |
| **Nhân viên 2** | `staff2` | `123456` | Lê Thị Thu | Đồng nghiệp tại cửa hàng 1 (dùng để test đổi ca) |
| **Nhân viên 3** | `staff3` | `123456` | Phạm Minh Đức | Đồng nghiệp tại cửa hàng 1 |

---

## 4. CHI TIẾT CÁC FILE THÊM MỚI & THAY ĐỔI

Tổng cộng có **26 file** được bổ sung và cập nhật trên nhánh `tuanth`:

### 4.1. Controller Layer
- **[MỚI] `src/main/java/com/example/coffee_hrm/controller/ScheduleController.java`**:
  - Điều phối tất cả request liên quan đến phân ca, hiển thị lịch và quy trình đổi ca.
  - Phân quyền nghiêm ngặt với `@PreAuthorize("hasRole('MANAGER')")` cho quản lý và `@PreAuthorize("hasRole('STAFF')")` cho nhân viên.

### 4.2. Service Layer
- **[MỚI] `src/main/java/com/example/coffee_hrm/service/ScheduleService.java`**:
  - Interface định nghĩa các nghiệp vụ: lấy lịch tuần quản lý/nhân viên, phân ca, xóa ca, công bố lịch, gửi yêu cầu đổi ca, phản hồi lời mời đổi ca, duyệt/từ chối đơn đổi ca.
- **[MỚI] `src/main/java/com/example/coffee_hrm/service/impl/ScheduleServiceImpl.java`**:
  - Triển khai logic nghiệp vụ cốt lõi: kiểm tra trạng thái hoạt động nhân viên, kiểm tra trùng ca làm việc trong ngày, hoán đổi 2 chiều hoặc chuyển giao 1 chiều giữa các nhân sự, xử lý duyệt/từ chối.
- **[SỬA] `src/main/java/com/example/coffee_hrm/service/impl/DashboardServiceImpl.java`**:
  - Đồng bộ số lượng đếm yêu cầu đổi ca chờ duyệt trên Dashboard Quản lý (chỉ đếm các đơn Quản lý cần duyệt: hoặc là nhờ Quản lý tự sắp, hoặc là đồng nghiệp đã bấm đồng ý).

### 4.3. Data Transfer Objects (DTOs)
- **[MỚI] `src/main/java/com/example/coffee_hrm/dto/request/AssignShiftRequest.java`**:
  - Dữ liệu biểu mẫu phân ca mới (gồm `shiftId`, `employeeId`, `workDate`).
- **[MỚI] `src/main/java/com/example/coffee_hrm/dto/request/CreateShiftChangeRequestDto.java`**:
  - Dữ liệu gửi yêu cầu đổi ca (gồm `assignmentId`, `changeType` [SWAP/TRANSFER/MANAGER_ASSIGN], `targetAssignmentId`, `targetEmployeeId`, `reason`).
- **[MỚI] `src/main/java/com/example/coffee_hrm/dto/response/WeeklyScheduleView.java`**:
  - Chứa cấu trúc dữ liệu hiển thị lịch làm việc tuần theo lưới: thông tin cửa hàng, danh sách 7 ngày, các hàng ca làm việc, các ca có thể hoán đổi của đồng nghiệp, danh sách đơn chờ duyệt và danh sách lời mời nhận ca.
- **[SỬA] `src/main/java/com/example/coffee_hrm/dto/response/StaffDashboardView.java`**:
  - Bổ sung trường `incomingShiftChanges` để hiển thị lời mời đổi ca trực tiếp ngoài màn hình Dashboard của nhân viên.

### 4.4. Domain Entities
- **[SỬA] `src/main/java/com/example/coffee_hrm/entity/ShiftAssignment.java`**:
  - Thêm cột `isPublished` (kiểu `Boolean`) và `publishedAt` (kiểu `LocalDateTime`) để phân biệt ca nháp và ca đã công bố.
- **[SỬA] `src/main/java/com/example/coffee_hrm/entity/ShiftChangeRequest.java`**:
  - Thêm quan hệ `targetAssignment` (cho phép liên kết tới ca đối ứng khi đổi chéo).
  - Thêm `targetEmployee` (nhân viên được chỉ định nhận ca).
  - Thêm `isTargetAgreed` (đồng nghiệp đã đồng ý hay từ chối) và `targetAgreedAt`.

### 4.5. Repository Layer
- **[MỚI] `src/main/java/com/example/coffee_hrm/repository/ShiftRepository.java`**:
  - Truy vấn danh sách các ca làm việc của cửa hàng theo thứ tự thời gian bắt đầu.
- **[SỬA] `src/main/java/com/example/coffee_hrm/repository/ShiftAssignmentRepository.java`**:
  - Thêm các query tìm kiếm ca tuần theo cửa hàng (`findWeeklyAssignmentsForStore`), theo nhân viên (`findWeeklyAssignmentsForEmployee`), và kiểm tra trùng ca (`existsByEmployee_IdAndShift_IdAndWorkDateAndStatus`).
- **[SỬA] `src/main/java/com/example/coffee_hrm/repository/ShiftChangeRequestRepository.java`**:
  - Thêm query lấy danh sách đơn chờ duyệt của quản lý (đã qua bước đồng nghiệp đồng ý), danh sách lời mời đến của nhân viên, và phương thức đếm số lượng `countPendingForManager`.
- **[SỬA] `src/main/java/com/example/coffee_hrm/repository/EmployeeRepository.java`**:
  - Bổ sung query `findByIdWithStore`.

### 4.6. Security & Configuration
- **[SỬA] `src/main/java/com/example/coffee_hrm/security/SecurityConfig.java`**:
  - Cấu hình phân quyền đường dẫn: `/schedule/manager/**` dành cho quyền `ROLE_MANAGER`, `/schedule/staff/**` dành cho quyền `ROLE_STAFF`.
- **[SỬA] `src/main/resources/application.properties`**:
  - Bổ sung cấu hình reload template Thymeleaf: `spring.thymeleaf.cache=false`.
  - Giữ mật khẩu kết nối `spring.datasource.password=sa`.
- **[SỬA] `pom.xml`**:
  - Cấu hình annotation processor cho Lombok giúp trình biên dịch nhận diện `@Getter`, `@Setter`, `@Builder`.

### 4.7. Views (Thymeleaf Templates) & CSS
- **[MỚI] `src/main/resources/templates/ManagerSchedule.html`**:
  - Màn hình lịch tuần của Quản lý: xem lưới ca, thanh điều hướng tuần, nút "Phân ca mới", nút "Công bố lịch tuần", bảng yêu cầu đổi ca chờ duyệt và Modal phân công người thay thế kèm token CSRF.
- **[MỚI] `src/main/resources/templates/StaffSchedule.html`**:
  - Màn hình lịch tuần của Nhân viên: xem ca làm việc của mình và đồng nghiệp, thẻ thông báo "Lời mời đổi ca từ đồng nghiệp" (có nút Đồng ý / Từ chối), modal chọn hình thức đổi ca với icon Lucide chuẩn UI.
- **[SỬA] `src/main/resources/templates/ManagerDashboard.html` & `StaffDashboard.html`**:
  - Cập nhật số đếm thống kê và danh sách lời mời đổi ca đồng bộ với lịch.
- **[MỚI] `src/main/resources/static/css/ManagerSchedule.css` & `StaffSchedule.css`**:
  - Định kiểu bảng lịch dạng lưới hiện đại, responsive, màu sắc phân biệt rõ ràng giữa ca nháp và ca đã công bố, huy hiệu trạng thái ca làm việc.

### 4.8. Testing
- **[MỚI] `src/test/java/com/example/coffee_hrm/service/impl/ScheduleServiceImplTest.java`**:
  - Unit Test bao phủ toàn diện các kịch bản: phân ca thành công, chặn phân ca trùng lặp, công bố lịch, quy trình nhân viên gửi yêu cầu đổi ca, quy trình đồng nghiệp xác nhận và quản lý duyệt ca.

---

## 5. GIẢI THÍCH CHI TIẾT CÁC HÀM, VỊ TRÍ CODE & LOGIC NGHIỆP VỤ

### 5.1. `ScheduleController.java`
| Phương thức (Method) | Đường dẫn (Mapping) | Quyền | Mục đích & Mô tả hoạt động |
| :--- | :--- | :--- | :--- |
| `managerSchedulePage()` | `GET /schedule/manager` | `MANAGER` | Lấy tham số `?date=yyyy-MM-dd` (mặc định tuần hiện tại), gọi service dựng ma trận lịch tuần cho cửa hàng mà Quản lý phụ trách và trả về `ManagerSchedule.html`. |
| `assignShift()` | `POST /schedule/manager/assign` | `MANAGER` | Nhận form phân ca mới gồm `shiftId`, `employeeId`, `workDate`, gọi `scheduleService.assignShift()` rồi redirect về tuần đang xem. |
| `cancelAssignment()` | `POST /schedule/manager/cancel/{id}` | `MANAGER` | Hủy một ca đã phân công (chuyển trạng thái sang `Cancelled`). |
| `publishSchedule()` | `POST /schedule/manager/publish` | `MANAGER` | Chuyển tất cả các ca nháp trong tuần hiện tại sang trạng thái `isPublished = true`, đánh dấu thời điểm `publishedAt`. |
| `approveShiftChange()` | `POST /schedule/manager/requests/{id}/approve` | `MANAGER` | Nhận `requestId` và `replacementEmployeeId` (nếu có), gọi `resolveShiftChange(..., approved = true)` để chốt đổi ca và cập nhật lịch làm việc. |
| `rejectShiftChange()` | `POST /schedule/manager/requests/{id}/reject` | `MANAGER` | Quản lý từ chối yêu cầu đổi ca (`approved = false`). |
| `staffSchedulePage()` | `GET /schedule/staff` | `STAFF` | Hiển thị bảng lịch tuần cho nhân viên kèm danh sách lời mời đổi ca đang chờ nhân viên đó phản hồi. |
| `requestShiftChange()` | `POST /schedule/staff/request-change` | `STAFF` | Nhân viên gửi yêu cầu đổi ca. Tùy thuộc vào hình thức (`SWAP`, `TRANSFER`, `MANAGER_ASSIGN`) mà hệ thống gửi lời mời đến đồng nghiệp hoặc đẩy thẳng lên quản lý. |
| `respondToIncomingRequest()` | `POST /schedule/staff/requests/{id}/respond` | `STAFF` | Đồng nghiệp bấm "Đồng ý hỗ trợ" (`agreed=true`) hoặc "Từ chối" (`agreed=false`). |

---

### 5.2. `ScheduleServiceImpl.java` - Các hàm xử lý nghiệp vụ chính

#### a) `assignShift(AssignShiftRequest request, AuthenticatedUser user)`
```java
// Vị trí: ScheduleServiceImpl.java (dòng 115)
```
- **Bước 1:** Xác định cửa hàng do quản lý phụ trách qua `resolveManagerStore(user)`.
- **Bước 2:** Kiểm tra nhân viên được gán có thuộc cửa hàng này không và có đang ở trạng thái `ACTIVE` không.
- **Bước 3 (Chống trùng ca):** Gọi `shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(...)`. Nếu nhân viên đã có phân công cùng ca trong ngày thì ném ra `BusinessException("Nhân viên này đã được phân công ca làm việc này rồi.")`.
- **Bước 4:** Tạo mới `ShiftAssignment` với `status = ASSIGNED`, `isPublished = false` (bản nháp).

#### b) `publishWeeklySchedule(LocalDate weekDate, AuthenticatedUser user)`
```java
// Vị trí: ScheduleServiceImpl.java (dòng 170)
```
- Xác định ngày Thứ 2 đầu tuần (`monday`) và Chủ nhật cuối tuần (`sunday`).
- Lấy danh sách tất cả các ca trong tuần chưa công bố (`isPublished = false`).
- Cập nhật hàng loạt `sa.setIsPublished(true)` và `sa.setPublishedAt(LocalDateTime.now())`.

#### c) `requestShiftChange(CreateShiftChangeRequestDto dto, AuthenticatedUser user)`
```java
// Vị trí: ScheduleServiceImpl.java (dòng 195)
```
- **Kiểm tra hợp lệ:**
  - Ca xin đổi phải là của chính nhân viên đang đăng nhập.
  - Ca xin đổi phải là ca đã được công bố (`isPublished = true`) và ngày làm việc chưa trôi qua (`!workDate.isBefore(today)`).
  - Không được có yêu cầu đổi ca khác đang ở trạng thái chờ duyệt trên cùng ca này.
- **Xử lý theo từng loại:**
  - **`SWAP` (Đổi chéo):** Lấy ca mục tiêu của đồng nghiệp qua `targetAssignmentId`. Gán `targetAssignment` và `targetEmployee = targetAssignment.getEmployee()`. Đặt cờ `isTargetAgreed = null` (chờ đồng nghiệp phản hồi).
  - **`TRANSFER` (Chuyển ca):** Lấy nhân viên nhận thay qua `targetEmployeeId`. Đặt cờ `isTargetAgreed = null`.
  - **`MANAGER_ASSIGN` (Quản lý tự sắp):** Để `targetEmployee = null`, `isTargetAgreed = true` (chuyển thẳng đến Quản lý).

#### d) `respondToShiftChange(Integer requestId, boolean agreed, AuthenticatedUser user)`
```java
// Vị trí: ScheduleServiceImpl.java (dòng 260)
```
- Chỉ người được chỉ định (`targetEmployee`) mới có quyền phản hồi.
- Nếu `agreed == true`: Đặt `isTargetAgreed = true`, ghi nhận `targetAgreedAt = now()`. Lúc này đơn mới đủ điều kiện hiện lên danh sách chờ duyệt của Quản lý.
- Nếu `agreed == false`: Đặt `isTargetAgreed = false`, cập nhật `status = REJECTED`, bổ sung ghi chú `"[Đồng nghiệp từ chối hoán đổi]"`.

#### e) `resolveShiftChange(Integer requestId, boolean approved, Integer replacementEmployeeId, AuthenticatedUser user)`
```java
// Vị trí: ScheduleServiceImpl.java (dòng 295)
```
- Kiểm tra quyền quản lý đối với cửa hàng sở tại.
- Nếu Quản lý **Từ chối** (`approved == false`): Chuyển `status = REJECTED`.
- Nếu Quản lý **Duyệt** (`approved == true`):
  1. **Trường hợp Đổi chéo (SWAP):**
     Hoán đổi nhân viên giữa 2 ca:
     ```java
     assignment.setEmployee(targetEmp);
     targetAssignment.setEmployee(requester);
     shiftAssignmentRepository.save(assignment);
     shiftAssignmentRepository.save(targetAssignment);
     ```
  2. **Trường hợp Chuyển ca (TRANSFER):**
     Quản lý có thể duyệt chuyển cho đúng đồng nghiệp đã đồng ý, hoặc chọn một nhân viên khác từ danh sách:
     ```java
     assignment.setEmployee(targetEmp);
     shiftAssignmentRepository.save(assignment);
     ```
  3. **Trường hợp Quản lý tự sắp (MANAGER_ASSIGN):**
     Bắt buộc Quản lý phải chọn `replacementEmployeeId`. Gán nhân viên thay thế vào ca.
- Đổi trạng thái yêu cầu sang `APPROVED`, ghi nhận `resolvedBy = managerUser` và `resolvedDate = now()`.

---

## 6. KỊCH BẢN DEMO LUỒNG HOÀN CHỈNH TỪNG BƯỚC

Bạn có thể chạy thử kịch bản này từ đầu đến cuối để trình bày hoặc kiểm thử:

### Bước 1: Quản lý xem lịch & Công bố lịch tuần
1. Đăng nhập tài khoản Quản lý: **`manager1` / `123456`**.
2. Vào mục **"Lịch làm việc"** ([http://localhost:8081/schedule/manager](http://localhost:8081/schedule/manager)).
3. Bấm sang tuần tiếp theo (hoặc tuần có ca làm việc).
4. Bấm nút **"✓ Công bố lịch tuần"** để kích hoạt các ca làm việc từ bản nháp sang chính thức.

### Bước 2: Nhân viên Tuấn gửi yêu cầu đổi ca
1. Đăng xuất và đăng nhập tài khoản: **`tuanth` / `123456`**.
2. Vào mục **"Lịch làm việc"** ([http://localhost:8081/schedule/staff](http://localhost:8081/schedule/staff)).
3. Tại ô ca làm việc của Tuấn (ví dụ Ca Sáng ngày 23/09), bấm nút **"Đổi ca"**.
4. Popup hiện lên:
   - Chọn hình thức **"Đổi chéo ca với đồng nghiệp"** (chọn 1 ca của Thu) HOẶC **"Nhờ đồng nghiệp nhận thay"** (chọn Lê Thị Thu).
   - Nhập lý do: *"Nhờ Thu làm giúp mình ca này nhé"*.
   - Bấm **"Gửi yêu cầu đổi ca"**.
5. Thông báo hiển thị: *"Đã gửi lời mời đổi ca đến đồng nghiệp. Đơn sẽ được chuyển lên Quản lý khi đồng nghiệp đồng ý"*. Trạng thái đơn lúc này là: **Chờ đồng nghiệp đồng ý**.

### Bước 3: Đồng nghiệp Thu xác nhận đồng ý
1. Đăng xuất và đăng nhập tài khoản của Thu: **`staff2` / `123456`**.
2. Ngay trên **Dashboard** ([http://localhost:8081/dashboard/staff](http://localhost:8081/dashboard/staff)) hoặc trang **Lịch làm việc** ([http://localhost:8081/schedule/staff](http://localhost:8081/schedule/staff)), Thu sẽ thấy khối thông báo màu vàng:
   > 📩 **Lời mời đổi ca từ đồng nghiệp**  
   > **Trần Văn Tuấn** muốn nhờ bạn nhận thay ca / đổi ca: Ca Sáng (07:00 – 12:00) ngày 23/09/2026.  
   > *Lý do: Nhờ Thu làm giúp mình ca này nhé*
3. Thu bấm nút **"✓ Đồng ý hỗ trợ"** và xác nhận popup.
4. Hệ thống thông báo thành công. Đơn đổi ca được chuyển sang trạng thái: **Đã đồng thuận — Chờ Quản lý duyệt**.

### Bước 4: Quản lý phê duyệt đơn đổi ca
1. Đăng xuất và đăng nhập lại tài khoản Quản lý: **`manager1` / `123456`**.
2. Trên Dashboard hoặc trang **Lịch làm việc** ([http://localhost:8081/schedule/manager](http://localhost:8081/schedule/manager)), mục **"Yêu cầu đổi ca chờ duyệt"** xuất hiện đơn của Trần Văn Tuấn với huy hiệu:
   - *Hình thức:* Chuyển ca / Đổi chéo ca.
   - *Đề xuất:* Chuyển cho Lê Thị Thu nhận thay.
3. Quản lý bấm nút **"Duyệt"**.
4. Modal hiện lên hiển thị thông tin ca và người nhận thay, Quản lý bấm **"Xác nhận duyệt"**.
5. Hệ thống xử lý thành công, thông báo *"Đã phê duyệt yêu cầu đổi ca"*.

### Bước 5: Kiểm tra kết quả
- Ngay trên bảng lưới lịch tuần của Quản lý và của cả hai nhân viên:
  - Ca Sáng ngày 23/09/2026 đã đổi tên hiển thị từ **Trần Văn Tuấn** sang **Lê Thị Thu**.
  - Trần Văn Tuấn hoàn toàn không còn bị phân công ở ca đó nữa.
  - Số lượng chờ duyệt trên Dashboard trở về `0`.

---
*Tài liệu được cập nhật tự động và khớp 100% với mã nguồn trên nhánh `tuanth`.*
