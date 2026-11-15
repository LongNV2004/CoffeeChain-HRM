document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('leaveWorkspace');
    if (!root) return;
    const api = root.dataset.api;
    const reviewApi = root.dataset.reviewApi;
    const ids = id => document.getElementById(id);
    const tableBody = ids('mlTableBody');
    const status = ids('mlStatus');
    const search = ids('mlSearch');
    const detailOverlay = ids('mlDetailOverlay');
    const suggestionsOverlay = ids('mlSuggestionsOverlay');
    const approve = ids('mlApprove');
    const reject = ids('mlReject');
    const rejectForm = ids('mlRejectForm');
    const reason = ids('mlRejectReason');
    const broadcastBlocks = ids('mlBroadcastBlocks');
    const selectAllCb = ids('mlSelectAllBroadcast');
    const bulkBtn = ids('mlBtnBulkBroadcast');
    const selectedCountSpan = ids('mlSelectedCount');
    let page = 0;
    let pages = 1;
    let detail = null;
    let busy = false;
    let listVersion = 0;
    let gapVersion = 0;
    let toastTimer = null;
    let searchTimer = null;

    function el(tag, content = '', className = '') {
        const node = document.createElement(tag);
        if (className) node.className = className;
        node.textContent = content == null ? '' : String(content);
        return node;
    }
    function date(value) {
        if (!value) return '—';
        const [year, month, day] = value.slice(0, 10).split('-');
        return `${day}/${month}/${year}`;
    }
    function time(value) { return value ? value.slice(0, 5) : '—'; }
    function todayInVietnam() {
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
        }).formatToParts(new Date());
        const values = Object.fromEntries(parts.map(p => [p.type, p.value]));
        return `${values.year}-${values.month}-${values.day}`;
    }
    function label(value) {
        return { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy' }[value] || value;
    }
    function toast(content, error = false) {
        const box = ids('mlToast');
        box.textContent = content;
        box.classList.toggle('is-error', error);
        box.hidden = false;
        if (toastTimer) clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { box.hidden = true; }, 6000);
    }
    async function get(url, options = {}) {
        const response = await fetch(url, {
            ...options, credentials: 'same-origin',
            headers: { Accept: 'application/json', ...options.headers }
        });
        const data = await response.json().catch(() => null);
        if (!response.ok || !data?.success) {
            throw new Error(data?.message || `Không tải được dữ liệu (${response.status}).`);
        }
        return data.data;
    }
    function open(overlay) {
        closeAll();
        overlay.classList.add('is-open');
        overlay.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden';
        overlay.querySelector('.ml-x')?.focus();
    }
    function closeAll() {
        [detailOverlay, suggestionsOverlay].forEach(node => {
            node.classList.remove('is-open');
            node.setAttribute('aria-hidden', 'true');
        });
        document.body.style.overflow = '';
    }
    function pill(value) {
        const node = el('span', label(value), 'ml-pill');
        node.dataset.status = value;
        return node;
    }
    async function summary() {
        try {
            const [data, records, oldGaps] = await Promise.all([
                get(`${api}/summary`), get(`${api}/replacements`), get(`${api}/gaps`)
            ]);
            ids('mlPendingCount').textContent = data.pending;
            ids('mlApprovedCount').textContent = data.approved;
            ids('mlRejectedCount').textContent = data.rejected;
            const tracked = new Set(records.map(r => r.assignmentId));
            const unresolved = records.filter(r => r.upcoming && ['OPEN', 'DEFERRED'].includes(r.status)).length;
            const oldUntracked = oldGaps.filter(g => g.upcoming && !tracked.has(g.assignmentId)).length;
            const pendingGaps = unresolved + oldUntracked;
            ids('mlGapCount').textContent = pendingGaps;
            ids('mlGapButtonCount').textContent = pendingGaps;
        } catch (error) { toast(error.message, true); }
    }
    function syncFilterButtons() {
        document.querySelectorAll('.ml-stat[data-filter]').forEach(node =>
            node.classList.toggle('is-active', node.dataset.filter === status.value));
    }
    async function loadList() {
        const version = ++listVersion;
        tableBody.replaceChildren();
        const waiting = el('tr');
        const waitCell = el('td', 'Đang tải danh sách...', 'ml-empty');
        waitCell.colSpan = 7;
        waiting.append(waitCell);
        tableBody.append(waiting);
        syncFilterButtons();
        const params = new URLSearchParams({ page: String(page), keyword: search.value.trim() });
        if (status.value) params.set('status', status.value);
        const from = ids('mlFrom');
        const to = ids('mlTo');
        if (from?.value && to?.value && to.value < from.value) {
            tableBody.replaceChildren();
            const row = el('tr');
            const cell = el('td', 'Ngày kết thúc bộ lọc phải từ ngày bắt đầu trở đi.', 'ml-empty');
            cell.colSpan = 7;
            row.append(cell);
            tableBody.append(row);
            toast('Ngày kết thúc bộ lọc phải từ ngày bắt đầu trở đi.', true);
            return;
        }
        if (from?.value) params.set('fromDate', from.value);
        if (to?.value) params.set('toDate', to.value);
        try {
            const result = await get(`${api}/requests?${params}`);
            if (version !== listVersion) return;
            tableBody.replaceChildren();
            pages = Math.max(1, result.totalPages);
            if (result.items.length === 0) {
                const row = el('tr');
                const cell = el('td', 'Không có đơn phù hợp.', 'ml-empty');
                cell.colSpan = 7;
                row.append(cell);
                tableBody.append(row);
            }
            result.items.forEach(item => {
                const row = el('tr');
                row.append(el('td', `${item.employeeName} · ${item.employeeCode || ''}`, 'ml-person'));
                row.append(el('td', item.leaveTypeLabel || 'Nghỉ phép năm'));
                row.append(el('td', `${date(item.startDate)} – ${date(item.endDate)}`));
                row.append(el('td', item.requestedWorkDays == null ? '—' : String(item.requestedWorkDays)));
                const reasonCell = el('td', item.reason || '—', 'ml-reason');
                reasonCell.title = item.reason || '';
                row.append(reasonCell);
                const statusCell = el('td');
                statusCell.append(pill(item.status));
                row.append(statusCell);
                const actions = el('td');
                const button = el('button', 'Chi tiết →', 'ml-inline');
                button.type = 'button';
                button.addEventListener('click', () => loadDetail(item.id));
                actions.append(button);
                row.append(actions);
                tableBody.append(row);
            });
            ids('mlResultCount').textContent = `Trang ${page + 1}/${pages} · ${result.totalElements} đơn`;
            renderListPages();
        } catch (error) {
            if (version !== listVersion) return;
            tableBody.replaceChildren();
            const row = el('tr');
            const cell = el('td', error.message, 'ml-empty');
            cell.colSpan = 7;
            row.append(cell);
            tableBody.append(row);
            ids('mlPages').replaceChildren();
            toast(error.message, true);
        }
    }
    function detailField(container, title, value, full = false) {
        const node = el('div', '', full ? 'ml-field full' : 'ml-field');
        node.append(el('small', title), el('p', value));
        container.append(node);
    }
    function renderDetail(data) {
        const request = data.request;
        const body = ids('mlDetailBody');
        body.replaceChildren();
        const grid = el('div', '', 'ml-detail-grid');
        detailField(grid, 'Nhân viên', request.employeeName);
        detailField(grid, 'Loại nghỉ', request.leaveTypeLabel || 'Nghỉ phép năm');
        detailField(grid, 'Số ca', request.requestedWorkDays == null ? '—' : String(request.requestedWorkDays));
        detailField(grid, 'Trừ phép năm', request.deductsAnnualLeave ? 'Có, khi duyệt' : 'Không');
        detailField(grid, 'Trạng thái', label(request.status));
        detailField(grid, 'Ca sớm nhất', date(request.startDate));
        detailField(grid, 'Ca muộn nhất', date(request.endDate));
        detailField(grid, 'Điện thoại', request.contactPhone || '—');
        detailField(grid, 'Lý do xin nghỉ', request.reason || '—', true);
        if (request.rejectReason) detailField(grid, 'Lý do từ chối', request.rejectReason, true);
        if (request.reviewedByName) detailField(grid, 'Người xử lý', request.reviewedByName);
        if (request.resolvedDate) detailField(grid, 'Thời điểm xử lý', String(request.resolvedDate).replace('T', ' ').slice(0, 16));
        body.append(grid);
        const approved = request.status === 'APPROVED';
        const historicalShifts = data.cancelledLeaveShifts;
        const quotaTitle = approved
            ? 'Ca đã duyệt nghỉ'
            : (request.deductsAnnualLeave ? 'Số ca phép năm dự kiến bị trừ' : 'Số ca bị ảnh hưởng');
        body.append(el('h3', quotaTitle, 'ml-section-label'));
        const quota = el('div', '', 'ml-detail-box');
        if (approved) {
            if (Array.isArray(historicalShifts)) {
                const dates = new Set(historicalShifts.map(item => item.workDate));
                quota.append(el('strong', `${historicalShifts.length} ca`));
                quota.append(el('p', 'Mỗi ca đã chọn được tính riêng, kể cả khi nhiều ca nằm cùng ngày.', 'ml-muted'));
            } else {
                quota.append(el('p', 'Chưa tải được các ca của đơn.', 'ml-muted'));
            }
        } else {
            quota.append(el('strong', `${data.workDaysToCharge} ca`));
            for (const y of data.yearlyCharges || []) {
                quota.append(el('p', `Năm ${y.year}: ${y.chargedDays} ca · Định mức ${y.totalDays} · Đã dùng ${y.usedDays} · Chờ khác ${y.pendingOtherDays} · Còn sau duyệt ${y.remainingAfterApproval}`));
            }
        }
        body.append(quota);
        body.append(el('h3', 'Ca trong đơn', 'ml-section-label'));
        const box = el('div', '', 'ml-detail-box');
        const shifts = (data.affectedShifts && data.affectedShifts.length)
            ? data.affectedShifts
            : (approved ? (historicalShifts || []) : []);
        if (!shifts.length) {
            const emptyText = approved
                ? 'Đơn không còn ca được gắn.'
                : 'Đơn chưa gắn ca làm việc.';
            box.append(el('p', emptyText));
        } else {
            const ul = el('ul');
            shifts.forEach(item => ul.append(el('li', `${date(item.workDate)} · ${item.shiftName} (${time(item.startTime)}–${time(item.endTime)})`)));
            box.append(ul);
        }
        body.append(box);
        if (data.approvalBlockedReason && request.status === 'PENDING') {
            body.append(el('p', data.approvalBlockedReason, 'ml-banner'));
        }
        const pending = request.status === 'PENDING';
        approve.hidden = !pending;
        approve.disabled = busy || !data.canApprove;
        reject.hidden = !pending;
        reject.disabled = busy;
        const actionsContainer = ids('mlDetailActions');
        if (actionsContainer) actionsContainer.hidden = !pending;
        rejectForm.hidden = true;
        reason.value = '';
    }
    async function loadDetail(id) {
        try {
            const data = await get(`${reviewApi}/${encodeURIComponent(id)}`);
            if (data.request.status === 'APPROVED') {
                try {
                    const params = new URLSearchParams({ leaveRequestId: String(data.request.id) });
                    // Stage 2 has switched affected assignments to CANCELLED after approval.
                    data.cancelledLeaveShifts = await get(`${api}/gaps?${params}`);
                } catch (error) {
                    // Never show a false 0 for approved requests when history cannot be loaded.
                    data.cancelledLeaveShifts = null;
                    toast(`Không tải được lịch sử ca đã hủy: ${error.message}`, true);
                }
            }
            detail = data;
            renderDetail(data);
            open(detailOverlay);
        } catch (error) { toast(error.message, true); }
    }
    async function review(nextStatus) {
        if (busy || !detail || detail.request.status !== 'PENDING') return;
        const input = reason.value.trim();
        if (nextStatus === 'REJECTED' && !input) {
            toast('Nhập lý do từ chối trước khi gửi.', true);
            reason.focus();
            return;
        }
        if (nextStatus === 'APPROVED' && !detail.canApprove) {
            toast('Ca đã bắt đầu, đã qua hoặc quỹ ca phép không đủ.', true);
            return;
        }
        const slotCount = detail.workDaysToCharge ?? detail.request.requestedWorkDays ?? 0;
        const accepted = await window.CoffeeDialog.confirm(nextStatus === 'APPROVED'
            ? {
                title: 'Duyệt đơn nghỉ phép',
                message: detail.request.deductsAnnualLeave
                    ? `Duyệt đơn này sẽ trừ ${slotCount} ca phép năm và chỉ hủy ${slotCount} ca đã chọn.`
                    : 'Duyệt đơn này không trừ ca phép năm. Chỉ các ca đã chọn bị hủy.',
                confirmText: 'Duyệt'
            }
            : {
                title: 'Từ chối đơn nghỉ phép',
                message: 'Xác nhận từ chối đơn này?',
                confirmText: 'Từ chối',
                tone: 'danger'
            });
        if (!accepted) return;
        busy = true;
        approve.disabled = true;
        reject.disabled = true;
        ids('mlConfirmReject').disabled = true;
        const leaveId = detail.request.id;
        try {
            const result = await get(`${reviewApi}/${encodeURIComponent(leaveId)}/review`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', [root.dataset.csrfHeader || 'X-CSRF-TOKEN']: root.dataset.csrf },
                body: JSON.stringify({ status: nextStatus, rejectReason: nextStatus === 'REJECTED' ? input : null })
            });
            detail = null;
            closeAll();
            toast(nextStatus === 'APPROVED'
                ? (result.chargedWorkDays > 0
                    ? `Đã duyệt. Trừ ${result.chargedWorkDays} ca phép, giải phóng ${result.releasedShifts} ca.`
                    : `Đã duyệt. Không trừ phép năm, giải phóng ${result.releasedShifts} ca.`)
                : 'Đã từ chối và lưu lý do.');
            await Promise.all([summary(), loadList()]);
            if (nextStatus === 'APPROVED' && result.releasedShifts > 0) await openSuggestions(leaveId);
        } catch (error) {
            toast(error.message, true);
        } finally {
            busy = false;
            ids('mlConfirmReject').disabled = false;
            if (detail) renderDetail(detail);
        }
    }
    const stage4Css = document.createElement('style');
    stage4Css.textContent = `
        .ml-phase4-confirm {padding:9px 14px;margin-top:9px;border:0;border-radius:8px;
            background:#916038;color:white;cursor:pointer;font:600 12px 'Be Vietnam Pro',Arial,sans-serif}
        .ml-phase4-defer {padding:9px 14px;margin:12px 0;border:1px solid #d8c6b2;
            border-radius:8px;background:#fff;color:#5a4033;cursor:pointer;
            font:600 12px 'Be Vietnam Pro',Arial,sans-serif}
        .ml-phase4-confirm:disabled,.ml-phase4-defer:disabled {opacity:.5;cursor:wait}
    `;
    document.head.append(stage4Css);

    async function postReplacement(url, json = null) {
        return get(url, {
            method: 'POST',
            headers: {
                [root.dataset.csrfHeader || 'X-CSRF-TOKEN']: root.dataset.csrf,
                ...(json === null ? {} : { 'Content-Type': 'application/json' })
            },
            ...(json === null ? {} : { body: JSON.stringify(json) })
        });
    }
    // ── LOGIC KÊU GỌI TĂNG CA (BROADCAST FIRST-COME, FIRST-SERVED) ──

    async function openSuggestions(requestId = null) {
        const version = ++gapVersion;
        broadcastBlocks.replaceChildren(el('p', 'Đang tải danh sách ca...', 'ml-muted'));
        if (selectAllCb) selectAllCb.checked = false;
        updateBulkButtonState();
        open(suggestionsOverlay);

        try {
            const params = requestId == null ? '' : `?${new URLSearchParams({ leaveRequestId: String(requestId) })}`;
            const [records, oldGaps] = await Promise.all([
                get(`${api}/replacements${params}`), get(`${api}/gaps${params}`)
            ]);
            if (version !== gapVersion) return;

            const tracked = new Set(records.map(r => r.assignmentId));
            const legacy = oldGaps.filter(g => !tracked.has(g.assignmentId)).map(g => ({ ...g, status: 'LEGACY' }));
            const gaps = requestId == null
                ? [...records.filter(g => g.status === 'OPEN' || g.status === 'DEFERRED'), ...legacy]
                : [...records, ...legacy];

            broadcastBlocks.replaceChildren();
            if (!gaps.length) {
                broadcastBlocks.append(el('p', 'Không có ca nào cần bố trí nhân sự thay thế.', 'ml-muted'));
                return;
            }

            gaps.forEach((gap, index) => {
                const card = el('div', '', 'ot-mgr-block');
                const isBroadcast = Boolean(gap.isOvertimeBroadcast);
                const isFilled = gap.status === 'FILLED';

                // Trạng thái: 🟢 Đã có người nhận | 🟡 Đang kêu gọi | 🔴 Chưa kêu gọi
                let statusHtml = '';
                let actionHtml = '';

                if (isFilled) {
                    card.classList.add('status-filled');
                    statusHtml = `<span class="ot-badge-filled">🟢 Đã có người nhận: <strong>${gap.replacementEmployeeName || 'Đồng nghiệp'}</strong></span>`;
                } else if (isBroadcast) {
                    card.classList.add('status-broadcasting');
                    statusHtml = `<span class="ot-badge-broadcasting">🟡 Đang kêu gọi tăng ca</span>`;
                    actionHtml = `<button type="button" class="btn-cancel-single" data-id="${gap.assignmentId}">✕ Hủy ca này</button>`;
                } else {
                    card.classList.add('status-pending');
                    statusHtml = `<span class="ot-badge-pending">🔴 Chưa kêu gọi</span>`;
                    actionHtml = `
                        <label class="cb-single-wrap">
                            <input type="checkbox" class="cb-select-shift" data-id="${gap.assignmentId}">
                        </label>
                        <button type="button" class="btn-broadcast-single" data-id="${gap.assignmentId}">📢 Kêu gọi</button>
                    `;
                }

                card.innerHTML = `
                    <div class="ot-block-head">
                        <span class="ot-block-num">${index + 1}.</span>
                        <strong class="ot-block-title">${gap.shiftName} (${time(gap.startTime)} – ${time(gap.endTime)}) · ${date(gap.workDate)}</strong>
                    </div>
                    <div class="ot-block-body">
                        <div class="ot-block-status">Trạng thái: ${statusHtml}</div>
                        <div class="ot-block-actions">${actionHtml}</div>
                    </div>
                `;

                // Event nút Hủy
                const cancelBtn = card.querySelector('.btn-cancel-single');
                if (cancelBtn) {
                    cancelBtn.addEventListener('click', async () => {
                        if (busy) return;
                        busy = true;
                        cancelBtn.disabled = true;
                        try {
                            await postReplacement(`${root.dataset.replacementApi || `${api}/replacements`}/${gap.assignmentId}/cancel-broadcast`);
                            toast('Đã hủy kêu gọi ca này.');
                            await summary();
                            await openSuggestions(requestId);
                        } catch (err) { toast(err.message, true); cancelBtn.disabled = false; }
                        finally { busy = false; }
                    });
                }

                // Event nút Kêu gọi lẻ
                const broadcastBtn = card.querySelector('.btn-broadcast-single');
                if (broadcastBtn) {
                    broadcastBtn.addEventListener('click', async () => {
                        if (busy) return;
                        const ok = await window.CoffeeDialog.confirm({
                            title: 'Kêu gọi tăng ca',
                            message: `Phát thông báo kêu gọi toàn bộ nhân viên đủ điều kiện nhận ca ${gap.shiftName} ngày ${date(gap.workDate)}?`,
                            confirmText: '📢 Phát lệnh'
                        });
                        if (!ok) return;
                        busy = true;
                        broadcastBtn.disabled = true;
                        try {
                            await postReplacement(`${root.dataset.replacementApi || `${api}/replacements`}/${gap.assignmentId}/broadcast`);
                            toast('Đã phát thông báo kêu gọi tăng ca!');
                            await summary();
                            await openSuggestions(requestId);
                        } catch (err) { toast(err.message, true); broadcastBtn.disabled = false; }
                        finally { busy = false; }
                    });
                }

                broadcastBlocks.append(card);
            });

            // Gán event cho các checkbox chọn ca
            broadcastBlocks.querySelectorAll('.cb-select-shift').forEach(cb => {
                cb.addEventListener('change', updateBulkButtonState);
            });

        } catch (error) {
            if (version === gapVersion) broadcastBlocks.replaceChildren(el('p', error.message, 'ml-banner'));
        }
    }

    function updateBulkButtonState() {
        const checked = broadcastBlocks ? broadcastBlocks.querySelectorAll('.cb-select-shift:checked') : [];
        const count = checked.length;
        if (selectedCountSpan) selectedCountSpan.textContent = count;
        if (bulkBtn) bulkBtn.disabled = count === 0 || busy;
    }

    // Event: Checkbox Chọn tất cả
    if (selectAllCb) {
        selectAllCb.addEventListener('change', () => {
            const boxes = broadcastBlocks.querySelectorAll('.cb-select-shift');
            boxes.forEach(cb => cb.checked = selectAllCb.checked);
            updateBulkButtonState();
        });
    }

    // Event: Nút Kêu gọi hàng loạt ở Footer
    if (bulkBtn) {
        bulkBtn.addEventListener('click', async () => {
            const checkedBoxes = Array.from(broadcastBlocks.querySelectorAll('.cb-select-shift:checked'));
            if (!checkedBoxes.length || busy) return;

            const ids = checkedBoxes.map(cb => cb.dataset.id);
            const ok = await window.CoffeeDialog.confirm({
                title: 'Kêu gọi tăng ca hàng loạt',
                message: `Phát thông báo kêu gọi nhân sự cho ${ids.length} ca đã chọn?`,
                confirmText: '📢 Kêu gọi ngay'
            });
            if (!ok) return;

            busy = true;
            bulkBtn.disabled = true;
            let successCount = 0;
            for (const id of ids) {
                try {
                    await postReplacement(`${root.dataset.replacementApi || `${api}/replacements`}/${id}/broadcast`);
                    successCount++;
                } catch (e) { console.error('Lỗi ca ' + id, e); }
            }
            busy = false;
            toast(`Đã phát lệnh kêu gọi cho ${successCount}/${ids.length} ca.`);
            await summary();
            await openSuggestions();
        });
    }
    const queryStatus = new URLSearchParams(window.location.search).get('status');
    if (queryStatus && ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].includes(queryStatus)) status.value = queryStatus;
    document.querySelectorAll('.ml-stat[data-filter]').forEach(button => button.addEventListener('click', () => {
        status.value = button.dataset.filter;
        page = 0;
        loadList();
    }));
    ids('mlStatGaps').addEventListener('click', () => openSuggestions());
    ids('mlOpenGaps').addEventListener('click', () => openSuggestions());
    status.addEventListener('change', () => { page = 0; loadList(); });
    ids('mlFrom')?.addEventListener('change', () => { page = 0; loadList(); });
    ids('mlTo')?.addEventListener('change', () => { page = 0; loadList(); });
    search.addEventListener('input', () => {
        if (searchTimer) clearTimeout(searchTimer);
        searchTimer = setTimeout(() => { page = 0; loadList(); }, 250);
    });
    function renderListPages() {
        // Numbered Pagination dùng chung (CoffeePager.renderPages); API tính trang từ 0.
        if (window.CoffeePager) {
            CoffeePager.renderPages(ids('mlPages'), page + 1, pages, target => { page = target - 1; loadList(); });
        }
    }
    ids('mlCloseDetail').addEventListener('click', closeAll);
    ids('mlCloseSuggestions').addEventListener('click', closeAll);
    ids('mlCloseSuggestionsFooter')?.addEventListener('click', closeAll);
    [detailOverlay, suggestionsOverlay].forEach(box => box.addEventListener('mousedown', event => {
        if (event.target === box) closeAll();
    }));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && (detailOverlay.classList.contains('is-open') || suggestionsOverlay.classList.contains('is-open'))) closeAll();
    });
    ids('mlApprove').addEventListener('click', () => review('APPROVED'));
    ids('mlReject').addEventListener('click', () => { rejectForm.hidden = false; reason.focus(); });
    ids('mlCancelReject').addEventListener('click', () => { rejectForm.hidden = true; reason.value = ''; });
    ids('mlConfirmReject').addEventListener('click', () => review('REJECTED'));
    summary();
    loadList();
});

