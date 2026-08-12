package com.streamvault.app.football

import com.streamvault.app.activation.ActivationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FootballRepository @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val client = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val base = ActivationManager.SERVER_URL.trimEnd('/')

    suspend fun loadLive(): Result<List<FootballFixture>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("$base/api/football/live")
            parseFixtures(json.optJSONArray("fixtures"))
        }
    }

    suspend fun loadToday(): Result<List<FootballFixture>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("$base/api/football/today")
            parseFixtures(json.optJSONArray("fixtures"))
        }
    }

    suspend fun loadPrediction(fixtureId: Int): Result<FootballPrediction?> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("$base/api/football/predictions/$fixtureId")
            val pred = json.optJSONObject("prediction") ?: return@runCatching null
            val percent = pred.optJSONObject("percent")
            FootballPrediction(
                winner = pred.optString("winner").ifBlank { null },
                winnerComment = pred.optString("winner_comment").ifBlank { null },
                advice = pred.optString("advice").ifBlank { null },
                percentHome = percent?.optString("home"),
                percentDraw = percent?.optString("draw"),
                percentAway = percent?.optString("away")
            )
        }
    }

    private fun getJson(url: String): JSONObject {
        val req = Request.Builder().url(url).header("Cache-Control", "no-cache").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return JSONObject(body.ifBlank { "{}" })
        }
    }

    private fun parseFixtures(arr: JSONArray?): List<FootballFixture> {
        if (arr == null) return emptyList()
        val out = ArrayList<FootballFixture>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val league = o.optJSONObject("league")
            val home = o.optJSONObject("home")
            val away = o.optJSONObject("away")
            val goals = o.optJSONObject("goals")
            val keywords = o.optJSONArray("match_keywords")
            val kw = mutableListOf<String>()
            if (keywords != null) {
                for (k in 0 until keywords.length()) {
                    keywords.optString(k)?.takeIf { it.isNotBlank() }?.let { kw.add(it) }
                }
            }
            out.add(
                FootballFixture(
                    id = o.optInt("id").takeIf { it > 0 },
                    date = o.optString("date").ifBlank { null },
                    timestamp = o.optLong("timestamp").takeIf { it > 0 },
                    status = o.optString("status"),
                    statusLong = o.optString("status_long"),
                    elapsed = o.optInt("elapsed").takeIf { o.has("elapsed") && !o.isNull("elapsed") },
                    isLive = o.optBoolean("is_live"),
                    isFinished = o.optBoolean("is_finished"),
                    isMajor = o.optBoolean("is_major", true),
                    league = FootballLeague(
                        id = league?.optInt("id"),
                        name = league?.optString("name").orEmpty(),
                        country = league?.optString("country").orEmpty(),
                        logo = league?.optString("logo").orEmpty(),
                        round = league?.optString("round").orEmpty()
                    ),
                    home = FootballTeam(
                        id = home?.optInt("id"),
                        name = home?.optString("name").orEmpty(),
                        logo = home?.optString("logo").orEmpty()
                    ),
                    away = FootballTeam(
                        id = away?.optInt("id"),
                        name = away?.optString("name").orEmpty(),
                        logo = away?.optString("logo").orEmpty()
                    ),
                    goals = FootballGoals(
                        home = goals?.optInt("home")?.takeIf { goals.has("home") && !goals.isNull("home") },
                        away = goals?.optInt("away")?.takeIf { goals.has("away") && !goals.isNull("away") }
                    ),
                    matchKeywords = kw
                )
            )
        }
        return out
    }
}
