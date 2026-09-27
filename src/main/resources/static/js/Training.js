document.addEventListener('DOMContentLoaded', function () {
  var form = document.getElementById('update-skill-form');
  var cancel = document.getElementById('cancel-edit');
  var editingId = document.getElementById('editingSkillId');

  var context = document.body.getAttribute('data-context') || '/';
  if (context.endsWith('/') && context.length > 1) {
    context = context.slice(0, -1);
  }
  if (context === '/') {
    context = '';
  }

  function openEdit(row) {
    if (!form) {
      return;
    }
    var id = row.getAttribute('data-id');
    form.action = context + '/training/skills/' + id;
    form.querySelector('[name="skillName"]').value = row.getAttribute('data-name') || '';
    form.querySelector('[name="description"]').value = row.getAttribute('data-description') || '';
    form.querySelector('[name="requirements"]').value = row.getAttribute('data-requirements') || '';
    form.classList.remove('hidden');
    form.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  document.querySelectorAll('.edit-skill').forEach(function (button) {
    button.addEventListener('click', function () {
      var row = button.closest('tr');
      if (row) {
        openEdit(row);
      }
    });
  });

  if (cancel && form) {
    cancel.addEventListener('click', function () {
      form.classList.add('hidden');
    });
  }

  if (form && editingId && editingId.value) {
    var match = document.querySelector('tr[data-id="' + editingId.value + '"]');
    if (match) {
      openEdit(match);
    } else {
      form.classList.remove('hidden');
    }
  }

  var classForm = document.getElementById('training-class-form');
  var startDate = document.getElementById('class-start-date');
  var endDate = document.getElementById('class-end-date');
  var startTime = document.getElementById('class-start-time');
  var endTime = document.getElementById('class-end-time');
  var clientError = document.getElementById('class-client-error');

  function showClassError(message) {
    if (!clientError) {
      return;
    }
    clientError.hidden = false;
    clientError.textContent = message;
  }

  function clearClassError() {
    if (!clientError) {
      return;
    }
    clientError.hidden = true;
    clientError.textContent = '';
  }

  if (startDate && endDate) {
    startDate.addEventListener('change', function () {
      if (startDate.value) {
        endDate.min = startDate.value;
        if (endDate.value && endDate.value < startDate.value) {
          endDate.value = startDate.value;
        }
      }
    });
  }

  if (classForm) {
    classForm.addEventListener('submit', function (event) {
      clearClassError();
      if (!startDate || !endDate || !startTime || !endTime) {
        return;
      }
      if (endDate.value < startDate.value) {
        event.preventDefault();
        showClassError('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.');
        return;
      }
      if (endTime.value <= startTime.value) {
        event.preventDefault();
        showClassError('Giờ bắt đầu phải nhỏ hơn giờ kết thúc.');
        return;
      }
      var clock = window.CoffeeHrmTime;
      if (clock && clock.isBeforeNow(startDate.value, startTime.value)) {
        event.preventDefault();
        showClassError('Thời gian bắt đầu đã qua. Vui lòng chọn thời điểm từ hiện tại trở đi theo giờ Việt Nam.');
      }
    });
  }
});
