/* Duyệt đăng ký lịch làm việc — lọc, phân trang và chi tiết đơn (chỉ xử lý giao diện, không đổi nghiệp vụ). */
(function () {
    "use strict";

    var board = document.querySelector(".review-board");
    if (!board) {
        return;
    }

    var PAGE_SIZE = 10;
    var STATUS_ORDER = { pending: 0, approved: 1, rejected: 2 };

    var weekStart = parseIsoDate(board.dataset.weekStart);
    var shiftFilter = document.getElementById("rvShiftFilter");
    var statusFilter = document.getElementById("rvStatusFilter");
    var searchInput = document.getElementById("rvSearch");
    var weekForm = document.getElementById("rvWeekForm");
    var weekInput = document.getElementById("rvWeekInput");

    // Ô ca × ngày trong bảng tuần; mỗi ô mang data-slot dạng ;thứ|tên ca;
    var slotRows = toArray(board.querySelectorAll(".rv-slot"));
    var shiftRows = toArray(board.querySelectorAll(".rv-shift-row"));
    var chips = toArray(board.querySelectorAll(".rv-people li"));
    var regBody = board.querySelector(".rv-regs tbody");
    var regRows = toArray(board.querySelectorAll(".rv-reg-row"));
    var staffingEmpty = document.getElementById("rvStaffingEmpty");
    var staffingSummary = document.getElementById("rvStaffingSummary");
    var regEmpty = document.getElementById("rvRegEmpty");
    var regCount = document.getElementById("rvRegCount");
    var pager = document.getElementById("rvPager");
    var pageInfo = document.getElementById("rvPageInfo");
    var prevPage = document.getElementById("rvPrevPage");
    var nextPage = document.getElementById("rvNextPage");
    var slotChip = document.getElementById("rvSlotChip");
    var slotChipText = document.getElementById("rvSlotChipText");
    var slotChipClear = document.getElementById("rvSlotChipClear");

    var state = { shift: "all", status: "all", query: "", slot: null, page: 1 };

    // Đơn chờ duyệt lên đầu, giữ nguyên thứ tự gốc trong cùng trạng thái.
    if (regBody) {
        regRows
            .map(function (row, index) { return { row: row, index: index }; })
            .sort(function (a, b) {
                var sa = rank(a.row.dataset.status);
                var sb = rank(b.row.dataset.status);
                return sa !== sb ? sa - sb : a.index - b.index;
            })
            .forEach(function (entry) { regBody.appendChild(entry.row); });
        regRows = toArray(regBody.querySelectorAll(".rv-reg-row"));
    }

    // ---------- Bộ lọc ----------
    if (weekInput && weekForm) {
        weekInput.addEventListener("change", function () {
            if (weekInput.value) {
                setLoading();
                weekForm.submit();
            }
        });
    }
    if (shiftFilter) {
        shiftFilter.addEventListener("change", function () {
            state.shift = shiftFilter.value;
            if (state.slot && !shiftMatches(state.slot.shiftName)) {
                clearSlot();
            }
            state.page = 1;
            render();
        });
    }
    if (statusFilter) {
        statusFilter.addEventListener("change", function () {
            state.status = statusFilter.value;
            state.page = 1;
            render();
        });
    }
    if (searchInput) {
        searchInput.addEventListener("input", function () {
            state.query = normalize(searchInput.value.trim());
            state.page = 1;
            render();
        });
    }

    // ---------- Chọn một ca để xem đăng ký ----------
    slotRows.forEach(function (row) {
        row.addEventListener("click", function (event) {
            if (event.target.closest(".rv-person")) return; // bấm tên nhân viên mở chi tiết đơn
            selectSlot(row);
        });
        row.addEventListener("keydown", function (event) {
            if (event.target !== row) return;
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                selectSlot(row);
            }
        });
    });

    // Bấm tên nhân viên trong ô để mở chi tiết đơn đăng ký tương ứng.
    board.addEventListener("click", function (event) {
        var person = event.target.closest(".rv-person");
        if (!person) return;
        var id = person.getAttribute("data-reg-id");
        for (var i = 0; i < regRows.length; i++) {
            if (regRows[i].dataset.regId === id) {
                openDetail(regRows[i], person);
                return;
            }
        }
    });
    if (slotChipClear) {
        slotChipClear.addEventListener("click", function () {
            clearSlot();
            render();
        });
    }

    function selectSlot(row) {
        if (state.slot && state.slot.row === row) {
            clearSlot();
        } else {
            slotRows.forEach(function (item) { item.classList.remove("is-selected"); });
            row.classList.add("is-selected");
            state.slot = { row: row, key: row.dataset.slot, shiftName: row.dataset.shiftName };
            if (slotChip) {
                slotChipText.textContent = "Đang lọc: " + row.dataset.dayLabel + " · " + row.dataset.shiftLabel;
                slotChip.hidden = false;
            }
            var section = document.getElementById("rvRegistrations");
            if (section && section.scrollIntoView) {
                section.scrollIntoView({ behavior: prefersReducedMotion() ? "auto" : "smooth", block: "start" });
            }
        }
        state.page = 1;
        render();
    }

    function clearSlot() {
        state.slot = null;
        slotRows.forEach(function (item) { item.classList.remove("is-selected"); });
        if (slotChip) {
            slotChip.hidden = true;
        }
    }

    // ---------- Phân trang ----------
    if (prevPage) {
        prevPage.addEventListener("click", function () {
            state.page = Math.max(1, state.page - 1);
            render();
        });
    }
    if (nextPage) {
        nextPage.addEventListener("click", function () {
            state.page += 1;
            render();
        });
    }

    function render() {
        renderStaffing();
        renderRegistrations();
    }

    function renderStaffing() {
        var visible = 0;
        var shortCount = 0;
        var waitCount = 0;
        shiftRows.forEach(function (row) {
            var show = shiftMatches(row.dataset.shiftName);
            row.hidden = !show;
            if (!show) return;
            visible++;
            toArray(row.querySelectorAll(".rv-slot")).forEach(function (cell) {
                if (cell.dataset.state === "short") shortCount++;
                if (cell.dataset.state === "wait") waitCount++;
            });
        });
        // Tên nhân viên trong bảng tuần cũng theo bộ lọc trạng thái và tìm kiếm.
        chips.forEach(function (chip) {
            var ok = state.status === "all" || chip.dataset.status === state.status;
            if (ok && state.query) {
                ok = normalize((chip.dataset.name || "") + " " + (chip.dataset.code || "")).indexOf(state.query) !== -1;
            }
            chip.hidden = !ok;
        });
        if (staffingEmpty) {
            staffingEmpty.hidden = visible > 0 || shiftRows.length === 0;
        }
        if (staffingSummary) {
            var parts = [];
            if (shortCount > 0) parts.push(shortCount + " ca thiếu người");
            if (waitCount > 0) parts.push(waitCount + " ca thiếu người đang có đơn chờ duyệt");
            staffingSummary.textContent = parts.length ? "· " + parts.join(" · ") + "." : (visible > 0 ? "· Không có ca thiếu người." : "");
        }
    }

    function renderRegistrations() {
        if (!regRows.length) {
            return;
        }
        var matched = regRows.filter(registrationMatches);
        var pages = Math.max(1, Math.ceil(matched.length / PAGE_SIZE));
        state.page = Math.min(state.page, pages);
        var from = (state.page - 1) * PAGE_SIZE;
        var to = from + PAGE_SIZE;

        regRows.forEach(function (row) { row.hidden = true; });
        matched.forEach(function (row, index) { row.hidden = index < from || index >= to; });

        if (regEmpty) regEmpty.hidden = matched.length > 0;
        if (regCount) {
            regCount.textContent = matched.length === regRows.length
                ? regRows.length + " đơn trong tuần"
                : matched.length + " / " + regRows.length + " đơn khớp bộ lọc";
        }
        if (pager) {
            pager.hidden = pages <= 1;
            pageInfo.textContent = "Trang " + state.page + "/" + pages;
            prevPage.disabled = state.page <= 1;
            nextPage.disabled = state.page >= pages;
        }
    }

    function registrationMatches(row) {
        var slots = row.dataset.slots || "";
        if (state.status !== "all" && row.dataset.status !== state.status) {
            return false;
        }
        if (state.slot && slots.indexOf(state.slot.key) === -1) {
            return false;
        }
        if (state.shift !== "all") {
            var any = parseSlots(slots).some(function (slot) { return shiftMatches(slot.shift.toLowerCase()); });
            if (!any) {
                return false;
            }
        }
        if (state.query) {
            var haystack = normalize((row.dataset.name || "") + " " + (row.dataset.code || ""));
            if (haystack.indexOf(state.query) === -1) {
                return false;
            }
        }
        return true;
    }

    function shiftMatches(shiftName) {
        return state.shift === "all" || (shiftName || "").indexOf(state.shift) !== -1;
    }

    // ---------- Chi tiết đơn ----------
    var modal = document.getElementById("rvDetail");
    var lastTrigger = null;

    board.addEventListener("click", function (event) {
        var button = event.target.closest(".rv-detail-btn");
        if (button) {
            openDetail(button.closest(".rv-reg-row"), button);
        }
    });

    if (modal) {
        modal.addEventListener("click", function (event) {
            if (event.target.hasAttribute("data-close")) closeDetail();
        });
        document.getElementById("rvDetailClose").addEventListener("click", closeDetail);
        document.getElementById("rvDetailCancel").addEventListener("click", closeDetail);
        document.addEventListener("keydown", function (event) {
            if (event.key !== "Escape" || modal.hidden) return;
            var confirmBox = document.getElementById("coffeeConfirmDialog");
            if (confirmBox && !confirmBox.hidden) return; // hộp xác nhận tự xử lý Esc
            closeDetail();
        });
    }

    function openDetail(row, trigger) {
        if (!modal || !row) return;
        var d = row.dataset;
        lastTrigger = trigger || null;

        setText("rvDetailName", d.name);
        setText("rvDetailMeta", d.cert || "");
        setText("rvDetailCode", d.code || "—");
        setText("rvDetailPosition", d.position || "—");
        setText("rvDetailSubmitted", d.submitted || "—");
        setText("rvDetailPeriod", [d.duration, d.period].filter(Boolean).join(" · ") || "—");

        var badge = document.getElementById("rvDetailStatus");
        badge.textContent = d.statusLabel || "";
        badge.className = "rv-badge " + statusClass(d.status);

        renderDetailSlots(d);

        var actions = document.getElementById("rvDetailActions");
        var pending = d.pending === "true";
        actions.hidden = !pending;
        if (pending) {
            prepareForm(document.getElementById("rvDetailApprove"), d,
                "Duyệt đơn đăng ký ca của " + d.name + "? Chỉ các ca trong đơn của nhân viên này được xếp vào lịch.");
            prepareForm(document.getElementById("rvDetailReject"), d,
                "Từ chối đơn đăng ký ca của " + d.name + "? Chỉ đơn của nhân viên này bị từ chối.");
        }

        modal.hidden = false;
        document.body.classList.add("rv-modal-open");
        modal.querySelector(".rv-modal-card").focus();
    }

    function closeDetail() {
        if (!modal || modal.hidden) return;
        modal.hidden = true;
        document.body.classList.remove("rv-modal-open");
        if (lastTrigger && typeof lastTrigger.focus === "function") lastTrigger.focus();
    }

    function prepareForm(form, d, message) {
        var keyInput = form.querySelector('input[name="registrationKey"]');
        var idInput = form.querySelector('input[name="availabilityId"]');
        var hasKey = !!d.reg;
        keyInput.value = hasKey ? d.reg : "";
        keyInput.disabled = !hasKey;
        idInput.value = hasKey ? "" : (d.availabilityId || "");
        idInput.disabled = hasKey;
        form.querySelector('button[type="submit"]').setAttribute("data-coffee-confirm", message);
    }

    function renderDetailSlots(d) {
        var tbody = document.getElementById("rvDetailSlots");
        tbody.textContent = "";
        var slots = parseSlots(d.slots || "");
        var dated = slots.filter(function (slot) { return slot.day >= 1 && slot.day <= 7; });

        if (!dated.length) {
            // Đơn cũ theo ngày cụ thể: dùng mô tả có sẵn từ máy chủ.
            var text = (d.slotsText || "").split(";").map(function (s) { return s.trim(); }).filter(Boolean);
            if (!text.length) {
                appendEmptyRow(tbody, "Không có ca nào trong tuần này.");
                return;
            }
            text.forEach(function (label) {
                var parts = label.split(":");
                appendRow(tbody, [parts[0] || label, (parts[1] || "").trim(), "—", badgeNode(d)]);
            });
            return;
        }

        dated.sort(function (a, b) { return a.day - b.day; });
        dated.forEach(function (slot) {
            var staffingRow = findSlotRow(slot);
            var dayLabel = staffingRow ? staffingRow.dataset.dayLabel : dayName(slot.day) + dateSuffix(slot.day);
            var shiftCell = document.createElement("div");
            var strong = document.createElement("strong");
            strong.textContent = slot.shift || "Ca";
            shiftCell.appendChild(strong);
            if (staffingRow && staffingRow.dataset.shiftTime) {
                var time = document.createElement("span");
                time.className = "rv-sub";
                time.textContent = staffingRow.dataset.shiftTime;
                shiftCell.appendChild(time);
            }
            var staffing = "—";
            if (staffingRow) {
                var demand = Number(staffingRow.dataset.demand || 0);
                staffing = demand > 0
                    ? staffingRow.dataset.approved + "/" + demand
                    : staffingRow.dataset.approved + " (chưa cấu hình nhu cầu)";
            }
            appendRow(tbody, [dayLabel, shiftCell, staffing, badgeNode(d)]);
        });
    }

    function findSlotRow(slot) {
        var key = ";" + slot.day + "|" + slot.shift + ";";
        for (var i = 0; i < slotRows.length; i++) {
            if (slotRows[i].dataset.slot === key) return slotRows[i];
        }
        return null;
    }

    function appendRow(tbody, cells) {
        var labels = ["Ngày", "Ca làm", "Nhân sự ca", "Trạng thái duyệt"];
        var tr = document.createElement("tr");
        cells.forEach(function (content, index) {
            var td = document.createElement("td");
            td.setAttribute("data-label", labels[index]);
            if (content instanceof Node) td.appendChild(content);
            else td.textContent = content;
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    }

    function appendEmptyRow(tbody, message) {
        var tr = document.createElement("tr");
        var td = document.createElement("td");
        td.colSpan = 4;
        td.className = "rv-sub";
        td.textContent = message;
        tr.appendChild(td);
        tbody.appendChild(tr);
    }

    function badgeNode(d) {
        var span = document.createElement("span");
        span.className = "rv-badge " + statusClass(d.status);
        span.textContent = d.statusLabel || "";
        return span;
    }

    // ---------- Trạng thái đang tải ----------
    function setLoading() {
        board.classList.add("is-loading");
    }
    board.querySelectorAll(".rv-week a, .rv-card-head .rv-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            if (!event.defaultPrevented && !event.metaKey && !event.ctrlKey && !event.shiftKey) setLoading();
        });
    });
    // Form duyệt/từ chối chỉ thực sự gửi sau khi người dùng xác nhận trong CoffeeDialog.
    window.addEventListener("beforeunload", setLoading);
    window.addEventListener("pageshow", function () { board.classList.remove("is-loading"); });

    render();

    // ---------- Tiện ích ----------
    function parseSlots(value) {
        return value.split(";").filter(Boolean).map(function (part) {
            var sep = part.indexOf("|");
            return {
                day: Number(sep === -1 ? 0 : part.slice(0, sep)),
                shift: sep === -1 ? part : part.slice(sep + 1)
            };
        });
    }

    function rank(status) {
        return STATUS_ORDER[status] != null ? STATUS_ORDER[status] : 3;
    }

    function statusClass(status) {
        return status === "approved" ? "is-ok" : status === "pending" ? "is-wait" : "is-short";
    }

    function dayName(day) {
        return day === 7 ? "Chủ nhật" : "Thứ " + (day + 1);
    }

    function dateSuffix(day) {
        if (!weekStart) return "";
        var date = new Date(weekStart.getTime());
        date.setDate(date.getDate() + day - 1);
        return " · " + pad(date.getDate()) + "/" + pad(date.getMonth() + 1);
    }

    function parseIsoDate(value) {
        if (!value) return null;
        var p = value.split("-");
        return p.length === 3 ? new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2])) : null;
    }

    function normalize(value) {
        return (value || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/g, "d");
    }

    function pad(n) {
        return n < 10 ? "0" + n : String(n);
    }

    function setText(id, value) {
        var node = document.getElementById(id);
        if (node) node.textContent = value == null ? "" : String(value);
    }

    function toArray(list) {
        return Array.prototype.slice.call(list);
    }

    function prefersReducedMotion() {
        return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }
})();
