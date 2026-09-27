(function () {
    var ZONE = "Asia/Ho_Chi_Minh";

    function pad(value) {
        return String(value).padStart(2, "0");
    }

    function readAnchor() {
        var node = document.querySelector("[data-vietnam-now]");
        var raw = node ? node.getAttribute("data-vietnam-now") : "";
        var match = raw && raw.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
        if (!match) {
            return null;
        }
        return {
            utcMs: Date.UTC(+match[1], +match[2] - 1, +match[3], +match[4], +match[5], +match[6]),
            loadedAt: Date.now()
        };
    }

    var anchor = readAnchor();

    function current() {
        if (!anchor) {
            return null;
        }
        var elapsed = Date.now() - anchor.loadedAt;
        var instant = new Date(anchor.utcMs + elapsed);
        return {
            date: instant.getUTCFullYear() + "-" + pad(instant.getUTCMonth() + 1) + "-" + pad(instant.getUTCDate()),
            time: pad(instant.getUTCHours()) + ":" + pad(instant.getUTCMinutes())
        };
    }

    function isBeforeNow(dateValue, timeValue) {
        var now = current();
        if (!now || !dateValue || !timeValue) {
            return false;
        }
        var time = String(timeValue).slice(0, 5);
        return (dateValue + "T" + time) < (now.date + "T" + now.time);
    }

    window.CoffeeHrmTime = {
        zone: ZONE,
        today: function () {
            var now = current();
            return now ? now.date : "";
        },
        currentTime: function () {
            var now = current();
            return now ? now.time : "";
        },
        isBeforeNow: isBeforeNow
    };
})();
