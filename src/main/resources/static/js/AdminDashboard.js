document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('form[action*="logout"]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm('Bạn muốn đăng xuất khỏi hệ thống?')) {
        event.preventDefault();
      }
    });
  });

  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm(form.dataset.confirm)) {
        event.preventDefault();
      }
    });
  });

  document.querySelectorAll('select[data-autosubmit]').forEach(function (select) {
    select.addEventListener('change', function () {
      if (select.value) {
        select.form.submit();
      }
    });
  });
});
