/* Hộp thoại xác nhận dùng chung. Không dùng alert/confirm/prompt của trình duyệt. */
(function () {
    'use strict';

    if (window.CoffeeDialog) return;

    const dialog = document.createElement('div');
    dialog.className = 'coffee-confirm';
    dialog.id = 'coffeeConfirmDialog';
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-labelledby', 'coffeeConfirmTitle');
    dialog.setAttribute('aria-describedby', 'coffeeConfirmText');
    dialog.hidden = true;

    const backdrop = document.createElement('button');
    backdrop.className = 'coffee-confirm-backdrop';
    backdrop.type = 'button';
    backdrop.tabIndex = -1;
    backdrop.setAttribute('aria-label', 'Đóng');

    const card = document.createElement('div');
    card.className = 'coffee-confirm-card';

    const title = document.createElement('h2');
    title.id = 'coffeeConfirmTitle';

    const message = document.createElement('p');
    message.id = 'coffeeConfirmText';

    const reasonWrap = document.createElement('label');
    reasonWrap.className = 'coffee-confirm-reason';
    reasonWrap.hidden = true;
    const reasonLabel = document.createElement('span');
    const reasonInput = document.createElement('textarea');
    reasonInput.rows = 3;
    reasonInput.maxLength = 500;
    reasonWrap.append(reasonLabel, reasonInput);

    const actions = document.createElement('div');
    actions.className = 'coffee-confirm-actions';

    const cancelButton = document.createElement('button');
    cancelButton.className = 'coffee-confirm-cancel';
    cancelButton.type = 'button';

    const confirmButton = document.createElement('button');
    confirmButton.className = 'coffee-confirm-ok';
    confirmButton.type = 'button';

    actions.append(cancelButton, confirmButton);
    card.append(title, message, reasonWrap, actions);
    dialog.append(backdrop, card);

    let resolver = null;
    let lastFocused = null;

    function focusableItems() {
        return [reasonInput, cancelButton, confirmButton].filter(function (item) {
            return !item.disabled && !item.hidden && item.getClientRects().length > 0;
        });
    }

    function finish(accepted) {
        const askingReason = !reasonWrap.hidden;
        const reason = reasonInput.value.trim();
        dialog.hidden = true;
        reasonWrap.hidden = true;
        reasonInput.value = '';
        document.body.classList.remove('coffee-dialog-open');
        const resolve = resolver;
        resolver = null;
        const previous = lastFocused;
        lastFocused = null;
        if (previous && typeof previous.focus === 'function') previous.focus();
        if (!resolve) return;
        if (accepted && askingReason) resolve({ accepted: true, reason: reason });
        else resolve(accepted);
    }

    function confirm(options) {
        const settings = typeof options === 'string' ? { message: options } : (options || {});
        if (resolver) finish(false);
        return new Promise(function (resolve) {
            resolver = resolve;
            title.textContent = settings.title || 'Xác nhận';
            message.textContent = settings.message || '';
            cancelButton.textContent = settings.cancelText || 'Hủy';
            confirmButton.textContent = settings.confirmText || 'Xác nhận';
            confirmButton.classList.toggle('is-danger', settings.tone === 'danger');
            reasonWrap.hidden = !settings.reason;
            reasonLabel.textContent = settings.reasonLabel || 'Lý do (không bắt buộc)';
            reasonInput.placeholder = settings.reasonPlaceholder || 'Nhập lý do nếu muốn';
            reasonInput.maxLength = settings.reasonMaxLength || 500;
            reasonInput.value = '';
            lastFocused = document.activeElement;
            dialog.hidden = false;
            document.body.classList.add('coffee-dialog-open');
            if (settings.reason) reasonInput.focus();
            else confirmButton.focus();
        });
    }

    function attribute(source, name) {
        return source && source.getAttribute(name);
    }

    function confirmLabel(submitter, form) {
        const explicit = attribute(submitter, 'data-coffee-ok') || attribute(form, 'data-coffee-ok');
        if (explicit) return explicit;
        const text = submitter ? submitter.textContent.replace(/\s+/g, ' ').trim() : '';
        if (text && text !== '×' && text.length <= 28) return text;
        return 'Xác nhận';
    }

    document.addEventListener('submit', function (event) {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) return;
        if (form.dataset.coffeeConfirmed === 'true') {
            delete form.dataset.coffeeConfirmed;
            return;
        }
        const submitter = event.submitter || null;
        const text = attribute(submitter, 'data-coffee-confirm')
            || attribute(form, 'data-coffee-confirm')
            || attribute(submitter, 'data-confirm')
            || attribute(form, 'data-confirm');
        if (!text) return;
        event.preventDefault();
        event.stopPropagation();
        const reasonName = attribute(submitter, 'data-coffee-reason') || attribute(form, 'data-coffee-reason');
        confirm({
            title: attribute(submitter, 'data-coffee-title') || attribute(form, 'data-coffee-title') || 'Xác nhận',
            message: text,
            confirmText: confirmLabel(submitter, form),
            cancelText: attribute(submitter, 'data-coffee-cancel') || attribute(form, 'data-coffee-cancel') || 'Hủy',
            tone: attribute(submitter, 'data-coffee-tone') || attribute(form, 'data-coffee-tone') || undefined,
            reason: !!reasonName,
            reasonLabel: attribute(submitter, 'data-coffee-reason-label') || attribute(form, 'data-coffee-reason-label') || 'Lý do (không bắt buộc)',
            reasonPlaceholder: attribute(submitter, 'data-coffee-reason-placeholder') || attribute(form, 'data-coffee-reason-placeholder') || 'Nhập lý do nếu muốn'
        }).then(function (accepted) {
            if (!accepted) return;
            if (reasonName && typeof accepted === 'object') {
                let input = form.querySelector('input[data-coffee-reason-value]');
                if (!input) {
                    input = document.createElement('input');
                    input.type = 'hidden';
                    input.setAttribute('data-coffee-reason-value', '');
                    form.appendChild(input);
                }
                input.name = reasonName;
                input.value = accepted.reason || '';
            }
            form.dataset.coffeeConfirmed = 'true';
            if (typeof form.requestSubmit === 'function') form.requestSubmit(submitter || undefined);
            else form.submit();
        });
    }, true);

    backdrop.addEventListener('click', function () { finish(false); });
    cancelButton.addEventListener('click', function () { finish(false); });
    confirmButton.addEventListener('click', function () { finish(true); });
    dialog.addEventListener('keydown', function (event) {
        if (dialog.hidden) return;
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            finish(false);
            return;
        }
        if (event.key !== 'Tab') return;
        const items = focusableItems();
        if (!items.length) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });

    document.body.appendChild(dialog);
    window.CoffeeDialog = { confirm: confirm };
})();
