package com.yinyuanyuan.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun ImportReviewDialog(review: ImportReview, onDismiss: () -> Unit, onImport: () -> Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal=12.dp),color=Paper,shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Text("核对导入课表",style=MaterialTheme.typography.titleLarge)
                Text("${review.method} · ${review.schedule.courses.size} 条排课",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
                Text(review.schedule.term,fontSize=13.sp,modifier=Modifier.padding(top=6.dp,bottom=12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    item {Text("确认后将替换当前课表。请核对课程是否齐全、单双周与节次是否正确。",fontSize=12.sp,lineHeight=19.sp,color=Muted,modifier=Modifier.padding(bottom=12.dp))}
                    items(review.warnings) {Text(it,fontSize=12.sp,lineHeight=19.sp,modifier=Modifier.padding(bottom=12.dp))}
                    items(review.schedule.courses) {course->
                        Text(course.name,fontSize=15.sp)
                        Text("星期${dayNames[course.day-1]} · ${course.periodLabel} · ${course.weekText}",fontSize=12.sp,lineHeight=19.sp,color=Muted)
                        if(course.location.isNotBlank()) Text(course.location,fontSize=12.sp,color=Muted)
                        HorizontalDivider(Modifier.padding(vertical=12.dp),color=Rule)
                    }
                }
                Button(onClick=onImport,modifier=Modifier.fillMaxWidth()) {Text("确认导入 ${review.schedule.courses.size} 条")}
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {Text("返回检查")}
            }
        }
    }
}
