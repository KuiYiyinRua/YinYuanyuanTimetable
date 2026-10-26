package com.yinyuanyuan.timetable

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun importIntent(context: Context): Intent {
    val options=ImportPreferences(context)
    if(options.compatibility && !ImportPreferences.validPortal(options.portal)) return Intent(context,ImportSettingsActivity::class.java)
    return Intent(context,PortalActivity::class.java).putExtra("compat_mode",options.compatibility)
        .putExtra("portal_url",if(options.compatibility) options.portal else School.PORTAL)
}

class ImportSettingsActivity: ComponentActivity() {
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var review by mutableStateOf<ImportReview?>(null)
    private var job: Job?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options=ImportPreferences(this)
        setContent {TimetableTheme {
            Surface(Modifier.fillMaxSize(),color=Paper) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={finish()},modifier=Modifier.semantics {contentDescription="返回课表设置"}) {Mark(Glyph.Left)}
                        Text("兼容与 AI 导入",fontSize=17.sp)
                    }
                    ImportOptions(options,busy,status,onOpen={startActivity(importIntent(this@ImportSettingsActivity));finish()},onText=::recognize,
                        onCancel={job?.cancel();busy=false;status="已取消，原课表已保留"})
                }
                review?.let {candidate->ImportReviewDialog(candidate,onDismiss={review=null},onImport={
                    runCatching {ScheduleStore(this@ImportSettingsActivity).save(candidate.schedule)}.onSuccess {
                        Toast.makeText(this@ImportSettingsActivity,"已导入 ${candidate.schedule.courses.size} 条排课",Toast.LENGTH_SHORT).show();finish()
                    }.onFailure {status="保存失败，原课表已保留";review=null}
                })}
            }
        }}
    }
    private fun recognize(text: String) {
        if(busy) return
        if(text.length>50000) {status="文本超过 50,000 字符，请保留一个完整学期的课程内容后重试";return}
        val source=runCatching {ImportSource.text(text)}.getOrElse {status=it.message ?: "课表文本无效";return}
        val options=ImportPreferences(this)
        val key=options.apiKey()
        busy=true
        status=if(key!=null && options.useAgent) "DeepSeek 正在识别与校验，必要时自动纠正一次…" else "正在使用本地规则识别…"
        job=lifecycleScope.launch {
            try {
                review=ImportEngine.recognize(source,key!=null,options.useAgent) {DeepSeekClient(this@ImportSettingsActivity,key!!).identify(it)}
                status="识别完成，请检查课程后确认导入"
            } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(error:Exception){status=error.message?.take(260) ?: "识别失败，原课表已保留"}
            finally {busy=false}
        }
    }
}

@Composable private fun ImportOptions(options: ImportPreferences,busy:Boolean,status:String,onOpen:()->Unit,onText:(String)->Unit,onCancel:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var compatible by remember {mutableStateOf(options.compatibility)}
    var portal by remember {mutableStateOf(options.portal)}
    var agent by remember {mutableStateOf(options.useAgent)}
    var configured by remember {mutableStateOf(options.apiKey()!=null)}
    // A Key is deliberately not saved in UI instance state or repopulated in the text field.
    var keyInput by remember {mutableStateOf("")}
    var keyStatus by remember {mutableStateOf("")}
    var textInput by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开放网址兼容模式",fontSize=15.sp)
                Text("自行输入大学官网、门户或课表网址",fontSize=12.sp,color=Muted)
            }
            Switch(checked=compatible,onCheckedChange={compatible=it;options.compatibility=it},enabled=!busy,
                modifier=Modifier.semantics {contentDescription="切换开放网址兼容"})
        }
        if(compatible) {
            OutlinedTextField(value=portal,onValueChange={portal=it},label={Text("大学官网 / 门户 / 课表网址")},placeholder={Text("https://…")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),modifier=Modifier.fillMaxWidth().padding(top=10.dp),enabled=!busy)
            Text("不设学校名单，支持任意大学的 HTTPS 网址，省略协议时自动补全。进入完整学期课表后尝试识别；无法识别时可粘贴课程文本。",fontSize=12.sp,lineHeight=19.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
        }
        Button(onClick={if(compatible) options.portal=portal.trim();onOpen()},enabled=!busy && (!compatible || ImportPreferences.validPortal(portal)),modifier=Modifier.fillMaxWidth().padding(top=10.dp)) {
            Text(if(compatible) "保存并打开网址" else "打开广西中医药大学门户")
        }
        HorizontalDivider(Modifier.padding(vertical=20.dp),color=Rule)
        Text("DeepSeek 辅助识别",fontSize=16.sp)
        Text("配置 Key 后，导课时自动识别、校验并尝试纠错。仅发送当前课表内容；无 Key 不调用。结果需要核对后确认导入。",fontSize=12.sp,lineHeight=20.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
        Text(if(configured) "Key 已加密保存在本机" else "尚未配置 Key",fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
        OutlinedTextField(value=keyInput,onValueChange={keyInput=it},label={Text(if(configured) "替换 API Key" else "API Key")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
            modifier=Modifier.fillMaxWidth().padding(top=8.dp),enabled=!busy)
        Row {
            TextButton(onClick={runCatching {options.saveApiKey(keyInput)}.onSuccess {keyInput="";configured=true;keyStatus="Key 已保存"}.onFailure {keyStatus=it.message ?: "保存失败"}},enabled=keyInput.isNotBlank() && !busy) {Text("保存 Key")}
            if(configured) TextButton(onClick={options.saveApiKey("");configured=false;keyInput="";keyStatus="Key 已清除，不再调用 AI"},enabled=!busy) {Text("清除 Key")}
        }
        if(keyStatus.isNotBlank()) Text(keyStatus,fontSize=12.sp,color=Muted)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("导课时调用 AI",fontSize=14.sp)
                Text("DeepSeek Flash · 按 API 账户用量计费",fontSize=11.sp,color=Muted)
            }
            Switch(checked=agent,onCheckedChange={agent=it;options.useAgent=it},enabled=!busy)
        }
        HorizontalDivider(Modifier.padding(vertical=20.dp),color=Rule)
        Text("从课表文本识别",fontSize=16.sp)
        Text("粘贴课程、星期、节次和周次。若已启用 AI，提交的文本将发送给 DeepSeek。",fontSize=12.sp,lineHeight=20.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
        OutlinedTextField(value=textInput,onValueChange={textInput=it},label={Text("课表文本")},minLines=4,maxLines=7,modifier=Modifier.fillMaxWidth().padding(top=10.dp),enabled=!busy,isError=textInput.length>50000)
        if(textInput.length>50000) Text("文本超过 50,000 字符，请保留一个完整学期的课程内容。",fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
        TextButton(onClick={textInput=(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()},enabled=!busy) {Text("从剪贴板粘贴")}
        Button(onClick={onText(textInput)},enabled=textInput.isNotBlank() && textInput.length<=50000 && !busy,modifier=Modifier.fillMaxWidth()) {Text("识别课表文本")}
        if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp));TextButton(onClick=onCancel){Text("取消识别")}}
        if(status.isNotBlank()) Text(status,fontSize=12.sp,lineHeight=20.sp,modifier=Modifier.padding(top=12.dp))
    }
}
