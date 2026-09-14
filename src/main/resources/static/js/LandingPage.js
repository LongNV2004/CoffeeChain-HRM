document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('login-form');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const message = document.getElementById('form-message');

  if (!form) return;

  form.addEventListener('submit', function (event) {
    event.preventDefault();

    const username = usernameInput.value.trim();
    const password = passwordInput.value.trim();

    if (!username || !password) {
      showMessage('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.', 'error');
      return;
    }

    // Demo: mô phỏng gọi API đăng nhập.
    showMessage('Đang kiểm tra thông tin đăng nhập…', 'success');

    setTimeout(function () {
      showMessage(
        'Đây là giao diện demo — hãy nối form này với API đăng nhập thực tế của hệ thống.',
        'success'
      );
    }, 700);
  });

  function showMessage(text, type) {
    message.textContent = text;
    message.className = 'form-msg show ' + type;
  }
});