(function () {
    var form = document.getElementById("contract-form");
    var fileInput = document.getElementById("contractFile");
    var drop = document.getElementById("cc-drop");
    if (!form || !fileInput || !drop) return;

    var statusBox = document.getElementById("cc-status");
    var title = document.getElementById("cc-status-title");
    var desc = document.getElementById("cc-status-desc");
    var fileBar = document.getElementById("cc-file");
    var fileError = document.getElementById("cc-file-error");
    var signs = document.getElementById("cc-signs");
    var warnings = document.getElementById("cc-warnings");
    var retry = document.getElementById("cc-retry");
    var create = document.getElementById("create-contract");
    var employee = document.getElementById("employeeId");
    var ocr = { done: false, failed: false, citizen: "", name: "", manager: "", worker: "" };
    var MAX = 2 * 1024 * 1024;

    ["dragenter", "dragover"].forEach(function (name) {
        drop.addEventListener(name, function (event) {
            event.preventDefault();
            drop.classList.add("is-over");
        });
    });
    ["dragleave", "drop"].forEach(function (name) {
        drop.addEventListener(name, function (event) {
            event.preventDefault();
            drop.classList.remove("is-over");
        });
    });
    drop.addEventListener("drop", function (event) {
        var files = event.dataTransfer && event.dataTransfer.files;
        if (!files || !files.length) return;
        fileInput.files = files;
        onFile();
    });
    fileInput.addEventListener("change", onFile);
    document.getElementById("cc-replace").addEventListener("click", function () {
        fileInput.click();
    });
    document.getElementById("cc-remove").addEventListener("click", function () {
        fileInput.value = "";
        resetResult();
        showFile(null);
        setStatus("idle", "Vui lòng tải file hợp đồng để bắt đầu.", "");
    });
    retry.addEventListener("click", function () {
        if (currentFile()) extract(currentFile());
    });
    if (employee) {
        employee.addEventListener("change", function () {
            employee.dataset.userEdited = "1";
            refreshCreate();
        });
    }
    ["salary", "startDate", "endDate", "weeklyShiftCount"].forEach(function (key) {
        var input = document.getElementById(key);
        if (!input) return;
        input.addEventListener("input", function () {
            input.dataset.userEdited = "1";
            input.classList.remove("is-recognized", "is-review");
        });
    });
    var start = document.getElementById("startDate");
    var end = document.getElementById("endDate");
    if (start && end) {
        start.addEventListener("change", checkEnd);
        end.addEventListener("input", checkEnd);
    }
    form.addEventListener("submit", function (event) {
        if (!canCreate() || !checkEnd()) event.preventDefault();
    });

    function onFile() {
        var file = currentFile();
        resetResult();
        if (!file) {
            showFile(null);
            setStatus("idle", "Vui lòng tải file hợp đồng để bắt đầu.", "");
            return;
        }
        var reason = invalid(file);
        showFile(file);
        if (reason) {
            fileError.hidden = false;
            fileError.textContent = reason;
            setStatus("error", "Không xử lý được file", reason);
            retry.hidden = false;
            return;
        }
        fileError.hidden = true;
        extract(file);
    }

    function extract(file) {
        var body = new FormData();
        body.append("contractFile", file);
        var token = form.querySelector("input[name=_csrf]");
        if (token) body.append("_csrf", token.value);
        retry.hidden = true;
        create.disabled = true;
        setStatus("running", "Đang trích xuất thông tin", "OCR đang đọc nội dung hợp đồng và kiểm tra chữ ký.");
        fetch(form.getAttribute("action").replace(/\/create$/, "/ocr"), {
            method: "POST",
            body: body,
            headers: { "Accept": "application/json" }
        }).then(function (response) {
            if (!response.ok) throw new Error("OCR không xử lý được file.");
            return response.json();
        }).then(showResult).catch(function (error) {
            ocr.failed = true;
            setStatus("error", "Không trích xuất được thông tin", error.message || "OCR hết thời gian chờ hoặc không đọc được file.");
            paintSign("manager", null);
            paintSign("employee", null);
            signs.hidden = false;
            retry.hidden = false;
        }).finally(refreshCreate);
    }

    function showResult(data) {
        if (!data || data.status === "FAILED") {
            ocr.failed = true;
            setStatus("error", "Không trích xuất được thông tin", (data && data.message) || "OCR không đọc được nội dung cần thiết.");
            clearFields();
            paintSign("manager", data && data.managerSignature);
            paintSign("employee", data && data.employeeSignature);
            signs.hidden = false;
            retry.hidden = false;
            listWarnings([]);
            return;
        }
        ocr.done = true;
        applyFields(data.fields || []);
        if (data.suggestedEmployeeId && employee && employee.dataset.userEdited !== "1") {
            var option = employee.querySelector("option[value='" + data.suggestedEmployeeId + "']");
            if (option) {
                employee.value = String(data.suggestedEmployeeId);
            }
        }
        paintSign("manager", data.managerSignature);
        paintSign("employee", data.employeeSignature);
        signs.hidden = false;
        listWarnings(data.warnings || []);
        ocr.manager = data.managerSignature ? data.managerSignature.status : "";
        ocr.worker = data.employeeSignature ? data.employeeSignature.status : "";
        setOutcome(data);
    }

    function setOutcome(data) {
        var missing = [];
        if (ocr.manager === "KHONG_CO_CHU_KY") missing.push("bên sử dụng lao động");
        if (ocr.worker === "KHONG_CO_CHU_KY") missing.push("người lao động");
        if (missing.length) {
            setStatus("missing", "Chưa phát hiện đủ chữ ký", "Chưa phát hiện chữ ký của " + missing.join(" và ") + ".");
            return;
        }
        if (ocr.manager !== "CO_CHU_KY" || ocr.worker !== "CO_CHU_KY" || data.overall === "CAN_KIEM_TRA") {
            setStatus("review", "Cần kiểm tra thủ công", data.message || "Hệ thống chưa đủ độ tin cậy để kết luận chữ ký hoặc thông tin đối chiếu.");
            return;
        }
        if (data.overall === "KHONG_DAT") {
            setStatus("review", "Cần đối chiếu nhân viên", data.message || "Hãy chọn đúng nhân viên khớp họ tên và số căn cước trên hợp đồng.");
            return;
        }
        setStatus("ok", "Trích xuất thông tin thành công", "Đã đọc hợp đồng và phát hiện chữ ký của cả hai bên. Hãy kiểm tra lại trước khi tạo.");
    }

    function applyFields(fields) {
        var known = { startDate: 1, endDate: 1, salary: 1, weeklyShiftCount: 1 };
        fields.forEach(function (field) {
            var confidence = field.confidence || (field.recognized ? "OK" : "MISSING");
            if (field.key === "citizenId") {
                ocr.citizen = field.recognized ? field.value || "" : "";
                document.getElementById("citizenDisplay").value = field.recognized ? (field.display || field.value) : "";
                mark("citizen", confidence);
            } else if (field.key === "fullName") {
                ocr.name = field.recognized ? field.display || field.value || "" : "";
            } else if (known[field.key]) {
                var input = document.getElementById(field.key);
                if (!input) return;
                var next = field.recognized && field.value ? field.value : "";
                if (input.dataset.userEdited === "1" && input.value && next && input.value !== next) {
                    input.classList.add("is-review");
                    mark(field.key, "REVIEW", "Cần kiểm tra: hợp đồng ghi " + (field.display || next) + ".");
                    return;
                }
                if (input.dataset.userEdited !== "1") {
                    input.value = next;
                }
                input.classList.remove("is-recognized", "is-review");
                if (next && input.dataset.userEdited !== "1") {
                    input.classList.add(confidence === "REVIEW" ? "is-review" : "is-recognized");
                }
                mark(field.key, next ? confidence : "MISSING");
            }
        });
    }

    function clearFields() {
        ["startDate", "endDate", "salary", "weeklyShiftCount"].forEach(function (key) {
            var input = document.getElementById(key);
            if (!input) return;
            input.classList.remove("is-recognized", "is-review");
            if (input.dataset.userEdited === "1") return;
            input.value = "";
            mark(key, "");
        });
        document.getElementById("citizenDisplay").value = "";
        mark("citizen", "");
    }

    function paintSign(who, sign) {
        var card = document.getElementById("sign-" + who);
        var status = document.getElementById("sign-" + who + "-status");
        var place = document.getElementById("sign-" + who + "-place");
        card.classList.remove("is-ok", "is-bad", "is-review");
        if (!sign || !sign.status || sign.status === "CHUA_XAC_DINH") {
            status.textContent = "Không đủ độ tin cậy — cần kiểm tra thủ công";
            card.classList.add("is-review");
        } else if (sign.status === "CO_CHU_KY") {
            status.textContent = "Đã phát hiện chữ ký";
            card.classList.add("is-ok");
        } else {
            status.textContent = "Chưa phát hiện chữ ký";
            card.classList.add("is-bad");
        }
        var where = sign && sign.location ? sign.location : "Chưa xác định vị trí";
        place.textContent = where + (sign && sign.reason ? ". " + sign.reason : "");
    }

    function identityOk() {
        var option = selected();
        if (!option || !option.value) return false;
        var citizen = option.getAttribute("data-citizen") || "";
        var name = option.getAttribute("data-name") || "";
        if (!citizen || !ocr.citizen || citizen !== ocr.citizen) return false;
        return !!ocr.name && fold(ocr.name) === fold(name);
    }

    function canCreate() {
        if (!ocr.done || ocr.failed) return false;
        if (ocr.manager !== "CO_CHU_KY" || ocr.worker !== "CO_CHU_KY") return false;
        return identityOk() && checkEnd();
    }

    function refreshCreate() {
        var option = selected();
        var citizen = option ? (option.getAttribute("data-citizen") || "") : "";
        var missing = document.getElementById("citizen-missing");
        var flag = document.getElementById("employee-flag");
        if (missing) missing.hidden = !option || !option.value || citizen.length > 0;
        if (flag) {
            var mismatch = ocr.done && option && option.value && ocr.citizen && citizen !== ocr.citizen;
            var nameMismatch = ocr.done && option && option.value && ocr.name && fold(ocr.name) !== fold(option.getAttribute("data-name") || "");
            flag.hidden = !(mismatch || nameMismatch);
            flag.textContent = mismatch || nameMismatch
                ? "Cần kiểm tra: họ tên hoặc CCCD không khớp hồ sơ đã chọn."
                : "";
        }
        var citizenFlag = document.getElementById("citizen-flag");
        if (citizenFlag && ocr.done) {
            citizenFlag.classList.remove("is-ok", "is-review");
            if (!ocr.citizen) {
                citizenFlag.hidden = false;
                citizenFlag.textContent = "Chưa trích xuất được";
            } else if (option && option.value && citizen && citizen !== ocr.citizen) {
                citizenFlag.hidden = false;
                citizenFlag.classList.add("is-review");
                citizenFlag.textContent = "Không khớp CCCD trên hồ sơ nhân viên.";
            } else if (citizenFlag.textContent.indexOf("Cần kiểm tra") === 0) {
                citizenFlag.hidden = false;
                citizenFlag.classList.add("is-review");
            } else {
                citizenFlag.hidden = false;
                citizenFlag.classList.add("is-ok");
                citizenFlag.textContent = "Đã nhận dạng";
            }
        }
        if (ocr.done && !ocr.failed && ocr.manager === "CO_CHU_KY" && ocr.worker === "CO_CHU_KY") {
            if (identityOk()) {
                setStatus("ok", "Trích xuất thông tin thành công", "Đã đọc hợp đồng và phát hiện chữ ký của cả hai bên. Hãy kiểm tra lại trước khi tạo.");
            } else if (option && option.value) {
                setStatus("review", "Cần đối chiếu nhân viên", "Họ tên hoặc số căn cước chưa khớp nhân viên đã chọn.");
            }
        }
        if (create) create.disabled = !canCreate();
    }

    function checkEnd() {
        var startInput = document.getElementById("startDate");
        var endInput = document.getElementById("endDate");
        var error = document.getElementById("end-client-error");
        if (!startInput || !endInput) return true;
        if (endInput.value && startInput.value && endInput.value < startInput.value) {
            endInput.setCustomValidity("Ngày kết thúc không được trước ngày bắt đầu hiệu lực.");
            if (error) {
                error.hidden = false;
                error.textContent = "Ngày kết thúc không được trước ngày bắt đầu hiệu lực.";
            }
            return false;
        }
        endInput.setCustomValidity("");
        if (error) error.hidden = true;
        return true;
    }

    function resetResult() {
        ocr.done = false;
        ocr.failed = false;
        ocr.citizen = "";
        ocr.name = "";
        ocr.manager = "";
        ocr.worker = "";
        clearFields();
        listWarnings([]);
        signs.hidden = true;
        retry.hidden = true;
        fileError.hidden = true;
        if (create) create.disabled = true;
        var flag = document.getElementById("employee-flag");
        if (flag) flag.hidden = true;
    }

    function showFile(file) {
        fileBar.hidden = !file;
        if (!file) return;
        document.getElementById("cc-file-name").textContent = file.name;
        document.getElementById("cc-file-size").textContent = Math.max(1, Math.ceil(file.size / 1024)) + " KB";
    }

    function invalid(file) {
        var lower = file.name.toLowerCase();
        var allowed = lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        if (!allowed) return "File hợp đồng phải là PDF, JPG, JPEG hoặc PNG.";
        if (file.size > MAX) return "File hợp đồng vượt quá dung lượng cho phép (2 MB).";
        return "";
    }

    function setStatus(state, heading, detail) {
        statusBox.dataset.state = state;
        title.textContent = heading;
        desc.textContent = detail || "";
    }

    function mark(key, confidence, custom) {
        var node = key === "citizen"
            ? document.getElementById("citizen-flag")
            : document.getElementById(key + "-flag");
        if (!node) return;
        node.classList.remove("is-ok", "is-review");
        if (confidence === "OK") {
            node.hidden = false;
            node.textContent = "Đã nhận dạng";
            node.classList.add("is-ok");
        } else if (confidence === "REVIEW") {
            node.hidden = false;
            node.textContent = custom || "Cần kiểm tra";
            node.classList.add("is-review");
        } else if (confidence === "MISSING") {
            node.hidden = false;
            node.textContent = "Chưa trích xuất được";
        } else {
            node.hidden = true;
            node.textContent = "";
        }
    }

    function listWarnings(items) {
        warnings.replaceChildren();
        items.forEach(function (item) {
            var li = document.createElement("li");
            li.textContent = item;
            warnings.appendChild(li);
        });
    }

    function selected() {
        return employee && employee.options[employee.selectedIndex];
    }

    function currentFile() {
        return fileInput.files && fileInput.files[0];
    }

    function fold(value) {
        return (value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "")
            .replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase().replace(/[^a-z0-9]/g, "");
    }
})();
