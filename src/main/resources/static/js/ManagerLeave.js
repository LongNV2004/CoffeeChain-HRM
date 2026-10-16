document.addEventListener('DOMContentLoaded', () => {
    const overlay = document.getElementById('managerLeaveOverlay');
    if (!overlay) return;
    const listPanel = document.getElementById('managerLeaveScroll');
    const detailPanel = document.getElementById('managerLeaveDetail');
    const list = document.getElementById('managerLeaveList');
    const detailBody = document.getElementById('managerLeaveDetailContent');
    const filter = document.getElementById('managerLeaveFilter');
    const more = document.getElementById('managerLeaveMore');
    const message = document.getElementById('managerLeaveMessage');
    const approveButton = document.getElementById('managerLeaveApprove');
    const rejectToggle = document.getElementById('managerLeaveRejectToggle');
    const rejectPanel = document.getElementById('managerLeaveRejectPanel');
    const reasonInput = document.getElementById('managerLeaveRejectReason');
    const rejectConfirm = document.getElementById('managerLeaveRejectConfirm');
    const base = overlay.dataset.apiBase;
    let page = 0;
    let selected = null;
    let busy = false;
    let loadVersion = 0;
    let activeListVersion = -1;

    function text(tag, content, className = '') {
        const element = document.createElement(tag);
        if (className) element.className = className;
        element.textContent = content == null ? '—' : String(content);
        return element;
    }

    function showMessage(content, error = false) {
        message.textContent = content;
        message.hidden = !content;
        message.classList.toggle('is-error', error);
    }

    function localDate() {
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
        }).formatToParts(new Date());
        const dict = Object.fromEntries(parts.map(piece => [piece.type, piece.value]));
        return `${dict.year}-${dict.month}-${dict.day}`;
    }

    async function request(url, options = {}) {
        const response = await fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json', ...options.headers }, ...options });
        const result = await response.json().catch(() => null);
        if (!response.ok || !result?.success) {
            throw new Error(result?.message || 'Không xử lý được yêu cầu. Vui lòng thử lại.');
        }
        return result.data;
    }

    function open() {
        overlay.classList.add('is-open');
        overlay.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden';
        showList();
        resetList();
        document.getElementById('managerLeaveClose').focus();
    }

    function close() {
        loadVersion++;
        overlay.classList.remove('is-open');
        overlay.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';
        showMessage('');
    }

    function showList() {
        selected = null;
        listPanel.hidden = false;
        detailPanel.hidden = true;
        rejectPanel.hidden = true;
    }

    function resetList() {
        page = 0;
        loadVersion++;
        list.replaceChildren();
        more.hidden = true;
        loadList(loadVersion);
    }

    function statusLabel(status) {
        return { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' }[status] || status;
    }

    function showListItem(item) {
        const card = text('article', '', 'manager-leave-card');
        const header = text('div', '', 'manager-leave-card-header');
        header.append(text('h3', item.employeeName));
        const status = text('span', statusLabel(item.status), 'manager-leave-status');
        status.dataset.status = item.status;
        header.append(status);
        card.append(header);
        card.append(text('p', `${item.startDate} → ${item.endDate}`));
        card.append(text('small', `#${item.id} · ${item.createdAt?.replace('T', ' ').slice(0, 16) || '—'}`));
        const button = text('button', 'Xem chi tiết →', 'manager-leave-btn manager-leave-btn-ghost');
        button.type = 'button';
        button.addEventListener('click', () => loadDetail(item.id));
        card.append(button);
        list.append(card);
    }

    async function loadList(version) {
        if (busy || activeListVersion === version) return;
        activeListVersion = version;
        more.disabled = true;
        try {
            const params = new URLSearchParams({ page: String(page) });
            if (filter.value) params.set('status', filter.value);
            const data = await request(`${base}?${params}`);
            if (version !== loadVersion || !overlay.classList.contains('is-open')) return;
            if (page === 0 && data.items.length === 0) list.append(text('p', 'Không có đơn nào ở trạng thái này.'));
            data.items.forEach(showListItem);
            more.hidden = !data.hasMore;
            page++;
        } catch (error) {
            if (version === loadVersion) showMessage(error.message, true);
        } finally {
            if (activeListVersion === version) {
                activeListVersion = -1;
                more.disabled = false;
            }
        }
    }

    function row(label, value) {
        const container = document.createElement('div');
        container.append(text('dt', label), text('dd', value));
        return container;
    }

    function renderDetail(data) {
        detailBody.replaceChildren();
        detailBody.append(text('h3', `Đơn nghỉ phép #${data.request.id}`));
        const dl = text('dl', '', 'manager-leave-info');
        const item = data.request;
        dl.append(row('Nhân viên', item.employeeName), row('Từ ngày', item.startDate),
            row('Đến ngày', item.endDate), row('Trạng thái', statusLabel(item.status)),
            row('Lý do xin nghỉ', item.reason));
        if (item.rejectReason) dl.append(row('Lý do từ chối', item.rejectReason));
        if (item.reviewedByName) dl.append(row('Người xử lý', item.reviewedByName));
        detailBody.append(dl);
        const quota = text('div', '', 'manager-leave-quota');
        quota.append(text('strong', `Ngày công nghỉ dự kiến: ${data.workDaysToCharge} ngày`));
        data.yearlyCharges.forEach(year => quota.append(text('p',
            `Năm ${year.year}: ${year.chargedDays} ngày · Định mức ${year.totalDays} · Đã dùng ${year.usedDays} · Đang chờ khác ${year.pendingOtherDays} · Khả dụng sau duyệt ${year.remainingAfterApproval} ngày`)));
        detailBody.append(quota);
        const section = text('section', '', 'manager-leave-shifts');
        section.append(text('h4', `Các ca sẽ giải phóng (${data.affectedShifts.length})`));
        if (data.affectedShifts.length === 0) {
            section.append(text('p', 'Hiện không có ca ASSIGNED trong khoảng nghỉ.'));
        } else {
            const ul = document.createElement('ul');
            data.affectedShifts.forEach(shift => ul.append(text('li',
                `${shift.workDate} · ${shift.shiftName} (${shift.startTime}–${shift.endTime})`)));
            section.append(ul);
        }
        detailBody.append(section);
        if (data.approvalBlockedReason) detailBody.append(text('p', data.approvalBlockedReason, 'manager-leave-blocked'));
        const isPending = item.status === 'PENDING';
        const todayOrPast = item.startDate <= localDate();
        approveButton.hidden = !isPending;
        approveButton.disabled = !data.canApprove || todayOrPast;
        rejectToggle.hidden = !isPending;
        rejectToggle.disabled = false;
        if (todayOrPast && isPending && data.canApprove) {
            detailBody.append(text('p', 'Ngày nghỉ đã bắt đầu/đã qua.', 'manager-leave-blocked'));
        }
        rejectPanel.hidden = true;
        reasonInput.value = '';
    }

    async function loadDetail(id) {
        const version = ++loadVersion;
        showMessage('');
        try {
            const data = await request(`${base}/${id}`);
            if (version !== loadVersion) return;
            selected = data;
            listPanel.hidden = true;
            detailPanel.hidden = false;
            renderDetail(data);
            detailPanel.scrollTop = 0;
        } catch (error) {
            showMessage(error.message, true);
        }
    }

    async function review(status) {
        if (!selected || busy) return;
        const item = selected.request;
        if (item.status !== 'PENDING') return;
        if (status === 'APPROVED' && (!selected.canApprove || item.startDate <= localDate())) {
            showMessage('Ngày nghỉ đã bắt đầu/đã qua hoặc quỹ phép không đủ.', true);
            approveButton.disabled = true;
            return;
        }
        const reason = reasonInput.value.trim();
        if (status === 'REJECTED' && !reason) {
            showMessage('Vui lòng nhập lý do từ chối.', true);
            reasonInput.focus();
            return;
        }
        const accepted = await window.CoffeeDialog.confirm(status === 'APPROVED'
            ? {
                title: 'Duyệt đơn nghỉ phép',
                message: `Duyệt đơn của ${item.employeeName}? Ca liên quan sẽ được giải phóng.`,
                confirmText: 'Duyệt'
            }
            : {
                title: 'Từ chối đơn nghỉ phép',
                message: 'Xác nhận từ chối đơn nghỉ phép này?',
                confirmText: 'Từ chối',
                tone: 'danger'
            });
        if (!accepted) return;
        busy = true;
        approveButton.disabled = true;
        rejectConfirm.disabled = true;
        rejectToggle.disabled = true;
        try {
            const result = await request(`${base}/${item.id}/review`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [overlay.dataset.csrfHeader || 'X-CSRF-TOKEN']: overlay.dataset.csrfToken
                },
                body: JSON.stringify({ status, rejectReason: status === 'REJECTED' ? reason : null })
            });
            const count = document.querySelector('#leave .value');
            if (count) count.textContent = String(Math.max(0, Number(count.textContent) - 1));
            if (status === 'APPROVED') {
                // Keep the released shift list visible after approval for the future replacement workflow.
                const originalShifts = selected.affectedShifts;
                renderDetail({ ...selected, request: { ...item, status: 'APPROVED' }, canApprove: false,
                    approvalBlockedReason: 'Đơn đã được duyệt.', affectedShifts: originalShifts });
                showMessage(`Đã duyệt, trừ ${result.chargedWorkDays} ngày phép và giải phóng ${result.releasedShifts} ca. Có thể bổ sung người thay ở giai đoạn 3.`);
            } else {
                renderDetail({ ...selected, request: { ...item, status: 'REJECTED', rejectReason: reason },
                    canApprove: false, approvalBlockedReason: 'Đơn đã bị từ chối.', affectedShifts: [] });
                showMessage('Đã từ chối đơn nghỉ phép.');
            }
            selected = null;
        } catch (error) {
            showMessage(error.message, true);
            approveButton.disabled = !selected.canApprove;
            rejectConfirm.disabled = false;
            rejectToggle.disabled = false;
        } finally {
            busy = false;
        }
    }

    document.querySelectorAll('[data-open-manager-leave]').forEach(button => button.addEventListener('click', open));
    document.getElementById('managerLeaveClose').addEventListener('click', close);
    overlay.addEventListener('mousedown', event => { if (event.target === overlay) close(); });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && overlay.classList.contains('is-open')) close();
    });
    document.getElementById('managerLeaveBack').addEventListener('click', () => {
        showMessage('');
        showList();
        resetList();
    });
    filter.addEventListener('change', () => {
        showMessage('');
        resetList();
    });
    more.addEventListener('click', () => loadList(loadVersion));
    approveButton.addEventListener('click', () => review('APPROVED'));
    rejectToggle.addEventListener('click', () => {
        rejectPanel.hidden = false;
        reasonInput.focus();
    });
    document.getElementById('managerLeaveRejectCancel').addEventListener('click', () => {
        rejectPanel.hidden = true;
        reasonInput.value = '';
    });
    rejectConfirm.addEventListener('click', () => review('REJECTED'));
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && selected && overlay.classList.contains('is-open')) {
            approveButton.disabled = !selected.canApprove || selected.request.startDate <= localDate();
        }
    });
});
