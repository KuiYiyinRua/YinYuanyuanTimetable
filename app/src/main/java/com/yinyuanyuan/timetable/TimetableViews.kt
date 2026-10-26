package com.yinyuanyuan.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val dayNames = listOf("一","二","三","四","五","六","日")

@Composable fun WeekGrid(schedule:Schedule, week:Int, start:LocalDate?, today:LocalDate, times:TimeProfile, onCourse:(List<Course>)->Unit) {
    val courses=schedule.forWeek(week)
    val monday=start?.plusWeeks((week-1).toLong())
    val hasEarly=schedule.courses.any {it.start==14}
    val periods=if(hasEarly) listOf(14)+(1..13) else (1..13).toList()
    val rowHeight=54.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=4.dp,end=6.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.width(44.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(monday?.monthValue?.let { "$it" } ?: "节",fontSize=12.sp,color=Muted)
                Text(if(monday!=null) "月" else "次",fontSize=10.sp,color=Muted)
            }
            (1..7).forEach { day ->
                val date=monday?.plusDays((day-1).toLong())
                val isToday=date==today
                Column(Modifier.weight(1f).padding(horizontal=2.dp).background(if(isToday) Ink else Color.Transparent,RoundedCornerShape(8.dp)).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(dayNames[day-1],fontSize=12.sp,color=if(isToday) Paper else Muted)
                    if(date!=null) Text(date.dayOfMonth.toString(),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=if(isToday) Paper else Ink,modifier=Modifier.padding(top=3.dp))
                }
            }
        }
        HorizontalDivider(color=Rule)
        if(courses.isEmpty()) Text("本周暂无课程，可切换周次查看",fontSize=12.sp,color=Muted,modifier=Modifier.fillMaxWidth().background(Wash).padding(12.dp),textAlign=TextAlign.Center)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start=4.dp,end=6.dp)) {
                Column(Modifier.width(44.dp)) {
                    times.slots(hasEarly).forEach { slot ->
                        Column(Modifier.height(rowHeight*slot.size).fillMaxWidth().semantics {contentDescription="${slot.label} ${slot.time?.text ?: "时间未设置"}"},
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text(when {slot.first==14->"早读";slot.size>1->"${slot.first}–${slot.last}";else->slot.first.toString().padStart(2,'0')},fontSize=10.sp,lineHeight=14.sp,color=Ink)
                            if(slot.time!=null) {
                                Text(slot.time.startText,fontSize=8.sp,lineHeight=11.sp,color=Muted)
                                Text(slot.time.endText,fontSize=8.sp,lineHeight=11.sp,color=Muted)
                            } else Text("未设",fontSize=8.sp,lineHeight=11.sp,color=Muted)
                        }
                    }
                }
                (1..7).forEach { day ->
                    val isToday=monday?.plusDays((day-1).toLong())==today
                    Box(Modifier.weight(1f).height(rowHeight*periods.size).background(if(isToday) Color(0xFFF4F4F1) else Paper)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(Rule,Offset(0f,0f),Offset(0f,size.height),1.dp.toPx()/2)
                            for(i in 1..periods.size) drawLine(Rule,Offset(0f,i*rowHeight.toPx()),Offset(size.width,i*rowHeight.toPx()),1.dp.toPx()/2)
                        }
                        courseGroups(courses.filter {it.day==day}).forEach { group ->
                            val c=group.courses.first()
                            val rowIndex=if(hasEarly) group.startRow else group.startRow-1
                            val blockHeight=rowHeight*(group.endRow-group.startRow+1)-4.dp
                            val black=isToday
                            Surface(onClick={onCourse(group.courses)},modifier=Modifier.fillMaxWidth().offset(y=rowHeight*rowIndex+2.dp).height(blockHeight).padding(horizontal=2.dp)
                                .semantics {contentDescription="星期${dayNames[day-1]} ${group.courses.joinToString { it.name }} ${c.periodLabel}${if(group.courses.size>1) " 时间重叠" else ""}"},
                                shape=RoundedCornerShape(6.dp),color=if(black) Ink else Wash,
                                border=if(black) null else BorderStroke(0.5.dp,Color(0xFFDCDCD7))) {
                                Column(Modifier.padding(horizontal=4.dp,vertical=6.dp).clip(RoundedCornerShape(2.dp))) {
                                    Text(c.name.replace(Regex("[（(](理论|网络|实验|见习)[)）]$"),""),fontSize=10.sp,lineHeight=14.sp,fontWeight=FontWeight.Medium,color=if(black) Paper else Ink,maxLines=if(group.endRow==group.startRow) 2 else 5)
                                    if(group.courses.size>1) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("${group.courses.size}门重叠",fontSize=9.sp,lineHeight=12.sp,color=if(black) Color(0xFFD5D5D0) else Muted)
                                        Text("点此查看",fontSize=9.sp,lineHeight=12.sp,color=if(black) Color(0xFFD5D5D0) else Muted)
                                    } else if(group.endRow>group.startRow) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(c.location.replace(Regex("^(明秀|仙葫)校区[ ·]*"),""),fontSize=9.sp,lineHeight=12.sp,color=if(black) Color(0xFFD5D5D0) else Muted,maxLines=3)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("左右滑动切周 · 点击课程查看完整信息",modifier=Modifier.fillMaxWidth().padding(vertical=18.dp,horizontal=16.dp),textAlign=TextAlign.Center,color=Muted,fontSize=10.sp)
        }
    }
}

@Composable fun DaySchedule(schedule:Schedule, week:Int, selectedDay:Int, start:LocalDate?, today:LocalDate, times:TimeProfile, onDay:(Int)->Unit,onCourse:(Course)->Unit) {
    val monday=start?.plusWeeks((week-1).toLong())
    val date=monday?.plusDays((selectedDay-1).toLong())
    val courses=schedule.forWeek(week).filter {it.day==selectedDay}.sortedBy {it.startRow}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            (1..7).forEach {d->
                Column(Modifier.weight(1f).background(if(d==selectedDay) Ink else Color.Transparent,RoundedCornerShape(10.dp)).clickable {onDay(d)}.padding(vertical=10.dp)
                    .semantics {contentDescription="查看星期${dayNames[d-1]}"},horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(dayNames[d-1],fontSize=12.sp,color=if(d==selectedDay) Paper else Muted)
                    if(monday!=null) Text(monday.plusDays((d-1).toLong()).dayOfMonth.toString(),modifier=Modifier.padding(top=4.dp),fontSize=15.sp,fontWeight=FontWeight.Medium,color=if(d==selectedDay) Paper else Ink)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=24.dp),verticalAlignment=Alignment.Bottom) {
                Text(if(date==today) "今天" else "星期${dayNames[selectedDay-1]}",fontSize=28.sp,fontWeight=FontWeight.Light,modifier=Modifier.weight(1f))
                Text("${courses.size} 项课程",color=Muted,fontSize=12.sp,modifier=Modifier.padding(bottom=4.dp))
            }
            if(courses.isEmpty()) {
                HorizontalDivider(color=Rule)
                Mark(Glyph.Check,Muted,Modifier.padding(top=40.dp).size(34.dp))
                Text("这一天没有课程",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=20.dp))
                Text("留一点时间，给自己。",color=Muted,modifier=Modifier.padding(top=8.dp))
            }
            courses.forEach {c->
                val overlaps=courses.any { it!=c && it.startRow<=c.endRow && it.endRow>=c.startRow }
                val clock=times.forCourse(c)
                Row(Modifier.fillMaxWidth().clickable {onCourse(c)}.padding(bottom=24.dp),verticalAlignment=Alignment.Top) {
                    Column(Modifier.width(58.dp).padding(top=3.dp)) {
                        if(clock.time!=null) {
                            Text(clock.time.startText,fontSize=14.sp,lineHeight=21.sp,fontWeight=FontWeight.Medium)
                            Text(clock.time.endText,fontSize=12.sp,lineHeight=19.sp,color=Muted)
                        }
                        Text(if(c.start==14) "早读" else if(c.start==c.end) "${c.start} 节" else "${c.start}–${c.end} 节",color=Muted,fontSize=10.sp,lineHeight=16.sp)
                        if(clock.time==null) Text("时间未设",fontSize=10.sp,lineHeight=16.sp,color=Muted)
                    }
                    Column(Modifier.weight(1f).background(Wash,RoundedCornerShape(12.dp)).padding(18.dp)) {
                        Text(c.name,fontSize=17.sp,lineHeight=25.sp,fontWeight=FontWeight.Medium)
                        if(clock.includesWholeEvening) Text("时间包含晚间整段，逐节时间待设置",fontSize=11.sp,lineHeight=17.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
                        Text(c.location.ifBlank {"未安排地点"},fontSize=12.sp,lineHeight=19.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
                        if(c.teacher.isNotBlank()) Text(c.teacher,fontSize=12.sp,lineHeight=19.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                        if(overlaps) Text("与其他课程时间重叠",fontSize=11.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=10.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
