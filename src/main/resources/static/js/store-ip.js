/*
 * "Lấy IP mạng hiện tại" trong ô "Địa chỉ IP mạng được phép chấm công" (form tạo/sửa cửa hàng).
 * 1) Ưu tiên IP mà máy chủ thực sự nhận được (GET data-ip-url) — chính IP được so khớp khi chấm công.
 * 2) Nếu máy chủ chỉ thấy IP nội bộ (vd. chạy local), trình duyệt hỏi dịch vụ công khai để lấy IP public.
 * IP chỉ được điền vào ô để Admin kiểm tra; không gửi form, không lưu. Backend vẫn kiểm tra khi lưu
 * và cơ chế xác thực IP lúc chấm công không thay đổi.
 */
(function () {
    var PUBLIC_IP_SOURCES = [
        { url: "https://api.ipify.org?format=json", json: true },
        { url: "https://api64.ipify.org?format=json", json: true },
        { url: "https://icanhazip.com", json: false }
    ];
    var IPV4 = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;
    var IPV6 = /^[0-9a-fA-F:]+$/;

    document.addEventListener("click", function (event) {
        var button = event.target.closest("[data-fill-ip]");
        if (!button) {
            return;
        }
        event.preventDefault();
        var form = button.closest("form");
        var field = form && form.querySelector("[name=allowedIps]");
        if (!field) {
            return;
        }
        button.disabled = true;
        setStatus(form, "Đang lấy IP public của mạng hiện tại...", null);
        serverIp(button.getAttribute("data-ip-url")).then(function (server) {
            if (server.ok && server.ip) {
                fill(form, field, server.ip, "Đã điền IP " + server.ip + ". Hãy kiểm tra và bấm lưu để áp dụng.");
                return null;
            }
            return publicIp().then(function (ip) {
                var note = server.ip
                    ? " Lưu ý: máy chủ đang nhận IP nội bộ " + server.ip
                      + ", nên IP public chỉ khớp khi hệ thống chạy trên Internet."
                    : "";
                fill(form, field, ip, "Đã điền IP public " + ip + ". Hãy kiểm tra và bấm lưu để áp dụng." + note);
            });
        }).catch(function () {
            setStatus(form, "Không lấy được IP public của mạng hiện tại. Vui lòng nhập IP thủ công.", "is-error");
        }).finally(function () {
            button.disabled = false;
        });
    });

    /** IP mà máy chủ nhận được từ kết nối; lỗi mạng thì coi như chưa xác định. */
    function serverIp(url) {
        return fetch(url, {
            method: "GET",
            headers: { "Accept": "application/json" },
            credentials: "same-origin",
            cache: "no-store"
        }).then(function (response) {
            return response.ok ? response.json() : {};
        }).then(function (data) {
            return { ok: !!(data && data.ok), ip: data && data.ip ? String(data.ip) : null };
        }).catch(function () {
            return { ok: false, ip: null };
        });
    }

    /** IP public của mạng đang dùng, thử lần lượt các nguồn; chỉ nhận chuỗi đúng định dạng IP. */
    function publicIp(index) {
        var i = index || 0;
        if (i >= PUBLIC_IP_SOURCES.length) {
            return Promise.reject(new Error("no-public-ip"));
        }
        var source = PUBLIC_IP_SOURCES[i];
        return fetch(source.url, { method: "GET", cache: "no-store", credentials: "omit" })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error();
                }
                return source.json ? response.json().then(function (data) { return data && data.ip; }) : response.text();
            })
            .then(function (raw) {
                var ip = String(raw || "").trim();
                if (!isIp(ip)) {
                    throw new Error();
                }
                return ip;
            })
            .catch(function () {
                return publicIp(i + 1);
            });
    }

    function isIp(value) {
        return IPV4.test(value) || (value.indexOf(":") !== -1 && IPV6.test(value));
    }

    function fill(form, field, ip, message) {
        var existing = field.value.split(/[,;\s]+/).filter(Boolean);
        if (existing.indexOf(ip) !== -1) {
            setStatus(form, "IP " + ip + " đã có trong danh sách.", "is-ok");
            return;
        }
        existing.push(ip);
        field.value = existing.join(", ");
        field.dispatchEvent(new Event("input", { bubbles: true }));
        field.focus();
        setStatus(form, message, "is-ok");
    }

    function setStatus(form, text, tone) {
        var status = form.querySelector("[data-ip-status]");
        if (!status) {
            return;
        }
        status.textContent = text;
        status.classList.remove("is-error", "is-ok");
        if (tone) {
            status.classList.add(tone);
        }
    }
})();
