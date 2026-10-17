package com.matrix.launcher.system

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OPTIONAL remote AI client (OpenAI-compatible /chat/completions). It is used only when the user turns it on
 * in Settings and supplies their own HTTPS endpoint, model and API key. Only the typed question is sent -
 * no device data, app list or contacts. Nothing is sent otherwise. Call off the main thread.
 */
object RemoteAi {
    fun ask(baseUrl: String, apiKey: String, model: String, question: String): Result<String> = runCatching {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "ENDPOINT MUST START WITH https://" }
        require(apiKey.isNotBlank()) { "NO API KEY SAVED" }
        require(model.isNotBlank()) { "NO MODEL NAME SAVED" }

        val body = JSONObject()
            .put("model", model.trim())
            .put("max_tokens", 300)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are a concise assistant inside an Android launcher terminal. Answer in plain text, under 120 words."))
                    .put(JSONObject().put("role", "user").put("content", question.take(1000)))
            )
            .toString()

        val conn = URL("$base/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 12_000
            conn.readTimeout = 25_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) error("AI SERVICE ERROR HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } finally {
            conn.disconnect()
        }
    }
}
