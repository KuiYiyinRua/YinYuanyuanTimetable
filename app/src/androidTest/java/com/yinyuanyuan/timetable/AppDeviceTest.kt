package com.yinyuanyuan.timetable

import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import android.widget.TimePicker
import android.widget.Button
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val store=ScheduleStore(context)
    private fun waitFor(label:String,seconds:Long=20,block:()->Boolean) {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds)
        while(System.nanoTime()<until) {if(block()) return;Thread.sleep(180)}
        screenshot("failure-"+label.replace(' ','-'))
        android.util.Log.i("TimetableTest",nodes().mapNotNull {it.text?.toString()}.takeLast(12).joinToString(" | "))
        fail("Timed out: $label")
    }
    private fun allNodes(root:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> {
        if(root==null)return emptyList()
        return listOf(root)+(0 until root.childCount).flatMap {allNodes(root.getChild(it))}
    }
    private fun nodes()=allNodes(inst.uiAutomation.rootInActiveWindow)
    private fun has(text:String)=nodes().any {it.text?.toString()?.contains(text)==true || it.contentDescription?.toString()?.contains(text)==true}
    private fun click(text:String) {
        waitFor("visible $text") {has(text)}
        var node:AccessibilityNodeInfo?=nodes().first {it.text?.toString()==text || it.contentDescription?.toString()==text}
        while(node!=null && !node.isClickable) node=node.parent
        assertNotNull("Clickable $text",node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(350)
    }
    private fun screenshot(name:String) {
        Thread.sleep(450)
        val bitmap=inst.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val dir=File(context.filesDir,"verification").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    private fun scrollTo(text:String) {
        repeat(15) {
            if(nodes().any {it.isVisibleToUser && (it.text?.toString()==text || it.contentDescription?.toString()==text)}) return
            val scroller=nodes().firstOrNull {it.isScrollable && it.isVisibleToUser}
            assertNotNull("Scrollable content for $text",scroller)
            scroller!!.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(250)
        }
        fail("Not visible after scrolling: $text")
    }
    private fun swipe(fromX:Float,fromY:Float,toX:Float,toY:Float) {
        val size=context.resources.displayMetrics
        val down=SystemClock.uptimeMillis()
        fun event(action:Int,x:Float,y:Float) {
            val motion=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x*size.widthPixels,y*size.heightPixels,0)
            motion.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(inst.uiAutomation.injectInputEvent(motion,true));motion.recycle()
        }
        event(MotionEvent.ACTION_DOWN,fromX,fromY)
        for(step in 1..14){Thread.sleep(16);event(MotionEvent.ACTION_MOVE,fromX+(toX-fromX)*step/14,fromY+(toY-fromY)*step/14)}
        event(MotionEvent.ACTION_UP,toX,toY)
        Thread.sleep(400)
    }
    private fun changeNativeTime(hour:Int,minute:Int) {
        fun picker(view:View):TimePicker? {
            if(view is TimePicker) return view
            if(view is ViewGroup) for(index in 0 until view.childCount) picker(view.getChildAt(index))?.let {return it}
            return null
        }
        inst.runOnMainSync {
            val root=WindowInspector.getGlobalWindowViews().first {picker(it)!=null}
            picker(root)!!.apply {this.hour=hour;this.minute=minute}
            root.findViewById<Button>(android.R.id.button1).performClick()
        }
        Thread.sleep(250)
    }
    private fun findWeb(view:View):WebView? {
        if(view is WebView)return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun fixture(invalid:Boolean=false):String {
        val json=JSONObject(inst.context.assets.open("edge-anonymized-extraction.json").bufferedReader().use {it.readText()})
        val rows=json.getJSONArray("rows")
        val cards=(0 until rows.length()).joinToString("") {i->
            val row=rows.getJSONObject(i)
            val timing=if(invalid && i==10) "(1-3节)未知" else row.getString("timing")
            "<tr><td id='${row.getInt("day")}-1'><div class='timetable_con'><span class='title'>${row.getString("name")}</span><p><span title='节/周'></span>$timing</p><p><span title='上课地点'></span>测试教室</p><p><span title='教师 '></span>示例教师</p></div></td></tr>"
        }
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><table id='kbgrid_table_0'><tr><td class='timetable_title'><h6>2026-2027学年第1学期</h6></td></tr>$cards</table></body></html>"
    }
    private fun loadFixture(activity:PortalActivity,html:String,url:String=School.TIMETABLE) {
        val web=findWeb(activity.window.decorView)!!
        web.stopLoading()
        val original=web.webViewClient
        web.webViewClient=object:WebViewClient() {
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? =
                if(request.url.toString().substringBefore('#')==url.substringBefore('#')) WebResourceResponse("text/html","UTF-8",html.byteInputStream()) else original.shouldInterceptRequest(view,request)
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=original.shouldOverrideUrlLoading(view,request)
            override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?)=original.onPageStarted(view,url,favicon)
            override fun onPageFinished(view:WebView,url:String?)=original.onPageFinished(view,url)
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:android.webkit.WebResourceError)=original.onReceivedError(view,request,error)
        }
        web.loadUrl(url)
    }
    @Test fun a_emptyStateAndDemoPreview() {
        store.clearSchedule()
        store.classTimes=ClassTimes()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("empty state") {has("从融合门户导入")}
            screenshot("01-empty")
            click("先看看示例")
            waitFor("demo") {has("示例预览")}
            screenshot("02-week")
            click("日程")
            waitFor("day") {has("项课程")}
            click("查看星期一")
            screenshot("03-day")
            click("中医内科学（一）")
            waitFor("details") {has("教师") && has("周次")}
            screenshot("08-course-details")
            click("收起")
            assertNull("Demo must not replace real timetable",store.load())
            click("课表设置")
            waitFor("settings") {has("第一教学周的周一")}
            screenshot("04-settings")
        }
    }
    @Test fun b_realStructureAutoImportsAll59() {
        store.clearSchedule()
        ActivityScenario.launch(PortalActivity::class.java).use {scenario->
            scenario.onActivity {loadFixture(it,fixture())}
            waitFor("automatic import",30) {store.load()?.courses?.size==59}
            assertEquals(4,store.load()!!.courses.count {it.start==14})
            assertEquals(4,store.load()!!.courses[8].start)
            assertNull(store.startMonday)
        }
    }
    @Test fun c_invalidImportPreservesPreviousSchedule() {
        store.save(Demo.schedule())
        ActivityScenario.launch(PortalActivity::class.java).use {scenario->
            scenario.onActivity {loadFixture(it,fixture(true))}
            waitFor("invalid hint",25) {has("无法识别周次")}
            assertEquals(Demo.schedule(),store.load())
            screenshot("05-invalid-preserves-data")
        }
    }
    @Test fun d_offlineRelaunchAndWeekSwitch() {
        store.save(Demo.schedule())
        store.startMonday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("schedule") {has("本周 11 项")}
            click("下一周")
            waitFor("second week") {has("第 2 周")}
            screenshot("06-next-week")
            click("本周")
            click("课表设置")
            waitFor("persisted date") {has("第一教学周的周一")}
            screenshot("07-settings-with-data")
        }
    }
    @Test fun e_calendarScriptMatchesCurrentWeekAndTerm() {
        val result=CountDownLatch(1)
        var extracted:String?=null
        ActivityScenario.launch(PortalActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                // A distinct URL forces a document navigation. The initial portal URL can be
                // treated as same-document navigation after stopLoading and leave an empty body.
                val calendarUrl=School.PORTAL.substringBefore('#')+"?fixture=calendar"
                loadFixture(activity,"<html><body>2026-2027学年第1学期 今天：2026年10月26日 当前教学周：第8周</body></html>",calendarUrl)
            }
            waitFor("calendar fixture loaded") {
                val ready=CountDownLatch(1)
                var loaded=false
                scenario.onActivity {activity->
                    findWeb(activity.window.decorView)!!.evaluateJavascript("!!document.body && document.body.innerText.indexOf('当前教学周')>=0") {raw->loaded=raw=="true";ready.countDown()}
                }
                ready.await(2,TimeUnit.SECONDS);loaded
            }
            scenario.onActivity {activity->
                val web=findWeb(activity.window.decorView)!!
                val script=context.assets.open("extract-calendar.js").bufferedReader().use {it.readText()}
                web.evaluateJavascript(script) {raw->extracted=JSONTokener(raw).nextValue() as String;result.countDown()}
            }
            assertTrue(result.await(10,TimeUnit.SECONDS))
            val hint=CalendarHint.fromJson(extracted!!,LocalDate.parse("2026-10-26"))
            assertEquals(LocalDate.parse("2026-09-07"),hint?.start)
            assertTrue(hint!!.appliesTo("2026-2027学年第1学期"))
        }
    }

    @Test fun f_timeEditingPersistsAndAppearsInCourse() {
        store.save(Demo.schedule());store.classTimes=ClassTimes()
        store.startMonday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            click("课表设置");click("上课时间")
            waitFor("mingxiu preset") {has("08:00–08:40")};screenshot("10-time-presets")
            click("仙葫校区")
            waitFor("xianhu preset") {has("08:30–09:10")}
            click("编辑第 1 节时间");click("选择上课时间");changeNativeTime(8,35);click("保存")
            waitFor("custom clock saved") {store.classTimes.active.periods[1]?.startText=="08:35"}
            scenario.recreate()
            waitFor("custom clock restored") {has("08:35–09:10")};screenshot("11-time-customized")
            assertEquals("08:00",store.classTimes.profiles.getValue(Campus.MINGXIU).periods[1]!!.startText)
            click("编辑第 1 节时间");click("选择上课时间");changeNativeTime(10,0)
            waitFor("invalid end time") {has("下课时间必须晚于上课时间")}
            assertEquals("08:35",store.classTimes.active.periods[1]!!.startText)
            click("取消");click("返回设置");click("返回课表");click("日程");click("查看星期一")
            waitFor("day clock updated") {has("08:35")}
            click("中医内科学（一）")
            waitFor("course clock updated") {has("08:35–10:40")};screenshot("12-custom-time-detail")
        }
        val savedTimes=store.classTimes
        store.clearSchedule()
        assertEquals(savedTimes,store.classTimes)
        store.classTimes=ClassTimes()
    }
    @Test fun g_authorLinksOpenRequestedDestinations() {
        val urls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val monitor=object:Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.action!=Intent.ACTION_VIEW) return null
                urls.add(intent.dataString.orEmpty())
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
            }
        }
        inst.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                click("课表设置");scrollTo("作者尹怨怨的 B 站主页");screenshot("13-author-support")
                click("在 Steam 关注与中草药娘的日常")
                assertTrue(urls.last().startsWith("https://store.steampowered.com/app/4893790/"))
                click("作者尹怨怨的 B 站主页")
                assertEquals("https://space.bilibili.com/3546821542545653",urls.last())
            }
        } finally {inst.removeMonitor(monitor)}
    }
    @Test fun h_horizontalSwipesChangeWeeksAndKeepVerticalScrolling() {
        store.save(Demo.schedule());store.startMonday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("week one") {has("第 1 周 ⌄")}
            swipe(.2f,.65f,.85f,.65f);assertTrue(has("第 1 周 ⌄"))
            swipe(.85f,.65f,.15f,.65f);waitFor("swipe next week") {has("第 2 周 ⌄")}
            repeat(3) {
                if(!has("左右滑动切周")) swipe(.5f,.85f,.5f,.45f)
                assertTrue(has("第 2 周 ⌄"))
            }
            waitFor("vertical scrolling still works") {has("左右滑动切周")}
            swipe(.15f,.65f,.85f,.65f);waitFor("swipe previous week") {has("第 1 周 ⌄")}
            click("日程");swipe(.85f,.65f,.15f,.65f);waitFor("day swipe") {has("第 2 周 ⌄")}
        }
        store.startMonday=null;store.selectedWeek=20
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("last week") {has("第 20 周 ⌄")}
            swipe(.85f,.65f,.15f,.65f);assertTrue(has("第 20 周 ⌄"))
            swipe(.15f,.65f,.85f,.65f);waitFor("previous from last") {has("第 19 周 ⌄")}
            assertEquals(19,store.selectedWeek)
        }
    }
    @Test fun i_genericTableWithoutKeyRequiresReviewBeforeSaving() {
        val options=ImportPreferences(context);options.saveApiKey("")
        store.save(Demo.schedule())
        val html="""<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><h1>2026-2027学年第1学期</h1><table border='1'>
            <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th></tr>
            <tr><td>第1节</td><td rowspan='2'>大学英语<br>1-16周</td><td></td><td></td><td></td><td></td></tr>
            <tr><td>第2节</td><td></td><td></td><td></td><td></td></tr>
            <tr><td>第3节</td><td></td><td rowspan='2'>高等数学<br>2-16周(双)</td><td></td><td></td><td></td></tr>
            <tr><td>第4节</td><td></td><td></td><td></td><td></td></tr>
            </table></body></html>""".trimIndent()
        val url="https://university.example.edu/timetable?fixture=generic"
        val intent=Intent(context,PortalActivity::class.java).putExtra("compat_mode",true).putExtra("portal_url","https://university.example.edu/")
        ActivityScenario.launch<PortalActivity>(intent).use {scenario->
            scenario.onActivity {loadFixture(it,html,url)}
            waitFor("generic import review",30) {has("核对导入课表")}
            assertEquals(Demo.schedule(),store.load());screenshot("14-compatible-review")
            click("返回检查");assertEquals(Demo.schedule(),store.load())
            click("导入当前页");waitFor("manual review") {has("核对导入课表")}
            click("确认导入 2 条")
            waitFor("generic import committed") {store.load()?.courses?.size==2}
            assertEquals(2,store.load()!!.courses[0].end)
            assertEquals(listOf(2,4,6,8,10,12,14,16),store.load()!!.courses[1].weeks)
            assertNull(options.apiKey())
        }
    }
    @Test fun j_keyIsEncryptedAndCanBeCleared() {
        val options=ImportPreferences(context)
        val fake="sk-test-only-not-live-0000000000000000"
        try {
            options.saveApiKey(fake)
            assertEquals(fake,ImportPreferences(context).apiKey())
            val encrypted=context.getSharedPreferences("import_options",0).getString("encrypted_key",null)!!
            assertFalse(encrypted.contains(fake));assertFalse(encrypted.contains("test-only"))
        } finally {options.saveApiKey("")}
        assertNull(options.apiKey())
    }
    @Test fun k_textImportOptionsWithoutKey() {
        val options=ImportPreferences(context)
        options.saveApiKey("");options.compatibility=false;options.useAgent=true
        store.save(Demo.schedule())
        ActivityScenario.launch(ImportSettingsActivity::class.java).use {scenario->
            waitFor("import options") {has("尚未配置 Key")};screenshot("15-import-options")
            scrollTo("从剪贴板粘贴")
            scenario.onActivity {activity->
                (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("匿名课表示例","大学英语 | 周一 第1-2节 1-16周\n高等数学 | 周二 第3-4节 2-16周(双)"))
            }
            click("从剪贴板粘贴");scrollTo("识别课表文本");click("识别课表文本")
            waitFor("text preview without key") {has("确认导入 2 条")}
            assertTrue(has("本地规则识别"));assertEquals(Demo.schedule(),store.load())
            screenshot("16-text-review")
            click("确认导入 2 条")
            waitFor("text committed") {store.load()?.courses?.size==2}
            assertNull(options.apiKey())
        }
    }
    @Test fun l_openCompatibilityAcceptsCustomWebsite() {
        val options=ImportPreferences(context)
        options.compatibility=false;options.portal=""
        var opened:Intent?=null
        val monitor=object:Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.component?.className!=PortalActivity::class.java.name) return null
                opened=intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
            }
        }
        inst.addMonitor(monitor)
        try {
            ActivityScenario.launch(ImportSettingsActivity::class.java).use {
                click("切换开放网址兼容")
                waitFor("website input") {has("大学官网 / 门户 / 课表网址")}
                val field=nodes().first {it.isEditable}
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"university.example.edu:8443/portal")
                }))
                waitFor("custom website entered") {has("university.example.edu:8443/portal")}
                screenshot("17-open-website")
                click("保存并打开网址")
                assertEquals("https://university.example.edu:8443/portal",opened?.getStringExtra("portal_url"))
                assertTrue(opened!!.getBooleanExtra("compat_mode",false))
                assertEquals("https://university.example.edu:8443/portal",ImportPreferences(context).portal)
            }
        } finally {inst.removeMonitor(monitor);options.compatibility=false;options.portal=""}
    }
    @Test fun z_networkPortalProbe() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("network")=="true")
        ActivityScenario.launch(PortalActivity::class.java).use {scenario->
            var found=false
            waitFor("real school login page",40) {
                val latch=CountDownLatch(1)
                scenario.onActivity {activity->
                    val web=findWeb(activity.window.decorView)!!
                    web.evaluateJavascript("JSON.stringify({host:location.hostname,title:document.title,ready:document.readyState==='complete',password:!!document.querySelector('input[type=password]') && document.querySelector('input[type=password]').getBoundingClientRect().height>0,school:(document.body.innerText||'').indexOf('广西中医药大学')>=0,login:(document.body.innerText||'').indexOf('登录')>=0})") {raw->
                        val result=runCatching {JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull()
                        found=result!=null && result.optBoolean("ready") && result.optBoolean("password") && School.allowed(web.url ?: "")
                        if(found) android.util.Log.i("TimetableNetwork",result.toString())
                        latch.countDown()
                    }
                }
                latch.await(2,TimeUnit.SECONDS)
                found
            }
            Thread.sleep(2000)
            screenshot("09-school-login")
        }
    }
}
