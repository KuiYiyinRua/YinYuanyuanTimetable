package com.yinyuanyuan.timetable

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var store: ScheduleStore
    private var savedSchedule by mutableStateOf<Schedule?>(null)
    private var startDate by mutableStateOf<LocalDate?>(null)
    private var classTimes by mutableStateOf(ClassTimes())
    private var storageError by mutableStateOf<String?>(null)
    private var refreshVersion by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ScheduleStore(this)
        reload()
        setContent { TimetableTheme {
            Surface(Modifier.fillMaxSize(), color = Paper) {
                App(savedSchedule, startDate, storageError, refreshVersion, store, classTimes,
                    onImport = { startActivity(importIntent(this)) },
                    onStart = { store.startMonday = it; reload() },
                    onTimes = { store.classTimes = it; classTimes = it },
                    onClear = { store.clearSchedule(); reload() })
            }
        } }
    }
    override fun onResume() { super.onResume(); if (::store.isInitialized) reload() }
    private fun reload() { savedSchedule = store.load(); startDate = store.startMonday; classTimes = store.classTimes; storageError = store.readError; refreshVersion++ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(saved: Schedule?, start: LocalDate?, error: String?, refreshVersion: Int,
    store: ScheduleStore, times: ClassTimes, onImport: () -> Unit, onStart: (LocalDate) -> Unit,
    onTimes: (ClassTimes) -> Unit, onClear: () -> Unit) {
    var demo by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var timeSettings by rememberSaveable { mutableStateOf(false) }
    var todayMode by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(refreshVersion) { while (true) { today = LocalDate.now(); delay(30_000) } }
    val schedule = if (demo) remember { Demo.schedule() } else saved
    val date = if (demo) today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else start
    val actualWeek = date?.let { teachingWeek(it, today) }
    var week by rememberSaveable { mutableIntStateOf(store.selectedWeek) }
    var day by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }
    var weekPicker by remember { mutableStateOf(false) }
    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    LaunchedEffect(schedule?.importedAt, date, actualWeek, refreshVersion, demo) {
        week = if (demo) 1 else actualWeek?.coerceIn(1, schedule?.maxWeek ?: 20) ?: store.selectedWeek.coerceIn(1, schedule?.maxWeek ?: 20)
    }
    BackHandler(enabled = timeSettings || settings || demo || todayMode) {
        when { timeSettings -> timeSettings = false; settings -> settings = false; todayMode -> todayMode = false; demo -> demo = false }
    }
    fun changeWeek(value: Int) { week = value.coerceIn(1, schedule?.maxWeek ?: 20); if (!demo) store.selectedWeek = week }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=16.dp, end=4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (settings) {
                IconButton(onClick={if(timeSettings) timeSettings=false else settings=false}, modifier=Modifier.semantics { contentDescription=if(timeSettings) "返回设置" else "返回课表" }) { Mark(Glyph.Left) }
                Text(if(timeSettings) "上课时间" else "课表设置", fontSize=17.sp, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f).padding(vertical=3.dp)) {
                    Text("尹怨怨课程表", fontWeight=FontWeight.SemiBold, fontSize=17.sp, lineHeight=21.sp)
                    if (schedule != null) Text(
                        if (demo) "示例预览 · 非真实课表" else schedule.term,
                        fontSize=11.sp, lineHeight=14.sp, color=Muted
                    )
                }
                if (demo) TextButton(onClick={demo=false}, contentPadding=PaddingValues(horizontal=8.dp)) {
                    Text("退出示例", fontSize=12.sp)
                } else IconButton(onClick=onImport, modifier=Modifier.semantics { contentDescription="导入课表" }) { Mark(Glyph.Import) }
                IconButton(onClick={settings=true}, modifier=Modifier.semantics { contentDescription="课表设置" }) { Mark(Glyph.Settings) }
            }
        }
        if (timeSettings) {
            TimeSettingsScreen(times,onTimes)
        } else if (settings) {
            SettingsScreen(saved, start, times, {timeSettings=true}, onImport, onStart, onClear)
        } else if (schedule == null) {
            EmptySchedule(error, onImport) { demo=true; todayMode=false }
        } else {
            Column(Modifier.padding(horizontal=12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={changeWeek(week-1)}, enabled=week>1, modifier=Modifier.semantics { contentDescription="上一周" }) { Mark(Glyph.Left, if(week>1) Ink else Rule) }
                    Column(Modifier.weight(1f).heightIn(min=48.dp).clickable { weekPicker=true }
                        .semantics { contentDescription="选择教学周，当前第 $week 周" }, verticalArrangement=Arrangement.Center) {
                        Text("第 $week 周 ⌄", fontSize=16.sp, lineHeight=21.sp, fontWeight=FontWeight.SemiBold)
                        val weekDate=date?.plusWeeks((week-1).toLong())
                        Text(when {
                            date==null -> "未设置开学日期"
                            actualWeek!=null && actualWeek<1 -> "尚未开学"
                            actualWeek!=null && actualWeek>schedule.maxWeek -> "本学期已结束"
                            else -> weekDate!!.format(DateTimeFormatter.ofPattern("M.d")) + " – " + weekDate.plusDays(6).format(DateTimeFormatter.ofPattern("M.d"))
                        }, fontSize=11.sp, lineHeight=15.sp, color=Muted)
                    }
                    IconButton(onClick={changeWeek(week+1)}, enabled=week<schedule.maxWeek, modifier=Modifier.semantics { contentDescription="下一周" }) { Mark(Glyph.Right, if(week<schedule.maxWeek) Ink else Rule) }
                    TextButton(onClick={ if(date==null) settings=true else { changeWeek(actualWeek!!.coerceIn(1,schedule.maxWeek)); day=today.dayOfWeek.value } }, contentPadding=PaddingValues(horizontal=8.dp)) { Text(if(date==null) "设置" else "本周",fontSize=12.sp) }
                }
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp), verticalAlignment=Alignment.CenterVertically) {
                    Row {
                        ModeButton("周课表", !todayMode) { todayMode=false }
                        ModeButton("日程", todayMode) { todayMode=true }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("本周 ${schedule.forWeek(week).size} 项", color=Muted, fontSize=12.sp)
                }
            }
            HorizontalDivider(color=Rule)
            Box(Modifier.weight(1f).swipeWeeks(week,schedule.maxWeek,::changeWeek)) {
                if (todayMode) DaySchedule(schedule, week, day, date, today, times.active, {day=it}, { selectedCourses=listOf(it) })
                else WeekGrid(schedule, week, date, today, times.active) { selectedCourses=it }
            }
        }
    }
    if (weekPicker && schedule != null) {
        AlertDialog(onDismissRequest={weekPicker=false}, title={Text("选择教学周")}, text={
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (1..schedule.maxWeek).chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { w ->
                            TextButton(onClick={changeWeek(w);weekPicker=false}, modifier=Modifier.weight(1f).background(if(w==week) Ink else Color.Transparent,RoundedCornerShape(8.dp))) {
                                Text(w.toString(),color=if(w==week) Paper else Ink)
                            }
                        }
                        repeat(5-row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }, confirmButton={TextButton(onClick={weekPicker=false}) { Text("关闭") }})
    }
    if(selectedCourses.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest={selectedCourses=emptyList()}, containerColor=Paper,
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=24.dp,end=24.dp,bottom=32.dp)) {
                if(selectedCourses.size>1) Text("${selectedCourses.size} 门课程时间重叠",color=Muted,modifier=Modifier.padding(bottom=16.dp))
                selectedCourses.forEachIndexed { index, c ->
                    if(index>0) HorizontalDivider(Modifier.padding(vertical=20.dp), color=Rule)
                    Text(c.name,style=MaterialTheme.typography.titleLarge)
                    Text("星期${dayNames[c.day-1]} · ${c.periodLabel}",modifier=Modifier.padding(top=6.dp,bottom=20.dp),color=Muted)
                    DetailRow("时间",times.active.forCourse(c).text)
                    DetailRow("时间表",times.description)
                    DetailRow("周次",c.weekText)
                    DetailRow("地点",c.location.ifBlank { "未安排地点" })
                    DetailRow("教师",c.teacher.ifBlank { "未提供" })
                    if(c.className.isNotBlank()) DetailRow("教学班",c.className)
                    if(c.assessment.isNotBlank()) DetailRow("考核",c.assessment)
                }
                TextButton(onClick={selectedCourses=emptyList()}, modifier=Modifier.align(Alignment.End).padding(top=20.dp)) { Text("收起") }
            }
        }
    }
}

