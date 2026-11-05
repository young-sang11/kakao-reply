package com.youngsang.kakaoreply

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var style: EditText
    private lateinit var kakao: CheckBox
    private lateinit var sms: CheckBox
    private lateinit var testOut: TextView

    private val listener: () -> Unit = { runOnUiThread { refreshList() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        val col = Ui.column(this)

        col.addView(Ui.text(this, "카톡·문자 AI 답장", 22f, true))
        col.addView(Ui.text(this, "새 메시지가 오면 답장 후보를 알림으로 보여 드립니다. 하나를 누르면 바로 보내집니다.", 14f))

        // 1. 권한
        col.addView(Ui.text(this, "\n1. 권한 설정", 18f, true))
        status = Ui.text(this, "", 15f)
        col.addView(status)
        col.addView(Ui.button(this, "① 알림 접근 허용하기") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(Ui.text(this,
            "※ '제한된 설정' 안내가 나오면: 아래 ④ 앱 정보 → 오른쪽 위 ⋮ → '제한된 설정 허용' 후 다시 ①을 누르세요.", 13f))
        col.addView(Ui.button(this, "② 알림 표시 허용하기") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        col.addView(Ui.button(this, "③ 배터리 최적화에서 제외하기") {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }.onFailure { Toast.makeText(this, "설정을 열 수 없습니다", Toast.LENGTH_SHORT).show() }
        })
        col.addView(Ui.button(this, "④ 앱 정보 열기") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        // 2. AI 설정
        col.addView(Ui.text(this, "\n2. AI 설정", 18f, true))
        col.addView(Ui.text(this, "Gemini API 키", 14f))
        key = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "AIza로 시작하는 키"
            setText(Prefs.apiKey(this@MainActivity))
        }
        col.addView(key)
        col.addView(Ui.text(this, "모델 이름 (보통 그대로 두세요)", 14f))
        model = EditText(this).apply {
            isSingleLine = true
            setText(Prefs.model(this@MainActivity))
        }
        col.addView(model)
        col.addView(Ui.text(this, "내 정보·말투 (선택)", 14f))
        style = EditText(this).apply {
            hint = "예: 50대 남성, 디자인 일을 함. 지인에게는 존댓말, 짧게 답함"
            minLines = 2
            setText(Prefs.style(this@MainActivity))
        }
        col.addView(style)
        kakao = CheckBox(this).apply { text = "카카오톡 메시지 받기"; isChecked = Prefs.kakaoOn(this@MainActivity) }
        sms = CheckBox(this).apply { text = "문자 메시지 받기"; isChecked = Prefs.smsOn(this@MainActivity) }
        col.addView(kakao)
        col.addView(sms)
        col.addView(Ui.button(this, "저장") {
            save()
            Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
        })
        col.addView(Ui.button(this, "AI 연결 테스트") { test() })
        testOut = Ui.text(this, "", 14f)
        col.addView(testOut)

        // 3. 최근 메시지
        col.addView(Ui.text(this, "\n3. 최근 메시지", 18f, true))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)

        col.addView(Ui.text(this,
            "\n알아두실 점\n" +
                "• 카톡 PC가 켜져 있으면 폰에 알림이 오지 않아 작동하지 않을 수 있습니다.\n" +
                "• 카톡 설정 → 알림 → '메시지 미리보기'가 켜져 있어야 내용을 읽을 수 있습니다.\n" +
                "• 사진·이모티콘은 내용을 알 수 없습니다.\n" +
                "• 앱이 재시작되면 최근 메시지 목록은 비워집니다.", 13f))

        val scroll = ScrollView(this).apply { fitsSystemWindows = true }
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Store.listeners.add(listener)
        refreshStatus()
        refreshList()
    }

    override fun onPause() {
        super.onPause()
        Store.listeners.remove(listener)
        save()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun save() {
        Prefs.save(this, key.text.toString(), model.text.toString(), style.text.toString(), kakao.isChecked, sms.isChecked)
    }

    private fun refreshStatus() {
        val access = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val post = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val hasKey = Prefs.apiKey(this).isNotBlank()
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        status.text = "${mark(access)} 알림 접근\n${mark(post)} 알림 표시\n${mark(battery)} 배터리 최적화 제외\n${mark(hasKey)} API 키"
    }

    private fun test() {
        save()
        testOut.text = "테스트 중..."
        val m = Msg(-1, "test", "카톡", "김철수", null, "내일 점심 몇 시에 볼까요?", emptyList(), System.currentTimeMillis(), null)
        Thread {
            val result = try {
                val r = Gemini.generate(this, m)
                "✅ 연결 성공 (예시: \"내일 점심 몇 시에 볼까요?\")\n" +
                    r.mapIndexed { i, x -> "${i + 1}. [${x.tone}] ${x.text}" }.joinToString("\n")
            } catch (e: Exception) {
                "❌ ${e.message}"
            }
            runOnUiThread { testOut.text = result; refreshStatus() }
        }.start()
    }

    private fun refreshList() {
        list.removeAllViews()
        val msgs = Store.all()
        if (msgs.isEmpty()) {
            list.addView(Ui.text(this, "아직 받은 메시지가 없습니다.", 14f))
            return
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.KOREA)
        for (m in msgs.take(20)) {
            val card = Ui.card(this)
            card.addView(Ui.text(this, "${m.title()}  ${fmt.format(Date(m.time))}", 14f, true))
            card.addView(Ui.text(this, m.text, 15f))
            card.addView(Ui.text(this, m.error?.let { "오류: $it" } ?: m.status, 12f))
            card.setOnClickListener {
                startActivity(Intent(this, ReplyActivity::class.java).putExtra("id", m.id))
            }
            list.addView(card)
        }
    }
}
