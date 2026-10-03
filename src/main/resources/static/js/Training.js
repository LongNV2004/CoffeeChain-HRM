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
      if (!classForm.querySelector('input[name="skillIds"]:checked')) {
        event.preventDefault();
        showClassError('Vui lòng chọn ít nhất một kỹ năng đào tạo.');
        return;
      }
      var clock = window.CoffeeHrmTime;
      if (clock && clock.isBeforeNow(startDate.value, startTime.value)) {
        event.preventDefault();
        showClassError('Thời gian bắt đầu đã qua. Vui lòng chọn thời điểm từ hiện tại trở đi theo giờ Việt Nam.');
      }
    });
  }

  initMultiSelects();
});

function initMultiSelects() {
  var boxes = document.querySelectorAll('[data-ms]');
  if (!boxes.length) {
    return;
  }

  function closeAll() {
    boxes.forEach(function (box) {
      box.classList.remove('is-open');
      var menu = box.querySelector('.ms-menu');
      var trigger = box.querySelector('.ms-trigger');
      if (menu) {
        menu.hidden = true;
      }
      if (trigger) {
        trigger.setAttribute('aria-expanded', 'false');
      }
    });
  }

  function checkedValues(name) {
    return Array.prototype.map.call(
      document.querySelectorAll('input[name="' + name + '"]:checked'),
      function (input) { return input.value; }
    );
  }

  function refreshBox(box) {
    var value = box.querySelector('.ms-value');
    var placeholder = box.getAttribute('data-placeholder') || 'Chọn';
    var filterBy = box.getAttribute('data-filter-by');
    var hint = box.querySelector('.ms-hint');
    var selectedStores = filterBy ? checkedValues(filterBy) : null;
    var visible = 0;

    box.querySelectorAll('.ms-item').forEach(function (item) {
      var show = true;
      if (filterBy) {
        show = selectedStores.indexOf(item.getAttribute('data-store-id')) >= 0;
        item.hidden = !show;
        if (!show) {
          var input = item.querySelector('input');
          if (input) {
            input.checked = false;
          }
        }
      }
      if (show) {
        visible += 1;
      }
    });

    if (hint) {
      if (filterBy && !selectedStores.length) {
        hint.hidden = false;
        hint.textContent = box.getAttribute('data-need-store') || 'Chọn cửa hàng trước.';
      } else if (filterBy && !visible) {
        hint.hidden = false;
        hint.textContent = box.getAttribute('data-no-match') || 'Không có nhân viên thuộc cửa hàng đã chọn.';
      } else {
        hint.hidden = true;
      }
    }

    if (!value) {
      return;
    }
    var labels = Array.prototype.map.call(
      box.querySelectorAll('.ms-item:not([hidden]) input:checked'),
      function (input) {
        var text = input.closest('.ms-item').querySelector('.ms-text');
        if (!text) {
          return '';
        }
        var name = text.querySelector('strong');
        return (name ? name.textContent : text.textContent).trim();
      }
    ).filter(Boolean);

    if (!labels.length) {
      value.textContent = filterBy && !selectedStores.length
        ? (box.getAttribute('data-need-store') || placeholder)
        : placeholder;
      value.classList.add('is-placeholder');
      return;
    }
    value.classList.remove('is-placeholder');
    value.textContent = labels.length <= 2
      ? labels.join(', ')
      : labels.slice(0, 2).join(', ') + ' +' + (labels.length - 2);
  }

  boxes.forEach(function (box) {
    var trigger = box.querySelector('.ms-trigger');
    var menu = box.querySelector('.ms-menu');
    if (!trigger || !menu) {
      return;
    }
    trigger.addEventListener('click', function (event) {
      event.stopPropagation();
      var willOpen = menu.hidden;
      closeAll();
      if (willOpen) {
        menu.hidden = false;
        box.classList.add('is-open');
        trigger.setAttribute('aria-expanded', 'true');
      }
    });
    menu.addEventListener('click', function (event) {
      event.stopPropagation();
    });
    box.querySelectorAll('input[type="checkbox"]').forEach(function (input) {
      input.addEventListener('change', function () {
        refreshBox(box);
        if (input.classList.contains('store-choice')) {
          boxes.forEach(refreshBox);
        }
        if (input.name === 'skillIds') {
          syncEmployeeEligibility(refreshBox);
        }
      });
    });
    refreshBox(box);
  });

  syncEmployeeEligibility(refreshBox);

  document.addEventListener('click', closeAll);
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      closeAll();
    }
  });
}

function skillIdList(item, attribute) {
  return (item.getAttribute(attribute) || '')
    .split(',')
    .map(function (value) { return value.trim(); })
    .filter(Boolean);
}

function syncEmployeeEligibility(refreshBox) {
  var skillInputs = document.querySelectorAll('input[name="skillIds"]');
  if (!skillInputs.length) {
    return;
  }
  var selected = Array.prototype.map.call(skillInputs, function (input) {
    return input.checked ? String(input.value) : '';
  }).filter(Boolean);

  document.querySelectorAll('[data-certified-skills]').forEach(function (item) {
    var badge = item.querySelector('.cert-badge');
    var input = item.querySelector('input[name="employeeIds"]');
    var held = skillIdList(item, 'data-certified-skills');
    var studying = skillIdList(item, 'data-studying-skills');
    var covered = selected.length > 0 && selected.every(function (skillId) {
      return held.indexOf(skillId) >= 0;
    });
    var inProgress = !covered && selected.length > 0 && selected.some(function (skillId) {
      return studying.indexOf(skillId) >= 0;
    });
    if (badge) {
      badge.hidden = selected.length === 0;
      badge.textContent = covered ? 'Có chứng chỉ' : (inProgress ? 'Đang học' : 'Không chứng chỉ');
      badge.classList.toggle('badge-cert', covered);
      badge.classList.toggle('badge-studying', inProgress);
      badge.classList.toggle('badge-nocert', selected.length > 0 && !covered && !inProgress);
    }
    item.classList.toggle('is-certified', covered);
    item.classList.toggle('is-studying', inProgress);
    if (input) {
      input.disabled = covered || inProgress;
      if (covered || inProgress) {
        input.checked = false;
      }
    }
  });

  if (typeof refreshBox === 'function') {
    document.querySelectorAll('[data-ms]').forEach(function (box) {
      if (box.querySelector('input[name="employeeIds"]')) {
        refreshBox(box);
      }
    });
  }
}
