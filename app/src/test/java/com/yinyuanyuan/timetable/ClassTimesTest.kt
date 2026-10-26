package com.yinyuanyuan.timetable

import org.junit.Assert.*
import org.junit.Test

class ClassTimesTest {
    private fun course(start:Int,end:Int)=Course("测试课程",1,start,end,listOf(1),"1周")
    @Test fun mingxiuMatchesProvidedImage() {
        assertEquals(listOf("08:00–08:40","08:45–09:25","09:30–10:10","10:30–11:10","11:15–11:55","14:30–15:10","15:15–15:55","16:05–16:45","16:50–17:30"),
            TimeProfile.preset(Campus.MINGXIU).periods.toSortedMap().values.map {it.text})
    }
    @Test fun xianhuMatchesProvidedImage() {
        assertEquals(listOf("08:30–09:10","09:15–09:55","10:00–10:40","10:50–11:30","11:35–12:15","14:00–14:40","14:45–15:25","15:35–16:15","16:20–17:00"),
            TimeProfile.preset(Campus.XIANHU).periods.toSortedMap().values.map {it.text})
    }
    @Test fun consecutiveLessonsIncludeBreaks() {assertEquals("08:00–10:10",ClassTimes().active.forCourse(course(1,3)).text)}
    @Test fun doNotInventIndividualEveningTimes() {
        val profile=ClassTimes().active
        assertEquals("19:00–21:20",profile.forCourse(course(10,12)).text)
        assertTrue(profile.forCourse(course(11,12)).includesWholeEvening)
        assertNull(profile.periods[10]);assertNull(profile.periods[11]);assertNull(profile.periods[12])
    }
    @Test fun unprovidedPeriodsRemainUnknown() {
        assertNull(ClassTimes().active.forCourse(course(13,13)).time)
        assertNull(ClassTimes().active.forCourse(course(14,14)).time)
        assertTrue(ClassTimes().active.forCourse(course(10,13)).incomplete)
    }
    @Test fun individualEveningRequiresActualValues() {
        val profile=ClassTimes().active.copy(separateEvening=true)
        assertNull(profile.forCourse(course(10,12)).time)
        val edited=profile.withTime(TimeSlot(10,10,null),ClassTime.of("19:00","19:40"))
        assertEquals("19:00–19:40",edited.forCourse(course(10,10)).text)
        assertFalse(edited.forCourse(course(10,10)).includesWholeEvening)
    }
    @Test fun bothCampusEditsSurviveRoundTrip() {
        val original=ClassTimes()
        val ming=original.update(original.active.withTime(TimeSlot(1,1,null),ClassTime.of("08:05","08:40")))
        val xian=ming.copy(campus=Campus.XIANHU)
        val both=xian.update(xian.active.withTime(TimeSlot(1,1,null),ClassTime.of("08:35","09:10")))
        assertEquals(both,ClassTimes.fromJson(both.toJson()))
        assertEquals("08:05",both.copy(campus=Campus.MINGXIU).active.periods[1]!!.startText)
        assertEquals("08:05",both.resetCurrent().copy(campus=Campus.MINGXIU).active.periods[1]!!.startText)
        assertFalse(both.resetCurrent().customized)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectReversedClock() {ClassTime.of("09:00","08:00")}
    @Test fun outOfOrderEditsDoNotProduceFalseCourseRange() {
        val profile=ClassTimes().active.withTime(TimeSlot(1,1,null),ClassTime.of("10:00","10:40"))
        assertTrue(profile.forCourse(course(1,3)).invalidOrder)
    }
    @Test fun groupedTimeAxisPreservesAllGridRows() {assertEquals(14,ClassTimes().active.slots().sumOf {it.size})}
}
