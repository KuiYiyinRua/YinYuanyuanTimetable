package com.yinyuanyuan.timetable

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONTokener
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PortalActivity : ComponentActivity() {
    private lateinit var web:WebView
    private lateinit var frame:android.widget.FrameLayout
    private lateinit var store:ScheduleStore
    private val main=Handler(Looper.getMainLooper())
    private var status by mutableStateOf("登录后进入教务系统，打开个人课表")
    private var address by mutableStateOf("ehall.gxtcmu.edu.cn")
    private var loading by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var autoImport by mutableStateOf(true)
    private var active=true
    private var importing by mutableStateOf(false)
    private var review by mutableStateOf<ImportReview?>(null)
    private var importJob:Job?=null
    private var attemptedPayload:String?=null
    private var compatible=false
    private var initialPortal=School.PORTAL
    private lateinit var options:ImportPreferences
    private var previousPayload:String?=null
    private var hint:CalendarHint?=null
    private var calendarOnly=false
    private var pending=false
    private var navigationVersion=0
    private lateinit var extract:String
    private lateinit var extractCalendar:String
    private lateinit var extractCompatible:String
    private val popups=mutableListOf<WebView>()
    private val poll=object:Runnable {
        override fun run() { if(active) { inspect(false);main.postDelayed(this,1500) } }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        store=ScheduleStore(this)
        options=ImportPreferences(this)
        calendarOnly=intent.getBooleanExtra("calendar_only",false)
        compatible=!calendarOnly && intent.getBooleanExtra("compat_mode",false)
        if(compatible) initialPortal=intent.getStringExtra("portal_url")?.takeIf {ImportPreferences.validPortal(it)}?.let {ImportPreferences.normalizePortal(it)} ?: School.PORTAL
        if(compatible) status="兼容模式：登录后打开完整学期课表，识别后请核对"
        if(calendarOnly) status="打开学校校历或显示当前教学周的页面"
        extract=assets.open("extract-timetable.js").bufferedReader().use {it.readText()}
        extractCalendar=assets.open("extract-calendar.js").bufferedReader().use {it.readText()}
        extractCompatible=assets.open("extract-compatible.js").bufferedReader().use {it.readText()}
        frame=android.widget.FrameLayout(this)
        web=createWebView()
        frame.addView(web,ViewGroup.LayoutParams(-1,-1))
        setContent { TimetableTheme {
            Column(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(start=4.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={finish()},modifier=Modifier.semantics {contentDescription="关闭导入"}) {Mark(Glyph.Close)}
                    Column(Modifier.weight(1f)) {
                        Text(if(calendarOnly) "识别开学周" else if(compatible) "兼容导入" else "从学校导入",fontSize=16.sp)
                        Text(address,fontSize=11.sp,color=Muted,maxLines=1)
                    }
                    TextButton(onClick={web.reload()}) {Text("刷新",fontSize=12.sp)}
                }
                HorizontalDivider(color=Rule)
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton(onClick={web.loadUrl(initialPortal)}) {Text(if(compatible) "学校首页" else "融合门户",fontSize=12.sp)}
                    if(!compatible) TextButton(onClick={web.loadUrl(School.TIMETABLE)}) {Text("个人课表",fontSize=12.sp)}
                    TextButton(onClick={navigateBack(false)}) {Text("网页返回",fontSize=12.sp)}
                }
                if(loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Ink,trackColor=Wash)
                AndroidView(factory={frame},modifier=Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider(color=Rule)
                Column(Modifier.fillMaxWidth().background(Paper).padding(horizontal=16.dp,vertical=10.dp)) {
                    Text(status,fontSize=12.sp,lineHeight=18.sp,color=Ink)
                    if(importing && review==null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=6.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        if(!calendarOnly) {
                            Checkbox(checked=autoImport,onCheckedChange={autoImport=it;previousPayload=null})
                            Text("自动导入",fontSize=12.sp,modifier=Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        TextButton(onClick={inspect(true)},enabled=!importing) {Text(if(calendarOnly) "识别当前页" else "导入当前页",fontSize=12.sp)}
                    }
                }
            }
            review?.let {candidate->ImportReviewDialog(candidate,onDismiss={review=null;importing=false;status="已取消导入，原课表已保留"},onImport={save(candidate.schedule)})}
        } }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {navigateBack(true)}
        })
        web.loadUrl(initialPortal)
    }

    private fun allowed(url:String)=if(compatible) ImportPreferences.validPortal(url) else School.allowed(url)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView():WebView = WebView(this).apply {
        setBackgroundColor(Color.WHITE)
        settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true
            allowFileAccess=false;allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically=false
            useWideViewPort=true;loadWithOverviewMode=true
            builtInZoomControls=true;displayZoomControls=false
            // The school portal serves its supported desktop layout; pinch zoom remains available.
            userAgentString="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
        importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!allowed(request.url.toString())) {
                    if(request.isForMainFrame) status=if(compatible) "兼容模式只打开 HTTPS 网页" else "此链接不属于学校 HTTPS 页面，已停止打开"
                    return true
                }
                return false
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:android.graphics.Bitmap?) {
                if(view!==web) return
                navigationVersion++;previousPayload=null;attemptedPayload=null;loading=true;failed=false
                importJob?.cancel();importing=false;review=null
                address=runCatching {Uri.parse(url).host}.getOrNull() ?: "学校页面"
                if(url!=null && !allowed(url)) {view.stopLoading();failed=true;status="仅允许打开 HTTPS 页面"}
            }
            override fun onPageFinished(view:WebView,url:String?) {
                if(view!==web) return
                loading=false;CookieManager.getInstance().flush()
                if(!failed) status=if(calendarOnly) "正在检查页面中的学期与教学周信息" else if(compatible) "打开完整学期课表，识别后将展示核对结果" else if(url!=null && School.canExtract(url)) "等待课表加载，正在识别课程" else "登录后点击「教务系统」，再打开「个人课表」"
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame && view===web) {loading=false;failed=true;status="学校页面未能加载，请检查网络后刷新。原课表已保留。"}
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(request.isForMainFrame && view===web && response.statusCode>=400) {loading=false;failed=true;status="学校页面暂不可用（${response.statusCode}），请稍后刷新"}
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError) {
                handler.cancel();failed=true;loading=false;status="学校页面证书验证失败，无法继续加载"
            }
        }
        webChromeClient=object:WebChromeClient() {
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(!isUserGesture || popups.size>=8) return false
                // SSO links often open a new window. Keep that actual WebView so POST and opener state survive.
                val child=createWebView()
                val parent=web
                popups.add(parent)
                frame.removeView(parent)
                web=child
                frame.addView(child,ViewGroup.LayoutParams(-1,-1))
                val transport=resultMsg.obj as WebView.WebViewTransport
                transport.webView=child;resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window:WebView) {
                if(window===web && popups.isNotEmpty()) {
                    frame.removeView(window);window.destroy();web=popups.removeAt(popups.lastIndex)
                    frame.addView(web,ViewGroup.LayoutParams(-1,-1));previousPayload=null
                }
            }
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
            override fun onJsAlert(view:WebView,url:String,message:String,result:JsResult):Boolean {
                AlertDialog.Builder(this@PortalActivity).setMessage(message).setPositiveButton("确定") {_,_->result.confirm()}.setOnCancelListener {result.cancel()}.show();return true
            }
            override fun onJsConfirm(view:WebView,url:String,message:String,result:JsResult):Boolean {
                AlertDialog.Builder(this@PortalActivity).setMessage(message).setPositiveButton("确定") {_,_->result.confirm()}.setNegativeButton("取消") {_,_->result.cancel()}.setOnCancelListener {result.cancel()}.show();return true
            }
        }
    }

    private fun decode(value:String?):String?=runCatching {JSONTokener(value ?: "null").nextValue() as? String}.getOrNull()

    private fun navigateBack(closeIfEmpty:Boolean) {
        if(web.canGoBack()) web.goBack()
        else if(popups.isNotEmpty()) {
            val child=web
            frame.removeView(child);child.stopLoading();child.destroy()
            web=popups.removeAt(popups.lastIndex)
            frame.addView(web,ViewGroup.LayoutParams(-1,-1))
            navigationVersion++;previousPayload=null;pending=false;loading=false;failed=false
            address=Uri.parse(web.url).host ?: "学校页面"
        } else if(closeIfEmpty) finish()
    }

    private fun inspect(manual:Boolean) {
        if(!active || importing || review!=null || pending || loading || failed) return
        val url=web.url ?: return
        if(!allowed(url)) return
        val inspecting=web
        val version=navigationVersion
        pending=true
        inspecting.evaluateJavascript(extractCalendar) {raw->
            pending=false
            if(!active || web!==inspecting || version!=navigationVersion) return@evaluateJavascript
            decode(raw)?.let {CalendarHint.fromJson(it)}?.let {hint=it}
            if(calendarOnly) {
                val candidate=hint
                val saved=store.load()
                if(candidate!=null && saved!=null && candidate.appliesTo(saved.term)) {
                    store.startMonday=candidate.start
                    Toast.makeText(this,"已识别第一周：${candidate.start}",Toast.LENGTH_LONG).show();finish()
                } else if(manual) status="未找到与课表学期一致的日期和教学周。可在设置中手动选择第一周。"
                return@evaluateJavascript
            }
            if(!compatible && !School.canExtract(url)) {
                if(manual) status="请先进入教务系统的个人课表，再点击导入"
                return@evaluateJavascript
            }
            if(!autoImport && !manual) return@evaluateJavascript
            pending=true
            inspecting.evaluateJavascript(extract) extraction@{payload->
                pending=false
                if(!active || web!==inspecting || version!=navigationVersion) return@extraction
                if(!manual && !autoImport) return@extraction
                val text=decode(payload) ?: return@extraction
                val detected=runCatching {JSONObject(text).optBoolean("detected")}.getOrDefault(false)
                if(!detected && (compatible || (options.useAgent && options.apiKey()!=null))) {
                    pending=true
                    inspecting.evaluateJavascript(extractCompatible) generic@{rawGeneric->
                        pending=false
                        if(!active || web!==inspecting || version!=navigationVersion || loading || failed) return@generic
                        decode(rawGeneric)?.let {processSource(it,manual,version)}
                    }
                } else processSource(text,manual,version)
            }
        }
    }

    private fun processSource(text:String,manual:Boolean,version:Int) {
        if(!manual && !autoImport) return
        val source=runCatching {ImportSource(text)}.getOrElse {
            previousPayload=null
            if(manual || text.contains("\"detected\":true")) status=it.message ?: "暂时无法识别课表"
            return
        }
        if(!manual && attemptedPayload==text) return
        if(!manual && previousPayload!=text) {previousPayload=text;status="已发现课表，正在确认页面稳定";return}
        val key=if(options.useAgent) options.apiKey() else null
        if(!compatible && key==null) {
            runCatching {source.local()}.onSuccess {importing=true;save(it)}.onFailure {status=it.message ?: "无法识别课表"}
            return
        }
        attemptedPayload=text
        importing=true
        status=if(key==null) "正在尝试兼容识别…" else "DeepSeek 正在识别与校验，必要时自动纠正一次…"
        importJob=lifecycleScope.launch {
            try {
                val result=ImportEngine.recognize(source,key!=null,options.useAgent) {DeepSeekClient(this@PortalActivity,key!!).identify(it)}
                if(version==navigationVersion && !isFinishing) {review=result;status="识别完成，请核对后导入"}
            } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(error:Exception){if(version==navigationVersion) status=error.message?.take(260) ?: "识别失败，原课表已保留"}
            finally {if(review==null) importing=false}
        }
    }

    private fun save(result:Schedule) {
        runCatching {
            store.save(result)
            hint?.takeIf {it.appliesTo(result.term)}?.let {if(store.startMonday==null) store.startMonday=it.start}
            Toast.makeText(this,"已导入 ${result.courses.size} 条排课",Toast.LENGTH_LONG).show()
            finish()
        }.onFailure {review=null;importing=false;status="保存失败，原课表已保留，请重试"}
    }

    override fun onResume() {super.onResume();active=true;if(::web.isInitialized) web.onResume();main.removeCallbacks(poll);main.postDelayed(poll,1500)}
    override fun onPause() {active=false;pending=false;importJob?.cancel();if(review==null) importing=false;main.removeCallbacks(poll);if(::web.isInitialized) web.onPause();super.onPause()}
    override fun onDestroy() {
        active=false;main.removeCallbacksAndMessages(null)
        if(::frame.isInitialized) frame.removeAllViews()
        if(::web.isInitialized) {web.stopLoading();web.destroy()}
        popups.forEach {it.stopLoading();it.destroy()};popups.clear()
        super.onDestroy()
    }
}
