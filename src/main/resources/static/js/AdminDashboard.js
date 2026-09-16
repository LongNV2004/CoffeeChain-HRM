document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('form[action*="logout"]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm('Bạn muốn đăng xuất khỏi hệ thống?')) {
        event.preventDefault();
      }
    });
  });
});
