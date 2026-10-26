package com.yinyuanyuan.timetable

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Course(
    val name: String, val day: Int, val start: Int, val end: Int,
    val weeks: List<Int>, val weekText: String, val location: String = "",
    val teacher: String = "", val className: String = "", val assessment: String = ""
) {
    val startRow: Int get() = if (start == 14) 0 else start
    val endRow: Int get() = if (end == 14) 0 else end
    val periodLabel: String get() = if (start == 14) "早读" else if (start == end) "第 $start 节" else "第 $start–$end 节"
    fun toJson() = JSONObject().put("name", name).put("day", day).put("start", start)
        .put("end", end).put("weeks", JSONArray(weeks)).put("weekText", weekText)
        .put("location", location).put("teacher", teacher).put("className", className).put("assessment", assessment)
}

data class Schedule(val term: String, val courses: List<Course>, val importedAt: Long = System.currentTimeMillis()) {
    val maxWeek get() = maxOf(20, courses.flatMap { it.weeks }.maxOrNull() ?: 20)
    fun forWeek(week: Int) = courses.filter { week in it.weeks }.sortedWith(compareBy({ it.day }, { it.startRow }, { it.name }))
    fun toJson() = JSONObject().put("version", 1).put("term", term).put("importedAt", importedAt)
        .put("courses", JSONArray(courses.map { it.toJson() })).toString()
    companion object {
        fun fromJson(text: String): Schedule {
            val obj = JSONObject(text)
            require(obj.optInt("version") == 1) { "不支持的课表版本" }
            val rows = obj.getJSONArray("courses")
            require(rows.length() in 1..2000) { "课表数据不完整" }
            val courses = (0 until rows.length()).map { i ->
                val r = rows.getJSONObject(i)
                val w = r.getJSONArray("weeks")
                Course(r.getString("name"), r.getInt("day"), r.getInt("start"), r.getInt("end"),
                    (0 until w.length()).map { w.getInt(it) }, r.getString("weekText"),
                    r.optString("location"), r.optString("teacher"), r.optString("className"), r.optString("assessment"))
                    .also { validateCourse(it) }
            }
            return Schedule(obj.optString("term", "我的课表"), courses, obj.optLong("importedAt"))
        }
    }
}

fun validateCourse(c: Course) {
    require(c.name.isNotBlank() && c.name.length <= 300) { "课程名称无效" }
    require(c.day in 1..7) { "星期超出范围" }
    require((c.start in 1..13 && c.end in c.start..13) || (c.start == 14 && c.end == 14)) { "无法识别节次" }
    require(c.weeks.isNotEmpty() && c.weeks.all { it in 1..60 }) { "无法识别周次" }
}

object ScheduleParser {
    private fun normalized(text: String) = text.replace('（', '(').replace('）', ')')
        .replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',')
        .replace('～', '-').replace('~', '-').replace('至', '-').replace('—', '-').replace('–', '-')
        .replace(Regex("\\s+"), "")

    fun parseWeeks(source: String): List<Int> {
        val text = normalized(source).removePrefix("第").trim(',')
        require(text.isNotEmpty()) { "周次为空" }
        val result = sortedSetOf<Int>()
        val pattern = Regex("^(\\d{1,2})(?:-(\\d{1,2}))?周?(?:\\((单|双)(?:周)?\\))?$")
        text.split(',').forEach { segment ->
            val m = pattern.matchEntire(segment.removePrefix("第")) ?: error("无法识别周次：$source")
            val from = m.groupValues[1].toInt()
            val to = m.groupValues[2].toIntOrNull() ?: from
            require(from in 1..60 && to in from..60) { "周次超出范围：$source" }
            val parity = m.groupValues[3]
            result.addAll((from..to).filter { parity.isEmpty() || (parity == "单" && it % 2 == 1) || (parity == "双" && it % 2 == 0) })
        }
        require(result.isNotEmpty()) { "没有有效周次：$source" }
        return result.toList()
    }

    fun parseTiming(timing: String): Triple<Int, Int, String> {
        val text = normalized(timing)
        val m = Regex("^\\((\\d{1,2})(?:-(\\d{1,2}))?节\\)(.+)$").matchEntire(text)
            ?: error("无法识别节次：$timing")
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: m.groupValues[1].toInt(), m.groupValues[3])
    }

    fun parseExtraction(json: String): Schedule {
        val root = JSONObject(json)
        require(root.optBoolean("detected")) { "请先进入个人课表，选择学年学期并点击查询" }
        require(root.optBoolean("ready",true)) { "学期正在切换，请点击查询并等待加载完成" }
        require(root.optString("scope") == "full") { "请切换到整学期课表后再导入" }
        val rows = root.getJSONArray("rows")
        require(rows.length() in 1..2000) { "课表为空，原课表已保留" }
        val result = (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i)
            val (start, end, weekText) = parseTiming(r.getString("timing"))
            Course(r.getString("name").trim(), r.getInt("day"), start, end, parseWeeks(weekText), weekText,
                r.optString("location").trim(), r.optString("teacher").trim(), r.optString("className").trim(), r.optString("assessment").trim())
                .also { validateCourse(it) }
        }.distinct()
        return Schedule(root.optString("term").ifBlank { "我的课表" }, result)
    }
}

