package com.youngsang.kakaoreply

import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

object Sender {

    /** 원본 알림(카톡/문자)의 답장 버튼을 통해 전송한다. 실패하면 클립보드에 복사한다. */
    fun send(c: Context, m: Msg, text: String): Boolean {
        val action = m.action
        val inputs = action?.remoteInputs
        if (action == null || inputs.isNullOrEmpty()) {
            copy(c, text)
            m.status = "전송 실패(복사됨)"
            return false
        }
        return try {
            val intent = Intent()
            val results = Bundle()
            for (ri in inputs) results.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            Store.rememberSent(text)
            action.actionIntent.send(c, 0, intent)
            m.status = "보냄: $text"
            true
        } catch (e: Exception) {
            copy(c, text)
            m.status = "전송 실패(복사됨)"
            false
        }
    }

    fun copy(c: Context, text: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("답장", text))
    }
}
