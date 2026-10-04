/* Sidebar CoffeeHRM: thu gọn/mở rộng và nhóm menu; không thay đổi routing. */
(function () {
    'use strict';

    function initSidebar(sidebar) {
        if (sidebar.dataset.sidebarReady === 'true') return;
        sidebar.dataset.sidebarReady = 'true';

        const toggle = sidebar.querySelector('[data-sidebar-toggle]');
        const groups = sidebar.querySelectorAll('.coffee-menu-group');
        const preferenceKey = 'coffeehrm.sidebar.collapsed';
        let savedPreference = null;

        // Trình duyệt chặn localStorage vẫn cho phép sử dụng sidebar bình thường.
        try { savedPreference = window.localStorage.getItem(preferenceKey); }
        catch (_) { /* Không có quyền lưu cục bộ. */ }

        const mobileScreen = typeof window.matchMedia === 'function'
            ? window.matchMedia('(max-width: 760px)')
            : { matches: false };
        let collapsed = savedPreference === null ? mobileScreen.matches : savedPreference === 'true';

        function applyState() {
            sidebar.classList.toggle('is-collapsed', collapsed);
            if (toggle) {
                toggle.setAttribute('aria-expanded', String(!collapsed));
                toggle.setAttribute('aria-label', collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng');
                toggle.title = collapsed ? 'Mở rộng' : 'Thu gọn';
            }
        }

        function setCollapsed(value, savePreference) {
            collapsed = value;
            applyState();
            if (savePreference) {
                savedPreference = String(value);
                try { window.localStorage.setItem(preferenceKey, savedPreference); }
                catch (_) { /* Không có quyền lưu cục bộ. */ }
            }
        }

        if (toggle) toggle.addEventListener('click', function () {
            setCollapsed(!collapsed, true);
        });

        groups.forEach(function (group) {
            const button = group.querySelector('[data-group-toggle]');
            if (!button) return;
            // Th:classappend mở sẵn nhóm chứa trang đang được chọn.
            button.setAttribute('aria-expanded', String(group.classList.contains('is-open')));
            button.addEventListener('click', function () {
                if (collapsed) {
                    setCollapsed(false, true);
                    group.classList.add('is-open');
                } else {
                    group.classList.toggle('is-open');
                }
                button.setAttribute('aria-expanded', String(group.classList.contains('is-open')));
            });
        });

        // Khi chưa lưu lựa chọn, kích thước màn hình quyết định trạng thái mặc định.
        function onScreenChange(event) {
            if (savedPreference === null) setCollapsed(event.matches, false);
        }
        if (mobileScreen.addEventListener) mobileScreen.addEventListener('change', onScreenChange);
        else if (mobileScreen.addListener) mobileScreen.addListener(onScreenChange);

        applyState();
    }

    function initAll() {
        document.querySelectorAll('.coffee-sidebar').forEach(initSidebar);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll, { once: true });
    } else {
        initAll();
    }
})();
