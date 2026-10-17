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

    const PAGE_SIZE = 20;
    let currentPage = 1;

    function normal(value) {
        return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    }

    function filteredRows() {
        const query = normal(search.value.trim());
        return rows.filter(row => {
            const matchesQuery = !query || [row.dataset.employeeName, row.dataset.employeeEmail, row.dataset.employeeCode]
                .some(value => normal(value).includes(query));
            const matchesRole = !roleFilter || roleFilter.value === 'ALL' || row.dataset.employeeRole === roleFilter.value;
            const matchesStatus = !statusFilter || statusFilter.value === 'ALL' || row.dataset.employeeStatus === statusFilter.value;
            return matchesQuery && matchesRole && matchesStatus;
        });
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
        next.disabled = currentPage >= totalPages || result.length === 0;
    }

    function resetFilter() {
        currentPage = 1;
        renderTable();
    }

    search.addEventListener('input', resetFilter);
    if (roleFilter) roleFilter.addEventListener('change', resetFilter);
    if (statusFilter) statusFilter.addEventListener('change', resetFilter);

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

    renderTable();
})();

(function () {
    'use strict';

    const page = document.getElementById('chainStorePage');
    if (!page) return;

    const cards = [...page.querySelectorAll('.chain-store-card')];
    const search = document.getElementById('chainStoreSearch');
    const noMatch = document.getElementById('chainStoreNoMatch');
    if (!search || !noMatch) return;

    function normal(value) {
        return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    }

    function refresh() {
        const query = normal(search.value.trim());
        let visible = 0;
        cards.forEach(card => {
            const matches = !query
                || normal(card.dataset.storeName).includes(query)
                || normal(card.dataset.storeCode).includes(query);
            card.hidden = !matches;
            if (matches) visible++;
        });
        noMatch.hidden = visible !== 0 || cards.length === 0;
    }

    search.addEventListener('input', refresh);
})();
