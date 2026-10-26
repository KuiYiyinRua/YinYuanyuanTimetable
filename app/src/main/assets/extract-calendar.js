(function () {
  'use strict';
  function scan(doc) {
    if (!doc || !doc.body) return null;
    var text = (doc.body.innerText || '').replace(/\s+/g, ' ');
    var termPattern=/20\d{2}\s*[-—至]\s*20\d{2}\s*学年\s*第?\s*[123一二三]\s*学期/g;
    var terms=text.match(termPattern) || [];
    if (new Set(terms.map(function(t){return t.replace(/\s/g,'');})).size!==1) return null;
    var term=[terms[0]];
    var first = text.match(/(?:第一(?:教学)?周(?:的)?(?:周一|星期一)|第\s*1\s*(?:教学)?周(?:的)?(?:周一|星期一))\s*[:：]?\s*(20\d{2})[年/-](\d{1,2})[月/-](\d{1,2})/);
    function date(match, i) { return match[i] + '-' + match[i+1].padStart(2,'0') + '-' + match[i+2].padStart(2,'0'); }
    if (first) return { found:true, mode:'firstMonday', term:term[0], date:date(first,1), evidence:'学校页面标注的第一周周一' };
    var current = text.match(/(?:今天|今日|当前日期)\s*[:：]?\s*(20\d{2})[年/-](\d{1,2})[月/-](\d{1,2})日?[^。]{0,100}?(?:当前|本周|教学周)[^\d]{0,8}(\d{1,2})\s*周/);
    if(current) return {found:true, mode:'currentWeek', term:term[0], date:date(current,1), week:Number(current[4]), evidence:'学校页面的当前日期与教学周'};
    return null;
  }
  var result=scan(document);
  if(result) return JSON.stringify(result);
  var frames=Array.from(document.querySelectorAll('iframe'));
  for(var i=0;i<frames.length;i++) {try {result=scan(frames[i].contentDocument);if(result)return JSON.stringify(result);}catch(_) {}}
  return JSON.stringify({found:false});
})()
