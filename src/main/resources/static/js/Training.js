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
});
