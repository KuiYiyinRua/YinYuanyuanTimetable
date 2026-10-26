package com.yinyuanyuan.timetable

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A hint must name the same academic year and term. Never infer from September 1. */
data class CalendarHint(val term:String,val start:LocalDate,val evidence:String) {
    fun appliesTo(otherTerm:String):Boolean {
        fun key(value:String)=Regex("(20\\d{2})\\D+(20\\d{2}).*?第?([123一二三])学期").find(value.replace(" ",""))?.let {
            it.groupValues.drop(1).map { v->when(v){"一"->"1";"二"->"2";"三"->"3";else->v} }
        }
        return key(term)!=null && key(term)==key(otherTerm)
    }
    companion object {
        fun fromJson(text:String,today:LocalDate=LocalDate.now()):CalendarHint? = runCatching {
            val obj=JSONObject(text)
            if(!obj.optBoolean("found")) return null
            val source=LocalDate.parse(obj.getString("date"))
            val start=when(obj.getString("mode")) {
                "firstMonday" -> { require(source.dayOfWeek==DayOfWeek.MONDAY);source }
                "currentWeek" -> {
                    require(source==today) { "学校日期与今天不符" }
                    val week=obj.getInt("week");require(week in 1..60)
                    source.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((week-1).toLong())
                }
                else->return null
            }
            CalendarHint(obj.getString("term"),start,obj.optString("evidence"))
        }.getOrNull()
    }
}
