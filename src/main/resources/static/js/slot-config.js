(function () {
    var form = document.getElementById("slotConfigForm");
    if (!form) {
        return;
    }
    var staffCap = Number(form.dataset.staff || 0);
    var error = document.getElementById("slotConfigError");
    if (staffCap < 1) {
        form.querySelectorAll(".js-step, .js-total").forEach(function (field) {
            field.disabled = true;
        });
    }

    document.querySelectorAll("[data-shift-filter]").forEach(function (button) {
        button.addEventListener("click", function () {
            document.querySelectorAll("[data-shift-filter]").forEach(function (item) {
                item.classList.remove("is-on");
            });
            button.classList.add("is-on");
            var filter = button.dataset.shiftFilter;
            document.querySelectorAll(".sc-shift").forEach(function (section) {
                var name = section.dataset.shiftName || "";
                section.classList.toggle("is-filtered", filter !== "all" && name.indexOf(filter) === -1);
            });
        });
    });

    form.querySelectorAll(".sc-day").forEach(function (card) {
        card.querySelectorAll(".js-step").forEach(function (button) {
            button.addEventListener("click", function () {
                var input = card.querySelector(".js-total");
                var next = Number(input.value || 0) + Number(button.dataset.step);
                input.value = String(Math.max(0, next));
                refresh(card);
            });
        });
        var input = card.querySelector(".js-total");
        if (input) {
            input.addEventListener("input", function () {
                input.value = input.value.replace(/\D/g, "");
                refresh(card);
            });
        }
        refresh(card, true);
    });

    form.addEventListener("submit", function (event) {
        var dirty = 0;
        var blocked = false;
        form.querySelectorAll(".sc-day").forEach(function (card) {
            var message = cardMessage(card);
            var cardError = card.querySelector(".sc-card-error");
            if (isDirty(card)) {
                dirty += 1;
                if (message) {
                    blocked = true;
                    cardError.hidden = false;
                    cardError.textContent = message;
                }
                nameFields(card, true);
            } else {
                nameFields(card, false);
            }
        });
        if (dirty === 0) {
            event.preventDefault();
            error.hidden = false;
            error.textContent = "Không có thay đổi cấu hình để lưu.";
            return;
        }
        if (blocked) {
            event.preventDefault();
            error.hidden = false;
            error.textContent = "Hãy sửa các ô đang báo lỗi trước khi lưu.";
            return;
        }
        error.hidden = true;
        var save = form.querySelector(".sc-save");
        if (save) {
            save.disabled = true;
            save.textContent = "Đang lưu...";
        }
    });

    function refresh(card, initial) {
        var input = card.querySelector(".js-total");
        var invalid = input && input.value !== "" && !/^\d+$/.test(input.value);
        var total = Number(input ? input.value || 0 : 0);
        card.querySelectorAll(".js-demand, .js-demand-text").forEach(function (node) {
            node.textContent = String(total);
        });
        var registered = Number(card.dataset.registered || 0);
        var ratio = total > 0 ? Math.min(100, Math.floor(registered * 100 / total)) : 0;
        var ratioNode = card.querySelector(".js-ratio");
        if (ratioNode) {
            ratioNode.textContent = String(ratio);
        }
        var bar = card.querySelector(".sc-bar > span");
        if (bar) {
            bar.style.width = ratio + "%";
        }
        var cardError = card.querySelector(".sc-card-error");
        var message = initial ? "" : cardMessage(card, invalid);
        if (cardError) {
            cardError.hidden = message === "";
            cardError.textContent = message;
        }
        updateSummary();
    }

    function cardMessage(card, invalid) {
        if (!isDirty(card)) {
            return "";
        }
        var input = card.querySelector(".js-total");
        var bad = !input || (input.value !== "" && !/^\d+$/.test(input.value));
        var total = Number(input ? input.value || 0 : 0);
        if (invalid || bad) {
            return "Số nhân viên tối đa phải là số nguyên không âm.";
        }
        if (total < 1) {
            return "Số nhân viên tối đa của ca phải lớn hơn 0.";
        }
        if (staffCap > 0 && total > staffCap) {
            return "Số nhân viên tối đa không được vượt " + staffCap + " nhân viên Staff của cửa hàng.";
        }
        return "";
    }

    function isDirty(card) {
        var input = card.querySelector(".js-total");
        if (!input) {
            return false;
        }
        return String(Number(input.value || 0)) !== String(Number(input.dataset.initial || 0));
    }

    function nameFields(card, enabled) {
        card.querySelectorAll(".js-meta, .js-total").forEach(function (field) {
            if (enabled) {
                field.name = field.dataset.name;
            } else {
                field.removeAttribute("name");
            }
        });
    }

    function updateSummary() {
        var demand = 0;
        var shortSlots = 0;
        form.querySelectorAll(".sc-day").forEach(function (card) {
            var shown = card.querySelector(".js-demand");
            var total = Number(shown ? shown.textContent : 0);
            demand += total;
            var registered = Number(card.dataset.registered || 0);
            if (total > 0 && registered < total) {
                shortSlots += 1;
            }
        });
        var demandNode = document.getElementById("liveDemand");
        var shortNode = document.getElementById("liveShort");
        if (demandNode) {
            demandNode.textContent = String(demand);
        }
        if (shortNode) {
            shortNode.textContent = shortSlots === 0
                ? "Không có ca thiếu người"
                : shortSlots + " ca còn thiếu người";
        }
    }
})();
