document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('staffLeave');
    const form = document.getElementById('leaveCreateForm');
    if (!root || !form) return;

    const type = document.getElementById('leaveType');
    const reason = document.getElementById('leaveReason');
    const phone = document.getElementById('leavePhone');
    const preview = document.getElementById('leavePreview');
    const submit = document.getElementById('leaveSubmit');
    const historyBody = document.getElementById('leaveHistoryBody');
    const filters = document.getElementById('leaveFilters');
    const statusFilter = document.getElementById('historyStatus');
    const prev = document.getElementById('leavePrev');
    const next = document.getElementById('leaveNext');
    const pageLabel = document.getElementById('leavePageLabel');
    const toastBox = document.getElementById('leaveToast');
    const detailOverlay = document.getElementById('leaveDetailOverlay');
    const detailBody = document.getElementById('leaveDetailBody');
    const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    const slotBody = document.getElementById('leaveSlotBody');
    let page = 0;
    let totalPages = 1;
    let previewVersion = 0;
    let previewReady = false;
    let toastTimer = 0;
    let loadedSlots = [];

    function todayLocal() {
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
        }).formatToParts(new Date());
        const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
        return `${values.year}-${values.month}-${values.day}`;
    }

    function toast(message, isError = false) {
        toastBox.textContent = message;
        toastBox.classList.toggle('is-error', isError);
        toastBox.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { toastBox.hidden = true; }, 4500);
    }

    function message(value, isError = false) {
        preview.textContent = value;
        preview.classList.toggle('is-error', isError);
    }

    function isoDate(year, month, day) {
        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }

    function addDays(iso, days) {
        const [year, month, day] = iso.split('-').map(Number);
        const next = new Date(Date.UTC(year, month - 1, day + days));
        return isoDate(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate());
    }

    function mondayOf(iso) {
        const [year, month, day] = iso.split('-').map(Number);
        const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
        const offset = weekday === 0 ? -6 : 1 - weekday;
        return addDays(iso, offset);
    }

    function selectedSlots() {
        return [...slotBody.querySelectorAll('input[name="assignmentIds"]:checked')]
            .map(input => loadedSlots.find(slot => String(slot.assignmentId) === input.value))
            .filter(Boolean);
    }

    function slotLine(slot) {
        return `${formatDate(slot.workDate)} ${slot.weekday || ''} · ${slot.shiftName} ${formatTime(slot.startTime)}–${formatTime(slot.endTime)}`;
    }

    function renderSlotRows() {
        const visible = loadedSlots.filter(slot => slot.blockedReason !== 'Ca đã bắt đầu hoặc đã qua.');
        slotBody.replaceChildren();
        if (visible.length === 0) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 8;
            cell.className = 'leave-empty';
            cell.textContent = 'Không có ca chưa bắt đầu trong tuần đang xem.';
            row.append(cell);
            slotBody.append(row);
            validatePreview();
            return;
        }
        visible.forEach(slot => {
            const row = document.createElement('tr');
            if (!slot.selectable) row.className = 'leave-slot-row is-blocked';
            const pick = document.createElement('td');
            const box = document.createElement('input');
            box.type = 'checkbox';
            box.name = 'assignmentIds';
            box.value = slot.assignmentId;
            box.disabled = !slot.selectable;
            box.addEventListener('change', validatePreview);
            pick.append(box);
            row.append(pick);
            row.append(cell(formatDate(slot.workDate)));
            row.append(cell(slot.weekday));
            row.append(cell(slot.shiftName));
            row.append(cell(`${formatTime(slot.startTime)}–${formatTime(slot.endTime)}`));
            row.append(cell(slot.storeName));
            row.append(cell(slot.scheduleStatus));
            row.append(cell(slot.leaveStatus || slot.blockedReason || ''));
            slotBody.append(row);
        });
        validatePreview();
    }

    async function loadSlots(from, to) {
        slotBody.replaceChildren();
        const row = document.createElement('tr');
        const loading = document.createElement('td');
        loading.colSpan = 8;
        loading.className = 'leave-empty';
        loading.textContent = 'Đang tải ca làm việc...';
        row.append(loading);
        slotBody.append(row);
        try {
            const params = new URLSearchParams({ from, to });
            const response = await fetch(`${root.dataset.slotsUrl}?${params}`, { headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (!response.ok || !result.success) throw new Error(result.message || 'Không tải được ca làm việc.');
            loadedSlots = result.data.slots || [];
            renderSlotRows();
            if (result.data.truncated) toast('Danh sách đã được giới hạn. Hãy thu hẹp khoảng ngày.', true);
        } catch (error) {
            loadedSlots = [];
            slotBody.replaceChildren();
            const errorRow = document.createElement('tr');
            const errorCell = document.createElement('td');
            errorCell.colSpan = 8;
            errorCell.className = 'leave-empty';
            errorCell.textContent = error.message || 'Không tải được ca làm việc.';
            errorRow.append(errorCell);
            slotBody.append(errorRow);
            message(errorCell.textContent, true);
        }
    }

    async function validatePreview() {
        const version = ++previewVersion;
        previewReady = false;
        submit.disabled = true;
        const chosen = selectedSlots();
        if (!type.value || chosen.length === 0) {
            message('Chọn loại nghỉ và ít nhất một ca làm việc.');
            return;
        }
        message('Đang kiểm tra ca đã chọn và quỹ ca phép...');
        try {
            const params = new URLSearchParams({ leaveType: type.value });
            chosen.forEach(slot => params.append('assignmentIds', slot.assignmentId));
            const response = await fetch(`${root.dataset.previewUrl}?${params}`, { headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (version !== previewVersion) return;
            if (!response.ok || !result.success) throw new Error(result.message || 'Không kiểm tra được đơn.');
            const data = result.data;
            const lines = [
                `Số ca đang chọn: ${data.requestedWorkDays}.`,
                ...chosen.map(slot => slotLine(slot)),
                `Loại nghỉ: ${data.leaveTypeLabel}.`,
                'Trạng thái sau khi gửi: Chờ duyệt.',
                data.deductsAnnualLeave
                    ? `Số ca phép năm dự kiến bị trừ khi duyệt: ${data.requestedWorkDays}. Chưa trừ lúc gửi.`
                    : 'Đơn này không trừ ca phép năm.'
            ];
            (data.years || []).forEach(year => {
                lines.push(`Năm ${year.year}: xin ${year.requestedDays} ca · định mức ${year.totalDays} · đã dùng ${year.usedDays} · đang chờ ${year.pendingDays} · khả dụng ${year.availableDays} ca.`);
            });
            if (data.processNote) lines.push(data.processNote);
            message(lines.join('\n'));
            preview.style.whiteSpace = 'pre-line';
            previewReady = true;
            submit.disabled = false;
        } catch (error) {
            if (version === previewVersion) message(error.message || 'Có lỗi khi kiểm tra.', true);
        }
    }

    function showWeek(offset) {
        const monday = addDays(mondayOf(todayLocal()), offset * 7);
        loadSlots(monday, addDays(monday, 6));
    }

    type.addEventListener('change', validatePreview);
    document.getElementById('leaveThisWeek').addEventListener('click', () => showWeek(0));
    document.getElementById('leaveNextWeek').addEventListener('click', () => showWeek(1));
    showWeek(0);
    form.addEventListener('submit', event => {
        const text = reason.value.trim();
        if (!text) {
            event.preventDefault();
            message('Vui lòng nhập lý do xin nghỉ.', true);
            return;
        }
        if (phone.value.trim() && !/^[0-9 .\-]{9,20}$/.test(phone.value.trim())) {
            event.preventDefault();
            message('Số điện thoại gồm 9 đến 15 chữ số.', true);
            return;
        }
        if (selectedSlots().length === 0) {
            event.preventDefault();
            message('Vui lòng chọn ít nhất một ca làm việc.', true);
            return;
        }
        if (!previewReady) {
            event.preventDefault();
            message('Vui lòng kiểm tra lại ngày nghỉ trước khi gửi.', true);
            return;
        }
        submit.disabled = true;
    });
    function cell(text) {
        const node = document.createElement('td');
        node.textContent = text == null || text === '' ? '—' : String(text);
        return node;
    }

    function formatDate(value) {
        if (!value) return '—';
        const [year, month, day] = value.split('-');
        return day && month && year ? `${day}/${month}/${year}` : value;
    }

    function formatTime(value) {
        if (!value) return '';
        return String(value).slice(0, 5);
    }

    function formatDateTime(value) {
        if (!value) return '—';
        const [date, time] = value.split('T');
        return `${formatDate(date)}${time ? ' ' + time.slice(0, 5) : ''}`;
    }

    async function loadHistory() {
        historyBody.replaceChildren();
        const waiting = document.createElement('tr');
        const waitingCell = document.createElement('td');
        waitingCell.colSpan = 9;
        waitingCell.className = 'leave-empty';
        waitingCell.textContent = 'Đang tải danh sách...';
        waiting.append(waitingCell);
        historyBody.append(waiting);
        const params = new URLSearchParams({ page: String(page) });
        if (statusFilter.value) params.set('status', statusFilter.value);
        try {
            const response = await fetch(`${root.dataset.historyUrl}?${params}`, { headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (!response.ok || !result.success) throw new Error(result.message || 'Không tải được lịch sử.');
            historyBody.replaceChildren();
            const items = result.data.items || [];
            totalPages = Math.max(result.data.totalPages || (result.data.hasMore ? page + 2 : page + 1), 1);
            if (items.length === 0) {
                const row = document.createElement('tr');
                const empty = document.createElement('td');
                empty.colSpan = 9;
                empty.className = 'leave-empty';
                empty.textContent = 'Chưa có đơn nghỉ phép phù hợp.';
                row.append(empty);
                historyBody.append(row);
            }
            items.forEach(item => {
                const row = document.createElement('tr');
                row.append(cell(item.id));
                row.append(cell(item.leaveTypeLabel));
                row.append(cell((item.slots || []).map(slot => `${formatDate(slot.workDate)} ${slot.shiftName}`).join(', ')
                    || `${formatDate(item.startDate)} – ${formatDate(item.endDate)}`));
                row.append(cell(item.requestedWorkDays));
                row.append(cell(formatDateTime(item.createdAt)));
                const statusCell = document.createElement('td');
                const badge = document.createElement('span');
                badge.className = 'leave-history-status';
                badge.dataset.status = item.status;
                badge.textContent = item.statusLabel || item.status;
                statusCell.append(badge);
                row.append(statusCell);
                row.append(cell(item.approvedByName));
                row.append(cell(item.status === 'REJECTED' ? item.rejectReason : ''));
                const actions = document.createElement('td');
                actions.className = 'leave-actions';
                const view = document.createElement('button');
                view.type = 'button';
                view.className = 'leave-btn leave-btn-outline';
                view.textContent = 'Chi tiết';
                view.addEventListener('click', () => openDetail(item.id));
                actions.append(view);
                if (item.cancellable) {
                    const cancel = document.createElement('button');
                    cancel.type = 'button';
                    cancel.className = 'leave-btn leave-btn-danger';
                    cancel.textContent = 'Hủy';
                    cancel.addEventListener('click', () => cancelRequest(item.id));
                    actions.append(cancel);
                }
                row.append(actions);
                historyBody.append(row);
            });
            pageLabel.textContent = `Trang ${page + 1}${totalPages ? '/' + totalPages : ''} · ${result.data.totalElements ?? items.length} đơn`;
            prev.disabled = page === 0;
            next.disabled = !result.data.hasMore;
        } catch (error) {
            historyBody.replaceChildren();
            const row = document.createElement('tr');
            const errorCell = document.createElement('td');
            errorCell.colSpan = 9;
            errorCell.className = 'leave-empty';
            errorCell.textContent = error.message || 'Không tải được lịch sử.';
            row.append(errorCell);
            historyBody.append(row);
            toast(errorCell.textContent, true);
        }
    }

    function field(label, value) {
        const wrap = document.createElement('div');
        const title = document.createElement('small');
        title.textContent = label;
        const body = document.createElement('p');
        body.textContent = value == null || value === '' ? '—' : String(value);
        wrap.append(title, body);
        return wrap;
    }

    async function openDetail(id) {
        try {
            const response = await fetch(`${root.dataset.detailUrl}/${encodeURIComponent(id)}`, { headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (!response.ok || !result.success) throw new Error(result.message || 'Không tải được chi tiết.');
            const item = result.data;
            detailBody.replaceChildren();
            const grid = document.createElement('div');
            grid.className = 'leave-detail-grid';
            grid.append(
                field('Mã đơn', item.id),
                field('Loại nghỉ', item.leaveTypeLabel),
                field('Số ca', item.requestedWorkDays),
                field('Ca xin nghỉ', (item.slots || []).map(slot =>
                    `${formatDate(slot.workDate)} ${slot.weekday || ''} ${slot.shiftName} ${formatTime(slot.startTime)}–${formatTime(slot.endTime)}`).join('; ') || '—'),
                field('Trạng thái', item.statusLabel),
                field('Người xử lý', item.approvedByName),
                field('Thời điểm xử lý', formatDateTime(item.resolvedDate)),
                field('Số điện thoại', item.contactPhone)
            );
            const reasonText = document.createElement('p');
            reasonText.textContent = `Lý do: ${item.reason || '—'}`;
            detailBody.append(grid, reasonText);
            if (item.rejectReason) {
                const rejected = document.createElement('p');
                rejected.textContent = `Lý do từ chối: ${item.rejectReason}`;
                detailBody.append(rejected);
            }
            detailOverlay.classList.add('is-open');
            detailOverlay.setAttribute('aria-hidden', 'false');
        } catch (error) {
            toast(error.message || 'Không tải được chi tiết.', true);
        }
    }

    async function cancelRequest(id) {
        const accepted = window.CoffeeDialog
            ? await window.CoffeeDialog.confirm({
                title: 'Hủy đơn nghỉ phép',
                message: 'Đơn chờ duyệt sẽ được hủy và không trừ phép. Đơn đã duyệt chỉ hủy được khi các ca chưa bắt đầu, chưa có người thay và chưa có chấm công; ca phép năm sẽ được hoàn lại một lần.',
                confirmText: 'Hủy đơn',
                tone: 'danger'
            })
            : window.confirm('Hủy đơn đang chờ duyệt?');
        if (!accepted) return;
        try {
            const response = await fetch(`${root.dataset.detailUrl}/${encodeURIComponent(id)}/cancel`, {
                method: 'POST',
                headers: { Accept: 'application/json', [csrfHeader]: csrf }
            });
            const result = await response.json();
            if (!response.ok || !result.success) throw new Error(result.message || 'Không hủy được đơn.');
            toast(result.message || 'Đã hủy đơn nghỉ phép.');
            await loadHistory();
        } catch (error) {
            toast(error.message || 'Không hủy được đơn.', true);
        }
    }

    function closeDetail() {
        detailOverlay.classList.remove('is-open');
        detailOverlay.setAttribute('aria-hidden', 'true');
    }

    document.getElementById('leaveCloseDetail')?.addEventListener('click', closeDetail);
    detailOverlay.addEventListener('mousedown', event => {
        if (event.target === detailOverlay) closeDetail();
    });
    filters.addEventListener('submit', event => {
        event.preventDefault();
        page = 0;
        loadHistory();
    });
    prev.addEventListener('click', () => { if (page > 0) { page -= 1; loadHistory(); } });
    next.addEventListener('click', () => { page += 1; loadHistory(); });
    loadHistory();
});
