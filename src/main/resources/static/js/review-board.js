(function () {
    var board = document.querySelector(".review-board");
    if (!board) {
        return;
    }

    board.querySelectorAll("[data-shift-filter]").forEach(function (button) {
        button.addEventListener("click", function () {
            board.querySelectorAll("[data-shift-filter]").forEach(function (item) {
                item.classList.remove("is-on");
            });
            button.classList.add("is-on");
            var filter = button.dataset.shiftFilter;
            board.querySelectorAll(".rv-shift").forEach(function (section) {
                var name = section.dataset.shiftName || "";
                section.classList.toggle("is-filtered", filter !== "all" && name.indexOf(filter) === -1);
            });
            recount();
        });
    });

    board.querySelectorAll(".rv-toggle").forEach(function (button) {
        button.addEventListener("click", function () {
            var panel = button.nextElementSibling;
            if (!panel) {
                return;
            }
            var open = panel.classList.toggle("is-closed") === false;
            button.textContent = open ? "Ẩn danh sách nhân viên" : "Xem danh sách nhân viên đăng ký";
        });
    });

    function recount() {
        var seen = {};
        var counts = { total: 0, approved: 0, pending: 0, rejected: 0 };
        board.querySelectorAll(".rv-shift:not(.is-filtered) .rv-person").forEach(function (person) {
            var key = person.dataset.reg;
            if (!key || seen[key]) {
                return;
            }
            seen[key] = true;
            counts.total += 1;
            var status = person.dataset.status;
            if (counts[status] != null) {
                counts[status] += 1;
            }
        });
        setText("rvTotal", counts.total);
        setText("rvApproved", counts.approved);
        setText("rvPending", counts.pending);
        setText("rvRejected", counts.rejected);
        setText("rvSumTotal", counts.total);
        setText("rvSumApproved", counts.approved);
        setText("rvSumPending", counts.pending);
        setText("rvSumRejected", counts.rejected);
    }

    function setText(id, value) {
        var node = document.getElementById(id);
        if (node) {
            node.textContent = String(value);
        }
    }
})();
