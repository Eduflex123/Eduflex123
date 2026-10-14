package com.givayo.futboltahmin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("futbol_tahmin", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val green = Color.rgb(17, 105, 76)
    private val ink = Color.rgb(28, 38, 48)
    private val muted = Color.rgb(98, 110, 120)
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        showHome()
        if (prefs.getString("api_key", "").isNullOrBlank()) showApiKeyDialog(false)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245, 247, 249)) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(16)); setBackgroundColor(green)
        }
        header.addView(TextView(this).apply { text = "FUTBOL TAHMİN"; textSize = 22f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
        header.addView(TextView(this).apply { text = "İstatistik tabanlı maç analizi · Oran yok"; textSize = 13f; setTextColor(Color.rgb(220, 240, 231)); setPadding(0, dp(5), 0, 0) })
        root.addView(header)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(Color.WHITE) }
        nav.addView(navButton("Bugünün Maçları") { showHome() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(navButton("Tahmin Geçmişi") { showHistory() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(navButton("API Ayarı") { showApiKeyDialog(true) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(nav)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false; setTextColor(green); setOnClickListener { action() }
    }

    private fun showHome() {
        content.removeAllViews()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        content.addView(title("Bugünün maçları"))
        content.addView(body("Tarih: $date  ·  Veriler API-Football üzerinden alınır."))
        val refresh = Button(this).apply { text = if (loading) "Yükleniyor…" else "Maçları güncelle"; isAllCaps = false; setOnClickListener { loadFixtures(date) } }
        content.addView(refresh, matchWidth())
        if (prefs.getString("api_key", "").isNullOrBlank()) {
            content.addView(note("Başlamak için API Ayarı bölümüne API-Football anahtarınızı girin. API anahtarı olmadan canlı veriler alınamaz."))
        } else {
            content.addView(body("Tahminler son 5 maçın gol verilerinden hesaplanır. Örnek veya garanti sonuç değildir."))
            loadFixtures(date)
        }
    }

    private fun loadFixtures(date: String) {
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isBlank()) { showApiKeyDialog(true); return }
        if (loading) return
        loading = true
        content.removeAllViews(); content.addView(title("Maçlar yükleniyor…")); content.addView(body("Fikstür ve takım istatistikleri alınıyor."))
        Thread {
            try {
                val json = apiGet("https://v3.football.api-sports.io/fixtures?date=$date&timezone=Europe%2FIstanbul", key)
                val response = json.optJSONArray("response") ?: JSONArray()
                runOnUiThread {
                    loading = false
                    content.removeAllViews()
                    content.addView(title("Bugünün maçları (${response.length()})"))
                    content.addView(body("Tarih: $date · Her maç için analiz isteği API kullanım limitlerine tabi olabilir."))
                    if (response.length() == 0) content.addView(note("Bu tarih için maç bulunamadı veya veri sağlayıcı sonuç döndürmedi."))
                    for (i in 0 until response.length()) {
                        val item = response.optJSONObject(i) ?: continue
                        addFixtureCard(item, key)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false; content.removeAllViews(); content.addView(title("Veri alınamadı"))
                    content.addView(note("${e.message ?: "Bağlantı hatası"}\n\nAPI anahtarınızı, internet bağlantınızı ve sağlayıcı kotanızı kontrol edin."))
                    content.addView(Button(this).apply { text = "Tekrar dene"; isAllCaps = false; setOnClickListener { loadFixtures(date) } })
                }
            }
        }.start()
    }

    private fun addFixtureCard(item: JSONObject, key: String) {
        val fixture = item.optJSONObject("fixture") ?: JSONObject()
        val teams = item.optJSONObject("teams") ?: JSONObject()
        val home = teams.optJSONObject("home") ?: JSONObject()
        val away = teams.optJSONObject("away") ?: JSONObject()
        val league = item.optJSONObject("league") ?: JSONObject()
        val homeId = home.optInt("id", 0); val awayId = away.optInt("id", 0)
        val homeName = home.optString("name", "Ev sahibi"); val awayName = away.optString("name", "Deplasman")
        val id = fixture.optInt("id", 0)
        val kickoff = fixture.optString("date", "").replace("T", " ").take(16)
        val card = verticalCard()
        card.addView(TextView(this).apply { text = "${league.optString("name", "Lig")} · $kickoff"; textSize = 12f; setTextColor(muted) })
        card.addView(TextView(this).apply { text = "$homeName  -  $awayName"; textSize = 17f; setTextColor(ink); setTypeface(null, Typeface.BOLD); setPadding(0, dp(7), 0, dp(4)) })
        val status = fixture.optJSONObject("status")?.optString("short", "") ?: ""
        if (status == "FT" || status == "AET" || status == "PEN") {
            val goals = item.optJSONObject("goals") ?: JSONObject()
            card.addView(body("Sonuç: ${goals.opt("home")} - ${goals.opt("away")}"))
        }
        val result = TextView(this).apply { text = "Analiz için düğmeye basın. API kotasını korumak için istatistikler istek üzerine alınır."; textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, dp(6)) }
        card.addView(result)
        val analyze = Button(this).apply { text = "İstatistikleri analiz et"; isAllCaps = false }
        card.addView(analyze, matchWidth())
        val save = Button(this).apply { text = "Tahmini geçmişe kaydet"; isAllCaps = false; isEnabled = false }
        card.addView(save, matchWidth())
        save.setOnClickListener {
            val text = result.text.toString()
            val predicted = Regex("Maç sonucu: ([1X2])").find(text)?.groupValues?.getOrNull(1) ?: "?"
            if (predicted == "?") { Toast.makeText(this, "Önce analiz yapın", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val entry = JSONObject().put("fixtureId", id).put("home", homeName).put("away", awayName)
                .put("league", league.optString("name", "Lig")).put("date", kickoff).put("prediction", predicted)
                .put("analysis", text.toString()).put("rated", false)
            val hist = readHistory(); hist.put(entry); prefs.edit().putString("history", hist.toString()).apply()
            save.text = "Kaydedildi"; save.isEnabled = false
            Toast.makeText(this, "Tahmin geçmişe kaydedildi", Toast.LENGTH_SHORT).show()
        }
        content.addView(card)
        analyze.setOnClickListener {
            if (homeId <= 0 || awayId <= 0) { result.text = "Takım kimlikleri alınamadı."; return@setOnClickListener }
            analyze.isEnabled = false; analyze.text = "Analiz ediliyor…"; result.text = "Son 5 maçın istatistikleri alınıyor…"
            Thread {
                try {
                    val homeRecent = apiGet("https://v3.football.api-sports.io/fixtures?team=$homeId&last=5", key).optJSONArray("response") ?: JSONArray()
                    val awayRecent = apiGet("https://v3.football.api-sports.io/fixtures?team=$awayId&last=5", key).optJSONArray("response") ?: JSONArray()
                    val hs = teamStats(homeRecent, homeId); val as_ = teamStats(awayRecent, awayId)
                    val analysis = calculatePrediction(hs, as_)
                    runOnUiThread { result.text = analysis; save.isEnabled = true; analyze.isEnabled = true; analyze.text = "Yeniden analiz et" }
                } catch (e: Exception) {
                    runOnUiThread { result.text = "Analiz alınamadı: ${e.message ?: "API/bağlantı hatası"}"; analyze.isEnabled = true; analyze.text = "Tekrar dene" }
                }
            }.start()
        }
    }

    private data class Stats(val scored: Double, val conceded: Double, val count: Int, val form: String)

    private fun teamStats(matches: JSONArray, teamId: Int): Stats {
        var scored = 0.0; var conceded = 0.0; var count = 0; val form = StringBuilder()
        for (i in 0 until matches.length()) {
            val m = matches.optJSONObject(i) ?: continue
            val teams = m.optJSONObject("teams") ?: continue
            val goals = m.optJSONObject("goals") ?: continue
            val h = teams.optJSONObject("home") ?: continue; val a = teams.optJSONObject("away") ?: continue
            val hg = goals.optInt("home", -1); val ag = goals.optInt("away", -1)
            if (hg < 0 || ag < 0) continue
            val isHome = h.optInt("id") == teamId
            val forGoals = if (isHome) hg else ag; val against = if (isHome) ag else hg
            scored += forGoals; conceded += against; count++
            val outcome = if (forGoals > against) "G" else if (forGoals == against) "B" else "M"
            form.append(outcome).append(" ")
        }
        if (count == 0) return Stats(1.25, 1.25, 0, "Veri yok")
        return Stats(scored / count, conceded / count, count, form.toString().trim())
    }

    private fun calculatePrediction(home: Stats, away: Stats): String {
        val lh = (home.scored * 0.55 + away.conceded * 0.45).coerceIn(0.15, 4.0)
        val la = (away.scored * 0.55 + home.conceded * 0.45).coerceIn(0.15, 4.0)
        val hp = DoubleArray(8) { poisson(lh, it) }; val ap = DoubleArray(8) { poisson(la, it) }
        var p1 = 0.0; var px = 0.0; var p2 = 0.0; var over = 0.0; var btts = 0.0; var best = 0.0; var score = "0-0"
        for (h in 0..7) for (a in 0..7) {
            val p = hp[h] * ap[a]
            if (h > a) p1 += p else if (h == a) px += p else p2 += p
            if (h + a >= 3) over += p
            if (h > 0 && a > 0) btts += p
            if (p > best) { best = p; score = "$h-$a" }
        }
        val outcome = if (p1 >= px && p1 >= p2) "1" else if (p2 >= p1 && p2 >= px) "2" else "X"
        fun pct(v: Double) = "%.0f%%".format(Locale.US, v * 100)
        return "Maç sonucu: $outcome\n1: ${pct(p1)}   X: ${pct(px)}   2: ${pct(p2)}\n2,5 üst: ${pct(over)}   Karşılıklı gol: ${pct(btts)}\nEn olası skor: $score\nSon 5 maç gol ort. — Ev sahibi: ${"%.2f".format(Locale.US, home.scored)} atılan / ${"%.2f".format(Locale.US, home.conceded)} yenilen (${home.count} maç)\nDeplasman: ${"%.2f".format(Locale.US, away.scored)} atılan / ${"%.2f".format(Locale.US, away.conceded)} yenilen (${away.count} maç)\nForm (G: galibiyet, B: beraberlik, M: mağlubiyet): ${home.form} | ${away.form}\nModel: son maçların gol ortalamalarına dayalı Poisson tahmini; kesinlik veya garanti içermez."
    }

    private fun poisson(lambda: Double, k: Int): Double {
        var factorial = 1.0; for (i in 2..k) factorial *= i.toDouble()
        return exp(-lambda) * Math.pow(lambda, k.toDouble()) / factorial
    }

    private fun showHistory() {
        content.removeAllViews(); content.addView(title("Tahmin geçmişi"))
        val hist = readHistory()
        var rated = 0; var correct = 0
        for (i in 0 until hist.length()) { val e = hist.optJSONObject(i) ?: continue; if (e.optBoolean("rated")) { rated++; if (e.optBoolean("correct")) correct++ } }
        val rate = if (rated == 0) "Henüz değerlendirilmiş tahmin yok" else "Değerlendirilen: $rated · Doğru: $correct · Başarı: ${"%.1f".format(Locale.US, correct * 100.0 / rated)}%"
        content.addView(note(rate))
        content.addView(body("Tahminleri maç kartındaki düğmeyle kaydedin. Maç bittikten sonra sonucu el ile değerlendirerek başarı takibi yapabilirsiniz."))
        if (hist.length() == 0) content.addView(note("Henüz kaydedilmiş tahmin bulunmuyor."))
        for (i in hist.length() - 1 downTo 0) {
            val e = hist.optJSONObject(i) ?: continue
            val card = verticalCard()
            card.addView(TextView(this).apply { text = "${e.optString("home")} - ${e.optString("away")}"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) })
            card.addView(body("${e.optString("league")} · ${e.optString("date")}\nTahmin: ${e.optString("prediction")}"))
            if (e.optBoolean("rated")) card.addView(body(if (e.optBoolean("correct")) "Değerlendirme: Doğru" else "Değerlendirme: Yanlış"))
            else card.addView(Button(this).apply {
                text = "Sonuç tahminimi değerlendir"; isAllCaps = false
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("Tahmin doğru muydu?")
                        .setItems(arrayOf("Doğru", "Yanlış", "İptal")) { _, which ->
                            if (which < 2) { e.put("rated", true).put("correct", which == 0); prefs.edit().putString("history", hist.toString()).apply(); showHistory() }
                        }.show()
                }
            })
            content.addView(card)
        }
        if (hist.length() > 0) content.addView(Button(this).apply { text = "Geçmişi temizle"; isAllCaps = false; setOnClickListener { AlertDialog.Builder(this@MainActivity).setMessage("Tüm tahmin geçmişi silinsin mi?").setPositiveButton("Sil") { _, _ -> prefs.edit().remove("history").apply(); showHistory() }.setNegativeButton("Vazgeç", null).show() } })
    }

    private fun showApiKeyDialog(allowCancel: Boolean) {
        val input = EditText(this).apply { hint = "API-Football anahtarı"; setSingleLine(true); setText(prefs.getString("api_key", "")) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        box.addView(TextView(this).apply { text = "Canlı fikstür ve takım istatistikleri için API-Football hesabından alınan API anahtarını girin. Sağlayıcının ücretsiz planı ve kotaları değişebilir."; textSize = 14f; setTextColor(ink) })
        box.addView(input)
        val builder = AlertDialog.Builder(this).setTitle("Veri kaynağı ayarı").setView(box)
            .setPositiveButton("Kaydet", null)
        if (allowCancel) builder.setNegativeButton("İptal", null)
        val dialog = builder.create(); dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = input.text.toString().trim()
                if (key.isBlank()) { input.error = "API anahtarı gerekli"; return@setOnClickListener }
                prefs.edit().putString("api_key", key).apply(); dialog.dismiss(); showHome()
            }
        }; dialog.show()
    }

    private fun apiGet(url: String, key: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.setRequestProperty("x-apisports-key", key); conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            if (code !in 200..299) {
                val errors = json.optJSONObject("errors")?.toString() ?: text.take(300)
                throw IllegalStateException("HTTP $code: $errors")
            }
            val errors = json.optJSONObject("errors")
            if (errors != null && errors.length() > 0) throw IllegalStateException("API: $errors")
            return json
        } finally { conn.disconnect() }
    }

    private fun readHistory(): JSONArray = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
    private fun title(s: String) = TextView(this).apply { text = s; textSize = 21f; setTextColor(ink); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(7)) }
    private fun body(s: String) = TextView(this).apply { text = s; textSize = 13f; setTextColor(muted); setPadding(0, dp(4), 0, dp(6)) }
    private fun note(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(ink); setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(Color.rgb(231, 241, 236)); setLineSpacing(dp(2).toFloat(), 1f) }
    private fun verticalCard() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(Color.WHITE); elevation = dp(2).toFloat(); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) } }
    private fun matchWidth() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
