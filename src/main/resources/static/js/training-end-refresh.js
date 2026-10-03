(function () {
  var nowText = document.body.getAttribute('data-now');
  if (!nowText) {
    return;
  }
  var serverNow = Date.parse(nowText);
  if (isNaN(serverNow)) {
    return;
  }
  var soonest = null;
  document.querySelectorAll('[data-end]').forEach(function (node) {
    var endText = node.getAttribute('data-end');
    if (!endText) {
      return;
    }
    var end = Date.parse(endText);
    if (isNaN(end) || end <= serverNow) {
      return;
    }
    if (soonest == null || end < soonest) {
      soonest = end;
    }
  });
  if (soonest == null) {
    return;
  }
  var delay = soonest - serverNow + 1000;
  if (delay < 1000) {
    delay = 1000;
  }
  if (delay > 2147483647) {
    return;
  }
  setTimeout(function () {
    window.location.reload();
  }, delay);
})();
