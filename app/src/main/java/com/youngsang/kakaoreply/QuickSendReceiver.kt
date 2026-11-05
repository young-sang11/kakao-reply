package com.youngsang.kakaoreply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 알림의 "1번 보내기" 같은 버튼을 눌렀을 때 바로 전송 */
class QuickSendReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val m = Store.get(intent.getIntExtra("id", -1))
        if (m == null) {
            Toast.makeText(c, "메시지를 찾을 수 없습니다. 카톡에서 직접 답장해 주세요.", Toast.LENGTH_LONG).show()
            return
        }
        val r = m.replies.getOrNull(intent.getIntExtra("idx", -1)) ?: return
        val ok = Sender.send(c, m, r.text)
        Notifier.cancel(c, m)
        Store.changed()
        Toast.makeText(
            c,
            if (ok) "보냈습니다: ${r.text}" else "전송 실패 — 답장을 복사했습니다. 붙여넣어 보내 주세요.",
            Toast.LENGTH_LONG
        ).show()
    }
}
