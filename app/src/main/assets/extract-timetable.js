(function () {
  'use strict';
  function clean(value) { return (value || '').replace(/[\uE000-\uF8FF]/g, '').replace(/\s+/g, ' ').trim(); }
  function extract(doc) {
    var table = doc.querySelector('#kbgrid_table_0');
    if (!table) return null;
    var nodes = Array.from(table.querySelectorAll('.timetable_con'));
    var rows = nodes.map(function (node) {
      var fields = {};
      Array.from(node.querySelectorAll('p')).forEach(function (p) {
        var label = p.querySelector('[title], [data-original-title]');
        if (label) fields[clean(label.getAttribute('title') || label.getAttribute('data-original-title'))] = clean(p.textContent);
      });
      var cell = node.closest('td');
      var dayMatch = (cell && cell.id || '').match(/^([1-7])-\d+$/);
      return {
        name: clean((node.querySelector('.title') || {}).textContent),
        day: dayMatch ? Number(dayMatch[1]) : 0,
        timing: fields['节/周'] || '', location: fields['上课地点'] || '',
        teacher: fields['教师'] || '', className: fields['教学班名称'] || '', assessment: fields['考核方式'] || ''
      };
    });
    // The first h6 is the term. Do not extract the student's name, ID or login fields.
    var term = clean((table.querySelector('.timetable_title h6') || {}).textContent);
    if (!term) {
      var year = doc.querySelector('#xnm'), semester = doc.querySelector('#xqm');
      term = clean(year && year.selectedOptions[0] && year.selectedOptions[0].textContent) + '学年 第' + clean(semester && semester.selectedOptions[0] && semester.selectedOptions[0].textContent) + '学期';
    }
    var weekFilter = doc.querySelector('select#zcd, select#zc');
    var filtered = weekFilter && weekFilter.value && !/^(0|all)$/i.test(weekFilter.value);
    var yearSelect=doc.querySelector('#xnm'), semesterSelect=doc.querySelector('#xqm');
    var selectedYear=clean(yearSelect && yearSelect.selectedOptions[0] && yearSelect.selectedOptions[0].textContent);
    var selectedSemester=clean(semesterSelect && semesterSelect.selectedOptions[0] && semesterSelect.selectedOptions[0].textContent);
    var ready=(!selectedYear || term.indexOf(selectedYear)>=0) && (!selectedSemester || new RegExp('第\\s*'+selectedSemester+'\\s*学期').test(term));
    return { detected: true, ready:ready, scope: filtered ? 'week' : 'full', term: term, rows: rows };
  }
  var own = extract(document);
  if (own) return JSON.stringify(own);
  var frames = Array.from(document.querySelectorAll('iframe'));
  for (var i = 0; i < frames.length; i++) {
    try { var result = extract(frames[i].contentDocument); if (result) return JSON.stringify(result); } catch (_) { /* Cross-origin frames are never bypassed. */ }
  }
  return JSON.stringify({ detected: false, rows: [] });
})()
