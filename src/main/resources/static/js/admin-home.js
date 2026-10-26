document.addEventListener('DOMContentLoaded', function () {
    var roleHost = document.getElementById('adminRoleChart');
    var trendHost = document.getElementById('adminTrendChart');
    if (roleHost) {
        drawRoleChart(roleHost);
    }
    if (trendHost) {
        drawTrendChart(trendHost);
    }
});

function drawRoleChart(host) {
    var slices = [
        { label: 'Manager', value: numberAttr(host, 'manager'), color: '#8C5E32' },
        { label: 'Staff', value: numberAttr(host, 'staff'), color: '#C4A574' },
        { label: 'Chưa có tài khoản', value: numberAttr(host, 'unassigned'), color: '#7A6A5C' },
        { label: 'Vai trò khác', value: numberAttr(host, 'other'), color: '#5C3C2E' }
    ].filter(function (slice) { return slice.value > 0; });
    var total = slices.reduce(function (sum, slice) { return sum + slice.value; }, 0);
    if (total <= 0) {
        return;
    }

    var svg = svgEl('svg');
    svg.setAttribute('viewBox', '0 0 200 200');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', slices.map(function (slice) {
        return slice.label + ' ' + slice.value;
    }).join(', '));

    var radius = 68;
    var circumference = 2 * Math.PI * radius;
    var offset = 0;
    slices.forEach(function (slice) {
        var length = (slice.value / total) * circumference;
        var gap = slices.length > 1 && length > 4 ? 2 : 0;
        var circle = svgEl('circle');
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
        var title = svgEl('title');
        title.textContent = slice.label + ': ' + slice.value;
        circle.appendChild(title);
        svg.appendChild(circle);
        offset += length;
    });

    var value = svgEl('text');
    value.setAttribute('x', '100');
    value.setAttribute('y', '98');
    value.setAttribute('text-anchor', 'middle');
    value.setAttribute('fill', '#26170F');
    value.setAttribute('font-family', 'Fraunces, serif');
    value.setAttribute('font-size', '32');
    value.setAttribute('font-weight', '600');
    value.textContent = String(total);
    var caption = svgEl('text');
    caption.setAttribute('x', '100');
    caption.setAttribute('y', '118');
    caption.setAttribute('text-anchor', 'middle');
    caption.setAttribute('fill', '#7A6A5C');
    caption.setAttribute('font-family', 'Be Vietnam Pro, Inter, sans-serif');
    caption.setAttribute('font-size', '12');
    caption.textContent = 'nhân sự';
    svg.appendChild(value);
    svg.appendChild(caption);
    host.appendChild(svg);
}

function drawTrendChart(host) {
    var labels = splitAttr(host, 'labels');
    var hired = splitNumbers(host, 'hired');
    var resigned = splitNumbers(host, 'resigned');
    var count = Math.min(labels.length, hired.length, resigned.length);
    if (count === 0) {
        return;
    }
    labels = labels.slice(0, count);
    hired = hired.slice(0, count);
    resigned = resigned.slice(0, count);

    var width = 640;
    var height = 280;
    var pad = { top: 16, right: 36, bottom: 36, left: 42 };
    var innerW = width - pad.left - pad.right;
    var innerH = height - pad.top - pad.bottom;
    var peak = Math.max.apply(null, hired.concat(resigned));
    var yMax = niceMax(peak);
    var svg = svgEl('svg');
    svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-hidden', 'true');

    axisTicks(yMax).forEach(function (tick) {
        var y = yPos(tick, yMax, pad.top, innerH);
        var line = svgEl('line');
        line.setAttribute('x1', String(pad.left));
        line.setAttribute('x2', String(width - pad.right));
        line.setAttribute('y1', String(y));
        line.setAttribute('y2', String(y));
        line.setAttribute('stroke', '#EFE6DA');
        line.setAttribute('stroke-width', '1');
        svg.appendChild(line);
        svg.appendChild(axisText(String(tick), 4, y + 4, 'start'));
    });

    svg.appendChild(seriesLine(hired, '#2F6B45', count, yMax, pad, innerW, innerH));
    svg.appendChild(seriesLine(resigned, '#B15A3E', count, yMax, pad, innerW, innerH));

    labels.forEach(function (label, index) {
        var x = xAt(index, count, pad.left, innerW);
        var anchor = index === 0 ? 'start' : index === count - 1 ? 'end' : 'middle';
        var labelX = index === 0 ? x - 16 : index === count - 1 ? x + 16 : x;
        svg.appendChild(axisText(axisLabel(label), labelX, height - 12, anchor));
        [hired[index], resigned[index]].forEach(function (value, seriesIndex) {
            var dot = svgEl('circle');
            dot.setAttribute('cx', String(x));
            dot.setAttribute('cy', String(yPos(value, yMax, pad.top, innerH)));
            dot.setAttribute('r', '4');
            dot.setAttribute('fill', seriesIndex === 0 ? '#2F6B45' : '#B15A3E');
            svg.appendChild(dot);
        });
    });

    var tip = document.createElement('div');
    tip.className = 'admin-tip';
    tip.hidden = true;
    host.appendChild(svg);
    host.appendChild(tip);

    labels.forEach(function (label, index) {
        var band = svgEl('rect');
        var bandWidth = innerW / count;
        var x = pad.left + bandWidth * index;
        band.setAttribute('x', String(x));
        band.setAttribute('y', String(pad.top));
        band.setAttribute('width', String(bandWidth));
        band.setAttribute('height', String(innerH));
        band.setAttribute('fill', 'transparent');
        band.addEventListener('mousemove', function (event) {
            var rect = host.getBoundingClientRect();
            tip.hidden = false;
            tip.textContent = label + '\nTuyển mới: ' + hired[index] + '\nNghỉ việc: ' + resigned[index];
            var left = event.clientX - rect.left;
            if (left > rect.width - 90) {
                left = rect.width - 90;
            }
            if (left < 48) {
                left = 48;
            }
            tip.style.left = left + 'px';
            tip.style.top = (event.clientY - rect.top) + 'px';
        });
        band.addEventListener('mouseleave', function () {
            tip.hidden = true;
        });
        svg.appendChild(band);
    });
}

