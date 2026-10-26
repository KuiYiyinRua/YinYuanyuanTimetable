package com.yinyuanyuan.timetable

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.swipeWeeks(week: Int, lastWeek: Int, onWeek: (Int) -> Unit): Modifier = pointerInput(week,lastWeek) {
    var distance=0f
    val threshold=48.dp.toPx()
    detectHorizontalDragGestures(
        onDragStart={distance=0f},
        onDragCancel={distance=0f},
        onDragEnd={
            if(abs(distance)>=threshold) onWeek((week+if(distance<0) 1 else -1).coerceIn(1,lastWeek))
            distance=0f
        },
        onHorizontalDrag={change,amount->change.consume();distance+=amount}
    )
}
