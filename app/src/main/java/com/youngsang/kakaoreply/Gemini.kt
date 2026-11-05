package com.youngsang.kakaoreply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {

    fun generate(c: Context, m: Msg): List<Reply> {
        val key = Prefs.apiKey(c)
        if (key.isBlank()) throw Exception("API 키가 없습니다. 앱을 열어 입력해 주세요.")
        val model = Prefs.model(c)

        val schema = JSONObject("""
            {"type":"ARRAY","items":{"type":"OBJECT",
             "properties":{"tone":{"type":"STRING"},"text":{"type":"STRING"}},
             "required":["tone","text"]}}
        """.trimIndent())

        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt(c, m))))
            ))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema)
                .put("temperature", 0.8))

        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val txt = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception(errorText(code, txt))
            return parse(txt)
        } finally {
            conn.disconnect()
        }
    }

    private fun prompt(c: Context, m: Msg): String {
        val sb = StringBuilder()
        sb.append("당신은 사용자의 휴대폰 메시지에 대한 답장을 대신 써 주는 비서입니다.\n")
        sb.append("아래 대화의 마지막 메시지(상대방이 보낸 것)에 대해, 사용자가 보낼 답장 후보 5개를 한국어로 만드세요.\n\n")
        sb.append("규칙:\n")
        sb.append("- 5개의 말투는 각각 다르게: 정중한, 친근한, 간단한, 업무용, 배려하는 (tone 칸에 이 이름을 적기)\n")
        sb.append("- 메시지 내용에 정확히 맞는 답을 쓰기. 질문이면 답하거나 확인하는 내용으로.\n")
        sb.append("- 모르는 사실(시간, 장소, 금액, 일정)은 지어내지 말고, 확인하거나 되묻는 표현을 쓰기.\n")
        sb.append("- 실제 메신저 답장처럼 자연스럽고 짧게(1~2문장). 따옴표나 설명 없이 답장 문장만.\n")
        sb.append("- 이전 대화에서 상대와의 관계(존댓말/반말)를 파악해 맞추기.\n\n")
        sb.append("앱: ${m.app}\n")
        sb.append("상대: ${m.sender}\n")
        if (m.room != null) sb.append("단체방 이름: ${m.room}\n")
        val style = Prefs.style(c).trim()
        if (style.isNotEmpty()) sb.append("사용자 정보·말투: $style\n")
        if (m.history.isNotEmpty()) {
            sb.append("\n[이전 대화]\n")
            m.history.forEach { sb.append(it).append('\n') }
        }
        sb.append("\n[답장할 메시지]\n${m.sender}: ${m.text}\n")
        return sb.toString()
    }

    private fun parse(txt: String): List<Reply> {
        val root = JSONObject(txt)
        val cands = root.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val reason = root.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
            throw Exception("AI가 답장을 만들지 않았습니다 $reason".trim())
        }
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception("AI 응답이 비어 있습니다")
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) out.append(p.optString("text", ""))
        }
        var s = out.toString().trim()
        if (s.startsWith("```")) s = s.substringAfter('\n').substringBeforeLast("```").trim()
        val arr = JSONArray(s)
        val list = mutableListOf<Reply>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.optString("text").trim()
            if (t.isNotEmpty()) list.add(Reply(o.optString("tone").trim(), t))
        }
        if (list.isEmpty()) throw Exception("AI 응답에 답장이 없습니다")
        return list.take(5)
    }

    private fun errorText(code: Int, txt: String): String {
        val msg = runCatching { JSONObject(txt).getJSONObject("error").optString("message") }.getOrDefault("")
        return when {
            txt.contains("API_KEY_INVALID") || code == 401 -> "API 키가 올바르지 않습니다"
            code == 403 -> "API 사용 권한이 없습니다 (키 또는 결제 설정 확인)"
            code == 404 -> "모델 이름을 찾을 수 없습니다. 설정의 모델 이름을 확인해 주세요"
            code == 429 -> "사용 한도를 넘었습니다. 잠시 후 다시 시도하거나 결제 설정을 확인해 주세요"
            code >= 500 -> "구글 서버 오류($code). 잠시 후 다시 시도해 주세요"
            else -> "오류 $code: $msg"
        }
    }
}
