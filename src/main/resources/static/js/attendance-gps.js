(function () {
    var firstTimeoutMs = 10000;
    var retryTimeoutMs = 5000;
    var unknownMessage = "Không thể xác định vị trí của bạn.";
    var inaccurateMessage = "Vị trí chưa đủ chính xác. Hãy thử lại ở nơi có tín hiệu tốt hơn.";
    var locatingTitle = "Đang xác định vị trí...";
    var lastPosition = null;
    var pending = null;

    function dialog() {
        var box = document.getElementById("gps-dialog");
        if (box) {
            return box;
        }
        box = document.createElement("div");
        box.id = "gps-dialog";
        box.className = "gps-dialog";
        box.hidden = true;
        box.innerHTML = '<p class="gps-dialog-title"></p><p class="gps-dialog-body"></p><button type="button" class="secondary-btn gps-dialog-close">Đóng</button>';
        document.body.appendChild(box);
        box.querySelector(".gps-dialog-close").addEventListener("click", function () {
            box.hidden = true;
        });
        return box;
    }

    function showLocating() {
        var box = dialog();
        box.hidden = false;
        box.querySelector(".gps-dialog-title").textContent = locatingTitle;
        box.querySelector(".gps-dialog-body").textContent = "";
        box.querySelector(".gps-dialog-close").hidden = true;
    }

    function showCoordinates(position, notice) {
        var coords = position.coords;
        var lines = [
            "Vĩ độ: " + Number(coords.latitude).toFixed(6),
            "Kinh độ: " + Number(coords.longitude).toFixed(6)
        ];
        if (typeof coords.accuracy === "number") {
            lines.push("Độ chính xác: " + Math.round(coords.accuracy) + " m");
        }
        if (notice) {
            lines.push("");
            lines.push(notice);
        }
        var box = dialog();
        box.hidden = false;
        box.querySelector(".gps-dialog-title").textContent = "Vị trí hiện tại";
        box.querySelector(".gps-dialog-body").textContent = lines.join("\n");
        box.querySelector(".gps-dialog-close").hidden = false;
    }

    function showDialogError(message) {
        var box = dialog();
        box.hidden = false;
        box.querySelector(".gps-dialog-title").textContent = "Không xác định được vị trí";
        box.querySelector(".gps-dialog-body").textContent = message;
        box.querySelector(".gps-dialog-close").hidden = false;
    }

    function showInlineError(form, message) {
        var actions = form.closest(".clock-actions") || form.parentNode;
        var box = actions.parentNode.querySelector(".gps-error");
        if (!box) {
            box = document.createElement("p");
            box.className = "flash gps-error";
            actions.parentNode.insertBefore(box, actions);
        }
        box.textContent = message;
    }

    function readOnce(timeout) {
        if (pending) {
            return pending;
        }
        pending = new Promise(function (resolve, reject) {
            if (!navigator.geolocation) {
                reject(new Error(unknownMessage));
                return;
            }
            navigator.geolocation.getCurrentPosition(function (position) {
                lastPosition = position;
                resolve(position);
            }, function () {
                reject(new Error(unknownMessage));
            }, {
                enableHighAccuracy: true,
                maximumAge: 0,
                timeout: timeout
            });
        });
        pending.finally(function () {
            pending = null;
        });
        return pending;
    }

    function requiredAccuracy(form) {
        if (!form || !form.hasAttribute("data-max-accuracy")) {
            return null;
        }
        var value = Number(form.getAttribute("data-max-accuracy"));
        return Number.isFinite(value) ? value : null;
    }

    function withinAccuracy(position, maxAccuracy) {
        return maxAccuracy == null
            || (position
                && typeof position.coords.accuracy === "number"
                && position.coords.accuracy >= 0
                && position.coords.accuracy <= maxAccuracy);
    }

    function locate() {
        return readOnce(lastPosition ? retryTimeoutMs : firstTimeoutMs).catch(function () {
            if (!lastPosition) {
                throw new Error(unknownMessage);
            }
            return lastPosition;
        });
    }

    function hideDialog() {
        var box = document.getElementById("gps-dialog");
        if (box) {
            box.hidden = true;
        }
    }

    function previewAttendance(form) {
        var url = form.getAttribute("data-preview-url");
        var body = new URLSearchParams();
        body.set("latitude", form.querySelector("[name=latitude]").value);
        body.set("longitude", form.querySelector("[name=longitude]").value);
        body.set("accuracy", form.querySelector("[name=accuracy]").value);
        var classId = form.querySelector("[name=classId]");
        if (classId) {
            body.set("classId", classId.value);
        }
        var csrf = form.querySelector("[name=_csrf]");
        if (csrf) {
            body.set(csrf.name, csrf.value);
        }
        return fetch(url, {
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
        box.innerHTML = '<div class="camera-card"><p class="gps-dialog-title">Chụp ảnh nhân viên</p><video autoplay playsinline muted></video><div class="camera-actions"><button type="button" class="primary-btn camera-capture">Chụp ảnh</button><button type="button" class="secondary-btn camera-cancel">Hủy</button></div></div>';
        document.body.appendChild(box);
        return box;
    }

    function stopCamera(stream) {
        if (!stream) {
            return;
        }
        stream.getTracks().forEach(function (track) {
            track.stop();
        });
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
                        var file = new File([blob], "attendance.jpg", { type: "image/jpeg" });
                        var transfer = new DataTransfer();
                        transfer.items.add(file);
                        input.files = transfer.files;
                        resolve();
                    }, "image/jpeg", 0.85);
                }
                capture.addEventListener("click", onCapture);
                cancel.addEventListener("click", onCancel);
            });
        });
    }

    var forms = document.querySelectorAll("form.gps-form");
    if (!forms.length) {
        return;
    }

    showLocating();
    locate().then(function (position) {
        showCoordinates(position);
    }).catch(function (error) {
        showDialogError(error && error.message ? error.message : unknownMessage);
    });

    forms.forEach(function (form) {
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var button = form.querySelector("button");
            if (button) {
                button.disabled = true;
            }
            showLocating();
            var maxAccuracy = requiredAccuracy(form);
            locate().then(function (position) {
                if (!withinAccuracy(position, maxAccuracy)) {
                    if (button) {
                        button.disabled = false;
                    }
                    showInlineError(form, inaccurateMessage);
                    showCoordinates(position, inaccurateMessage);
                    return;
                }
                showCoordinates(position);
                form.querySelector("[name=latitude]").value = String(position.coords.latitude);
                form.querySelector("[name=longitude]").value = String(position.coords.longitude);
                form.querySelector("[name=accuracy]").value = String(position.coords.accuracy);
                if (!form.hasAttribute("data-require-photo")) {
                    window.setTimeout(function () {
                        HTMLFormElement.prototype.submit.call(form);
                    }, 700);
                    return;
                }
                hideDialog();
                previewAttendance(form).then(function () {
                    return capturePhoto(form);
                }).then(function () {
                    HTMLFormElement.prototype.submit.call(form);
                }).catch(function (error) {
                    if (button) {
                        button.disabled = false;
                    }
                    var message = error && error.message ? error.message : "Không chụp được ảnh nhân viên.";
                    showInlineError(form, message);
                    showDialogError(message);
                });
            }).catch(function (error) {
                if (button) {
                    button.disabled = false;
                }
                var message = error && error.message ? error.message : unknownMessage;
                showInlineError(form, message);
                showDialogError(message);
            });
        });
    });
})();
