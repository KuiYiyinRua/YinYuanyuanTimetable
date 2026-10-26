package com.yinyuanyuan.timetable

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun TimeSettingsScreen(times: ClassTimes, onChange: (ClassTimes) -> Unit) {
    var editing by remember { mutableStateOf<TimeSlot?>(null) }
    var reset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp, vertical=12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Campus.entries.forEach { campus ->
                val active=times.campus==campus
                OutlinedButton(onClick={onChange(times.copy(campus=campus))}, modifier=Modifier.weight(1f).heightIn(min=48.dp),
                    shape=RoundedCornerShape(8.dp), colors=ButtonDefaults.outlinedButtonColors(containerColor=if(active) Ink else Paper,contentColor=if(active) Paper else Ink)) {
                    Text(campus.label,fontSize=14.sp)
                }
            }
        }
        Text("${times.description} · 两个校区的修改分别保存",fontSize=12.sp,lineHeight=19.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
        Text("预设参考学校 2014 年作息表，可按实际安排修改。早读与第 13 节可自行补充。",fontSize=12.sp,lineHeight=19.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=14.dp))
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("晚间按节设置",fontSize=14.sp)
                Text(if(times.active.separateEvening) "分别填写第 10、11、12 节时间" else "第 10–12 节使用整段时间",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
            }
            Switch(checked=times.active.separateEvening,onCheckedChange={onChange(times.update(times.active.copy(separateEvening=it)))},
                modifier=Modifier.semantics {contentDescription="切换晚间逐节时间"})
        }
        HorizontalDivider(color=Rule)
        times.active.slots().forEach { slot ->
            Row(Modifier.fillMaxWidth().clickable {editing=slot}.heightIn(min=56.dp).padding(vertical=12.dp)
                .semantics {contentDescription="编辑${slot.label}时间"},verticalAlignment=Alignment.CenterVertically) {
                Text(slot.label,fontSize=14.sp,modifier=Modifier.width(88.dp))
                Column(Modifier.weight(1f)) {
                    Text(slot.time?.text ?: "未设置",fontSize=15.sp,fontWeight=FontWeight.Medium,color=if(slot.time==null) Muted else Ink)
                    if(slot.size>1) Text("原表仅提供晚间整段时间",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
                }
                Mark(Glyph.Right,Muted,Modifier.size(18.dp))
            }
            HorizontalDivider(color=Rule)
        }
        TextButton(onClick={reset=true},modifier=Modifier.align(Alignment.End).padding(top=10.dp)) {Text("恢复当前校区预设")}
    }
    editing?.let { slot ->
        TimeRangeDialog(slot.label,slot.time,onDismiss={editing=null},onSave={time->
            onChange(times.update(times.active.withTime(slot,time)))
            editing=null
        })
    }
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("恢复${times.campus.label}预设？")},
        text={Text("替换当前校区的自定义时间，另一个校区的设置不受影响。")},
        confirmButton={TextButton(onClick={onChange(times.resetCurrent());reset=false}) {Text("恢复预设")}},
        dismissButton={TextButton(onClick={reset=false}) {Text("取消")}})
}

@Composable private fun TimeRangeDialog(label: String, current: ClassTime?, onDismiss: () -> Unit, onSave: (ClassTime?) -> Unit) {
    val context=LocalContext.current
    var start by remember {mutableStateOf(current?.start ?: LocalTime.of(8,0))}
    var end by remember {mutableStateOf(current?.end ?: LocalTime.of(8,40))}
    val valid=start<end
    val formatter=remember {DateTimeFormatter.ofPattern("HH:mm")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("$label · 上课时间")},text={
        Column {
            Text("选择上课和下课时间",fontSize=13.sp,color=Muted)
            Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).background(Wash,RoundedCornerShape(8.dp)).clickable {
                    TimePickerDialog(context,{_,hour,minute->start=LocalTime.of(hour,minute)},start.hour,start.minute,true).show()
                }.padding(14.dp).semantics {contentDescription="选择上课时间"}) {
                    Text("上课",fontSize=12.sp,color=Muted)
                    Text(start.format(formatter),fontSize=23.sp,modifier=Modifier.padding(top=6.dp))
                }
                Column(Modifier.weight(1f).background(Wash,RoundedCornerShape(8.dp)).clickable {
                    TimePickerDialog(context,{_,hour,minute->end=LocalTime.of(hour,minute)},end.hour,end.minute,true).show()
                }.padding(14.dp).semantics {contentDescription="选择下课时间"}) {
                    Text("下课",fontSize=12.sp,color=Muted)
                    Text(end.format(formatter),fontSize=23.sp,modifier=Modifier.padding(top=6.dp))
                }
            }
            if(!valid) Text("下课时间必须晚于上课时间",fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
        }
    },confirmButton={TextButton(onClick={onSave(ClassTime(start,end))},enabled=valid) {Text("保存")}},
        dismissButton={Row {TextButton(onClick={onSave(null)}) {Text("清空")}; TextButton(onClick=onDismiss) {Text("取消")}}})
}
