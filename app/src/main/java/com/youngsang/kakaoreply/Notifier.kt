package com.youngsang.kakaoreply

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

object Notifier {
    private const val CH = "replies"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            val ch = NotificationChannel(CH, "답장 추천", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "새 메시지에 대한 AI 답장 후보"
            nm.createNotificationChannel(ch)
        }
    }

    private fun nid(m: Msg) = m.convKey.hashCode()

    fun show(c: Context, m: Msg) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(c)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val open = PendingIntent.getActivity(
            c, m.id,
            Intent(c, ReplyActivity::class.java)
                .putExtra("id", m.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )

        val b = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(m.title())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)

        val body: String
        if (m.error != null) {
            body = "답장 생성 실패: ${m.error}"
        } else {
            body = "받은 메시지: ${m.text}\n\n" +
                m.replies.mapIndexed { i, r -> "${i + 1}. ${r.text}" }.joinToString("\n")
            m.replies.take(3).forEachIndexed { i, _ ->
                val pi = PendingIntent.getBroadcast(
                    c, m.id * 10 + i,
                    Intent(c, QuickSendReceiver::class.java)
                        .putExtra("id", m.id)
                        .putExtra("idx", i),
                    flags
                )
                b.addAction(Notification.Action.Builder(null as Icon?, "${i + 1}번 보내기", pi).build())
            }
        }
        b.setContentText(if (m.error != null) body else "답장 ${m.replies.size}개 준비됨 · 눌러서 보기")
        b.setStyle(Notification.BigTextStyle().bigText(body))

        c.getSystemService(NotificationManager::class.java).notify(nid(m), b.build())
    }

    fun cancel(c: Context, m: Msg) {
        c.getSystemService(NotificationManager::class.java).cancel(nid(m))
    }
}
