(function () {
    document.addEventListener("click", function (event) {
        var button = event.target.closest("[data-fill-location]");
        if (!button) {
            return;
        }
        event.preventDefault();
        var form = button.closest("form");
        if (!form) {
            return;
        }
        if (!navigator.geolocation) {
            setStatus(form, "Trình duyệt không hỗ trợ lấy vị trí. Hãy nhập vĩ độ và kinh độ thủ công.");
            return;
        }
        button.disabled = true;
        setStatus(form, "Đang xác định vị trí...");
        navigator.geolocation.getCurrentPosition(function (position) {
            var latitude = form.querySelector("[name=latitude]");
            var longitude = form.querySelector("[name=longitude]");
            if (latitude) {
                latitude.value = Number(position.coords.latitude).toFixed(7);
            }
            if (longitude) {
                longitude.value = Number(position.coords.longitude).toFixed(7);
            }
            setStatus(form, "Đã điền vị trí hiện tại. Hãy lưu cửa hàng để nhân viên chấm công được.");
            button.disabled = false;
        }, function () {
            setStatus(form, "Không thể xác định vị trí. Hãy nhập vĩ độ và kinh độ thủ công.");
            button.disabled = false;
        }, {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 0
        });
    });

    function setStatus(form, text) {
        var status = form.querySelector("[data-location-status]");
        if (status) {
            status.textContent = text;
        }
    }
})();
