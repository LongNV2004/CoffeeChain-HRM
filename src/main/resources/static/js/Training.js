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
    form.action = context + '/admin/training/skills/' + id;
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
        if (input.classList.contains('store-choice') || input.name === 'skillIds') {
          syncEmployeeEligibility();
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

function syncEmployeeEligibility() {
  var rows = document.querySelectorAll('.enroll-row');
  if (!rows.length) {
    return;
  }
  var selectedStores = Array.prototype.map.call(
    document.querySelectorAll('input[name="storeIds"]:checked'),
    function (input) { return input.value; }
  );
  var selectedSkills = Array.prototype.map.call(
    document.querySelectorAll('input[name="skillIds"]:checked'),
    function (input) { return String(input.value); }
  );
  var skillNames = {};
  document.querySelectorAll('input[name="skillIds"]').forEach(function (input) {
    var text = input.closest('.ms-item');
    var label = text ? text.querySelector('.ms-text') : null;
    skillNames[String(input.value)] = label ? label.textContent.trim() : '';
  });

  var visible = 0;
  rows.forEach(function (row) {
    var inStore = selectedStores.indexOf(row.getAttribute('data-store-id')) >= 0;
    row.hidden = !inStore;
    var input = row.querySelector('input[name="employeeIds"]');
    if (!inStore) {
      if (input) {
        input.checked = false;
        input.disabled = true;
      }
      return;
    }
    visible += 1;
    renderEnrollmentRow(row, selectedSkills, skillNames);
  });

  var empty = document.getElementById('enroll-empty');
  var list = document.getElementById('enroll-list');
  if (empty) {
    if (!selectedStores.length) {
      empty.hidden = false;
      empty.textContent = 'Chọn cửa hàng trước.';
    } else if (!visible) {
      empty.hidden = false;
      empty.textContent = 'Không có nhân viên thuộc các cửa hàng đã chọn.';
    } else {
      empty.hidden = true;
    }
  }
  if (list) {
    list.hidden = !selectedStores.length || !visible;
  }
}

function renderEnrollmentRow(row, selectedSkills, skillNames) {
  var input = row.querySelector('input[name="employeeIds"]');
  var cert = row.querySelector('.enroll-cert');
  var latest = row.querySelector('.enroll-latest');
  var badge = row.querySelector('.cert-badge');
  var snapshots = skillSnapshotMap(row.getAttribute('data-skills'));
  var studying = skillIdList(row, 'data-studying-skills');

  if (!selectedSkills.length) {
    if (cert) {
      cert.textContent = 'Chọn kỹ năng để xem chứng chỉ';
    }
    if (latest) {
      latest.textContent = '—';
    }
    setEligibilityBadge(badge, 'Chọn kỹ năng', 'badge-wait');
    row.classList.remove('is-blocked', 'is-retake');
    if (input) {
      input.disabled = true;
      input.checked = false;
    }
    return;
  }

  var certHtml = '';
  var latestHtml = '';
  var allCertified = true;
  var anyStudying = false;
  selectedSkills.forEach(function (skillId) {
    var snap = snapshots[skillId] || {};
    var name = skillNames[skillId] || snap.name || 'Kỹ năng';
    var inTraining = studying.indexOf(skillId) >= 0;
    var certified = !!snap.certified;
    if (!certified) {
      allCertified = false;
    }
    if (inTraining) {
      anyStudying = true;
    }
    certHtml += '<span class="skill-line"><span class="skill-name">' + escapeHtml(name) + '</span> '
      + (certified
        ? '<span class="cert-on">🟢 Đã có chứng chỉ</span>'
        : '<span class="cert-off">🔴 Chưa có chứng chỉ</span>')
      + (certified && snap.certifiedDate
        ? '<span class="muted">Ngày đạt: ' + escapeHtml(snap.certifiedDate) + '</span>'
        : '')
      + '</span>';
    latestHtml += '<span class="skill-line"><span class="skill-name">' + escapeHtml(name) + ':</span> '
      + escapeHtml(latestAttemptText(snap, inTraining)) + '</span>';
  });
  if (cert) {
    cert.innerHTML = certHtml;
  }
  if (latest) {
    latest.innerHTML = latestHtml;
  }

  var badgeText = 'Có thể đăng ký';
  var badgeClass = 'badge-ok';
  if (anyStudying) {
    badgeText = 'Không thể đăng ký';
    badgeClass = 'badge-off';
  } else if (allCertified) {
    badgeText = 'Có thể học lại';
    badgeClass = 'badge-cert';
  }
  setEligibilityBadge(badge, badgeText, badgeClass);
  row.classList.toggle('is-blocked', anyStudying);
  row.classList.toggle('is-retake', !anyStudying && allCertified);
  if (input) {
    input.disabled = anyStudying;
    if (anyStudying) {
      input.checked = false;
    }
  }
}

function latestAttemptText(snap, inTraining) {
  if (inTraining) {
    var trainingDate = snap && snap.latest === 'IN_TRAINING' ? snap.latestDate : '';
    return 'ĐANG ĐÀO TẠO' + (trainingDate ? ' · ' + trainingDate : '');
  }
  if (!snap || !snap.latest) {
    return '—';
  }
  var label = snap.latest === 'PASS'
    ? 'PASS'
    : (snap.latest === 'NOT_PASS' ? 'NOT PASS' : (snap.latest === 'IN_TRAINING' ? 'ĐANG ĐÀO TẠO' : snap.latest));
  return label + (snap.latestDate ? ' · ' + snap.latestDate : '');
}

function skillSnapshotMap(raw) {
  if (!raw) {
    return {};
  }
  try {
    var parsed = JSON.parse(raw);
    var map = {};
    (parsed || []).forEach(function (item) {
      if (item && item.id != null) {
        map[String(item.id)] = item;
      }
    });
    return map;
  } catch (error) {
    return {};
  }
}

function setEligibilityBadge(badge, text, badgeClass) {
  if (!badge) {
    return;
  }
  badge.hidden = false;
  badge.textContent = text;
  badge.classList.remove('badge-cert', 'badge-studying', 'badge-nocert', 'badge-ok', 'badge-off', 'badge-wait');
  badge.classList.add(badgeClass);
}

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
