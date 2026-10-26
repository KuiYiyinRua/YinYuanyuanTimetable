package com.yinyuanyuan.timetable

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompatibilityImportTest {
    private fun source()=ImportSource(JSONObject().put("detected",true).put("scope","full").put("term","测试学期")
        .put("cells",JSONArray().put(JSONObject().put("day",2).put("period","3-4").put("text","大学英语\n1-16周(双)"))).toString())
    private fun answer(name:String="大学英语")=JSONObject().put("complete",true).put("term","测试学期")
        .put("rows",JSONArray().put(JSONObject().put("name",name).put("day",2).put("timing","(3-4节)1-16周(双)"))).toString()
    @Test fun genericCellsKeepParityAndPeriods() {
        val parsed=source().local().courses.single()
        assertEquals(2,parsed.day);assertEquals(3,parsed.start);assertEquals(4,parsed.end)
        assertEquals(listOf(2,4,6,8,10,12,14,16),parsed.weeks)
    }
    @Test fun pastedTextCanWorkWithoutAi() {
        val parsed=ImportSource.text("大学英语 | 周一 第1-2节 1-16周\n\n高等数学 | 周二 第3-4节 2-16周(双)").local()
        assertEquals(2,parsed.courses.size);assertEquals("大学英语",parsed.courses[0].name)
    }
    @Test fun missingKeyNeverCallsAgent()=runBlocking {
        var calls=0
        val result=ImportEngine.recognize(source(),false,true) {calls++;error("must not call")}
        assertEquals(0,calls);assertEquals(1,result.schedule.courses.size)
    }
    @Test fun disabledAgentNeverCallsNetwork()=runBlocking {
        var calls=0
        ImportEngine.recognize(source(),true,false) {calls++;error("must not call")}
        assertEquals(0,calls)
    }
    @Test fun configuredAgentUsedAutomatically()=runBlocking {
        var calls=0
        ImportEngine.recognize(source(),true,true) {calls++;AiImportValidation.validate(answer(),it)}
        assertEquals(1,calls)
    }
    @Test fun aiFailureLeavesLocalResultForReview()=runBlocking {
        val result=ImportEngine.recognize(source(),true,true) {error("网络不可用")}
        assertEquals(1,result.schedule.courses.size);assertTrue(result.warnings.isNotEmpty())
    }
    @Test(expected=IllegalArgumentException::class) fun hallucinatedNameRejected() {AiImportValidation.validate(answer("不存在的课程"),source())}
    @Test(expected=IllegalArgumentException::class) fun incompleteAiResultRejected() {AiImportValidation.validate(JSONObject(answer()).put("complete",false).toString(),source())}
    @Test(expected=IllegalArgumentException::class) fun filteredWeekCannotReachAi() {ImportSource("""{"detected":true,"scope":"week","rows":[]}""")}
    @Test(expected=IllegalArgumentException::class) fun missingWeekDoesNotDefaultToWholeTerm() {ImportSource.text("大学英语 | 周一 第1-2节").local()}
    @Test(expected=IllegalArgumentException::class) fun aiCannotFillMissingWeekFromThinAir() {AiImportValidation.validate(answer(),ImportSource.text("大学英语 周二 第3-4节"))}
}