@Composable private fun ModeButton(label:String, active:Boolean, onClick:()->Unit) {
    Box(Modifier.heightIn(min=48.dp).clickable(onClick=onClick).padding(vertical=8.dp,horizontal=2.dp),contentAlignment=Alignment.Center) {
        Text(label, fontSize=13.sp, lineHeight=18.sp, color=if(active) Paper else Muted,
            modifier=Modifier.background(if(active) Ink else Color.Transparent,RoundedCornerShape(6.dp)).padding(horizontal=12.dp,vertical=7.dp))
    }
}

@Composable private fun EmptySchedule(error:String?, onImport:()->Unit, onDemo:()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact=maxWidth<360.dp || maxHeight<700.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if(compact) 20.dp else 28.dp)) {
        Spacer(Modifier.height(if(compact) 8.dp else 54.dp))
        Text("每一周，\n心中有数。",fontSize=if(compact) 32.sp else 38.sp,lineHeight=if(compact) 44.sp else 51.sp,fontWeight=FontWeight.Light)
        Text("广西中医药大学",fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=if(compact) 10.dp else 14.dp))
        Spacer(Modifier.height(if(compact) 18.dp else 34.dp))
        Box(Modifier.fillMaxWidth().height(if(compact) 100.dp else 160.dp).background(Wash,RoundedCornerShape(14.dp)).padding(if(compact) 12.dp else 18.dp)) {
            Column {
                Row(Modifier.fillMaxWidth()) { listOf("一","二","三","四","五").forEach { Text(it,modifier=Modifier.weight(1f),fontSize=11.sp,color=Muted) } }
                HorizontalDivider(Modifier.padding(vertical=if(compact) 6.dp else 12.dp),color=Rule)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    repeat(5) { index -> Column(Modifier.weight(1f)) {
                        if(index==1 || index==3) Spacer(Modifier.height(if(compact) 12.dp else 25.dp))
                        Box(Modifier.fillMaxWidth().height(if(index%2==0) {if(compact) 36.dp else 57.dp} else {if(compact) 24.dp else 37.dp}).background(if(index==0) Ink else Color(0xFFD5D5D0),RoundedCornerShape(5.dp)))
                    } }
                }
            }
        }
        Spacer(Modifier.height(if(compact) 18.dp else 30.dp))
        Text("导入一次，随时查看",style=MaterialTheme.typography.titleMedium)
        Text("登录学校门户，打开个人课表。\n课程按星期、节次与周次排列。",fontSize=if(compact) 13.sp else 15.sp,lineHeight=if(compact) 21.sp else 24.sp,color=Muted,modifier=Modifier.padding(top=8.dp,bottom=if(compact) 20.dp else 24.dp))
        if(error!=null) Text(error,color=Ink,modifier=Modifier.padding(bottom=16.dp))
        Button(onClick=onImport,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Text("从融合门户导入",modifier=Modifier.weight(1f)); Mark(Glyph.Arrow,Paper)
        }
        TextButton(onClick=onDemo,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp)) { Text("先看看示例",color=Muted) }
        Spacer(Modifier.height(20.dp))
        Text("导入后可离线使用 · AI 辅助识别可选",fontSize=11.sp,color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
    }
}

