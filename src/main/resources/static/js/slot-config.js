(function () {
    var form = document.getElementById("slotConfigForm");
    if (!form) {
        return;
    }
    var staffCap = Number(form.dataset.staff || 0);
    var error = document.getElementById("slotConfigError");
    if (staffCap < 1) {
        form.querySelectorAll(".js-step, .js-qty, .js-add, .sc-remove").forEach(function (field) {
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
                var input = button.parentElement.querySelector(".js-qty");
                var next = Number(input.value || 0) + Number(button.dataset.step);
                input.value = String(Math.max(0, next));
                refresh(card);
            });
        });
        card.querySelectorAll(".js-qty").forEach(function (input) {
            input.addEventListener("input", function () {
                input.value = input.value.replace(/\D/g, "");
                refresh(card);
            });
        });
        var add = card.querySelector(".js-add");
        if (add) {
            add.addEventListener("change", function () {
                if (!add.value) {
                    return;
                }
                var row = card.querySelector('.sc-pos[data-skill="' + add.value + '"]');
                if (row) {
                    row.classList.remove("is-hidden");
                    var input = row.querySelector(".js-qty");
                    if (Number(input.value || 0) < 1) {
                        input.value = "1";
                    }
                    var details = card.querySelector(".sc-details");
                    if (details) {
                        details.open = true;
                    }
                    refresh(card);
                }
                add.value = "";
            });
        }
        card.querySelectorAll(".sc-remove").forEach(function (button) {
            button.addEventListener("click", function () {
                var row = button.closest(".sc-pos");
                row.classList.add("is-hidden");
                row.querySelector(".js-qty").value = "0";
                refresh(card);
            });
        });
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
        var quantities = card.querySelectorAll(".js-qty");
        var sum = 0;
        var invalid = false;
        quantities.forEach(function (input) {
            if (input.value !== "" && !/^\d+$/.test(input.value)) {
                invalid = true;
            }
            sum += Number(input.value || 0);
        });
        var configured = card.dataset.configured === "true";
        var legacy = card.querySelector(".js-total").dataset.initial || "0";
        var total = (!configured && !isDirty(card) && sum === 0) ? Number(legacy) : sum;
        card.querySelectorAll(".js-demand, .js-demand-text").forEach(function (node) {
            node.textContent = String(total);
        });
        card.querySelector(".js-total").value = String(total);
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
        card.querySelectorAll(".sc-pos").forEach(function (row) {
            var qty = Number(row.querySelector(".js-qty").value || 0);
            var approved = Number(row.dataset.approved || 0);
            var gap = Math.max(0, qty - approved);
            var gapNode = row.querySelector(".js-gap");
            if (gapNode) {
                gapNode.classList.toggle("is-short", qty > 0 && gap > 0);
                gapNode.querySelector("b").textContent = String(gap);
            }
        });
        var cardError = card.querySelector(".sc-card-error");
        var message = initial ? "" : cardMessage(card, sum, invalid);
        if (cardError) {
            cardError.hidden = message === "";
            cardError.textContent = message;
        }
        updateSummary();
    }

    function cardMessage(card, sum, invalid) {
        if (!isDirty(card)) {
            return "";
        }
        var quantities = card.querySelectorAll(".js-qty");
        var total = 0;
        var bad = false;
        quantities.forEach(function (input) {
            if (input.value !== "" && !/^\d+$/.test(input.value)) {
                bad = true;
            }
            total += Number(input.value || 0);
        });
        if (invalid || bad) {
            return "Số lượng mỗi vị trí phải là số nguyên không âm.";
        }
        if (total < 1) {
            return "Tổng nhu cầu của ca phải lớn hơn 0.";
        }
        if (staffCap > 0 && total > staffCap) {
            return "Tổng nhu cầu không được vượt " + staffCap + " nhân viên Staff của cửa hàng.";
        }
        var overSkill = "";
        card.querySelectorAll(".sc-pos").forEach(function (row) {
            if (row.classList.contains("is-hidden") || overSkill) {
                return;
            }
            var qty = Number(row.querySelector(".js-qty").value || 0);
            var eligible = Number(row.dataset.eligible || 0);
            if (qty > eligible) {
                overSkill = "Vị trí " + row.dataset.skillName + " cần " + qty
                    + " người nhưng chỉ có " + eligible + " nhân viên đủ chứng chỉ.";
            }
        });
        if (overSkill) {
            return overSkill;
        }
        if (sum != null && sum !== total) {
            return "";
        }
        return "";
    }

    function isDirty(card) {
        var dirty = false;
        card.querySelectorAll(".js-qty").forEach(function (input) {
            if (String(Number(input.value || 0)) !== String(Number(input.dataset.initial || 0))) {
                dirty = true;
            }
        });
        return dirty;
    }

    function nameFields(card, enabled) {
        card.querySelectorAll(".js-meta, .js-total, .js-qty").forEach(function (field) {
            if (enabled) {
                field.name = field.dataset.name;
            } else {
                field.removeAttribute("name");
            }
        });
    }

    function updateSummary() {
        var demand = 0;
        var positions = 0;
        var missing = {};
        form.querySelectorAll(".sc-day").forEach(function (card) {
            var shown = card.querySelector(".js-demand");
            demand += Number(shown ? shown.textContent : 0);
            card.querySelectorAll(".sc-pos").forEach(function (row) {
                if (row.classList.contains("is-hidden")) {
                    return;
                }
                var qty = Number(row.querySelector(".js-qty").value || 0);
                positions += qty;
                var approved = Number(row.dataset.approved || 0);
                if (qty > approved) {
                    var name = row.dataset.skillName;
                    missing[name] = (missing[name] || 0) + (qty - approved);
                }
            });
        });
        var demandNode = document.getElementById("liveDemand");
        var positionNode = document.getElementById("livePositions");
        var shortNode = document.getElementById("liveShort");
        if (demandNode) {
            demandNode.textContent = String(demand);
        }
        if (positionNode) {
            positionNode.textContent = String(positions);
        }
        if (shortNode) {
            var names = Object.keys(missing);
            shortNode.textContent = names.length === 0
                ? "Không có vị trí thiếu"
                : names.map(function (name) { return "Thiếu " + missing[name] + " " + name; }).join(", ");
        }
    }
})();
