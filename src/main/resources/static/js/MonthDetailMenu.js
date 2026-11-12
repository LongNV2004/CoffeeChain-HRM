/*
 * Nút "Xem chi tiết" ở cột Thao tác của bảng Chấm công và lương theo tháng.
 * Menu dùng position:fixed để không bị khung cuộn ngang của bảng cắt mất.
 *   - [data-detail-toggle]  : nút mở/đóng menu (menu là phần tử kế tiếp .mp-menu)
 *   - [data-dialog]         : mục mở <dialog> chi tiết chấm công theo id
 */
(function () {
    var open = null;

    function close() {
        if (!open) {
            return;
        }
        open.menu.hidden = true;
        open.button.setAttribute("aria-expanded", "false");
        open = null;
    }

    function place(button, menu) {
        var rect = button.getBoundingClientRect();
        menu.hidden = false;
        var width = menu.offsetWidth;
        var height = menu.offsetHeight;
        var left = Math.min(Math.max(8, rect.right - width), window.innerWidth - width - 8);
        var top = rect.bottom + 6;
        if (top + height > window.innerHeight - 8) {
            top = Math.max(8, rect.top - height - 6); // không đủ chỗ phía dưới: mở lên trên
        }
        menu.style.left = left + "px";
        menu.style.top = top + "px";
    }

    document.addEventListener("click", function (event) {
        var toggle = event.target.closest("[data-detail-toggle]");
        if (toggle) {
            var menu = toggle.nextElementSibling;
            var same = open && open.button === toggle;
            close();
            if (!same && menu) {
                place(toggle, menu);
                toggle.setAttribute("aria-expanded", "true");
                open = { button: toggle, menu: menu };
                var first = menu.querySelector("a,button");
                if (first) {
                    first.focus();
                }
            }
            return;
        }
        var item = event.target.closest(".mp-menu [data-dialog]");
        if (item) {
            close();
            var dialog = document.getElementById(item.getAttribute("data-dialog"));
            if (dialog) {
                dialog.showModal();
            }
            return;
        }
        if (open && !open.menu.contains(event.target)) {
            close();
        }
    });
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && open) {
            var button = open.button;
            close();
            button.focus();
        }
    });
    window.addEventListener("resize", close);
    document.addEventListener("scroll", close, true);
})();
