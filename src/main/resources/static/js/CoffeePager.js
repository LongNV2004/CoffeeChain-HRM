/*
 * Phân trang dùng chung cho mọi danh sách trong CoffeeHRM.
 *
 * Cách dùng: thêm thuộc tính data-paginate vào <table> (phân trang các dòng tbody > tr)
 * hoặc vào phần tử chứa danh sách (phân trang các phần tử con trực tiếp, hoặc các phần tử
 * khớp data-paginate-item="selector").
 *   data-page-size="10"     số dòng mỗi trang (mặc định 10, không có ô chọn số dòng)
 *   data-paginate-label="nhân viên"  đơn vị hiển thị ở dòng đếm (mặc định "bản ghi")
 *   data-paginate-always    luôn hiện thanh phân trang (mặc định chỉ hiện khi > 10 mục)
 *
 * Phối hợp với bộ lọc/tìm kiếm sẵn có: dòng nào bị script khác đặt thuộc tính hidden
 * được coi là "không khớp lọc" và không tính vào phân trang. Phân trang chỉ ẩn bằng class
 * .pg-off nên không đụng tới thuộc tính hidden của các script khác.
 */
(function () {
    'use strict';

    var PAGE_SIZE = 10;

    function items(root) {
        if (root.tagName === 'TABLE') {
            var bodies = root.tBodies.length ? Array.prototype.slice.call(root.tBodies) : [root];
            var rows = [];
            bodies.forEach(function (body) {
                Array.prototype.forEach.call(body.children, function (row) {
                    if (row.tagName === 'TR') rows.push(row);
                });
            });
            return rows;
        }
        var selector = root.getAttribute('data-paginate-item');
        if (selector) {
            return Array.prototype.slice.call(root.querySelectorAll(selector));
        }
        return Array.prototype.filter.call(root.children, function (child) {
            return child.tagName !== 'TEMPLATE' && child.tagName !== 'SCRIPT';
        });
    }

    /** Dòng "Không có dữ liệu" (một ô colspan) hoặc dòng đánh dấu bỏ qua: luôn hiển thị, không đếm. */
    function isPlaceholder(item) {
        if (item.hasAttribute('data-paginate-skip')) return true;
        return item.tagName === 'TR' && item.cells.length === 1 && item.cells[0].colSpan > 1;
    }

    function anchorOf(root) {
        // Đặt thanh phân trang sau khung cuộn ngang của bảng (nếu có) để luôn nhìn thấy.
        var parent = root.parentElement;
        if (root.tagName === 'TABLE' && parent && parent.children.length === 1) {
            return parent;
        }
        return root;
    }

    function button(label, page, disabled, current, onPick) {
        var el = document.createElement('button');
        el.type = 'button';
        el.textContent = label;
        el.disabled = disabled;
        if (current) {
            el.className = 'is-on';
            el.setAttribute('aria-current', 'page');
        }
        el.addEventListener('click', function () { onPick(page); });
        return el;
    }

    var WINDOW = 5;

    /** Tối đa 5 số trang liên tiếp, trượt theo trang hiện tại: 1 2 3 4 5 → 4 5 6 7 8. */
    function pageNumbers(page, total) {
        var start = Math.max(1, page - Math.floor(WINDOW / 2));
        var end = Math.min(total, start + WINDOW - 1);
        start = Math.max(1, end - WINDOW + 1);
        var list = [];
        for (var n = start; n <= end; n += 1) {
            list.push(n);
        }
        return list;
    }

    /**
     * Vẽ dãy nút Numbered Pagination dùng chung: Trước 1 2 3 4 5 Sau.
     * Mọi bộ phân trang tự viết (nhân viên, nghỉ phép, chấm công, duyệt đăng ký…) gọi hàm này
     * để cùng giao diện và cùng cách rút gọn số trang.
     *
     * @param container phần tử chứa (thường có class coffee-pager-pages)
     * @param page      trang hiện tại, tính từ 1
     * @param total     tổng số trang (tối thiểu 1)
     * @param onPick    hàm nhận số trang (từ 1) khi người dùng bấm
     */
    function renderPages(container, page, total, onPick) {
        if (!container) return;
        var pages = Math.max(1, total || 1);
        var current = Math.min(Math.max(1, page || 1), pages);
        container.classList.add('coffee-pager-pages');
        container.innerHTML = '';
        container.appendChild(button('Trước', current - 1, current <= 1, false, onPick));
        pageNumbers(current, pages).forEach(function (n) {
            container.appendChild(button(String(n), n, false, n === current, onPick));
        });
        container.appendChild(button('Sau', current + 1, current >= pages, false, onPick));
    }

    function setup(root) {
        if (root.__coffeePager) return;
        var unit = root.getAttribute('data-paginate-label') || 'bản ghi';
        var always = root.hasAttribute('data-paginate-always');
        var startSize = parseInt(root.getAttribute('data-page-size'), 10);
        var state = { page: 1, size: startSize > 0 ? startSize : PAGE_SIZE, lastCount: -1 };

        var bar = document.createElement('div');
        bar.className = 'coffee-pager';
        bar.setAttribute('role', 'navigation');
        bar.setAttribute('aria-label', 'Phân trang');
        var count = document.createElement('p');
        count.className = 'coffee-pager-count';
        var pages = document.createElement('div');
        pages.className = 'coffee-pager-pages';
        bar.appendChild(count);
        bar.appendChild(pages);
        var anchor = anchorOf(root);
        anchor.parentNode.insertBefore(bar, anchor.nextSibling);

        var scheduled = false;
        var rendering = false;

        function pick(page) {
            state.page = page;
            render();
            var top = root.getBoundingClientRect().top;
            if (top < 0) {
                root.scrollIntoView({ block: 'start', behavior: 'smooth' });
            }
        }

        function render() {
            rendering = true;
            var all = items(root);
            var shown = all.filter(function (item) {
                return !isPlaceholder(item) && !item.hasAttribute('hidden');
            });
            // Bộ lọc vừa thay đổi số dòng khớp: quay về trang đầu.
            if (state.lastCount !== -1 && state.lastCount !== shown.length) {
                state.page = 1;
            }
            state.lastCount = shown.length;
            var totalPages = Math.max(1, Math.ceil(shown.length / state.size));
            if (state.page > totalPages) state.page = totalPages;
            var from = (state.page - 1) * state.size;
            var to = from + state.size;
            all.forEach(function (item) { item.classList.remove('pg-off'); });
            shown.forEach(function (item, index) {
                if (index < from || index >= to) item.classList.add('pg-off');
            });

            // data-paginate-always: luôn hiện thanh phân trang khi có dữ liệu (kể cả chỉ một trang).
            bar.hidden = always ? shown.length === 0 : shown.length <= state.size;
            count.textContent = shown.length === 0
                ? '0 ' + unit
                : (from + 1) + '–' + Math.min(to, shown.length) + ' / ' + shown.length + ' ' + unit;
            renderPages(pages, state.page, totalPages, pick);
            rendering = false;
        }

        function schedule() {
            if (rendering || scheduled) return;
            scheduled = true;
            window.requestAnimationFrame(function () {
                scheduled = false;
                render();
            });
        }

        // Theo dõi bộ lọc của trang (đặt/bỏ hidden) và dòng được thêm/bớt.
        new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i += 1) {
                var m = mutations[i];
                if (m.type === 'childList' || m.attributeName === 'hidden') {
                    schedule();
                    return;
                }
            }
        }).observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['hidden'] });

        root.__coffeePager = { refresh: render };
        render();
    }

    function init(scope) {
        (scope || document).querySelectorAll('[data-paginate]').forEach(setup);
    }

    window.CoffeePager = { init: init, renderPages: renderPages };
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { init(); });
    } else {
        init();
    }
})();