function seriesLine(values, color, count, yMax, pad, innerW, innerH) {
    var points = values.map(function (value, index) {
        var x = xAt(index, count, pad.left, innerW);
        var y = yPos(value, yMax, pad.top, innerH);
        return x + ',' + y;
    }).join(' ');
    var line = svgEl('polyline');
    line.setAttribute('points', points);
    line.setAttribute('fill', 'none');
    line.setAttribute('stroke', color);
    line.setAttribute('stroke-width', '2.5');
    line.setAttribute('stroke-linejoin', 'round');
    line.setAttribute('stroke-linecap', 'round');
    return line;
}

function axisLabel(label) {
    var parts = String(label).split('/');
    if (parts.length !== 2 || parts[1].length < 2) {
        return label;
    }
    return parts[0] + '/' + parts[1].slice(-2);
}

function xAt(index, count, left, innerW) {
    if (count <= 1) {
        return left + innerW / 2;
    }
    return left + (innerW / (count - 1)) * index;
}

function axisTicks(yMax) {
    if (yMax <= 6) {
        var ticks = [];
        for (var value = 0; value <= yMax; value += 1) {
            ticks.push(value);
        }
        return ticks;
    }
    return [0, Math.round(yMax / 2), yMax];
}

function yPos(value, yMax, top, innerH) {
    if (yMax <= 0) {
        return top + innerH;
    }
    return top + innerH - (value / yMax) * innerH;
}

function niceMax(peak) {
    if (!peak || peak <= 0) {
        return 1;
    }
    var pow = Math.pow(10, Math.floor(Math.log10(peak)));
    var normalized = peak / pow;
    var nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
    return nice * pow;
}

function axisText(content, x, y, anchor) {
    var text = svgEl('text');
    text.setAttribute('x', String(x));
    text.setAttribute('y', String(y));
    text.setAttribute('text-anchor', anchor);
    text.setAttribute('fill', '#7A6A5C');
    text.setAttribute('font-family', 'Be Vietnam Pro, Inter, sans-serif');
    text.setAttribute('font-size', '12');
    text.textContent = content;
    return text;
}

function svgEl(name) {
    return document.createElementNS('http://www.w3.org/2000/svg', name);
}

function numberAttr(host, name) {
    var value = Number(host.dataset[name]);
    return Number.isFinite(value) ? value : 0;
}

function splitAttr(host, name) {
    var value = host.dataset[name];
    if (!value) {
        return [];
    }
    return value.split(',');
}

function splitNumbers(host, name) {
    return splitAttr(host, name).map(function (part) {
        var value = Number(part);
        return Number.isFinite(value) ? value : 0;
    });
}
