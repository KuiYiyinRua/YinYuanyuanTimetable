package com.yinyuanyuan.timetable

import org.json.JSONArray
import org.json.JSONObject

data class ImportSource(val json: String) {
    val data=JSONObject(json)
    init {
        require(json.length<=60000 && !data.optBoolean("oversized")) {"课表内容过长，请分学期识别"}
        require(data.optBoolean("detected")) {"没有找到课表表格，可改为粘贴课表文本识别"}
        require(data.optString("scope")=="full") {"请先选择整学期课表，再识别导入"}
        require(data.optBoolean("ready",true)) {"学期正在切换，请等待查询完成"}
    }
    val term: String get()=data.optString("term").ifBlank {"我的课表"}
    val minimumRecords: Int get()=data.optJSONArray("rows")?.length() ?: data.optJSONArray("cells")?.length() ?: 0
    fun local(): Schedule = if(data.has("rows")) ScheduleParser.parseExtraction(json) else CompatibilityParser.parse(this)
    companion object {
        fun text(value: String)=ImportSource(JSONObject().put("detected",true).put("scope","full").put("term","我的课表").put("text",value.trim()).toString())
    }
}

object CompatibilityParser {
    private fun normalized(text: String)=text.replace('（','(').replace('）',')').replace('～','-').replace('–','-').replace('至','-')
    fun parse(source: ImportSource): Schedule {
        val cells=source.data.optJSONArray("cells") ?: JSONArray().apply {
            source.data.optString("text").split(Regex("\n\\s*\n|\n(?=[^\n]*(?:周[一二三四五六日天]|星期[一二三四五六日天]))")).filter {it.isNotBlank()}.forEach {put(JSONObject().put("text",it))}
        }
        require(cells.length() in 1..300) {"没有可识别的课程文本"}
        val courses=(0 until cells.length()).map { index ->
            val cell=cells.getJSONObject(index)
            val text=normalized(cell.optString("text"))
            val day=cell.optInt("day").takeIf {it in 1..7} ?: Regex("(?:星期|周|礼拜)([一二三四五六日天])").find(text)?.groupValues?.get(1)?.let {"一二三四五六日".indexOf(it.replace("天","日"))+1} ?: 0
            val periodText=cell.optString("period").takeIf {it.isNotBlank()} ?: Regex("(?:第|\\(|\\[)?(\\d{1,2}(?:[-~]\\d{1,2})?)\\s*[)\\]]?节").find(text)?.groupValues?.get(1).orEmpty()
            val period=Regex("^(?:第)?\\s*(\\d{1,2})(?:\\s*[-~]\\s*(\\d{1,2}))?\\s*节?$").matchEntire(normalized(periodText)) ?: error("第 ${index+1} 条缺少可识别节次，可配置 DeepSeek 辅助识别")
            val weeks=cell.optString("weeks").takeIf {it.isNotBlank()} ?: Regex("(?:第)?\\d{1,2}(?:[-~]\\d{1,2})?周(?:\\([单双]周?\\))?").findAll(text).joinToString(",") {it.value}
            val name=cell.optString("name").ifBlank {text.lineSequence().firstOrNull {it.isNotBlank()}.orEmpty().substringBefore('|').substringBefore("星期").replace(Regex("周[一二三四五六日天].*$"),"")}
                .removePrefix("课程名称：").removePrefix("课程：").trim()
            Course(name,day,period.groupValues[1].toInt(),period.groupValues[2].toIntOrNull() ?: period.groupValues[1].toInt(),
                ScheduleParser.parseWeeks(weeks),weeks,cell.optString("location"),cell.optString("teacher")).also {validateCourse(it)}
        }.distinct()
        return Schedule(source.term,courses)
    }
}

data class ImportReview(val schedule: Schedule, val method: String, val warnings: List<String> = emptyList())

object AiImportValidation {
    fun validate(raw: String, source: ImportSource): ImportReview {
        require(raw.length<=200000) {"AI 返回内容过长"}
        val json=JSONObject(raw)
        val warnings=json.optJSONArray("warnings")?.let {array->(0 until minOf(array.length(),20)).map {array.optString(it).take(300)}} ?: emptyList()
        require(json.optBoolean("complete",false)) {"AI 无法确定完整课表：${warnings.firstOrNull() ?: "请补充周次、星期和节次"}"}
        val schedule=ScheduleParser.parseExtraction(JSONObject().put("detected",true).put("scope","full")
            .put("term",json.optString("term").ifBlank {source.term}).put("rows",json.getJSONArray("rows")).toString())
        require(schedule.courses.size>=source.minimumRecords) {"AI 返回的课程数量少于原始记录，请重新识别"}
        val normalizedSource=source.json.replace(Regex("\\s+"),"")
        schedule.courses.forEach {course->
            require(normalizedSource.contains(course.name.replace(Regex("\\s+"),""))) {"AI 课程名称不在原始课表中：${course.name.take(40)}"}
            val records=source.data.optJSONArray("rows") ?: source.data.optJSONArray("cells")
            val evidence=records?.let {array->(0 until array.length()).map {array.getJSONObject(it)}.filter {it.toString().contains(course.name)}}
            val evidenceText=evidence?.joinToString {it.toString()} ?: source.data.optString("text")
            val explicitWeek=evidence?.any {it.optString("weeks").isNotBlank()}==true ||
                Regex("\\d{1,2}\\s*(?:周|[（(]周)|周次\\s*[:：]\\s*\\d").containsMatchIn(evidenceText)
            require(explicitWeek) {"原始资料缺少 ${course.name.take(30)} 的明确周次，不能猜测导入"}
        }
        return ImportReview(schedule,"DeepSeek 辅助识别",warnings)
    }
}
