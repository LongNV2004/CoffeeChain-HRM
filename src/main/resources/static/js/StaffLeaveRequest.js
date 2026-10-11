document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('leaveModal');
    const drawer = document.getElementById('leaveHistoryDrawer');
    const form = document.getElementById('leaveCreateForm');
    if (!modal || !drawer || !form) return;

    const start = document.getElementById('leaveStartDate');
    const end = document.getElementById('leaveEndDate');
    const preview = document.getElementById('leavePreview');
    const submit = document.getElementById('leaveSubmit');
    const historyList = document.getElementById('leaveHistoryList');
    const moreButton = document.getElementById('leaveLoadMore');
    let page = 0;
    let previewVersion = 0;
    let previewReady = false;
    let historyLoading = false;

    function open(panel) {
        [modal, drawer].forEach(item => {
            item.classList.toggle('is-open', item === panel);
            item.setAttribute('aria-hidden', String(item !== panel));
        });
        document.body.style.overflow = 'hidden';
        panel.querySelector('.leave-close')?.focus();
    }

    function close() {
        [modal, drawer].forEach(item => {
            item.classList.remove('is-open');
            item.setAttribute('aria-hidden', 'true');
        });
        document.body.style.overflow = '';
    }

    function message(value, isError = false) {
        preview.textContent = value;
        preview.classList.toggle('is-error', isError);
    }

    function todayLocal() {
        const now = new Date();
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
        }).formatToParts(now);
        const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
        return `${values.year}-${values.month}-${values.day}`;
    }

    const today = todayLocal();
    start.min = today;
    end.min = today;
    document.getElementById('openLeaveModal')?.addEventListener('click', () => open(modal));
    document.getElementById('openLeaveHistory')?.addEventListener('click', () => {
        page = 0;
        historyList.replaceChildren();
        moreButton.hidden = true;
        open(drawer);
        loadHistory();
    });
    document.querySelectorAll('[data-leave-close]').forEach(button => button.addEventListener('click', close));
    [modal, drawer].forEach(panel => panel.addEventListener('mousedown', event => {
        if (event.target === panel) close();
    }));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && (modal.classList.contains('is-open') || drawer.classList.contains('is-open'))) close();
    });

    async function validatePreview() {
        const version = ++previewVersion;
        previewReady = false;
        submit.disabled = true;
        if (!start.value || !end.value) {
            message('Chọn ngày để kiểm tra số ngày nghỉ và quỹ phép.');
            return;
        }
        if (end.value < start.value) {
            message('Ngày kết thúc phải từ ngày bắt đầu trở đi.', true);
            return;
        }
        message('Đang kiểm tra lịch làm việc và quỹ phép...');
        try {
            const params = new URLSearchParams({ startDate: start.value, endDate: end.value });
            const response = await fetch(`${modal.dataset.previewUrl}?${params}`, { headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (version !== previewVersion) return;
            if (!response.ok || !result.success) throw new Error(result.message || 'Không kiểm tra được quỹ phép.');
            const data = result.data;
            const lines = [
                `Số ngày phép dự kiến: ${data.requestedWorkDays} ngày.`,
                ...(data.requestedWorkDays === 0
                    ? ['Chưa có ca được phân trong khoảng này. Bạn vẫn có thể gửi đơn.']
                    : [])
            ];            data.years.forEach(year => {
                lines.push(`Năm ${year.year}: xin ${year.requestedDays} ngày · định mức ${year.totalDays} · đã dùng ${year.usedDays} · đang chờ ${year.pendingDays} · khả dụng ${year.availableDays} ngày.`);
            });
            message(lines.join('\n'));
            preview.style.whiteSpace = 'pre-line';
            previewReady = true;
            submit.disabled = false;
        } catch (error) {
            if (version === previewVersion) message(error.message || 'Có lỗi khi kiểm tra.', true);
        }
    }

    start.addEventListener('change', validatePreview);
    end.addEventListener('change', validatePreview);
    form.addEventListener('submit', event => {
        if (!previewReady) {
            event.preventDefault();
            message('Vui lòng kiểm tra lại ngày nghỉ trước khi gửi.', true);
        } else {
            submit.disabled = true;
        }
    });

    function appendHistory(item) {
        const card = document.createElement('article');
        card.className = 'leave-history-card';
        const head = document.createElement('div');
        head.className = 'leave-history-card-head';
        const dates = document.createElement('strong');
        dates.textContent = `${item.startDate} → ${item.endDate}`;
        const status = document.createElement('span');
        status.className = 'leave-history-status';
        status.dataset.status = item.status;
        status.textContent = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' }[item.status] || item.status;
        head.append(dates, status);
        const reason = document.createElement('p');
        reason.textContent = item.reason || 'Không có lý do';
        const created = document.createElement('small');
        created.textContent = `Mã đơn #${item.id} · Gửi lúc: ${item.createdAt?.replace('T', ' ').slice(0, 16) || '—'}`;
        card.append(head, reason, created);

        if (item.status === 'REJECTED' && item.rejectReason) {
            const rejectionReason = document.createElement('p');
            rejectionReason.textContent = `Lý do từ chối: ${item.rejectReason}`;
            card.append(rejectionReason);
        }

        historyList.append(card);
    }

    async function loadHistory() {
        if (historyLoading) return;
        historyLoading = true;
        moreButton.disabled = true;
        try {
            const response = await fetch(`${drawer.dataset.historyUrl}?page=${page}`, { headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (!response.ok || !result.success) throw new Error(result.message || 'Không tải được lịch sử.');
            if (page === 0 && result.data.items.length === 0) {
                const empty = document.createElement('p');
                empty.textContent = 'Bạn chưa có đơn nghỉ phép nào.';
                historyList.append(empty);
            }
            result.data.items.forEach(appendHistory);
            moreButton.hidden = !result.data.hasMore;
            page++;
        } catch (error) {
            const errorText = document.createElement('p');
            errorText.textContent = error.message || 'Không tải được lịch sử.';
            historyList.append(errorText);
        } finally {
            historyLoading = false;
            moreButton.disabled = false;
        }
    }
    moreButton.addEventListener('click', loadHistory);
    if (modal.dataset.openOnError === 'true') {
        open(modal);
        if (start.value && end.value) validatePreview();
    }
});
