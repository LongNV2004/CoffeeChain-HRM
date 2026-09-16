document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('login-form');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const usernameError = document.getElementById('username-error');
  const passwordError = document.getElementById('password-error');
  const message = document.getElementById('form-message');

  if (!form) return;

  form.addEventListener('submit', function (event) {
    clearClientErrors();

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    let valid = true;
    if (!username) {
      showFieldError(usernameError, 'Vui lòng nhập tên đăng nhập');
      valid = false;
    }
    if (!password) {
      showFieldError(passwordError, 'Vui lòng nhập mật khẩu');
      valid = false;
    }

    if (!valid) {
      event.preventDefault();
      showMessage('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.', 'error');
    }
  });

  function showFieldError(element, text) {
    if (!element) return;
    element.textContent = text;
    element.classList.add('show');
  }

  function clearClientErrors() {
    [usernameError, passwordError].forEach(function (element) {
      if (!element) return;
      if (!element.dataset.server) {
        element.textContent = '';
        element.classList.remove('show');
      }
    });
  }

  function showMessage(text, type) {
    message.textContent = text;
    message.className = 'form-msg show ' + type;
  }
});
