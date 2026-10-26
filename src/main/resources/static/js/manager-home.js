document.addEventListener('DOMContentLoaded', function () {
    var host = document.getElementById('managerAttendanceChart');
    if (host) {
        drawAttendanceDonut(host);
    }
});

function drawAttendanceDonut(host) {
    var completed = numberAttr(host, 'completed');
    var remaining = numberAttr(host, 'remaining');
    var rate = host.getAttribute('data-rate');
    var slices = [
        { label: 'Đã hoàn thành', value: completed, color: '#2F6B45' },
        { label: 'Chưa hoàn thành', value: remaining, color: '#C4A574' }
    ].filter(function (slice) { return slice.value > 0; });
    var total = slices.reduce(function (sum, slice) { return sum + slice.value; }, 0);
    if (total <= 0) {
        return;
    }

    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 200 200');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'Đã hoàn thành ' + completed + ', chưa hoàn thành ' + remaining);

    var radius = 68;
    var circumference = 2 * Math.PI * radius;
    var offset = 0;
    slices.forEach(function (slice) {
        var length = (slice.value / total) * circumference;
        var gap = slices.length > 1 && length > 4 ? 2 : 0;
        var circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', '100');
        circle.setAttribute('cy', '100');
        circle.setAttribute('r', String(radius));
        circle.setAttribute('fill', 'none');
        circle.setAttribute('stroke', slice.color);
        circle.setAttribute('stroke-width', '22');
        circle.setAttribute('stroke-linecap', 'butt');
        if (length < circumference - 0.5) {
            circle.setAttribute('stroke-dasharray', Math.max(length - gap, 0) + ' ' + circumference);
            circle.setAttribute('stroke-dashoffset', String(-offset));
        }
        circle.setAttribute('transform', 'rotate(-90 100 100)');
        var title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
        title.textContent = slice.label + ': ' + slice.value;
        circle.appendChild(title);
        svg.appendChild(circle);
        offset += length;
    });

    var value = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    value.setAttribute('x', '100');
    value.setAttribute('y', '98');
    value.setAttribute('text-anchor', 'middle');
    value.setAttribute('fill', '#26170F');
    value.setAttribute('font-family', 'Fraunces, serif');
    value.setAttribute('font-size', '28');
    value.setAttribute('font-weight', '600');
    value.textContent = rate == null || rate === '' ? completed + '/' + total : rate + '%';
    var caption = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    caption.setAttribute('x', '100');
    caption.setAttribute('y', '118');
    caption.setAttribute('text-anchor', 'middle');
    caption.setAttribute('fill', '#7A6A5C');
    caption.setAttribute('font-family', 'Be Vietnam Pro, Inter, sans-serif');
    caption.setAttribute('font-size', '12');
    caption.textContent = 'hoàn thành';
    svg.appendChild(value);
    svg.appendChild(caption);
    host.appendChild(svg);
}

function numberAttr(host, name) {
    var value = Number(host.getAttribute('data-' + name));
    return Number.isFinite(value) && value > 0 ? value : 0;
}
