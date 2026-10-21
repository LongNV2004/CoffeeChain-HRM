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
    const gapList = ids('mlGapsList');
    const candidatePanel = ids('mlCandidatePanel');
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
            ids('mlResultCount').textContent = `${result.totalElements} đơn`;
            ids('mlPageNumber').textContent = `Trang ${page + 1}/${pages}`;
            ids('mlPrevious').disabled = page === 0;
            ids('mlNext').disabled = page + 1 >= pages;
        } catch (error) {
            if (version !== listVersion) return;
            tableBody.replaceChildren();
            const row = el('tr');
            const cell = el('td', error.message, 'ml-empty');
            cell.colSpan = 7;
            row.append(cell);
            tableBody.append(row);
            ids('mlPrevious').disabled = true;
            ids('mlNext').disabled = true;
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
        detailField(grid, 'Số slot', request.requestedWorkDays == null ? '—' : String(request.requestedWorkDays));
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
            ? 'Slot ca đã duyệt nghỉ'
            : (request.deductsAnnualLeave ? 'Số slot phép năm dự kiến bị trừ' : 'Số slot bị ảnh hưởng');
        body.append(el('h3', quotaTitle, 'ml-section-label'));
        const quota = el('div', '', 'ml-detail-box');
        if (approved) {
            if (Array.isArray(historicalShifts)) {
                const dates = new Set(historicalShifts.map(item => item.workDate));
                quota.append(el('strong', `${historicalShifts.length} slot`));
                quota.append(el('p', 'Mỗi ca đã chọn tính một slot, kể cả khi nhiều ca nằm cùng ngày.', 'ml-muted'));
            } else {
                quota.append(el('p', 'Chưa tải được các ca của đơn.', 'ml-muted'));
            }
        } else {
            quota.append(el('strong', `${data.workDaysToCharge} slot`));
            for (const y of data.yearlyCharges || []) {
                quota.append(el('p', `Năm ${y.year}: ${y.chargedDays} slot · Định mức ${y.totalDays} · Đã dùng ${y.usedDays} · Chờ khác ${y.pendingOtherDays} · Còn sau duyệt ${y.remainingAfterApproval}`));
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
            toast('Ca đã bắt đầu, đã qua hoặc quỹ slot phép không đủ.', true);
            return;
        }
        const slotCount = detail.workDaysToCharge ?? detail.request.requestedWorkDays ?? 0;
        const accepted = await window.CoffeeDialog.confirm(nextStatus === 'APPROVED'
            ? {
                title: 'Duyệt đơn nghỉ phép',
                message: detail.request.deductsAnnualLeave
                    ? `Duyệt đơn này sẽ trừ ${slotCount} slot phép năm và chỉ hủy ${slotCount} ca đã chọn.`
                    : 'Duyệt đơn này không trừ slot phép năm. Chỉ các ca đã chọn bị hủy.',
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
                    ? `Đã duyệt. Trừ ${result.chargedWorkDays} slot phép, giải phóng ${result.releasedShifts} ca.`
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
    async function confirmReplacement(gap, candidate, button) {
        if (busy) return;
        const accepted = await window.CoffeeDialog.confirm({
            title: 'Phân công người thay',
            message: `Phân công ${candidate.employeeName} thay ${gap.absentEmployeeName} vào ${date(gap.workDate)} · ${gap.shiftName}?`,
            confirmText: 'Phân công'
        });
        if (!accepted) return;
        busy = true;
        button.disabled = true;
        try {
            await postReplacement(`${api}/replacements/${encodeURIComponent(gap.assignmentId)}/confirm`, {
                leaveRequestId: gap.leaveRequestId,
                employeeId: candidate.employeeId
            });
            toast(`Đã phân công ${candidate.employeeName} vào ca ${gap.shiftName} ngày ${date(gap.workDate)}.`);
            await Promise.all([summary(), loadList()]);
            await openSuggestions(gap.leaveRequestId);
        } catch (error) {
            toast(error.message, true);
            button.disabled = false;
        } finally { busy = false; }
    }
    async function deferReplacement(gap, button) {
        if (busy) return;
        busy = true;
        button.disabled = true;
        try {
            const params = new URLSearchParams({ leaveRequestId: String(gap.leaveRequestId) });
            await postReplacement(`${api}/replacements/${encodeURIComponent(gap.assignmentId)}/defer?${params}`);
            toast('Đã lưu để Manager xử lý sau.');
            await summary();
            await openSuggestions(gap.leaveRequestId);
        } catch (error) {
            toast(error.message, true);
            button.disabled = false;
        } finally { busy = false; }
    }
    async function openSuggestions(requestId = null) {
        const version = ++gapVersion;
        gapList.replaceChildren(el('p', 'Đang tải các ca cần bố trí...', 'ml-muted'));
        candidatePanel.replaceChildren(el('p', 'Chọn một ca để xem danh sách ưu tiên.', 'ml-muted'));
        open(suggestionsOverlay);
        try {
            const params = requestId == null ? '' : `?${new URLSearchParams({ leaveRequestId: String(requestId) })}`;
            const [records, oldGaps] = await Promise.all([
                get(`${api}/replacements${params}`), get(`${api}/gaps${params}`)
            ]);
            if (version !== gapVersion) return;
            const tracked = new Set(records.map(r => r.assignmentId));
            // Preserve Phase 3 read-only suggestions for already-approved requests predating the new table.
            const legacy = oldGaps.filter(g => !tracked.has(g.assignmentId))
                .map(g => ({ ...g, status: 'LEGACY' }));
            const gaps = requestId == null
                ? [...records.filter(g => g.status === 'OPEN' || g.status === 'DEFERRED'), ...legacy]
                : [...records, ...legacy];
            gapList.replaceChildren();
            if (!gaps.length) {
                gapList.append(el('p', requestId == null
                    ? 'Không có ca đang thiếu người. Đơn duyệt trước khi bổ sung theo dõi cần đối soát riêng.'
                    : 'Chưa có ca được ghi nhận cho đơn này. Nếu đây là đơn cũ, cần đối soát ca đã hủy trước khi xác nhận.', 'ml-muted'));
            }
            for (const gap of gaps) {
                const button = el('button', '', 'ml-gap-button');
                button.type = 'button';
                const left = el('span');
                left.append(el('strong', `${date(gap.workDate)} · ${gap.shiftName}`));
                left.append(el('div', `${gap.absentEmployeeName} · ${time(gap.startTime)}–${time(gap.endTime)}`));
                const text = gap.status === 'LEGACY' ? 'Ca cũ · Xem gợi ý'
                    : gap.status === 'FILLED'
                    ? `Đã bố trí: ${gap.replacementEmployeeName || '—'}`
                    : gap.status === 'SUPERSEDED' ? 'Người thay đã nghỉ tiếp'
                        : gap.upcoming ? (gap.status === 'DEFERRED' ? 'Xử lý sau →' : 'Chọn người →') : 'Ca đã qua';
                button.append(left, el('span', text));
                button.addEventListener('click', () => {
                    gapList.querySelectorAll('.ml-gap-button').forEach(b => b.classList.remove('is-active'));
                    button.classList.add('is-active');
                    loadCandidates(gap);
                });
                gapList.append(button);
            }
            const active = gaps.findIndex(g => g.upcoming && (g.status === 'OPEN' || g.status === 'DEFERRED' || g.status === 'LEGACY'));
            const first = gapList.querySelectorAll('.ml-gap-button')[active >= 0 ? active : 0];
            first?.click();
        } catch (error) {
            if (version === gapVersion) gapList.replaceChildren(el('p', error.message, 'ml-banner'));
        }
    }
    async function loadCandidates(gap) {
        const version = ++gapVersion;
        candidatePanel.replaceChildren(el('p', 'Đang tìm nhân viên phù hợp...', 'ml-muted'));
        if (gap.status === 'FILLED') {
            candidatePanel.replaceChildren(el('p', `Ca này đã giao cho ${gap.replacementEmployeeName || 'nhân viên thay thế'}.`, 'ml-muted'));
            return;
        }
        if (gap.status === 'SUPERSEDED') {
            candidatePanel.replaceChildren(el('p', 'Người thay cũng đã được duyệt nghỉ. Hệ thống theo dõi ca thiếu ở đơn nghỉ tiếp theo, không tính hai ca hổng.', 'ml-muted'));
            return;
        }
        if (!gap.upcoming) {
            candidatePanel.replaceChildren(el('p', 'Ca đã bắt đầu/đã qua. Không thể phân công thay.', 'ml-banner'));
            return;
        }
        try {
            const params = new URLSearchParams({ leaveRequestId: String(gap.leaveRequestId) });
            const data = await get(`${api}/gaps/${encodeURIComponent(gap.assignmentId)}/suggestions?${params}`);
            if (version !== gapVersion) return;
            candidatePanel.replaceChildren();
            candidatePanel.append(el('h3', `Gợi ý cho ${gap.shiftName} · ${date(gap.workDate)}`));
            if (gap.status === 'LEGACY') {
                candidatePanel.append(el('p', 'Ca của đơn cũ: có thể xem gợi ý nhưng cần đối soát nguồn ca hủy trước khi xác nhận.', 'ml-banner'));
            } else {
                const deferButton = el('button', 'Để trống, xử lý sau', 'ml-phase4-defer');
                deferButton.type = 'button';
                deferButton.addEventListener('click', () => deferReplacement(gap, deferButton));
                candidatePanel.append(deferButton);
            }
            const candidates = data.candidates || [];
            if (candidates.length === 0) {
                candidatePanel.append(el('p', 'Chưa tìm thấy nhân viên phù hợp. Manager cần chủ động sắp xếp.', 'ml-muted'));
                return;
            }
            for (const tier of [1, 2]) {
                const group = candidates.filter(c => c.priorityLevel === tier);
                candidatePanel.append(el('p', tier === 1
                    ? 'Ưu tiên 1 — Đã đăng ký rảnh đúng ca'
                    : 'Ưu tiên 2 — Chưa có đăng ký rảnh được duyệt cho ca này', 'ml-priority-title'));
                if (group.length === 0) candidatePanel.append(el('p', 'Không có ứng viên ở nhóm này.', 'ml-muted'));
                group.forEach(candidate => {
                    const card = el('article', '', 'ml-candidate');
                    const header = el('div', '', 'ml-candidate-head');
                    header.append(el('strong', candidate.employeeName));
                    header.append(el('span', tier === 1 ? 'Đã đăng ký rảnh' : 'Có thể nhận ca', 'ml-pill'));
                    card.append(header);
                    const details = [`${candidate.assignedShiftsThisWeek} ca đã phân công trong tuần`];
                    if (candidate.sameRole) details.push('Cùng vai trò');
                    card.append(el('p', details.join(' · ')));
                    if (gap.status !== 'LEGACY') {
                        const confirmButton = el('button', 'Xác nhận phân ca', 'ml-phase4-confirm');
                        confirmButton.type = 'button';
                        confirmButton.addEventListener('click', () => confirmReplacement(gap, candidate, confirmButton));
                        card.append(confirmButton);
                    }
                    candidatePanel.append(card);
                });
            }
        } catch (error) {
            if (version === gapVersion) candidatePanel.replaceChildren(el('p', error.message, 'ml-banner'));
        }
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
    ids('mlPrevious').addEventListener('click', () => { if (page > 0) { page--; loadList(); } });
    ids('mlNext').addEventListener('click', () => { if (page + 1 < pages) { page++; loadList(); } });
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

