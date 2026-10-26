package com.yinyuanyuan.timetable

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekClient(context: Context, private val key: String) {
    private val prompt=context.assets.open("deepseek-import-prompt.txt").bufferedReader().use {it.readText()}
    suspend fun identify(source: ImportSource): ImportReview {
        require(key.isNotBlank()) {"未配置 DeepSeek Key"}
        val messages=JSONArray().put(JSONObject().put("role","system").put("content",prompt))
            .put(JSONObject().put("role","user").put("content",source.json))
        var lastError="无法识别课表"
        repeat(2) {attempt->
            val raw=complete(messages)
            val checked=runCatching {AiImportValidation.validate(raw,source)}
            checked.getOrNull()?.let {return it}
            lastError=checked.exceptionOrNull()?.message ?: lastError
            if(attempt==0) {
                messages.put(JSONObject().put("role","assistant").put("content",raw))
                messages.put(JSONObject().put("role","user").put("content","校验失败：${lastError.take(600)}。请核对原始资料，修正并返回完整 JSON；缺失的信息不要猜测。"))
            }
        }
        error("AI 结果未通过校验：$lastError")
    }
    private suspend fun complete(messages: JSONArray): String = withContext(Dispatchers.IO) {
        val connection=URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod="POST"
            connection.instanceFollowRedirects=false
            connection.connectTimeout=20_000
            connection.readTimeout=90_000
            connection.setRequestProperty("Authorization","Bearer $key")
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            connection.doOutput=true
            val body=JSONObject().put("model","deepseek-flash").put("messages",messages)
                .put("thinking",JSONObject().put("type","disabled"))
                .put("response_format",JSONObject().put("type","json_object"))
                .put("temperature",0).put("max_tokens",16384).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use {it.write(body)}
            val code=connection.responseCode
            require(code==200) {when(code){401,403->"DeepSeek Key 无效或无访问权限";402->"DeepSeek 账户余额不足";429->"DeepSeek 请求较多，请稍后重试";else->"DeepSeek 暂不可用（HTTP $code）"}}
            val text=connection.inputStream.bufferedReader().use {reader->
                val output=StringBuilder()
                val buffer=CharArray(4096)
                while(true) {val read=reader.read(buffer);if(read<0) break;output.append(buffer,0,read);require(output.length<=1_000_000){"AI 响应过大"}}
                output.toString()
            }
            val choice=JSONObject(text).getJSONArray("choices").getJSONObject(0)
            require(choice.optString("finish_reason")=="stop") {"AI 输出未完整结束，请缩小课表范围后重试"}
            choice.getJSONObject("message").getString("content").also {require(it.isNotBlank()){ "AI 返回空内容，请重试" }}
        } finally {connection.disconnect()}
    }
}

object ImportEngine {
    suspend fun recognize(source: ImportSource, hasKey: Boolean, agentEnabled: Boolean,
        agent: suspend (ImportSource) -> ImportReview): ImportReview {
        val local=runCatching {source.local()}
        if(!hasKey || !agentEnabled) return ImportReview(local.getOrElse {throw IllegalArgumentException("本地识别失败：${it.message}。可修改文本或配置 DeepSeek 辅助识别。")},"本地规则识别")
        return try {
            val result=agent(source)
            val baseline=local.getOrNull()
            val differs=baseline!=null && baseline.courses.toSet()!=result.schedule.courses.toSet()
            if(differs) result.copy(warnings=listOf("AI 结果与本地规则有差异，请逐条核对时间与周次。")+result.warnings) else result
        } catch (cancel: kotlinx.coroutines.CancellationException) {throw cancel}
        catch (error: Exception) {
            val baseline=local.getOrNull() ?: throw error
            ImportReview(baseline,"本地识别（AI 未完成）",listOf("${error.message?.take(180) ?: "AI 暂不可用"}。以下为本地结果，请检查后确认。"))
        }
    }
}
