/*
 * Check-in / Check-out (ca làm, Manager, lớp đào tạo).
 * Xác thực theo IP mạng: máy chủ tự đọc IP từ kết nối của request, trình duyệt không gửi vị trí hay IP.
 * Luồng: bấm nút → máy chủ kiểm tra trước (ca, giờ, IP mạng cửa hàng) → chụp ảnh → gửi form.
 */
(function () {
    var checkingTitle = "Đang kiểm tra điều kiện chấm công...";

    function dialog() {
        var box = document.getElementById("clock-dialog");
        if (box) {
            return box;
        }
        box = document.createElement("div");
        box.id = "clock-dialog";
        box.className = "clock-dialog";
        box.hidden = true;
        box.innerHTML = '<p class="clock-dialog-title"></p><p class="clock-dialog-body"></p><button type="button" class="secondary-btn clock-dialog-close">Đóng</button>';
        document.body.appendChild(box);
        box.querySelector(".clock-dialog-close").addEventListener("click", function () {
            box.hidden = true;
        });
        return box;
    }

    function showDialog(title, message, closable) {
        var box = dialog();
        box.hidden = false;
        box.querySelector(".clock-dialog-title").textContent = title;
        box.querySelector(".clock-dialog-body").textContent = message || "";
        box.querySelector(".clock-dialog-close").hidden = !closable;
    }

    function hideDialog() {
        var box = document.getElementById("clock-dialog");
        if (box) {
            box.hidden = true;
        }
    }

    function showInlineError(form, message) {
        var actions = form.closest(".clock-actions") || form.parentNode;
        var box = actions.parentNode.querySelector(".clock-error");
        if (!box) {
            box = document.createElement("p");
            box.className = "flash clock-error";
            actions.parentNode.insertBefore(box, actions);
        }
        box.textContent = message;
    }

    function previewAttendance(form) {
        var body = new URLSearchParams();
        ["classId", "assignmentId", "_csrf"].forEach(function (name) {
            var field = form.querySelector("[name=" + name + "]");
            if (field) {
                body.set(field.name, field.value);
            }
        });
        return fetch(form.getAttribute("data-preview-url"), {
            method: "POST",
            headers: { "Accept": "application/json" },
            body: body,
            credentials: "same-origin"
        }).then(function (response) {
            return response.json().catch(function () {
                return {};
            }).then(function (data) {
                if (!response.ok) {
                    throw new Error(data.message || "Chưa đủ điều kiện chấm công.");
                }
            });
        });
    }

    function cameraDialog() {
        var box = document.getElementById("camera-dialog");
        if (box) {
            return box;
        }
        box = document.createElement("div");
        box.id = "camera-dialog";
        box.className = "camera-dialog";
        box.hidden = true;
        box.innerHTML = '<div class="camera-card"><p class="clock-dialog-title">Chụp ảnh nhân viên</p><video autoplay playsinline muted></video><div class="camera-actions"><button type="button" class="primary-btn camera-capture">Chụp ảnh</button><button type="button" class="secondary-btn camera-cancel">Hủy</button></div></div>';
        document.body.appendChild(box);
        return box;
    }

    function stopCamera(stream) {
        if (stream) {
            stream.getTracks().forEach(function (track) {
                track.stop();
            });
        }
    }

    function requestCamera() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            return Promise.reject(new Error("Máy này không có camera."));
        }
        return navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false })
            .catch(function (error) {
                if (error && (error.name === "NotAllowedError" || error.name === "PermissionDeniedError")) {
                    throw new Error("Hãy cho phép truy cập camera để chụp ảnh nhân viên.");
                }
                return navigator.mediaDevices.getUserMedia({ video: true, audio: false });
            });
    }

    function capturePhoto(form) {
        return requestCamera().then(function (stream) {
            return new Promise(function (resolve, reject) {
                var box = cameraDialog();
                var video = box.querySelector("video");
                var capture = box.querySelector(".camera-capture");
                var cancel = box.querySelector(".camera-cancel");
                box.hidden = false;
                video.srcObject = stream;
                function cleanup() {
                    stopCamera(stream);
                    video.srcObject = null;
                    box.hidden = true;
                    capture.removeEventListener("click", onCapture);
                    cancel.removeEventListener("click", onCancel);
                }
                function onCancel() {
                    cleanup();
                    reject(new Error("Đã hủy chụp ảnh."));
                }
                function onCapture() {
                    var width = video.videoWidth || 640;
                    var height = video.videoHeight || 480;
                    var scale = Math.min(1, 640 / width);
                    var canvas = document.createElement("canvas");
                    canvas.width = Math.max(1, Math.round(width * scale));
                    canvas.height = Math.max(1, Math.round(height * scale));
                    canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
                    canvas.toBlob(function (blob) {
                        cleanup();
                        if (!blob) {
                            reject(new Error("Không chụp được ảnh. Hãy thử lại."));
                            return;
                        }
                        var input = form.querySelector("[name=photo]");
                        var transfer = new DataTransfer();
                        transfer.items.add(new File([blob], "attendance.jpg", { type: "image/jpeg" }));
                        input.files = transfer.files;
                        resolve();
                    }, "image/jpeg", 0.85);
                }
                capture.addEventListener("click", onCapture);
                cancel.addEventListener("click", onCancel);
            });
        });
    }

    document.querySelectorAll("[data-check-notice]").forEach(function (button) {
        button.addEventListener("click", function () {
            showDialog("Thông báo", button.getAttribute("data-check-notice") || "Chưa thể Check-in.", true);
        });
    });

    document.querySelectorAll("form.clock-form").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var button = form.querySelector("button");
            if (button) {
                button.disabled = true;
            }
            showDialog(checkingTitle, "", false);
            previewAttendance(form).then(function () {
                hideDialog();
                if (!form.hasAttribute("data-require-photo")) {
                    return null;
                }
                return capturePhoto(form);
            }).then(function () {
                HTMLFormElement.prototype.submit.call(form);
            }).catch(function (error) {
                if (button) {
                    button.disabled = false;
                }
                var message = error && error.message ? error.message : "Chưa đủ điều kiện chấm công.";
                showInlineError(form, message);
                showDialog("Không thể chấm công", message, true);
            });
        });
    });
})();
