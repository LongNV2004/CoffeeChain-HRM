(function () {
    function init() {
        var wrap = document.querySelector(".notif-wrap");
        if (!wrap || wrap.dataset.bound === "true") {
            return;
        }
        wrap.dataset.bound = "true";

        var button = wrap.querySelector(".notif-bell-btn");
        var popup = wrap.querySelector(".notif-popup");
        var list = wrap.querySelector(".notif-popup-body");
        var markAll = wrap.querySelector(".notif-mark-all");
        if (!button || !popup || !list) {
            return;
        }

        var feedUrl = button.getAttribute("data-feed-url");
        var readBase = button.getAttribute("data-read-base");
        var readAllUrl = button.getAttribute("data-read-all-url");
        var csrf = button.getAttribute("data-csrf");
        var csrfHeader = button.getAttribute("data-csrf-header") || "X-CSRF-TOKEN";
        var open = false;
        var loading = false;

        function setOpen(next) {
            open = next;
            popup.hidden = !open;
            button.setAttribute("aria-expanded", open ? "true" : "false");
            if (open) {
                loadFeed();
            }
        }

        function setBadge(count) {
            var value = Number(count) || 0;
            var badge = button.querySelector(".notif-badge");
            if (value <= 0) {
                if (badge) {
                    badge.remove();
                }
                if (markAll) {
                    markAll.disabled = true;
                }
                return;
            }
            if (!badge) {
                badge = document.createElement("span");
                badge.className = "notif-badge";
                button.appendChild(badge);
            }
            badge.textContent = value > 99 ? "99+" : String(value);
            if (markAll) {
                markAll.disabled = false;
            }
        }

        function formatTime(value) {
            if (!value) {
                return "";
            }
            var year;
            var month;
            var day;
            var hour;
            var minute;
            if (Array.isArray(value) && value.length >= 5) {
                year = value[0];
                month = value[1];
                day = value[2];
                hour = value[3];
                minute = value[4];
            } else if (typeof value === "string") {
                var match = value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
                if (!match) {
                    return "";
                }
                year = match[1];
                month = match[2];
                day = match[3];
                hour = match[4];
                minute = match[5];
            } else {
                return "";
            }
            var pad = function (n) {
                return String(n).padStart(2, "0");
            };
            return pad(day) + "/" + pad(month) + "/" + year + " " + pad(hour) + ":" + pad(minute);
        }

        function safePath(url) {
            return typeof url === "string" && url.charAt(0) === "/" && url.charAt(1) !== "/";
        }

        function renderEmpty(text, isError) {
            list.replaceChildren();
            var message = document.createElement("p");
            message.className = isError ? "notif-error" : "notif-empty";
            message.textContent = text;
            list.appendChild(message);
        }

        function showError(text) {
            var existing = list.querySelector(".notif-error");
            if (existing) {
                existing.remove();
            }
            var message = document.createElement("p");
            message.className = "notif-error";
            message.textContent = text;
            list.prepend(message);
        }

        function readUrl(id) {
            var base = readBase.charAt(readBase.length - 1) === "/" ? readBase : readBase + "/";
            return base + id + "/read";
        }

        function renderItems(items) {
            list.replaceChildren();
            if (!items || items.length === 0) {
                renderEmpty("Chưa có thông báo.", false);
                if (markAll) {
                    markAll.disabled = true;
                }
                return;
            }
            var unread = 0;
            items.forEach(function (item) {
                if (!item.read) {
                    unread += 1;
                }
                list.appendChild(renderItem(item));
            });
            if (markAll) {
                markAll.disabled = unread === 0;
            }
        }

        function renderItem(item) {
            var row = document.createElement("button");
            row.type = "button";
            row.className = "notif-item" + (item.read ? " is-read" : " is-unread");
            row.dataset.id = item.id;

            var top = document.createElement("span");
            top.className = "notif-item-top";

            if (!item.read) {
                var dot = document.createElement("span");
                dot.className = "notif-dot";
                top.appendChild(dot);
            }

            var title = document.createElement("span");
            title.className = "notif-title";
            title.textContent = item.title || "Thông báo";
            top.appendChild(title);

            if (!item.read) {
                var badge = document.createElement("span");
                badge.className = "notif-new";
                badge.textContent = "Mới";
                top.appendChild(badge);
            }

            var message = document.createElement("span");
            message.className = "notif-message";
            message.textContent = item.message || "";

            var time = document.createElement("span");
            time.className = "notif-time";
            time.textContent = formatTime(item.createdAt);

            row.appendChild(top);
            row.appendChild(message);
            row.appendChild(time);

            row.addEventListener("click", function () {
                onItemClick(row, item);
            });
            return row;
        }

        function markRowRead(row) {
            row.classList.remove("is-unread");
            row.classList.add("is-read");
            var dot = row.querySelector(".notif-dot");
            var badge = row.querySelector(".notif-new");
            if (dot) {
                dot.remove();
            }
            if (badge) {
                badge.remove();
            }
        }

        function postJson(url) {
            var headers = {
                "X-Requested-With": "XMLHttpRequest",
                "Accept": "application/json"
            };
            if (csrf) {
                headers[csrfHeader] = csrf;
            }
            return fetch(url, {
                method: "POST",
                headers: headers,
                credentials: "same-origin"
            }).then(function (response) {
                var type = response.headers.get("content-type") || "";
                if (!type.includes("application/json")) {
                    throw new Error("Không cập nhật được thông báo.");
                }
                return response.json().then(function (body) {
                    if (!response.ok || body.ok === false) {
                        throw new Error(body.message || "Không cập nhật được thông báo.");
                    }
                    return body;
                });
            });
        }

        function onItemClick(row, item) {
            if (row.dataset.busy === "true") {
                return;
            }
            var go = function () {
                if (safePath(item.actionUrl)) {
                    window.location.href = item.actionUrl;
                }
            };
            if (item.read) {
                if (safePath(item.actionUrl)) {
                    window.location.href = item.actionUrl;
                } else {
                    setOpen(false);
                }
                return;
            }
            row.dataset.busy = "true";
            postJson(readUrl(item.id))
                .then(function (body) {
                    item.read = true;
                    markRowRead(row);
                    if (typeof body.unreadCount !== "undefined") {
                        setBadge(body.unreadCount);
                    }
                    go();
                })
                .catch(function (error) {
                    row.dataset.busy = "false";
                    showError(error.message || "Không đánh dấu được đã đọc.");
                })
                .finally(function () {
                    row.dataset.busy = "false";
                });
        }

        function loadFeed() {
            if (loading) {
                return;
            }
            loading = true;
            if (!list.childElementCount) {
                renderEmpty("Đang tải...", false);
            }
            fetch(feedUrl, {
                headers: { "Accept": "application/json" },
                credentials: "same-origin"
            }).then(function (response) {
                var type = response.headers.get("content-type") || "";
                if (!response.ok || !type.includes("application/json")) {
                    throw new Error("Không tải được thông báo.");
                }
                return response.json();
            }).then(function (items) {
                renderItems(items);
            }).catch(function (error) {
                renderEmpty(error.message || "Không tải được thông báo.", true);
            }).finally(function () {
                loading = false;
            });
        }

        button.addEventListener("click", function (event) {
            event.stopPropagation();
            setOpen(!open);
        });

        if (markAll) {
            markAll.addEventListener("click", function () {
                if (markAll.disabled) {
                    return;
                }
                markAll.disabled = true;
                postJson(readAllUrl).then(function () {
                    setBadge(0);
                    list.querySelectorAll(".notif-item").forEach(markRowRead);
                }).catch(function (error) {
                    markAll.disabled = false;
                    showError(error.message || "Không đánh dấu được đã đọc.");
                });
            });
        }

        document.addEventListener("click", function (event) {
            if (!open) {
                return;
            }
            if (event.target.closest("[data-open-notifications]")) {
                return;
            }
            if (!wrap.contains(event.target)) {
                setOpen(false);
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && open) {
                setOpen(false);
            }
        });

        document.addEventListener("click", function (event) {
            if (!event.target.closest("[data-open-notifications]")) {
                return;
            }
            event.preventDefault();
            setOpen(true);
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
