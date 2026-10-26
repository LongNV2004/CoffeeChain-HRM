(function () {
    'use strict';

    const page = document.getElementById('storePage');
    if (!page) return;

    page.querySelectorAll('[data-store-toast]').forEach(function (toast) {
        window.setTimeout(function () { toast.hidden = true; }, 8000);
        toast.addEventListener('click', function () { toast.hidden = true; });
    });

    const rows = [...page.querySelectorAll('.store-data-row')];
    const feedback = document.getElementById('storeFeedback');
    const modal = document.getElementById('storeModal');
    const modalBackdrop = document.getElementById('storeModalBackdrop');
    const modalBody = document.getElementById('storeModalBody');

    let filter = 'all';
    let modalRequest = 0;
    let saving = false;
    let lastFocused = null;

    function element(tag, className, value) {
        const item = document.createElement(tag);
        if (className) item.className = className;
        if (value !== undefined) item.textContent = value;
        return item;
    }

    function normal(text) {
        return (text || '').normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/Đ/g, 'D')
            .toLowerCase();
    }

    function message(target, value) {
        target.replaceChildren(element('p', 'store-loading', value));
    }

    function showError(value) {
        feedback.textContent = value;
        feedback.hidden = false;
        feedback.scrollIntoView({ block: 'nearest' });
    }

    const statusDialog = document.getElementById('storeStatusDialog');
    const statusBackdrop = document.getElementById('storeStatusBackdrop');
    const statusDetail = document.getElementById('storeStatusDetail');
    const statusError = document.getElementById('storeStatusError');
    const statusConfirm = document.getElementById('storeStatusConfirm');
    let pendingStatus = null;

    const savedNotice = sessionStorage.getItem('coffeehrm.storeStatusNotice');
    if (savedNotice) {
        sessionStorage.removeItem('coffeehrm.storeStatusNotice');
        const banner = document.getElementById('storeStatusNotice');
        if (banner) {
            banner.textContent = savedNotice;
            banner.hidden = false;
        }
    }

    function updateDialogLock() {
        document.body.classList.toggle(
            'store-dialog-open',
            !modal.hidden || (statusDialog && !statusDialog.hidden)
        );
    }

    const counts = {
        all: rows.length,
        active: 0,
        inactive: 0,
        'pre-opening': 0,
        employees: 0,
        managers: 0
    };

    rows.forEach(row => {
        if (Object.prototype.hasOwnProperty.call(counts, row.dataset.state)) {
            counts[row.dataset.state] += 1;
        }
        counts.employees += Number(row.dataset.employeeCount) || 0;
        counts.managers += row.dataset.managerAssigned === 'true' ? 1 : 0;
    });

    Object.keys(counts).forEach(key => {
        page.querySelectorAll(`[data-count="${key}"], [data-stat="${key}"]`)
            .forEach(target => target.textContent = String(counts[key]));
    });

    const search = page.querySelector('[data-store-search]');
    const noMatch = document.getElementById('storeNoMatch');

    function refreshTable() {
        const query = normal(search.value.trim());
        let visible = 0;

        rows.forEach(row => {
            const storeId = normal(
                row.querySelector('.store-name-cell small span')?.textContent
            );
            const matches = (filter === 'all' || row.dataset.state === filter)
                && storeId.includes(query);

            row.hidden = !matches;
            if (matches) visible++;
        });

        noMatch.hidden = visible !== 0 || rows.length === 0;
    }

    search.addEventListener('input', refreshTable);

    page.querySelectorAll('[data-status-filter]').forEach(button => {
        button.addEventListener('click', () => {
            filter = button.dataset.statusFilter;

            page.querySelectorAll('[data-status-filter]').forEach(item => {
                const current = item === button;
                item.classList.toggle('is-current', current);
                item.setAttribute('aria-pressed', String(current));
            });

            refreshTable();
        });
    });

    async function loadPage(url, options = {}) {
        const response = await fetch(url, {
            credentials: 'same-origin',
            ...options
        });

        const text = await response.text();
        const parsed = new DOMParser().parseFromString(text, 'text/html');
        const path = new URL(response.url).pathname;

        if (path.startsWith('/login') || response.status === 401 || response.status === 403) {
            throw new Error('Phiên đăng nhập không còn hợp lệ hoặc bạn không có quyền truy cập.');
        }

        if (!response.ok) {
            throw new Error(`Máy chủ trả về lỗi ${response.status}.`);
        }

        return { response, parsed };
    }

    function successfulSave(result) {
        const destination = new URL(result.response.url);

        if (
            result.response.redirected
            && destination.pathname.replace(/\/$/, '') === window.location.pathname.replace(/\/$/, '')
        ) {
            window.location.assign(destination.href);
            return true;
        }

        return false;
    }

    function errorText(parsed) {
        return parsed.querySelector('p.flash.error')?.textContent.trim()
            || parsed.querySelector('.field-error')?.textContent.trim()
            || 'Không thể hoàn tất thao tác. Vui lòng kiểm tra lại thông tin.';
    }

    function closeModal() {
        if (saving) return;

        modalRequest++;
        modal.hidden = true;
        modalBackdrop.hidden = true;
        modalBody.replaceChildren();
        updateDialogLock();
        lastFocused?.focus();
    }

    function renderForm(parsed) {
        const form = parsed.querySelector('form.stack-form');
        if (!form) throw new Error(errorText(parsed));

        form.removeAttribute('onsubmit');

        if (modal.dataset.mode === 'create') {
            form.classList.add('store-create-form');
        }

        const cancelLink = form.querySelector('.form-actions a');

        if (cancelLink) {
            const cancel = element('button', 'store-cancel', 'Hủy bỏ');
            cancel.type = 'button';
            cancel.addEventListener('click', closeModal);
            cancelLink.replaceWith(cancel);
        }

        modalBody.replaceChildren();

        const serverError = parsed.querySelector('p.flash.error');
        if (serverError) modalBody.appendChild(serverError);

        modalBody.appendChild(form);
        if (window.refreshStoreOpeningFields) window.refreshStoreOpeningFields(form);
        form.querySelector('input:not([type=hidden]):not([data-opening-picker])')?.focus();
    }

    async function openModal(link) {
        feedback.hidden = true;
        lastFocused = document.activeElement;

        const ticket = ++modalRequest;
        modal.hidden = false;
        modalBackdrop.hidden = false;
        modal.dataset.mode = new URL(link.href).pathname.endsWith('/create')
            ? 'create'
            : 'edit';

        document.getElementById('storeModalTitle').textContent = link.dataset.modalTitle;
        updateDialogLock();
        message(modalBody, 'Đang tải biểu mẫu…');
        modal.querySelector('[data-close-modal]').focus();

        try {
            const result = await loadPage(link.href);
            if (ticket === modalRequest && !modal.hidden) renderForm(result.parsed);
        } catch (error) {
            if (ticket !== modalRequest || modal.hidden) return;

            modalBody.replaceChildren(element('p', 'flash error', error.message));

            const fallback = element('a', 'store-btn store-btn--secondary', 'Mở trang biểu mẫu ↗');
            fallback.href = link.href;
            modalBody.appendChild(fallback);
        }
    }

    modalBody.addEventListener('submit', async event => {
        const form = event.target.closest('form.stack-form');
        if (!form) return;

        event.preventDefault();
        event.stopPropagation();
        if (saving || !form.reportValidity()) return;

        saving = true;
        let navigated = false;
        const submit = form.querySelector('[type=submit]');
        const idleLabel = submit ? submit.textContent : '';
        if (submit) {
            submit.disabled = true;
            submit.setAttribute('aria-busy', 'true');
            if (submit.dataset.submitLabel) submit.textContent = submit.dataset.submitLabel;
        }

        try {
            const result = await loadPage(form.action, {
                method: 'POST',
                body: new FormData(form)
            });

            if (successfulSave(result)) {
                navigated = true;
                return;
            }

            if (result.parsed.querySelector('form.stack-form')) {
                renderForm(result.parsed);
            } else {
                throw new Error(errorText(result.parsed));
            }
        } catch (error) {
            let banner = modalBody.querySelector('p.flash.error');

            if (!banner) {
                banner = element('p', 'flash error');
                modalBody.prepend(banner);
            }

            banner.textContent = error.message;
        } finally {
            saving = false;
            if (navigated) return;
            const activeSubmit = modalBody.querySelector('form.stack-form [type=submit]');
            if (activeSubmit) {
                activeSubmit.disabled = false;
                activeSubmit.removeAttribute('aria-busy');
                if (!activeSubmit.isConnected || activeSubmit === submit) {
                    activeSubmit.textContent = idleLabel;
                }
            }
        }
    });

    function closeStatusDialog() {
        if (pendingStatus && pendingStatus.busy) return;
        pendingStatus = null;
        if (statusDialog) statusDialog.hidden = true;
        if (statusBackdrop) statusBackdrop.hidden = true;
        if (statusError) statusError.hidden = true;
        updateDialogLock();
    }

    function openStatusDialog(link) {
        if (!statusDialog || !link.dataset.storeId) return;
        const inactive = link.closest('tr')?.dataset.state === 'inactive';
        const name = link.dataset.storeName || 'cửa hàng này';
        const opening = link.dataset.opening ? `Ngày khai trương: ${link.dataset.opening}. ` : '';
        pendingStatus = {
            id: link.dataset.storeId,
            action: inactive ? 'reactivate' : 'deactivate',
            busy: false
        };
        document.getElementById('storeStatusTitle').textContent = inactive
            ? 'Kích hoạt lại cửa hàng'
            : 'Ngừng hoạt động cửa hàng';
        document.getElementById('storeStatusLead').textContent = name;
        statusDetail.textContent = inactive
            ? `${opening}Nếu ngày khai trương còn ở tương lai, cửa hàng chuyển sang Chuẩn bị khai trương. Nếu ngày khai trương là hôm nay hoặc đã qua, cửa hàng chuyển sang Hoạt động.`
            : `${opening}Manager và nhân viên sẽ không dùng được các chức năng nghiệp vụ. Tài khoản, lịch làm, đơn nghỉ phép và dữ liệu chấm công được giữ nguyên. Cửa hàng không tự hoạt động lại khi đến ngày khai trương.`;
        statusError.hidden = true;
        statusConfirm.disabled = false;
        statusConfirm.textContent = 'Xác nhận';
        statusDialog.hidden = false;
        statusBackdrop.hidden = false;
        updateDialogLock();
        statusConfirm.focus();
    }

    async function confirmStatusChange() {
        if (!pendingStatus || pendingStatus.busy) return;
        pendingStatus.busy = true;
        statusConfirm.disabled = true;
        statusConfirm.textContent = 'Đang cập nhật…';
        statusError.hidden = true;
        feedback.hidden = true;
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        const body = new URLSearchParams();
        body.set('action', pendingStatus.action);
        if (token) body.set('_csrf', token);
        const headers = {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        };
        if (token && header) headers[header] = token;
        try {
            const response = await fetch(`/admin/stores/${encodeURIComponent(pendingStatus.id)}/status`, {
                method: 'POST',
                credentials: 'same-origin',
                headers,
                body
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(data.message || 'Không thể cập nhật trạng thái cửa hàng.');
            }
            sessionStorage.setItem('coffeehrm.storeStatusNotice', data.message || 'Đã cập nhật trạng thái cửa hàng.');
            window.location.reload();
        } catch (error) {
            statusError.textContent = error.message;
            statusError.hidden = false;
            pendingStatus.busy = false;
            statusConfirm.disabled = false;
            statusConfirm.textContent = 'Xác nhận';
        }
    }

    if (statusConfirm) statusConfirm.addEventListener('click', () => { void confirmStatusChange(); });
    document.querySelectorAll('[data-close-status]').forEach(item => {
        item.addEventListener('click', closeStatusDialog);
    });
    if (statusBackdrop) statusBackdrop.addEventListener('click', closeStatusDialog);

    page.addEventListener('click', event => {
        const modalLink = event.target.closest('[data-store-modal]');
        const toggleLink = event.target.closest('[data-store-toggle]');

        if (modalLink) {
            event.preventDefault();
            void openModal(modalLink);
        } else if (toggleLink) {
            event.preventDefault();
            openStatusDialog(toggleLink);
        }
    });

    document.querySelectorAll('[data-close-modal]').forEach(item => {
        item.addEventListener('click', closeModal);
    });

    document.addEventListener('keydown', event => {
        const currentDialog = statusDialog && !statusDialog.hidden
            ? statusDialog
            : !modal.hidden
                ? modal
                : null;

        if (!currentDialog) return;

        if (event.key === 'Escape') {
            event.preventDefault();

            if (currentDialog === statusDialog) closeStatusDialog();
            else if (currentDialog === modal) closeModal();
        }

        if (event.key !== 'Tab') return;

        const focusable = [
            ...currentDialog.querySelectorAll(
                'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled])'
            )
        ].filter(item => item.getClientRects().length > 0);

        if (!focusable.length) return;

        const first = focusable[0];
        const last = focusable[focusable.length - 1];

        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });
})();