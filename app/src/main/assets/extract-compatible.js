(function () {
  'use strict';
  function clean(s) { return (s || '').replace(/[\uE000-\uF8FF]/g, '').replace(/\u00a0/g, ' ').trim(); }
  function day(s) {
    var m = clean(s).match(/^(?:星期|周|礼拜)\s*([一二三四五六日天1-7])$/);
    if (m) return '一二三四五六日'.indexOf(m[1].replace('天', '日')) + 1 || Number(m[1]);
    var n = ['monday','tuesday','wednesday','thursday','friday','saturday','sunday'].indexOf(clean(s).toLowerCase());
    return n < 0 ? 0 : n + 1;
  }
  function period(s) {
    var m=clean(s).replace(/\s+/g,'').match(/^(?:第)?(\d{1,2})(?:[-–~～至](\d{1,2}))?(?:节)?$/);
    return m ? [Number(m[1]),Number(m[2] || m[1])] : null;
  }
  function extract(doc) {
    if (Array.from(doc.querySelectorAll('input[type=password]')).some(function(e){return e.getBoundingClientRect().height>0;})) return null;
    var cells=[];
    Array.from(doc.querySelectorAll('table')).forEach(function(table) {
      var trs=Array.from(table.rows).filter(function(tr){return tr.closest('table')===table;});
      if(trs.length<2 || trs.length>300) return;
      var matrix=[];
      trs.forEach(function(tr,r) {
        matrix[r]=matrix[r] || [];
        var c=0;
        Array.from(tr.cells).forEach(function(cell) {
          while(matrix[r][c]) c++;
          for(var y=0;y<Math.min(cell.rowSpan || 1,60);y++) {
            matrix[r+y]=matrix[r+y] || [];
            for(var x=0;x<Math.min(cell.colSpan || 1,30);x++) matrix[r+y][c+x]=cell;
          }
          c+=cell.colSpan || 1;
        });
      });
      var header=-1;
      for(var r=0;r<Math.min(6,trs.length);r++) {
        if(new Set((matrix[r] || []).map(function(c){return day(c.innerText);} ).filter(Boolean)).size>=3) {header=r;break;}
      }
      if(header>=0) {
        var columns=(matrix[header] || []).map(function(cell,i){return {i:i,day:day(cell.innerText)};}).filter(function(v){return v.day;});
        var seen=new Set();
        for(var r=header+1;r<trs.length;r++) {
          columns.forEach(function(column) {
            var cell=(matrix[r] || [])[column.i];
            if(!cell || seen.has(cell)) return;
            seen.add(cell);
            var raw=clean(cell.innerText);
            if(!raw || /^(无课|空|午休|休息|—|-)$/.test(raw)) return;
            var first=null,last=null;
            (matrix[r] || []).slice(0,columns[0].i).some(function(v){first=period(v.innerText);return !!first;});
            var endRow=Math.min(r+(cell.rowSpan || 1)-1,trs.length-1);
            (matrix[endRow] || []).slice(0,columns[0].i).some(function(v){last=period(v.innerText);return !!last;});
            var blocks=Array.from(cell.querySelectorAll('.kbcontent,.course-item,.timetable_con')).filter(function(n){return !n.parentElement.closest('.kbcontent,.course-item,.timetable_con');});
            if(!blocks.length) blocks=[cell];
            blocks.forEach(function(block){
              var text=clean(block.innerText);
              if(text) cells.push({day:column.day,period:first ? first[0]+'-'+(last || first)[1] : '',text:text});
            });
          });
        }
      } else {
        var headers=Array.from(trs[0].cells).map(function(c){return clean(c.innerText).replace(/\s+/g,'');});
        var name=headers.findIndex(function(s){return /^(课程名称|课程名|课程)$/.test(s);});
        if(name<0) return;
        var lookup=function(pattern){return headers.findIndex(function(s){return pattern.test(s);});};
        var fields={name:name,dayText:lookup(/星期|周几/),period:lookup(/节次|节数/),weeks:lookup(/周次|教学周/),location:lookup(/地点|教室/),teacher:lookup(/教师|任课老师/)};
        trs.slice(1).forEach(function(tr){
          var row={},values=[];
          Object.keys(fields).forEach(function(key){var i=fields[key];if(i>=0 && tr.cells[i]) {row[key]=clean(tr.cells[i].innerText);values.push(row[key]);}});
          if(row.name) {row.day=day(row.dayText) || Number(row.dayText) || 0;row.text=values.join('\n');cells.push(row);}
        });
      }
    });
    var termMatch=clean(doc.body && doc.body.innerText).match(/20\d{2}\s*[-–～]\s*20\d{2}\s*学年\s*第?\s*[一二12]\s*学期/);
    if(!cells.length) return null;
    var filter=doc.querySelector('select#zcd,select#zc');
    var filtered=filter && filter.value && !/^(0|all)$/i.test(filter.value);
    var result={detected:true,ready:true,scope:filtered?'week':'full',format:'generic',term:termMatch?termMatch[0]:'我的课表',cells:cells};
    if(JSON.stringify(result).length>60000 || cells.length>300) return {detected:true,oversized:true};
    return result;
  }
  var result=extract(document);
  if(!result) Array.from(document.querySelectorAll('iframe')).some(function(frame){try {result=extract(frame.contentDocument);return !!result;}catch(_){return false;}});
  return JSON.stringify(result || {detected:false});
})()