object School {
    const val PORTAL = "https://ehall.gxtcmu.edu.cn/default/index.html#/"
    const val TIMETABLE = "https://jwxt.gxtcmu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508&layout=default"
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            (uri.host.equals("gxtcmu.edu.cn", true) || uri.host?.lowercase()?.endsWith(".gxtcmu.edu.cn") == true)
    }.getOrDefault(false)
    fun canExtract(url: String) = allowed(url) && runCatching { URI(url).host.equals("jwxt.gxtcmu.edu.cn", true) }.getOrDefault(false)
}

fun teachingWeek(start: LocalDate, date: LocalDate): Int = Math.floorDiv(ChronoUnit.DAYS.between(start, date), 7).toInt() + 1

data class CourseGroup(val courses: List<Course>) {
    val startRow get() = courses.minOf { it.startRow }
    val endRow get() = courses.maxOf { it.endRow }
}

/** Connected overlap groups keep every course accessible, even when the source conflicts. */
fun courseGroups(courses: List<Course>): List<CourseGroup> {
    val result = mutableListOf<MutableList<Course>>()
    courses.sortedWith(compareBy({ it.day }, { it.startRow })).forEach { course ->
        val last = result.lastOrNull()
        if (last != null && last.first().day == course.day && course.startRow <= last.maxOf { it.endRow }) last.add(course)
        else result.add(mutableListOf(course))
    }
    return result.map { CourseGroup(it) }
}

object Demo {
    fun schedule() = Schedule("示例学年 · 第 1 学期", listOf(
        Course("中医内科学（一）", 1, 1, 3, (1..18).toList(), "1-18周", "明秀校区 · 时珍107", "示例教师"),
        Course("针灸学", 1, 6, 7, (1..18).toList(), "1-18周", "明秀校区 · 西A-3", "示例教师"),
        Course("温病学", 2, 1, 3, (1..18).toList(), "1-18周", "明秀校区 · 时珍106", "示例教师"),
        Course("综合实训", 2, 6, 9, (1..18).toList(), "1-18周", "护理实训室", "示例教师"),
        Course("内科学", 3, 1, 3, (1..18).toList(), "1-18周", "明秀校区 · 西五", "示例教师"),
        Course("中医经典临床应用", 3, 4, 5, (1..18).toList(), "1-18周", "明秀校区 · 东梯二", "示例教师"),
        Course("急诊与灾难医学", 4, 1, 3, (1..18).toList(), "1-18周", "明秀校区 · 时珍104", "示例教师"),
        Course("针灸技能实训", 4, 6, 9, (1..18).toList(), "1-18周", "针灸技能实训室", "示例教师"),
        Course("中医内科学（一）", 5, 4, 5, (1..18).toList(), "1-18周", "明秀校区 · 西A-3", "示例教师"),
        Course("温病学（网络）", 2, 14, 14, (1..8).toList(), "1-8周", "超星SPOC", "示例教师"),
        Course("中医经典临床应用", 7, 10, 12, (1..18).toList(), "1-18周", "明秀校区 · 东梯二", "示例教师")
    ), 0)
}
