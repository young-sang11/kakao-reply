package com.youngsang.kakaoreply

import android.app.Notification
import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

class Reply(val tone: String, val text: String)

/** 들어온 메시지 하나. 원본 알림의 답장 버튼(action)을 함께 보관해 답장이 다른 방으로 섞이지 않게 한다. */
class Msg(
    val id: Int,
    val convKey: String,
    val app: String,
    val sender: String,
    val room: String?,
    val text: String,
    val history: List<String>,
    val time: Long,
    val action: Notification.Action?,
) {
    @Volatile var replies: List<Reply> = emptyList()
    @Volatile var error: String? = null
    @Volatile var status: String = "답장 만드는 중"

    fun title(): String = "$app · $sender" + (room?.let { " ($it)" } ?: "")
}

object Store {
    private var nextId = 1
    private val msgs = mutableListOf<Msg>()
    val listeners = CopyOnWriteArrayList<() -> Unit>()
    val recentSent = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Synchronized
    fun add(
        convKey: String, app: String, sender: String, room: String?,
        text: String, history: List<String>, action: Notification.Action?,
    ): Msg {
        val m = Msg(nextId++, convKey, app, sender, room, text, history, System.currentTimeMillis(), action)
        msgs.add(0, m)
        while (msgs.size > 50) msgs.removeAt(msgs.size - 1)
        return m
    }

    @Synchronized fun get(id: Int): Msg? = msgs.firstOrNull { it.id == id }
    @Synchronized fun all(): List<Msg> = msgs.toList()

    fun rememberSent(text: String) {
        recentSent.add(text.trim())
        while (recentSent.size > 30) recentSent.removeAt(0)
    }

    fun changed() = listeners.forEach { runCatching { it() } }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    const val DEFAULT_MODEL = "gemini-flash-latest"

    fun apiKey(c: Context) = sp(c).getString("apiKey", "")!!.trim()
    fun model(c: Context) = sp(c).getString("model", DEFAULT_MODEL)!!.trim().ifBlank { DEFAULT_MODEL }
    fun style(c: Context) = sp(c).getString("style", "")!!
    fun kakaoOn(c: Context) = sp(c).getBoolean("kakao", true)
    fun smsOn(c: Context) = sp(c).getBoolean("sms", true)

    fun save(c: Context, apiKey: String, model: String, style: String, kakao: Boolean, sms: Boolean) {
        sp(c).edit()
            .putString("apiKey", apiKey.trim())
            .putString("model", model.trim())
            .putString("style", style)
            .putBoolean("kakao", kakao)
            .putBoolean("sms", sms)
            .apply()
    }
}