@Composable fun DetailRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=7.dp)) {
        Text(label,color=Muted,modifier=Modifier.width(64.dp),fontSize=13.sp)
        Text(value,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun SettingsScreen(schedule:Schedule?, start:LocalDate?, times:ClassTimes, onTimes:()->Unit,
    onImport:()->Unit,onStart:(LocalDate)->Unit,onClear:()->Unit) {
    val context=LocalContext.current
    var clear by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("学期",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=16.dp,bottom=12.dp))
        SettingRow("第一教学周的周一",start?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) ?: "未设置 · 设置后自动切换到本周") {
            val current=start ?: LocalDate.now()
            DatePickerDialog(context,{_,y,m,d->onStart(LocalDate.of(y,m+1,d).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))},current.year,current.monthValue-1,current.dayOfMonth).show()
        }
        if(schedule!=null) SettingRow("从学校页面识别","依据同学期的校历或当前教学周推算") {
            context.startActivity(Intent(context,PortalActivity::class.java).putExtra("calendar_only",true))
        }
        Text("选择开学第一周内的任意一天，会自动对齐到该周周一。",color=Muted,fontSize=12.sp,lineHeight=20.sp,modifier=Modifier.padding(top=12.dp,bottom=20.dp))
        SettingRow("上课时间","${times.description} · 点击选择校区或修改时间",onTimes)
        Spacer(Modifier.height(24.dp))
        Text("我的课表",color=Muted,fontSize=12.sp,modifier=Modifier.padding(bottom=12.dp))
        if(schedule!=null) {
            Text(schedule.term,style=MaterialTheme.typography.titleMedium)
            Text("${schedule.courses.map { it.name }.distinct().size} 门课程 · ${schedule.courses.size} 条排课",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=4.dp,bottom=18.dp))
        }
        SettingRow("重新导入","在学校页面打开需要的学期",onImport)
        SettingRow("兼容与 AI 导入","开放网址、课表文本与 DeepSeek Key") {
            context.startActivity(Intent(context,ImportSettingsActivity::class.java))
        }
        SettingRow("退出学校登录","清除本应用的学校会话，保留离线课表") {logout=true}
        if(schedule!=null) SettingRow("清除本地课表","清除后可从融合门户重新导入") {clear=true}
        if(message.isNotBlank()) Text(message,fontSize=13.sp,modifier=Modifier.padding(top=16.dp))
        Spacer(Modifier.height(32.dp))
        Text("关于",color=Muted,fontSize=12.sp)
        Text("尹怨怨课程表",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=14.dp))
        Text("${BuildConfig.VERSION_NAME} · 为简单的校园日常而做",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=14.dp))
        Text("优先适配广西中医药大学课表，可选其他学校兼容模式。课程保存在本机；仅在配置 Key 并启用 AI 时，把课程内容发送给 DeepSeek 辅助识别。不提取网页密码，学校登录状态由本机 WebView 保存，可随时退出。",fontSize=12.sp,lineHeight=21.sp,color=Muted)
        Text("这是个人课表工具，非学校官方应用。",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
        AuthorSupport()
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("清除本地课表？")},text={Text("会移除课程和开学日期。需要时可重新登录学校导入。")},confirmButton={TextButton(onClick={onClear();clear=false}) {Text("清除")}},dismissButton={TextButton(onClick={clear=false}) {Text("取消")}})
    if(logout) AlertDialog(onDismissRequest={logout=false},title={Text("退出学校登录？")},text={Text("仅清除本应用的学校登录状态，已导入的课表仍然可用。")},confirmButton={TextButton(onClick={
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush(); message="已退出学校登录" }
        WebStorage.getInstance().deleteAllData(); logout=false
    }) {Text("退出登录")}},dismissButton={TextButton(onClick={logout=false}) {Text("取消")}})
}

@Composable private fun SettingRow(title:String,subtitle:String,onClick:()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium);Text(subtitle,fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=5.dp))}
            Spacer(Modifier.width(12.dp));Mark(Glyph.Right,Muted,Modifier.size(18.dp))
        }
        HorizontalDivider(color=Rule)
    }
}
