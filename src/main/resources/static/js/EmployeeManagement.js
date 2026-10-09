(function () {
    'use strict';

    const page = document.getElementById('employeePage');
    if (!page) return;

    const rows = [...page.querySelectorAll('.employee-row')];
    const search = document.getElementById('employeeNameSearch');
    const roleFilter = document.getElementById('employeeRoleFilter');
    const statusFilter = document.getElementById('employeeStatusFilter');
    const noMatch = document.getElementById('employeeNoMatch');
    const pageInfo = document.getElementById('employeePageInfo');
    const prev = document.getElementById('employeePreviousPage');
    const next = document.getElementById('employeeNextPage');

    const modal = document.getElementById('employeeEditModal');
    const backdrop = document.getElementById('employeeEditBackdrop');
    const form = document.getElementById('employeeEditForm');
    const errorBox = document.getElementById('employeeEditError');
    const title = document.getElementById('employeeEditTitle');
    const storeSelect = document.getElementById('employeeEditStore');
    const statusSelect = document.getElementById('employeeEditStatus');
    const terminationBox = document.getElementById('employeeEditTermination');
    const terminationDate = document.getElementById('employeeEditTerminationDate');

    const PAGE_SIZE = 20;
    let currentPage = 1;
    let editingEmployee = null;
    let saving = false;

    function normal(value) {
        return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    }

    function filteredRows() {
        const name = normal(search.value.trim());
        return rows.filter(row =>
            normal(row.dataset.employeeName).includes(name) &&
            (roleFilter.value === 'ALL' || row.dataset.employeeRole === roleFilter.value) &&
            (statusFilter.value === 'ALL' || row.dataset.employeeStatus === statusFilter.value)
        );
    }

    function renderTable() {
        const result = filteredRows();
        const totalPages = Math.max(1, Math.ceil(result.length / PAGE_SIZE));
        if (currentPage > totalPages) currentPage = totalPages;

        rows.forEach(row => row.hidden = true);
        result.forEach((row, index) => row.querySelector('.employee-index').textContent = index + 1);

        const start = (currentPage - 1) * PAGE_SIZE;
        const visible = result.slice(start, start + PAGE_SIZE);
        visible.forEach(row => row.hidden = false);

        noMatch.hidden = result.length !== 0 || rows.length === 0;
        pageInfo.textContent = result.length
            ? `Hiển thị ${start + 1}–${start + visible.length} trong ${result.length} nhân viên`
            : '';

        prev.disabled = currentPage <= 1;
        next.disabled = currentPage >= totalPages;
    }

    function resetFilter() {
        currentPage = 1;
        renderTable();
    }

    search.addEventListener('input', resetFilter);
    roleFilter.addEventListener('change', resetFilter);
    statusFilter.addEventListener('change', resetFilter);

    prev.addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage--;
            renderTable();
        }
    });

    next.addEventListener('click', () => {
        const totalPages = Math.ceil(filteredRows().length / PAGE_SIZE);
        if (currentPage < totalPages) {
            currentPage++;
            renderTable();
        }
    });

    function showError(message) {
        errorBox.textContent = message;
        errorBox.hidden = false;
    }

    async function readResponse(response) {
        const type = response.headers.get('content-type') || '';
        const data = type.includes('json') ? await response.json() : null;
        if (!response.ok) throw new Error(data?.message || `Không thể xử lý yêu cầu (${response.status}).`);
        return data;
    }

    function refreshTermination() {
        const terminated = statusSelect.value === 'TERMINATED';
        terminationBox.hidden = !terminated;
        terminationDate.required = terminated;
        if (!terminated) terminationDate.value = '';
    }

    function openModal() {
        modal.hidden = false;
        backdrop.hidden = false;
        document.body.classList.add('store-dialog-open');
    }

    function closeModal() {
        if (saving) return;
        modal.hidden = true;
        backdrop.hidden = true;
        document.body.classList.remove('store-dialog-open');
        form.reset();
        errorBox.hidden = true;
        editingEmployee = null;
    }

    async function editEmployee(button) {
        const employeeId = button.dataset.employeeId;
        const storeId = button.dataset.storeId;

        openModal();
        errorBox.hidden = true;
        title.textContent = 'Đang tải...';

        try {
            const response = await fetch(
                `/admin/stores/${encodeURIComponent(storeId)}/employees/${encodeURIComponent(employeeId)}/edit-data`,
                { credentials: 'same-origin' }
            );

            const data = await readResponse(response);
            editingEmployee = data;
            title.textContent = `Chỉnh sửa nhân viên — ${data.fullName}`;
            storeSelect.value = data.storeId;
            statusSelect.value = data.status;
            terminationDate.value = data.terminationDate || '';

            const role = data.roleName === 'MANAGER' ? 'MANAGER' : 'STAFF';
            form.querySelector(`input[name="role"][value="${role}"]`).checked = true;
            refreshTermination();
        } catch (error) {
            showError(error.message);
        }
    }

    page.querySelectorAll('.employee-edit-button').forEach(button =>
        button.addEventListener('click', () => void editEmployee(button))
    );

    statusSelect.addEventListener('change', refreshTermination);
    document.getElementById('employeeEditClose').addEventListener('click', closeModal);
    document.getElementById('employeeEditCancel').addEventListener('click', closeModal);
    backdrop.addEventListener('click', closeModal);

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (saving || !editingEmployee || !form.reportValidity()) return;

        const status = statusSelect.value;
        const payload = {
            fullName: editingEmployee.fullName,
            phone: editingEmployee.phone || '',
            email: editingEmployee.email || '',
            address: editingEmployee.address || null,
            avatarUrl: editingEmployee.avatarUrl || null,
            storeId: Number(storeSelect.value),
            hireDate: editingEmployee.hireDate,
            role: form.querySelector('input[name="role"]:checked').value,
            status,
            terminationDate: status === 'TERMINATED' ? terminationDate.value : null
        };

        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        const headers = { 'Content-Type': 'application/json' };
        if (token && header) headers[header] = token;

        saving = true;
        errorBox.hidden = true;

        try {
            const response = await fetch(
                `/admin/stores/${editingEmployee.storeId}/employees/${editingEmployee.id}/edit`,
                {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers,
                    body: JSON.stringify(payload)
                }
            );
            await readResponse(response);
            window.location.reload();
        } catch (error) {
            showError(error.message);
        } finally {
            saving = false;
        }
    });

    renderTable();
})();