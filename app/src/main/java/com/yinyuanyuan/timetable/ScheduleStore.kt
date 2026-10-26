package com.yinyuanyuan.timetable

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate

class ScheduleStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "timetable.json"))
    private val preferences = context.getSharedPreferences("timetable_settings", Context.MODE_PRIVATE)
    var readError: String? = null
        private set
    fun load(): Schedule? {
        readError = null
        if (!file.baseFile.exists()) return null
        return try { Schedule.fromJson(file.openRead().bufferedReader().use { it.readText() }) }
        catch (_: Exception) { readError = "本地课表暂时无法读取，请重新导入。原文件已保留。"; null }
    }
    fun save(schedule: Schedule) {
        val old = load()
        val bytes = schedule.toJson().toByteArray(Charsets.UTF_8)
        // Validate the complete serialization before replacing any local timetable.
        Schedule.fromJson(bytes.toString(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        if (old != null && old.term != schedule.term) startMonday = null
    }
    var startMonday: LocalDate?
        get() = preferences.getString("start_monday", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) { preferences.edit().putString("start_monday", value?.toString()).apply() }
    var selectedWeek: Int
        get() = preferences.getInt("week", 1)
        set(value) { preferences.edit().putInt("week", value).apply() }
    var classTimes: ClassTimes
        get() = preferences.getString("class_times_v1", null)?.let { runCatching { ClassTimes.fromJson(it) }.getOrNull() } ?: ClassTimes()
        set(value) {
            val json=value.toJson()
            ClassTimes.fromJson(json)
            preferences.edit().putString("class_times_v1", json).apply()
        }
    fun clearSchedule() { file.delete(); preferences.edit().remove("week").remove("start_monday").apply() }
}
