(function () {
    'use strict';

    const page = document.getElementById('storePage');
    if (!page) return;

    const rows = [...page.querySelectorAll('.store-data-row')];
    const feedback = document.getElementById('storeFeedback');
    const modal = document.getElementById('storeModal');
    const modalBackdrop = document.getElementById('storeModalBackdrop');
    const modalBody = document.getElementById('storeModalBody');
    const drawer = document.getElementById('storeDrawer');
    const drawerBackdrop = document.getElementById('storeDrawerBackdrop');
    const drawerBody = document.getElementById('storeDrawerBody');
    const employeeSearch = document.getElementById('storeEmployeeSearch');
    const employeeModal = document.getElementById('employeeModal');
    const employeeBackdrop = document.getElementById('employeeModalBackdrop');
    const employeeForm = document.getElementById('employeeForm');
    const employeeError = document.getElementById('employeeFormError');
    const accountToggle = document.getElementById('employeeCreateAccount');
    const accountSection = document.getElementById('employeeAccountSection');
    const accountFields = document.getElementById('employeeAccountFields');
    const statusSection = document.getElementById('employeeStatusSection');
    const terminationSection = document.getElementById('employeeTerminationSection');
    const personalSection = document.getElementById('employeePersonalSection');

    let filter = 'all';
    let modalRequest = 0;
    let drawerRequest = 0;
    let employeeRequest = 0;
    let saving = false;
    let employeeSaving = false;
    let lastFocused = null;
    let employeeReturnFocus = null;

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

    function updateDialogLock() {
        document.body.classList.toggle(
            'store-dialog-open',
            !modal.hidden || !drawer.hidden || !employeeModal.hidden
        );
    }

    const counts = {
        all: rows.length,
        active: 0,
        inactive: 0,
        employees: 0,
        managers: 0
    };

    rows.forEach(row => {
        counts[row.dataset.state] += 1;
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
            const status = form.querySelector('[name="isActive"]');

            if (status) {
                const hidden = element('input');
                hidden.type = 'hidden';
                hidden.name = 'isActive';
                hidden.value = 'true';
                status.closest('label').replaceWith(hidden);
            }

            ['storeName', 'address', 'totalLeaveDays'].forEach(name => {
                const input = form.elements.namedItem(name);
                if (!input) return;

                input.required = true;
                const star = element('span', 'store-required', ' *');
                star.setAttribute('aria-hidden', 'true');
                input.closest('label').insertBefore(star, input);
            });
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
        form.querySelector('input:not([type=hidden])')?.focus();
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
        if (saving || !form.reportValidity()) return;

        saving = true;
        const submit = form.querySelector('[type=submit]');
        if (submit) submit.disabled = true;

        try {
            const result = await loadPage(form.action, {
                method: 'POST',
                body: new FormData(form)
            });

            if (successfulSave(result)) return;

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
            const activeSubmit = modalBody.querySelector('form.stack-form [type=submit]');
            if (activeSubmit) activeSubmit.disabled = false;
        }
    });

    function closeDrawer() {
        drawerRequest++;
        drawer.hidden = true;
        drawerBackdrop.hidden = true;
        drawerBody.replaceChildren();
        updateDialogLock();
        lastFocused?.focus();
    }

    function filterEmployees() {
        const query = normal(employeeSearch.value.trim());
        const cards = drawerBody.querySelectorAll('.store-employee-card');
        let visible = 0;

        cards.forEach(card => {
            card.hidden = !card.dataset.search.includes(query);
            if (!card.hidden) visible++;
        });

        const count = drawerBody.querySelector('[data-drawer-count]');

        if (count) {
            count.textContent = query
                ? `Hiển thị ${visible}/${cards.length} nhân viên`
                : `${cards.length} nhân viên tại chi nhánh`;
        }

        const empty = drawerBody.querySelector('[data-drawer-empty]');
        if (empty) empty.hidden = visible !== 0;
    }

    employeeSearch.addEventListener('input', filterEmployees);

    function renderEmployees(parsed) {
        const selected = [...parsed.querySelectorAll('main > section.panel')]
            .find(section => section.querySelectorAll('thead th').length === 8);

        if (!selected) throw new Error(errorText(parsed));

        const employeeRows = [
            ...selected.querySelectorAll('tbody tr.employee-row')
        ];

        const managerName = drawer.dataset.managerName;
        const managerRow = employeeRows.find(
            row => row.dataset.employeeName === managerName
        );
        const managerPhone = managerRow?.dataset.employeePhone;

        if (managerPhone) {
            document.getElementById('storeDrawerMeta').textContent +=
                ` · SĐT: ${managerPhone}`;
        }

        drawerBody.replaceChildren();

        const count = element(
            'p',
            'store-employee-summary',
            `${employeeRows.length} nhân viên tại chi nhánh`
        );

        count.dataset.drawerCount = '';
        drawerBody.appendChild(count);

        employeeRows.forEach(row => {
            const id = row.dataset.employeeId;
            const name = row.dataset.employeeName;
            const email = row.dataset.employeeEmail || '—';
            const phone = row.dataset.employeePhone || '—';

            const role = row.dataset.employeeRole === 'MANAGER'
                ? 'Manager'
                : row.dataset.employeeRole === 'STAFF'
                    ? 'Staff'
                    : 'Chưa có tài khoản';

            const status = row.dataset.employeeStatus === 'ACTIVE'
                ? 'Đang làm việc'
                : row.dataset.employeeStatus === 'ON_LEAVE'
                    ? 'Đang nghỉ phép'
                    : 'Đã nghỉ việc';

            const card = element('article', 'store-employee-card');
            const heading = element('div', 'store-employee-top');
            const identity = element('div', 'store-employee-identity');

            identity.append(
                element(
                    'span',
                    'store-employee-avatar',
                    name.split(/\s+/).at(-1)?.charAt(0) || '?'
                ),
                element('strong', '', name)
            );

            heading.append(
                identity,
                element('span', 'store-role', role)
            );

            const actions = element('div', 'store-employee-card-actions');
            const edit = element('button', 'store-employee-edit', 'Sửa');

            edit.type = 'button';
            edit.title = 'Chỉnh sửa nhân viên';
            edit.addEventListener('click', () => void openEmployeeModal('edit', id));
            actions.appendChild(edit);

            card.append(
                heading,
                element('p', '', `SĐT: ${phone}`),
                element('p', '', `Email: ${email}`),
                element(
                    'span',
                    status === 'Đang làm việc'
                        ? 'store-employee-status store-employee-status--active'
                        : 'store-employee-status',
                    status
                ),
                actions
            );

            card.dataset.search = normal(name);
            drawerBody.appendChild(card);
        });

        const empty = element(
            'p',
            'store-loading',
            employeeRows.length
                ? 'Không tìm thấy nhân viên phù hợp.'
                : 'Cửa hàng hiện chưa có nhân viên.'
        );

        empty.dataset.drawerEmpty = '';
        empty.hidden = employeeRows.length > 0;
        drawerBody.appendChild(empty);
        filterEmployees();
    }

    async function openDrawer(link) {
        feedback.hidden = true;
        lastFocused = document.activeElement;

        const ticket = ++drawerRequest;
        drawer.hidden = false;
        drawerBackdrop.hidden = false;

        const row = link.closest('tr');
        const storeName = row.querySelector('.store-name-cell strong').textContent.trim();
        const storeId = row.querySelector('.store-name-cell small span')?.textContent.trim() || '';
        const address = row.querySelector('.store-address').textContent.trim();
        const manager = row.querySelector('.store-manager')?.textContent.trim() || '';

        document.getElementById('storeDrawerTitle').textContent = `Nhân sự ${storeName}`;
        document.getElementById('storeDrawerMeta').textContent =
            `Mã cửa hàng: ${storeId} · ${address}\nManager: ${manager || 'Chưa phân công'}`;

        drawer.dataset.managerName = manager;
        drawer.dataset.storeId = storeId;
        document.getElementById('storeEmployeePageLink').href = '/admin/employees';
        employeeSearch.value = '';

        updateDialogLock();
        message(drawerBody, 'Đang tải nhân viên…');
        drawer.querySelector('[data-close-drawer]').focus();

        try {
            const result = await loadPage(link.href);
            if (ticket === drawerRequest && !drawer.hidden) renderEmployees(result.parsed);
        } catch (error) {
            if (ticket === drawerRequest && !drawer.hidden) {
                message(drawerBody, error.message);
            }
        }
    }

    async function toggleStore(link) {
        if (link.classList.contains('is-busy')) return;

        const active = link.closest('tr').dataset.state === 'active';
        const question = active
            ? 'Ngừng hoạt động cửa hàng này?'
            : 'Kích hoạt lại cửa hàng này?';

        if (!window.confirm(question)) return;

        link.classList.add('is-busy');
        feedback.hidden = true;

        try {
            const existing = await loadPage(link.href);
            const form = existing.parsed.querySelector('form.stack-form');
            const status = form?.elements.namedItem('isActive');

            if (!form || !status) {
                throw new Error('Không đọc được form cập nhật của cửa hàng.');
            }

            if (status.value !== String(active)) {
                throw new Error('Trạng thái cửa hàng vừa thay đổi. Hãy tải lại trang.');
            }

            status.value = String(!active);

            const target = new URL(form.getAttribute('action'), existing.response.url);
            const saved = await loadPage(target.href, {
                method: 'POST',
                body: new FormData(form)
            });

            if (successfulSave(saved)) return;
            throw new Error(errorText(saved.parsed));
        } catch (error) {
            showError(error.message);
        } finally {
            link.classList.remove('is-busy');
        }
    }

    function employeeBaseUrl() {
        return `/admin/stores/${encodeURIComponent(drawer.dataset.storeId)}/employees`;
    }

    function showEmployeeError(value) {
        employeeError.textContent = value;
        employeeError.hidden = false;
    }

    function refreshEmployeeFields() {
        const creating = employeeForm.dataset.mode === 'create';
        const manager = employeeForm.querySelector('input[name="role"]:checked')?.value === 'MANAGER';

        if (creating && manager) accountToggle.checked = true;
        accountToggle.disabled = creating && manager;
        accountFields.hidden = !creating || !accountToggle.checked;

        ['username', 'temporaryPassword'].forEach(name => {
            employeeForm.elements.namedItem(name).required =
                creating && accountToggle.checked;
        });

        terminationSection.hidden = true;
        employeeForm.elements.namedItem('terminationDate').required = false;
    }

    function closeEmployeeModal() {
        if (employeeSaving) return;

        employeeRequest++;
        employeeModal.hidden = true;
        employeeBackdrop.hidden = true;
        updateDialogLock();
        employeeReturnFocus?.focus();
    }

    async function readEmployeeResponse(response) {
        const type = response.headers.get('content-type') || '';
        const data = type.includes('json') ? await response.json() : null;

        if (
            response.redirected
            && new URL(response.url).pathname.startsWith('/login')
        ) {
            throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        }

        if (!response.ok) {
            throw new Error(
                response.status === 403
                    ? 'Không có quyền thực hiện hoặc CSRF token không hợp lệ.'
                    : data?.message || data?.detail || `Không thể xử lý yêu cầu (${response.status}).`
            );
        }

        if (!data || typeof data !== 'object' || !data.id) {
            throw new Error('Phản hồi máy chủ không hợp lệ.');
        }

        return data;
    }

    async function openEmployeeModal(mode, employeeId = null) {
        employeeReturnFocus = document.activeElement;
        const ticket = ++employeeRequest;

        employeeForm.reset();
        employeeForm.dataset.mode = mode;
        employeeForm.dataset.employeeId = employeeId || '';
        employeeForm.dataset.avatarUrl = '';

        employeeError.hidden = true;
        accountToggle.disabled = false;

        const creating = mode === 'create';

        accountSection.hidden = !creating;
        statusSection.hidden = creating;
        personalSection.hidden = !creating;
        terminationSection.hidden = true;

        document.getElementById('employeeModalTitle').textContent =
            creating ? 'Thêm nhân viên mới' : 'Chỉnh sửa nhân viên';

        const select = employeeForm.elements.namedItem('storeId');
        select.replaceChildren(new Option('Chọn chi nhánh', ''));

        rows.forEach(row => {
            const id = row.querySelector('.store-name-cell small span')?.textContent.trim();
            const name = row.querySelector('.store-name-cell strong')?.textContent.trim();
            if (id && name) select.add(new Option(name, id));
        });

        select.value = drawer.dataset.storeId;

        employeeModal.hidden = false;
        employeeBackdrop.hidden = false;
        updateDialogLock();
        refreshEmployeeFields();

        const submit = employeeForm.querySelector('[type="submit"]');
        submit.disabled = !creating;

        if (creating) {
            employeeForm.elements.namedItem('fullName').focus();
            return;
        }

        try {
            const response = await fetch(
                `${employeeBaseUrl()}/${encodeURIComponent(employeeId)}/edit-data`,
                { credentials: 'same-origin' }
            );

            const data = await readEmployeeResponse(response);
            if (ticket !== employeeRequest || employeeModal.hidden) return;

            document.getElementById('employeeModalTitle').textContent =
                `Chỉnh sửa nhân viên — ${data.fullName}`;

            ['fullName', 'phone', 'email', 'address', 'hireDate'].forEach(name => {
                employeeForm.elements.namedItem(name).value = data[name] || '';
            });

            select.value = data.storeId;
            employeeForm.elements.namedItem('status').value = data.status;
            employeeForm.elements.namedItem('terminationDate').value =
                data.terminationDate || '';

            employeeForm.dataset.avatarUrl = data.avatarUrl || '';

            const role = data.roleName === 'MANAGER' ? 'MANAGER' : 'STAFF';

            employeeForm.querySelector(
                `input[name="role"][value="${role}"]`
            ).checked = true;

            refreshEmployeeFields();
            submit.disabled = false;
            select.focus();
        } catch (error) {
            if (ticket === employeeRequest && !employeeModal.hidden) {
                showEmployeeError(error.message);
            }
        }
    }

    document.getElementById('storeAddEmployeeButton').addEventListener('click', () => {
        void openEmployeeModal('create');
    });

    document.querySelectorAll('[data-close-employee]').forEach(button => {
        button.addEventListener('click', closeEmployeeModal);
    });

    accountToggle.addEventListener('change', refreshEmployeeFields);

    employeeForm.querySelectorAll('input[name="role"]').forEach(radio => {
        radio.addEventListener('change', refreshEmployeeFields);
    });

    employeeForm.elements.namedItem('status').addEventListener(
        'change',
        refreshEmployeeFields
    );

    employeeForm.addEventListener('submit', async event => {
        event.preventDefault();

        if (employeeSaving || !employeeForm.reportValidity()) return;

        const value = name =>
            employeeForm.elements.namedItem(name).value.trim();

        const creating = employeeForm.dataset.mode === 'create';

        const payload = {
            fullName: value('fullName'),
            phone: value('phone'),
            email: value('email'),
            address: value('address') || null,
            storeId: Number(value('storeId')),
            hireDate: value('hireDate'),
            role: employeeForm.querySelector('input[name="role"]:checked').value
        };

        if (creating) {
            payload.createAccount = accountToggle.checked;

            if (payload.createAccount) {
                payload.username = value('username');
                payload.temporaryPassword =
                    employeeForm.elements.namedItem('temporaryPassword').value;
            }
        } else {
            payload.status = value('status');
            payload.terminationDate = payload.status === 'TERMINATED'
                ? value('terminationDate') || new Date().toISOString().split('T')[0]
                : null;
            payload.avatarUrl = employeeForm.dataset.avatarUrl || null;
        }

        const url = creating
            ? `${employeeBaseUrl()}/create`
            : `${employeeBaseUrl()}/${encodeURIComponent(
                employeeForm.dataset.employeeId
            )}/edit`;

        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        const headers = { 'Content-Type': 'application/json' };

        if (token && header) headers[header] = token;

        const submit = employeeForm.querySelector('[type="submit"]');

        employeeSaving = true;
        submit.disabled = true;
        employeeError.hidden = true;

        try {
            const response = await fetch(url, {
                method: 'POST',
                credentials: 'same-origin',
                headers,
                body: JSON.stringify(payload)
            });

            await readEmployeeResponse(response);

            window.location.hash =
                `store-employees-${drawer.dataset.storeId}`;
            window.location.reload();
        } catch (error) {
            showEmployeeError(error.message);
        } finally {
            employeeSaving = false;
            submit.disabled = false;
        }
    });

    page.addEventListener('click', event => {
        const modalLink = event.target.closest('[data-store-modal]');
        const drawerLink = event.target.closest('[data-store-drawer]');
        const toggleLink = event.target.closest('[data-store-toggle]');

        if (modalLink) {
            event.preventDefault();
            void openModal(modalLink);
        } else if (drawerLink) {
            event.preventDefault();
            void openDrawer(drawerLink);
        } else if (toggleLink) {
            event.preventDefault();
            void toggleStore(toggleLink);
        }
    });

    document.querySelectorAll('[data-close-modal]').forEach(item => {
        item.addEventListener('click', closeModal);
    });

    document.querySelectorAll('[data-close-drawer]').forEach(item => {
        item.addEventListener('click', closeDrawer);
    });

    document.addEventListener('keydown', event => {
        const currentDialog = !employeeModal.hidden
            ? employeeModal
            : !modal.hidden
                ? modal
                : !drawer.hidden
                    ? drawer
                    : null;

        if (!currentDialog) return;

        if (event.key === 'Escape') {
            event.preventDefault();

            if (currentDialog === employeeModal) closeEmployeeModal();
            else if (currentDialog === modal) closeModal();
            else closeDrawer();
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

    const reopenStoreId = window.location.hash.match(
        /^#store-employees-(\d+)$/
    )?.[1];

    if (reopenStoreId) {
        const row = rows.find(item =>
            item.querySelector('.store-name-cell small span')
                ?.textContent.trim() === reopenStoreId
        );

        const link = row?.querySelector('[data-store-drawer]');

        if (link) {
            history.replaceState(
                null,
                '',
                window.location.pathname + window.location.search
            );

            void openDrawer(link);
        }
    }
})();