package com.yinyuanyuan.timetable

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class ScheduleTest {
    @Test fun all59ObservedSchoolRecordsParse() {
        val text=javaClass.classLoader!!.getResourceAsStream("edge-anonymized-extraction.json")!!.bufferedReader().use {it.readText()}
        val schedule=ScheduleParser.parseExtraction(text)
        assertEquals(59,schedule.courses.size)
        assertEquals(4,schedule.courses.count {it.start==14})
        assertEquals(listOf(2,4,7,8,9,10,12,13,16,17),schedule.courses[8].weeks)
        assertEquals(4,schedule.courses[8].start)
        assertEquals(13,schedule.courses.maxOf {it.endRow})
    }
    @Test fun separateParityPerRange() {
        assertEquals(listOf(2,4,7,8,9,10,12,13,16,17),ScheduleParser.parseWeeks("2-4周(双),7-10周,12-13周,16-17周"))
    }
    @Test fun mixedOddAndEven() {
        assertEquals(listOf(2,4,6,7,9,12,13,15,16),ScheduleParser.parseWeeks("2-6周(双),7-9周(单),12-13周,15-16周"))
    }
    @Test fun unicodeAndWhitespace() {assertEquals(listOf(1,3,5,8,10),ScheduleParser.parseWeeks(" 1～5周（单）， 8、10周 "))}
    @Test fun gapsAndDeduplication() {assertEquals(listOf(1,2,3,5,6),ScheduleParser.parseWeeks("1-3周,3周,5-6周"))}
    @Test fun singleWeek() {assertEquals(listOf(9),ScheduleParser.parseWeeks("9周"))}
    @Test(expected=IllegalStateException::class) fun rejectUnknownWeek() {ScheduleParser.parseWeeks("待定")}
    @Test(expected=IllegalArgumentException::class) fun rejectReversedWeeks() {ScheduleParser.parseWeeks("8-2周")}
    @Test(expected=IllegalArgumentException::class) fun rejectOutOfRange() {ScheduleParser.parseWeeks("1-99周")}
    @Test(expected=IllegalStateException::class) fun rejectPartialGarbage() {ScheduleParser.parseWeeks("1-8周,待定")}
    @Test fun earlyReadingSortsFirst() {val c=Course("早读",1,14,14,listOf(1),"1周");assertEquals(0,c.startRow);assertEquals("早读",c.periodLabel)}
    @Test fun periodComesFromTimingNotCell() {assertEquals(Triple(4,5,"2-4周(双),7-10周"),ScheduleParser.parseTiming("(4-5节)2-4周(双),7-10周"))}
    @Test fun serializationRoundTrip() {val s=Demo.schedule();assertEquals(s,Schedule.fromJson(s.toJson()))}
    @Test fun beforeTermIsNotWeekOne() {assertEquals(0,teachingWeek(LocalDate.parse("2026-09-07"),LocalDate.parse("2026-09-06")))}
    @Test fun sundayAndMondayBoundary() {
        val start=LocalDate.parse("2026-09-07")
        assertEquals(1,teachingWeek(start,start.plusDays(6)));assertEquals(2,teachingWeek(start,start.plusDays(7)))
    }
    @Test fun overlappingCoursesPreserved() {
        val c=Course("A",1,1,3,listOf(1),"1周")
        val groups=courseGroups(listOf(c,c.copy(name="B",start=3,end=5),c.copy(name="C",start=6,end=7)))
        assertEquals(2,groups.size);assertEquals(2,groups.first().courses.size);assertEquals(5,groups.first().endRow)
    }
    @Test fun samePeriodsDifferentDaysNeverConflict() {
        val c=Course("A",1,1,3,listOf(1),"1周")
        assertEquals(2,courseGroups(listOf(c,c.copy(day=2))).size)
    }
    @Test fun onlyOfficialHttpsHosts() {
        assertTrue(School.allowed(School.PORTAL));assertTrue(School.canExtract(School.TIMETABLE))
        listOf("http://jwxt.gxtcmu.edu.cn/","https://gxtcmu.edu.cn.evil.test/","https://evilgxtcmu.edu.cn/","javascript:alert(1)","file:///data/private","https://user@jwxt.gxtcmu.edu.cn/","https://jwxt.gxtcmu.edu.cn:8443/").forEach {assertFalse(it,School.allowed(it))}
        assertFalse(School.canExtract(School.PORTAL))
    }
    private fun extracted(timing:String="(1-3节)1-5周,7-17周",scope:String="full")=JSONObject().put("detected",true).put("scope",scope).put("term","2026-2027学年第1学期")
        .put("rows",JSONArray().put(JSONObject().put("name","测试课程").put("day",1).put("timing",timing))).toString()
    @Test fun parseCompleteExtraction() {val s=ScheduleParser.parseExtraction(extracted());assertEquals(16,s.courses.single().weeks.size);assertEquals(3,s.courses.single().end)}
    @Test(expected=IllegalArgumentException::class) fun rejectWeekFilteredSource() {ScheduleParser.parseExtraction(extracted(scope="week"))}
    @Test(expected=IllegalArgumentException::class) fun staleSelectedTermCannotBeImported() {
        ScheduleParser.parseExtraction(JSONObject(extracted()).put("ready",false).toString())
    }
    @Test(expected=IllegalStateException::class) fun invalidRecordRejectsWholeImport() {ScheduleParser.parseExtraction(extracted("(1-3节)未知"))}
    @Test fun deduplicateExactRecords() {
        val json=JSONObject(extracted());val array=json.getJSONArray("rows");array.put(array.getJSONObject(0));assertEquals(1,ScheduleParser.parseExtraction(json.toString()).courses.size)
    }
    @Test fun calendarHintRequiresMatchingTerm() {
        val hint=CalendarHint("2026-2027学年第1学期",LocalDate.parse("2026-09-07"),"学校校历")
        assertTrue(hint.appliesTo("2026-2027学年 第1学期"));assertFalse(hint.appliesTo("2026-2027学年第2学期"));assertFalse(hint.appliesTo("我的课表"))
    }
    @Test fun inferMondayFromCurrentWeek() {
        val hint=CalendarHint.fromJson("""{"found":true,"mode":"currentWeek","term":"2026-2027学年第1学期","date":"2026-10-26","week":8}""",LocalDate.parse("2026-10-26"))
        assertEquals(LocalDate.parse("2026-09-07"),hint?.start)
    }
    @Test fun staleCalendarDoesNotSetFirstWeek() {
        assertNull(CalendarHint.fromJson("""{"found":true,"mode":"currentWeek","term":"2026-2027学年第1学期","date":"2026-10-25","week":8}""",LocalDate.parse("2026-10-26")))
    }
    @Test fun explicitMondayMustBeMonday() {
        assertNull(CalendarHint.fromJson("""{"found":true,"mode":"firstMonday","term":"2026-2027学年第1学期","date":"2026-09-08"}"""))
    }
}
