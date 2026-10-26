package com.yinyuanyuan.timetable

import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Campus(val label: String) { MINGXIU("明秀校区"), XIANHU("仙葫校区") }

data class ClassTime(val start: LocalTime, val end: LocalTime) {
    init { require(start < end) { "下课时间必须晚于上课时间" } }
    val startText: String get() = start.format(clockFormat)
    val endText: String get() = end.format(clockFormat)
    val text: String get() = "$startText–$endText"
    fun toJson(): JSONObject = JSONObject().put("start", startText).put("end", endText)
    companion object {
        private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
        fun of(start: String, end: String) = ClassTime(LocalTime.parse(start), LocalTime.parse(end))
        fun fromJson(json: JSONObject) = of(json.getString("start"), json.getString("end"))
    }
}

data class TimeSlot(val first: Int, val last: Int, val time: ClassTime?) {
    val label: String get() = when {
        first == 14 -> "早读"
        first == last -> "第 $first 节"
        else -> "第 $first–$last 节"
    }
    val size: Int get() = last - first + 1
}

data class CourseClock(val time: ClassTime?, val includesWholeEvening: Boolean = false, val incomplete: Boolean = false, val invalidOrder: Boolean = false) {
    val text: String get() = when {
        invalidOrder -> "节次时间顺序有冲突，请检查上课时间设置"
        time == null -> if (incomplete) "部分节次时间未设置" else "未设置上课时间"
        includesWholeEvening -> "${time.text}（含晚间整段）"
        else -> time.text
    }
}

data class TimeProfile(
    val periods: Map<Int, ClassTime>,
    val evening: ClassTime? = ClassTime.of("19:00", "21:20"),
    val separateEvening: Boolean = false
) {
    init { require(periods.keys.all { it in 1..14 }) }

    fun slots(includeEarly: Boolean = true): List<TimeSlot> = buildList {
        if (includeEarly) add(TimeSlot(14, 14, periods[14]))
        (1..9).forEach { add(TimeSlot(it, it, periods[it])) }
        if (separateEvening) (10..12).forEach { add(TimeSlot(it, it, periods[it])) }
        else add(TimeSlot(10, 12, evening))
        add(TimeSlot(13, 13, periods[13]))
    }

    fun withTime(slot: TimeSlot, time: ClassTime?): TimeProfile = if (slot.first == 10 && slot.last == 12) {
        copy(evening = time)
    } else {
        copy(periods = periods.toMutableMap().apply { if (time == null) remove(slot.first) else put(slot.first, time) }.toMap())
    }

    fun forCourse(course: Course): CourseClock {
        val slots = if (course.start == 14) listOf(TimeSlot(14, 14, periods[14]))
        else slots(false).filter { it.first <= course.end && it.last >= course.start }
        val known = slots.mapNotNull { it.time }
        if (known.size != slots.size || known.isEmpty()) return CourseClock(null, incomplete = known.isNotEmpty())
        // Do not report a false envelope when independently edited periods are out of order.
        if (known.zipWithNext().any { (a, b) -> a.end > b.start }) return CourseClock(null, invalidOrder = true)
        val wholeEvening = slots.any { it.size > 1 && (course.start > it.first || course.end < it.last) }
        return CourseClock(ClassTime(known.first().start, known.last().end), wholeEvening)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("periods", JSONObject().apply { periods.forEach { (period, time) -> put(period.toString(), time.toJson()) } })
        put("evening", evening?.toJson() ?: JSONObject.NULL)
        put("separateEvening", separateEvening)
    }

    companion object {
        fun preset(campus: Campus): TimeProfile {
            val times = when (campus) {
                Campus.MINGXIU -> listOf("08:00/08:40", "08:45/09:25", "09:30/10:10", "10:30/11:10", "11:15/11:55",
                    "14:30/15:10", "15:15/15:55", "16:05/16:45", "16:50/17:30")
                Campus.XIANHU -> listOf("08:30/09:10", "09:15/09:55", "10:00/10:40", "10:50/11:30", "11:35/12:15",
                    "14:00/14:40", "14:45/15:25", "15:35/16:15", "16:20/17:00")
            }
            return TimeProfile(times.mapIndexed { index, value ->
                val (start, end) = value.split('/')
                (index + 1) to ClassTime.of(start, end)
            }.toMap())
        }

        fun fromJson(json: JSONObject): TimeProfile {
            val rows = json.getJSONObject("periods")
            val periods = rows.keys().asSequence().associate { key -> key.toInt() to ClassTime.fromJson(rows.getJSONObject(key)) }
            return TimeProfile(periods, if (json.isNull("evening")) null else ClassTime.fromJson(json.getJSONObject("evening")),
                json.getBoolean("separateEvening"))
        }
    }
}

data class ClassTimes(
    val campus: Campus = Campus.MINGXIU,
    val profiles: Map<Campus, TimeProfile> = Campus.entries.associateWith { TimeProfile.preset(it) }
) {
    init { require(profiles.keys == Campus.entries.toSet()) }
    val active: TimeProfile get() = profiles.getValue(campus)
    val customized: Boolean get() = active != TimeProfile.preset(campus)
    val description: String get() = "${campus.label} · ${if (customized) "自定义" else "预设"}"
    fun update(profile: TimeProfile) = copy(profiles = profiles + (campus to profile))
    fun resetCurrent() = update(TimeProfile.preset(campus))
    fun toJson(): String = JSONObject().put("version", 1).put("campus", campus.name)
        .put("profiles", JSONObject().apply { profiles.forEach { (campus, profile) -> put(campus.name, profile.toJson()) } }).toString()

    companion object {
        fun fromJson(text: String): ClassTimes {
            val json = JSONObject(text)
            require(json.getInt("version") == 1)
            val profiles = json.getJSONObject("profiles")
            return ClassTimes(Campus.valueOf(json.getString("campus")), Campus.entries.associateWith { TimeProfile.fromJson(profiles.getJSONObject(it.name)) })
        }
    }
}
