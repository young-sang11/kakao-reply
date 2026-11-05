package com.youngsang.kakaoreply

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class ListenerService : NotificationListenerService() {

    companion object {
        const val KAKAO = "com.kakao.talk"
        val SMS_APPS = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
        )
        @Volatile var connected = false
    }

    private val pool = Executors.newFixedThreadPool(3)
    private val seen = LinkedHashSet<String>()

    override fun onListenerConnected() {
        connected = true
        Notifier.ensureChannel(this)
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Throwable) {
            Log.w("KakaoReply", "알림 처리 실패", e)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val app = when {
            pkg == KAKAO && Prefs.kakaoOn(this) -> "카톡"
            pkg in SMS_APPS && Prefs.smsOn(this) -> "문자"
            else -> return
        }
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 답장 버튼이 있는 알림만 메시지로 본다
        val action = n.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: return

        var sender: String
        var text: String
        var room: String? = null
        var stamp = 0L
        val history = mutableListOf<String>()

        val ms = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (ms != null && ms.messages.isNotEmpty()) {
            val last = ms.messages.last()
            val me = ms.user.name?.toString()
            val who = last.person?.name?.toString()
            if (last.person == null || (me != null && who == me)) return // 내가 보낸 메시지
            sender = who ?: "상대"
            text = last.text?.toString() ?: return
            stamp = last.timestamp
            if (ms.isGroupConversation) room = ms.conversationTitle?.toString()
            ms.messages.dropLast(1).takeLast(8).forEach {
                val name = it.person?.name?.toString() ?: "나"
                history.add("$name: ${it.text}")
            }
        } else {
            val ex = n.extras
            sender = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
            room = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            stamp = n.`when`
        }

        text = text.trim()
        if (text.isEmpty()) return
        if (Store.recentSent.contains(text)) return

        val sig = "${sbn.key}|$text|$stamp"
        synchronized(seen) {
            if (!seen.add(sig)) return
            if (seen.size > 300) seen.remove(seen.first())
        }

        val m = Store.add(sbn.key, app, sender, room, text, history, action)
        Store.changed()

        pool.execute {
            try {
                m.replies = Gemini.generate(this, m)
                m.error = null
                m.status = "답장 준비됨"
            } catch (e: Exception) {
                m.error = e.message ?: "알 수 없는 오류"
                m.status = "오류"
            }
            Store.changed()
            Notifier.show(this, m)
        }
    }
}
