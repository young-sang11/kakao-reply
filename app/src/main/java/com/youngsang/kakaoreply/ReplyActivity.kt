package com.youngsang.kakaoreply

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 알림을 눌렀을 때: 답장 5개 보기 → 골라서(수정 가능) 보내기 */
class ReplyActivity : Activity() {

    private var m: Msg? = null
    private lateinit var statusTv: TextView
    private lateinit var box: LinearLayout
    private lateinit var edit: EditText

    private val listener: () -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    override fun onResume() {
        super.onResume()
        Store.listeners.add(listener)
    }

    override fun onPause() {
        super.onPause()
        Store.listeners.remove(listener)
    }

    private fun load(i: Intent) {
        val msg = Store.get(i.getIntExtra("id", -1))
        m = msg
        val col = Ui.column(this)
        val scroll = ScrollView(this).apply { fitsSystemWindows = true }
        scroll.addView(col)
        setContentView(scroll)

        if (msg == null) {
            col.addView(Ui.text(this, "메시지를 찾을 수 없습니다.\n앱이 재시작되면 목록이 비워집니다. 카톡에서 직접 답장해 주세요.", 16f))
            return
        }

        col.addView(Ui.text(this, msg.title(), 18f, true))
        if (msg.history.isNotEmpty()) {
            col.addView(Ui.text(this, msg.history.joinToString("\n"), 13f))
        }
        val orig = Ui.card(this, 0x33FEE500)
        orig.addView(Ui.text(this, msg.text, 17f))
        col.addView(orig)

        statusTv = Ui.text(this, "", 13f)
        col.addView(statusTv)
        col.addView(Ui.text(this, "답장 후보 (눌러서 선택)", 15f, true))
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(box)

        edit = EditText(this).apply {
            hint = "답장을 고르거나 직접 입력"
            minLines = 2
        }
        col.addView(edit)
        col.addView(Ui.button(this, "보내기") { sendNow() })
        col.addView(Ui.button(this, "답장 다시 만들기") { regenerate() })
        col.addView(Ui.button(this, "복사만 하기") {
            val t = edit.text.toString().trim()
            if (t.isNotEmpty()) {
                Sender.copy(this, t)
                Toast.makeText(this, "복사했습니다", Toast.LENGTH_SHORT).show()
            }
        })
        render()
    }

    private fun render() {
        val msg = m ?: return
        if (!::box.isInitialized) return
        statusTv.text = msg.error?.let { "오류: $it" } ?: msg.status
        box.removeAllViews()
        msg.replies.forEachIndexed { i, r ->
            val card = Ui.card(this)
            card.addView(Ui.text(this, "${i + 1}. ${r.tone}", 12f, true))
            card.addView(Ui.text(this, r.text, 16f))
            card.setOnClickListener {
                edit.setText(r.text)
                edit.setSelection(r.text.length)
            }
            box.addView(card)
        }
        if (msg.replies.isNotEmpty() && edit.text.isEmpty()) {
            edit.setText(msg.replies[0].text)
            edit.setSelection(msg.replies[0].text.length)
        }
    }

    private fun sendNow() {
        val msg = m ?: return
        val t = edit.text.toString().trim()
        if (t.isEmpty()) {
            Toast.makeText(this, "답장을 고르거나 입력해 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = Sender.send(this, msg, t)
        Notifier.cancel(this, msg)
        Store.changed()
        if (ok) {
            Toast.makeText(this, "보냈습니다", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "전송 실패 — 복사했습니다. 카톡에 붙여넣어 보내 주세요.", Toast.LENGTH_LONG).show()
            render()
        }
    }

    private fun regenerate() {
        val msg = m ?: return
        msg.status = "다시 만드는 중..."
        msg.error = null
        render()
        Thread {
            try {
                msg.replies = Gemini.generate(this, msg)
                msg.status = "답장 준비됨"
            } catch (e: Exception) {
                msg.error = e.message ?: "알 수 없는 오류"
            }
            runOnUiThread {
                edit.setText("")
                render()
            }
        }.start()
    }
}
