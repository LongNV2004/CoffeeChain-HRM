(function () {
    'use strict';

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function parseDisplay(value) {
        const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec((value || '').trim());
        if (!match) return null;
        const day = Number(match[1]);
        const month = Number(match[2]);
        const year = Number(match[3]);
        const check = new Date(year, month - 1, day);
        if (check.getFullYear() !== year || check.getMonth() !== month - 1 || check.getDate() !== day) {
            return null;
        }
        return year + '-' + pad(month) + '-' + pad(day);
    }

    function formatIso(iso) {
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
        return match ? match[3] + '/' + match[2] + '/' + match[1] : '';
    }

    function preview(form, iso) {
        const today = form.dataset.vietnamToday;
        const known = Boolean(iso && today);
        const future = known && iso > today;
        const label = future ? 'Chuẩn bị khai trương' : 'Hoạt động';
        const note = !known
            ? 'Trạng thái thực tế do hệ thống xác định khi lưu, theo ngày tại Việt Nam.'
            : (future
                ? 'Ngày khai trương còn ở tương lai. Đến ngày đó, cửa hàng tự chuyển sang Hoạt động.'
                : 'Ngày khai trương là hôm nay hoặc đã qua. Cửa hàng sẽ được lưu ở trạng thái Hoạt động.');

        const box = form.querySelector('[data-status-preview]');
        if (box) {
            box.hidden = !known;
            if (known) {
                box.dataset.tone = future ? 'pre-opening' : 'active';
                const labelEl = box.querySelector('[data-status-preview-label]');
                if (labelEl) {
                    labelEl.textContent = label;
                    labelEl.classList.toggle('store-badge--preopening', future);
                    labelEl.classList.toggle('store-badge--active', !future);
                }
                const noteEl = box.querySelector('[data-status-preview-note]');
                if (noteEl) noteEl.textContent = note;
            }
        }

        const badge = form.querySelector('[data-preview-badge]');
        if (badge) {
            badge.hidden = !known;
            badge.textContent = label;
            badge.classList.toggle('store-badge--preopening', known && future);
            badge.classList.toggle('store-badge--active', known && !future);
        }
        const empty = form.querySelector('[data-preview-status-empty]');
        if (empty) empty.hidden = known;
        const sideNote = form.querySelector('[data-preview-status-note]');
        if (sideNote) sideNote.textContent = note;
    }

    function fieldValue(form, name) {
        const field = form.querySelector('[name="' + name + '"]');
        return field ? field.value.trim() : '';
    }

    function setPreview(form, selector, value, emptyLabel) {
        const el = form.querySelector(selector);
        if (!el) return;
        const empty = !value;
        el.textContent = empty ? emptyLabel : value;
        el.classList.toggle('is-placeholder', empty);
    }

    function monogram(name) {
        const parts = (name || '').trim().split(/\s+/).filter(Boolean);
        if (!parts.length) return 'CH';
        const first = Array.from(parts[0])[0] || '';
        const second = parts.length > 1
            ? (Array.from(parts[parts.length - 1])[0] || '')
            : (Array.from(parts[0])[1] || '');
        return (first + second).toLocaleUpperCase('vi-VN') || 'CH';
    }

    function refreshPreview(form) {
        if (!form || !form.classList.contains('store-create-form')) return;
        const name = fieldValue(form, 'storeName');
        const address = fieldValue(form, 'address');
        const leave = fieldValue(form, 'totalLeaveDays');
        const opening = (form.querySelector('[data-opening-date]') || {}).value;
        const openingText = (opening || '').trim();
        const latitude = fieldValue(form, 'latitude');
        const longitude = fieldValue(form, 'longitude');
        setPreview(form, '[data-preview-name]', name, 'Chưa nhập tên');
        setPreview(form, '[data-preview-address]', address, 'Chưa nhập địa chỉ');
        setPreview(form, '[data-preview-opening]', openingText, 'Chưa chọn');
        setPreview(form, '[data-preview-leave]', leave === '' ? '' : leave + ' ngày', 'Chưa nhập');
        if (latitude && longitude) {
            setPreview(form, '[data-preview-coords]', latitude + ', ' + longitude, 'Chưa có tọa độ');
        } else {
            setPreview(form, '[data-preview-coords]', '', (latitude || longitude) ? 'Chưa đủ tọa độ' : 'Chưa có tọa độ');
        }
        const mark = form.querySelector('[data-preview-monogram]');
        if (mark) mark.textContent = monogram(name);
    }

    function clientMessage(field) {
        if (field.validity.valueMissing) {
            if (field.name === 'fullName') return 'Vui lòng nhập họ và tên';
            if (field.name === 'email') return 'Vui lòng nhập email';
            if (field.name === 'phone') return 'Vui lòng nhập số điện thoại';
            if (field.name === 'storeName') return 'Vui lòng nhập tên cửa hàng';
            if (field.name === 'address') return 'Vui lòng nhập địa chỉ';
            if (field.name === 'openingDate') return 'Vui lòng chọn ngày khai trương';
            if (field.name === 'totalLeaveDays') return 'Vui lòng nhập số ngày phép';
            if (field.name === 'latitude') return 'Vui lòng nhập vĩ độ';
            if (field.name === 'longitude') return 'Vui lòng nhập kinh độ';
        }
        if (field.validity.typeMismatch && field.name === 'email') return 'Email không đúng định dạng';
        if (field.validity.patternMismatch && field.name === 'phone') return 'Số điện thoại gồm 9 đến 15 chữ số';
        if (field.validity.patternMismatch || field.validity.customError) {
            if (field.name === 'openingDate') return 'Ngày khai trương phải theo định dạng dd/MM/yyyy';
            if (field.name === 'storeName') return 'Tên cửa hàng không được để trống';
            if (field.name === 'address') return 'Địa chỉ không được để trống';
        }
        if (field.validity.rangeUnderflow && field.name === 'totalLeaveDays') {
            return 'Số ngày phép không được âm';
        }
        if (field.validity.tooLong) return 'Giá trị vượt quá độ dài cho phép';
        return field.validationMessage || 'Thông tin chưa hợp lệ';
    }

    function clearClientError(field) {
        const label = field.closest('label');
        const slot = label && label.querySelector('[data-client-error]');
        if (slot) slot.remove();
        field.removeAttribute('aria-invalid');
        field.classList.remove('is-invalid');
    }

    function showClientError(field) {
        const label = field.closest('label');
        if (!label) return;
        clearClientError(field);
        const slot = document.createElement('span');
        slot.className = 'field-error';
        slot.dataset.clientError = 'true';
        slot.textContent = clientMessage(field);
        const anchor = field.closest('.store-date-field') || field;
        anchor.insertAdjacentElement('afterend', slot);
        field.setAttribute('aria-invalid', 'true');
        field.classList.add('is-invalid');
    }

    function syncText(text) {
        const form = text.closest('form');
        if (!form) return;
        const picker = form.querySelector('[data-opening-picker]');
        const iso = parseDisplay(text.value);
        if (text.value.trim() && !iso) {
            text.setCustomValidity('Ngày khai trương phải theo định dạng dd/MM/yyyy');
        } else {
            text.setCustomValidity('');
        }
        if (picker && iso) picker.value = iso;
        preview(form, iso);
        refreshPreview(form);
    }

    function refresh(root) {
        const scope = root || document;
        scope.querySelectorAll('[data-opening-date]').forEach(syncText);
        scope.querySelectorAll('.store-create-form').forEach(refreshPreview);
        scope.querySelectorAll('.store-manager-form').forEach(refreshManagerPreview);
    }

    function refreshManagerPreview(form) {
        setPreview(form, '[data-preview-manager]', fieldValue(form, 'fullName'), 'Chưa nhập họ tên');
        setPreview(form, '[data-preview-email]', fieldValue(form, 'email'), 'Chưa nhập email');
        setPreview(form, '[data-preview-phone]', fieldValue(form, 'phone'), 'Chưa nhập số điện thoại');
    }

    window.refreshStoreOpeningFields = refresh;

    document.addEventListener('input', function (event) {
        const field = event.target;
        if (!(field instanceof HTMLInputElement) || !field.form) return;
        if (field.matches('[data-opening-date]')) {
            syncText(field);
            if (field.validity.valid) clearClientError(field);
            return;
        }
        if (field.form.classList.contains('store-manager-form')) {
            if (field.validity.valid) clearClientError(field);
            refreshManagerPreview(field.form);
            return;
        }
        if (!field.form.classList.contains('store-create-form')) return;
        if (field.validity.valid) clearClientError(field);
        refreshPreview(field.form);
    });

    document.addEventListener('invalid', function (event) {
        const field = event.target;
        if (!(field instanceof HTMLInputElement)) return;
        const form = field.form;
        if (!form || (!form.classList.contains('store-create-form') && !form.classList.contains('store-manager-form'))) return;
        showClientError(field);
        if (form.dataset.invalidSweep === 'true') return;
        form.dataset.invalidSweep = 'true';
        field.scrollIntoView({ block: 'center', behavior: 'smooth' });
        window.setTimeout(function () { delete form.dataset.invalidSweep; }, 0);
    }, true);

    document.addEventListener('change', function (event) {
        if (!event.target.matches('[data-opening-picker]')) return;
        const form = event.target.closest('form');
        const text = form && form.querySelector('[data-opening-date]');
        if (!text || !event.target.value) return;
        text.value = formatIso(event.target.value);
        syncText(text);
    });

    document.addEventListener('click', function (event) {
        const button = event.target.closest('[data-opening-trigger]');
        if (!button) return;
        event.preventDefault();
        const form = button.closest('form');
        const picker = form && form.querySelector('[data-opening-picker]');
        if (!picker) return;
        if (typeof picker.showPicker === 'function') {
            picker.showPicker();
        } else {
            picker.focus();
        }
    });

    document.addEventListener('submit', function (event) {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) return;
        if (!form.classList.contains('store-create-form')
                && !form.classList.contains('store-edit-form')
                && !form.classList.contains('store-manager-form')) return;
        const text = form.querySelector('[data-opening-date]');
        if (text) syncText(text);
        if (!form.checkValidity()) {
            event.preventDefault();
            return;
        }
        if (form.dataset.submitting === 'true') {
            event.preventDefault();
            return;
        }
        form.dataset.submitting = 'true';
        const button = form.querySelector('[type="submit"]');
        if (!button) return;
        button.setAttribute('aria-busy', 'true');
        button.classList.add('is-busy');
        if (button.dataset.submitLabel) button.textContent = button.dataset.submitLabel;
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { refresh(document); }, { once: true });
    } else {
        refresh(document);
    }
})();
