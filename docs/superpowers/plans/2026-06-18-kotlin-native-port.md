# Kotlin Native Android And Wear Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the phone TWA wrapper with a Kotlin native Android app and convert the Wear OS companion from Java to Kotlin while preserving the existing ForestMate backend contracts.

**Architecture:** Add a shared `:core` Android library for API URLs, DTO parsing, typed results, repositories, and test fakes. Rebuild `:app` as a Kotlin Activity with native Home, Hike, SOS, AI, and My screens backed by core repositories. Convert `:wear` into Kotlin files with the same pairing, sensor upload, foreground service, and watch-face behavior it has today.

**Tech Stack:** Android Gradle Plugin 9.2.1, Kotlin Gradle plugin 2.2.10, AndroidX Activity, Kotlin/JVM unit tests, `HttpURLConnection`, `org.json`, Android `SharedPreferences`, existing FastAPI endpoints.

---

## Preflight Notes

- Worktree: `/Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port`
- Branch: `codex/kotlin-native-port`
- Spec: `docs/superpowers/specs/2026-06-18-kotlin-native-port-design.md`
- Current blockers:
  - `./gradlew --no-daemon :wear:assembleDebug` fails before Gradle starts because no Java Runtime is installed.
  - `python3 -m pytest server/tests/test_services.py -q` fails because dev dependencies are not installed in this worktree.
  - Disk has only about 1.7 GB free after deleting regenerable Gradle and pip caches.
- Do not edit the main checkout at `/Users/dong9733/Documents/GitHub/forest-mate`. Keep all work in the worktree above.

## File Structure

- Modify `packaging/android/settings.gradle`: include `:core`.
- Modify `packaging/android/build.gradle`: add Kotlin Gradle plugin and shared test dependency versions.
- Create `packaging/android/core/build.gradle`: Android library module.
- Create `packaging/android/core/src/main/java/kr/forestmate/core/...`: shared API, DTO, repository, and fake-free production code.
- Create `packaging/android/core/src/test/java/kr/forestmate/core/...`: core unit tests.
- Replace `packaging/android/app/build.gradle`: Kotlin Android app, no `androidbrowserhelper`.
- Replace `packaging/android/app/src/main/AndroidManifest.xml`: native `MainActivity`, permissions, no TWA metadata.
- Delete `packaging/android/app/src/main/java/kr/forestmate/app/Application.java`, `DelegationService.java`, and `LauncherActivity.java`.
- Create `packaging/android/app/src/main/java/kr/forestmate/app/...`: native phone app state, repositories, Activity, and views.
- Replace `packaging/android/wear/build.gradle`: Kotlin Android app consuming `:core`.
- Convert `packaging/android/wear/src/main/java/kr/forestmate/watch/*.java` into Kotlin files.
- Update docs and metadata: `docs/ARCHITECTURE.md`, `packaging/android/README.md`, `.github/workflows/android-release.yml`, `README.md`, `README.en.md`, `fastlane/metadata/android/en-US/full_description.txt`.

---

### Task 1: Unblock Local Verification

**Files:**
- No repository files should change in this task.

- [ ] **Step 1: Confirm the current blocker**

Run:

```bash
cd /Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port/packaging/android
/usr/libexec/java_home -V
./gradlew --no-daemon :wear:assembleDebug
```

Expected: `java_home` reports no runtime and Gradle reports it cannot locate a Java Runtime.

- [ ] **Step 2: Install or select a JDK without committing machine-local paths**

Use one of these options:

```bash
brew install openjdk@17
```

or point `JAVA_HOME` at an already installed JDK if the user installs one externally:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
```

Expected: `java -version` reports Java 17 or newer.

- [ ] **Step 3: Verify Gradle can start**

Run:

```bash
cd /Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port/packaging/android
./gradlew --no-daemon tasks --all
```

Expected: Gradle lists tasks. If dependency download fails from disk pressure, stop and free regenerable cache space before continuing.

- [ ] **Step 4: Commit**

No commit for this task unless repository files changed unexpectedly. If files changed, inspect with:

```bash
git status --short
git diff --check
```

Expected: no repository changes.

---

### Task 2: Add Kotlin Core Module With The First Failing Test

**Files:**
- Modify: `packaging/android/settings.gradle`
- Modify: `packaging/android/build.gradle`
- Create: `packaging/android/core/build.gradle`
- Test: `packaging/android/core/src/test/java/kr/forestmate/core/api/ApiConfigTest.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/api/ApiConfig.kt`

- [ ] **Step 1: Write the failing test**

Create `packaging/android/core/src/test/java/kr/forestmate/core/api/ApiConfigTest.kt`:

```kotlin
package kr.forestmate.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigTest {
    @Test
    fun normalizesBaseUrlAndBuildsPaths() {
        val config = ApiConfig("https://forestmate.onrender.com/api/v1/")

        assertEquals("https://forestmate.onrender.com/api/v1", config.baseUrl)
        assertEquals("https://forestmate.onrender.com/api/v1/index", config.url("/index"))
        assertEquals("https://forestmate.onrender.com/api/v1/watch/latest?hike_id=abc", config.url("watch/latest?hike_id=abc"))
    }

    @Test
    fun blankBaseFallsBackToProductionApi() {
        val config = ApiConfig(" ")

        assertEquals("https://forestmate.onrender.com/api/v1", config.baseUrl)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port/packaging/android
./gradlew --no-daemon :core:testDebugUnitTest --tests kr.forestmate.core.api.ApiConfigTest
```

Expected: FAIL because project `:core` or `ApiConfig` does not exist.

- [ ] **Step 3: Add Gradle module scaffolding**

Modify `packaging/android/settings.gradle` to:

```groovy
include ':core', ':app', ':wear'
```

Keep `packaging/android/build.gradle` on AGP 9.2.1 only. AGP 9 has built-in Kotlin
support, so do not add `org.jetbrains.kotlin.android` or a Kotlin Gradle plugin
classpath. Applying the old Kotlin Android plugin creates a duplicate `kotlin`
extension and fails the build.

Create `packaging/android/core/build.gradle`:

```groovy
plugins {
    id 'com.android.library'
}

android {
    namespace "kr.forestmate.core"
    compileSdkVersion 36

    defaultConfig {
        minSdkVersion 23
        targetSdkVersion 35
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

}

dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 4: Write minimal implementation**

Create `packaging/android/core/src/main/java/kr/forestmate/core/api/ApiConfig.kt`:

```kotlin
package kr.forestmate.core.api

class ApiConfig(rawBaseUrl: String) {
    val baseUrl: String = normalize(rawBaseUrl)

    fun url(path: String): String {
        val cleanPath = path.trim().removePrefix("/")
        return "$baseUrl/$cleanPath"
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return trimmed.ifEmpty { DEFAULT_BASE_URL }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://forestmate.onrender.com/api/v1"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew --no-daemon :core:testDebugUnitTest --tests kr.forestmate.core.api.ApiConfigTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add packaging/android/settings.gradle packaging/android/build.gradle packaging/android/core
git commit -m "feat(android): add Kotlin core module"
```

---

### Task 3: Add Core Result, HTTP Transport, And JSON DTO Parsing

**Files:**
- Test: `packaging/android/core/src/test/java/kr/forestmate/core/api/ApiResultTest.kt`
- Test: `packaging/android/core/src/test/java/kr/forestmate/core/model/DtoParsingTest.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/api/ApiResult.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/api/HttpTransport.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/model/Models.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/model/JsonParsers.kt`

- [ ] **Step 1: Write failing result tests**

Create `ApiResultTest.kt`:

```kotlin
package kr.forestmate.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {
    @Test
    fun successMapsValue() {
        val result = ApiResult.Success("ok").map { it.length }

        assertEquals(ApiResult.Success(2), result)
    }

    @Test
    fun failureKeepsStatusAndMessage() {
        val result = ApiResult.Failure(statusCode = 503, message = "offline")

        assertTrue(result.isRetryable)
        assertEquals("HTTP 503 offline", result.displayMessage)
    }
}
```

- [ ] **Step 2: Write failing DTO parsing tests**

Create `DtoParsingTest.kt`:

```kotlin
package kr.forestmate.core.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DtoParsingTest {
    @Test
    fun parsesHikeIndexResponse() {
        val json = JSONObject(
            """
            {
              "score": 82,
              "label": "good",
              "fire": {"level": "low"},
              "conditions": {"name": "Bukhansan", "weather": {"temp": 18, "label": "clear", "wind": 3, "rain_prob": 10}},
              "place": "Seoul"
            }
            """.trimIndent()
        )

        val index = JsonParsers.hikeIndex(json)

        assertEquals(82, index.score)
        assertEquals("good", index.label)
        assertEquals("Bukhansan", index.regionName)
        assertEquals("Seoul", index.place)
        assertEquals(18.0, index.temperatureC, 0.01)
    }

    @Test
    fun parsesCourseListResponse() {
        val json = JSONObject(
            """
            {"items":[{"id":"bukhansan","name":"Bukhansan loop","km":4.2,"minutes":95,"route":"trail","hazards":[{"type":"rock","grade":"caution","at":0.5}]}]}
            """.trimIndent()
        )

        val courses = JsonParsers.courses(json)

        assertEquals(1, courses.size)
        assertEquals("bukhansan", courses[0].id)
        assertEquals(1, courses[0].hazards.size)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew --no-daemon :core:testDebugUnitTest --tests kr.forestmate.core.api.ApiResultTest --tests kr.forestmate.core.model.DtoParsingTest
```

Expected: FAIL because `ApiResult`, `JsonParsers`, and model classes do not exist.

- [ ] **Step 4: Implement result and transport contracts**

Create `ApiResult.kt`:

```kotlin
package kr.forestmate.core.api

sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Failure(val statusCode: Int, val message: String, val cause: Throwable? = null) : ApiResult<Nothing>() {
        val isRetryable: Boolean get() = statusCode == 0 || statusCode == 408 || statusCode >= 500
        val displayMessage: String get() = if (statusCode > 0) "HTTP $statusCode $message" else message
    }

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}
```

Create `HttpTransport.kt`:

```kotlin
package kr.forestmate.core.api

import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val statusCode: Int, val body: String)

interface HttpTransport {
    fun get(url: String, bearerToken: String? = null): HttpResponse
    fun post(url: String, jsonBody: String, bearerToken: String? = null): HttpResponse
}

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 5000,
) : HttpTransport {
    override fun get(url: String, bearerToken: String?): HttpResponse =
        request("GET", url, null, bearerToken)

    override fun post(url: String, jsonBody: String, bearerToken: String?): HttpResponse =
        request("POST", url, jsonBody, bearerToken)

    private fun request(method: String, url: String, body: String?, bearerToken: String?): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = method
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        if (!bearerToken.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        if (body != null) {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        val response = HttpResponse(conn.responseCode, text)
        conn.disconnect()
        return response
    }
}
```

- [ ] **Step 5: Implement DTOs and parsers**

Create `Models.kt`:

```kotlin
package kr.forestmate.core.model

data class HikeIndex(
    val score: Int,
    val label: String,
    val regionName: String,
    val place: String,
    val temperatureC: Double,
    val weatherLabel: String,
    val windMps: Double,
    val rainProbability: Int,
    val fireLevel: String,
)

data class Course(
    val id: String,
    val name: String,
    val km: Double,
    val minutes: Int,
    val route: String,
    val hazards: List<Hazard>,
)

data class Hazard(
    val type: String,
    val grade: String,
    val at: Double,
)

data class DeviceRegistration(val deviceId: String, val token: String, val name: String)
data class HikeStart(val hikeId: String, val courseId: String)
data class TrackUpdate(val progress: Double, val distressLevel: Int)
data class WatchPairCode(val code: String, val expiresIn: Int, val hikeId: String?)
data class ChatReply(val reply: String, val intent: String, val engine: String, val sources: List<String>)
data class SosReceipt(val sosId: String, val status: String, val gridNo: String, val gps: String, val station: String, val etaMin: Int)
data class HikeSummary(val totalHikes: Int, val totalKm: Double, val totalKcal: Int, val level: Int)
data class HikeLogItem(val course: String, val km: Double, val kcal: Int, val date: String)
```

Create `JsonParsers.kt`:

```kotlin
package kr.forestmate.core.model

import org.json.JSONArray
import org.json.JSONObject

object JsonParsers {
    fun hikeIndex(json: JSONObject): HikeIndex {
        val conditions = json.optJSONObject("conditions") ?: JSONObject()
        val weather = conditions.optJSONObject("weather") ?: JSONObject()
        val fire = json.optJSONObject("fire") ?: JSONObject()
        return HikeIndex(
            score = json.optInt("score", 0),
            label = json.optString("label", ""),
            regionName = conditions.optString("name", ""),
            place = json.optString("place", ""),
            temperatureC = weather.optDouble("temp", 0.0),
            weatherLabel = weather.optString("label", ""),
            windMps = weather.optDouble("wind", 0.0),
            rainProbability = weather.optInt("rain_prob", 0),
            fireLevel = fire.optString("level", ""),
        )
    }

    fun courses(json: JSONObject): List<Course> {
        val array = json.optJSONArray("items") ?: JSONArray()
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Course(
                id = item.optString("id", ""),
                name = item.optString("name", ""),
                km = item.optDouble("km", 0.0),
                minutes = item.optInt("minutes", 0),
                route = item.optString("route", ""),
                hazards = hazards(item.optJSONArray("hazards") ?: JSONArray()),
            )
        }
    }

    private fun hazards(array: JSONArray): List<Hazard> =
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Hazard(
                type = item.optString("type", ""),
                grade = item.optString("grade", ""),
                at = item.optDouble("at", 0.0),
            )
        }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
./gradlew --no-daemon :core:testDebugUnitTest --tests kr.forestmate.core.api.ApiResultTest --tests kr.forestmate.core.model.DtoParsingTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add packaging/android/core
git commit -m "feat(android): add core API DTO parsing"
```

---

### Task 4: Add Core Repository Methods For Phone And Watch Flows

**Files:**
- Test: `packaging/android/core/src/test/java/kr/forestmate/core/repo/ForestMateRepositoryTest.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/api/FakeTransport.kt`
- Create: `packaging/android/core/src/main/java/kr/forestmate/core/repo/ForestMateRepository.kt`

- [ ] **Step 1: Write failing repository tests**

Create `ForestMateRepositoryTest.kt`:

```kotlin
package kr.forestmate.core.repo

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.FakeTransport
import kr.forestmate.core.api.HttpResponse
import kr.forestmate.core.api.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestMateRepositoryTest {
    @Test
    fun loadsIndexAndCourses() {
        val transport = FakeTransport(
            getResponses = mapOf(
                "https://example.test/api/index" to HttpResponse(200, """{"score":75,"label":"ok","conditions":{"name":"Region","weather":{"temp":12,"label":"wind","wind":4,"rain_prob":30}}}"""),
                "https://example.test/api/courses" to HttpResponse(200, """{"items":[{"id":"c1","name":"Course","km":3.0,"minutes":70,"route":"Loop","hazards":[]}]}"""),
            )
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val index = repo.hikeIndex()
        val courses = repo.courses()

        assertEquals(75, (index as ApiResult.Success).value.score)
        assertEquals("c1", (courses as ApiResult.Success).value[0].id)
    }

    @Test
    fun sendsBearerTokenForWatchLatest() {
        val transport = FakeTransport(
            getResponses = mapOf("https://example.test/api/watch/latest?hike_id=h1" to HttpResponse(200, """{"connected":true,"hr":88}"""))
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val result = repo.watchLatest(deviceToken = "device-token", hikeId = "h1")

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer device-token", transport.lastAuthorizationHeader)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew --no-daemon :core:testDebugUnitTest --tests kr.forestmate.core.repo.ForestMateRepositoryTest
```

Expected: FAIL because repository and fake transport do not exist.

- [ ] **Step 3: Add fake transport and repository skeleton**

Create `FakeTransport.kt`:

```kotlin
package kr.forestmate.core.api

class FakeTransport(
    private val getResponses: Map<String, HttpResponse> = emptyMap(),
    private val postResponses: Map<String, HttpResponse> = emptyMap(),
) : HttpTransport {
    var lastAuthorizationHeader: String? = null
        private set
    var lastPostBody: String? = null
        private set

    override fun get(url: String, bearerToken: String?): HttpResponse {
        lastAuthorizationHeader = bearerToken?.let { "Bearer $it" }
        return getResponses[url] ?: HttpResponse(404, """{"detail":"not found"}""")
    }

    override fun post(url: String, jsonBody: String, bearerToken: String?): HttpResponse {
        lastAuthorizationHeader = bearerToken?.let { "Bearer $it" }
        lastPostBody = jsonBody
        return postResponses[url] ?: HttpResponse(404, """{"detail":"not found"}""")
    }
}
```

Create `ForestMateRepository.kt` with methods required by tests:

```kotlin
package kr.forestmate.core.repo

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.HttpResponse
import kr.forestmate.core.api.HttpTransport
import kr.forestmate.core.model.Course
import kr.forestmate.core.model.HikeIndex
import kr.forestmate.core.model.JsonParsers
import org.json.JSONObject

class ForestMateRepository(
    private val config: ApiConfig,
    private val transport: HttpTransport,
) {
    fun hikeIndex(): ApiResult<HikeIndex> =
        parse(transport.get(config.url("/index"))) { JsonParsers.hikeIndex(it) }

    fun courses(): ApiResult<List<Course>> =
        parse(transport.get(config.url("/courses"))) { JsonParsers.courses(it) }

    fun watchLatest(deviceToken: String, hikeId: String?): ApiResult<JSONObject> {
        val suffix = if (hikeId.isNullOrBlank()) "/watch/latest" else "/watch/latest?hike_id=$hikeId"
        return parse(transport.get(config.url(suffix), bearerToken = deviceToken)) { it }
    }

    private fun <T> parse(response: HttpResponse, mapper: (JSONObject) -> T): ApiResult<T> {
        if (response.statusCode !in 200..299) {
            return ApiResult.Failure(response.statusCode, response.body.ifBlank { "request failed" })
        }
        return try {
            ApiResult.Success(mapper(JSONObject(response.body.ifBlank { "{}" })))
        } catch (ex: Exception) {
            ApiResult.Failure(0, "invalid response", ex)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew --no-daemon :core:testDebugUnitTest --tests kr.forestmate.core.repo.ForestMateRepositoryTest
```

Expected: PASS.

- [ ] **Step 5: Add concrete repository endpoint methods with tests first**

Extend `ForestMateRepositoryTest.kt` with one test per method before implementing:

```kotlin
@Test
fun registersDeviceAndStartsHike() {
    val transport = FakeTransport(
        postResponses = mapOf(
            "https://example.test/api/devices" to HttpResponse(201, """{"device_id":"d1","token":"t1","name":"phone"}"""),
            "https://example.test/api/hikes" to HttpResponse(201, """{"hike_id":"h1","course_id":"c1"}"""),
        )
    )
    val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

    val device = repo.registerDevice("phone")
    val hike = repo.startHike("t1", "c1")

    assertEquals("t1", (device as ApiResult.Success).value.token)
    assertEquals("h1", (hike as ApiResult.Success).value.hikeId)
}
```

Add this second test before writing production code:

```kotlin
@Test
fun sendsChatAndSosRequests() {
    val transport = FakeTransport(
        postResponses = mapOf(
            "https://example.test/api/chat" to HttpResponse(200, """{"reply":"safe","intent":"weather","engine":"rules","sources":["weather"]}"""),
            "https://example.test/api/sos" to HttpResponse(201, """{"sos_id":"s1","status":"dispatched","grid_no":"ABC","gps":"37.0N 127.0E","station":"119","eta_min":12}"""),
        )
    )
    val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

    val chat = repo.sendChat(message = "weather", lang = "ko", courseId = "c1", progress = 0.2)
    val sos = repo.sendSos(deviceToken = "t1", hikeId = "h1")

    assertEquals("safe", (chat as ApiResult.Success).value.reply)
    assertEquals("s1", (sos as ApiResult.Success).value.sosId)
}
```

Add these concrete DTO parsers to `JsonParsers.kt`:

```kotlin
fun device(json: JSONObject): DeviceRegistration =
    DeviceRegistration(
        deviceId = json.optString("device_id", ""),
        token = json.optString("token", ""),
        name = json.optString("name", ""),
    )

fun hikeStart(json: JSONObject): HikeStart =
    HikeStart(
        hikeId = json.optString("hike_id", ""),
        courseId = json.optString("course_id", ""),
    )

fun trackUpdate(json: JSONObject): TrackUpdate {
    val distress = json.optJSONObject("distress") ?: JSONObject()
    return TrackUpdate(
        progress = json.optDouble("progress", 0.0),
        distressLevel = distress.optInt("level", 0),
    )
}

fun watchPairCode(json: JSONObject): WatchPairCode =
    WatchPairCode(
        code = json.optString("code", ""),
        expiresIn = json.optInt("expires_in", 0),
        hikeId = if (json.isNull("hike_id")) null else json.optString("hike_id", ""),
    )

fun chatReply(json: JSONObject): ChatReply {
    val sources = json.optJSONArray("sources") ?: JSONArray()
    return ChatReply(
        reply = json.optString("reply", ""),
        intent = json.optString("intent", ""),
        engine = json.optString("engine", ""),
        sources = (0 until sources.length()).map { sources.optString(it, "") },
    )
}

fun sosReceipt(json: JSONObject): SosReceipt =
    SosReceipt(
        sosId = json.optString("sos_id", ""),
        status = json.optString("status", ""),
        gridNo = json.optString("grid_no", ""),
        gps = json.optString("gps", ""),
        station = json.optString("station", ""),
        etaMin = json.optInt("eta_min", 0),
    )

fun hikeSummary(json: JSONObject): HikeSummary =
    HikeSummary(
        totalHikes = json.optInt("total_hikes", 0),
        totalKm = json.optDouble("total_km", 0.0),
        totalKcal = json.optInt("total_kcal", 0),
        level = json.optInt("level", 1),
    )

fun hikeLog(json: JSONObject): List<HikeLogItem> {
    val items = json.optJSONArray("items") ?: JSONArray()
    return (0 until items.length()).map { i ->
        val item = items.getJSONObject(i)
        HikeLogItem(
            course = item.optString("course", ""),
            km = item.optDouble("km", 0.0),
            kcal = item.optInt("kcal", 0),
            date = item.optString("date", ""),
        )
    }
}
```

Add these concrete methods to `ForestMateRepository.kt`:

```kotlin
fun registerDevice(name: String): ApiResult<DeviceRegistration> {
    val body = JSONObject()
        .put("name", name)
        .put("fit", "normal")
        .put("knee", false)
        .put("heart", false)
    return parse(transport.post(config.url("/devices"), body.toString())) { JsonParsers.device(it) }
}

fun startHike(deviceToken: String, courseId: String): ApiResult<HikeStart> {
    val body = JSONObject().put("course_id", courseId)
    return parse(transport.post(config.url("/hikes"), body.toString(), bearerToken = deviceToken)) { JsonParsers.hikeStart(it) }
}

fun trackHike(deviceToken: String, hikeId: String, progress: Double, alt: Int? = null, hr: Int? = null): ApiResult<TrackUpdate> {
    val body = JSONObject().put("progress", progress)
    if (alt != null) body.put("alt", alt)
    if (hr != null) body.put("hr", hr)
    return parse(transport.post(config.url("/hikes/$hikeId/track"), body.toString(), bearerToken = deviceToken)) { JsonParsers.trackUpdate(it) }
}

fun endHike(deviceToken: String, hikeId: String): ApiResult<JSONObject> =
    parse(transport.post(config.url("/hikes/$hikeId/end"), "{}", bearerToken = deviceToken)) { it }

fun startWatchPairing(deviceToken: String, hikeId: String?): ApiResult<WatchPairCode> {
    val body = JSONObject()
    if (!hikeId.isNullOrBlank()) body.put("hike_id", hikeId)
    return parse(transport.post(config.url("/watch/pair/start"), body.toString(), bearerToken = deviceToken)) { JsonParsers.watchPairCode(it) }
}

fun sendSos(deviceToken: String, hikeId: String?): ApiResult<SosReceipt> {
    val body = JSONObject().put("note", "native android")
    if (!hikeId.isNullOrBlank()) body.put("hike_id", hikeId)
    return parse(transport.post(config.url("/sos"), body.toString(), bearerToken = deviceToken)) { JsonParsers.sosReceipt(it) }
}

fun sendChat(message: String, lang: String, courseId: String?, progress: Double): ApiResult<ChatReply> {
    val body = JSONObject()
        .put("message", message)
        .put("lang", lang)
        .put("progress", progress)
    if (!courseId.isNullOrBlank()) body.put("course_id", courseId)
    return parse(transport.post(config.url("/chat"), body.toString())) { JsonParsers.chatReply(it) }
}

fun hikeSummary(deviceToken: String): ApiResult<HikeSummary> =
    parse(transport.get(config.url("/hikes/summary"), bearerToken = deviceToken)) { JsonParsers.hikeSummary(it) }

fun hikeLog(deviceToken: String): ApiResult<List<HikeLogItem>> =
    parse(transport.get(config.url("/hikes"), bearerToken = deviceToken)) { JsonParsers.hikeLog(it) }
```

- [ ] **Step 6: Run full core tests**

Run:

```bash
./gradlew --no-daemon :core:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add packaging/android/core
git commit -m "feat(android): add ForestMate core repositories"
```

---

### Task 5: Replace Phone TWA Gradle And Manifest With Native Kotlin App Shell

**Files:**
- Modify: `packaging/android/app/build.gradle`
- Modify: `packaging/android/app/src/main/AndroidManifest.xml`
- Delete: `packaging/android/app/src/main/java/kr/forestmate/app/Application.java`
- Delete: `packaging/android/app/src/main/java/kr/forestmate/app/DelegationService.java`
- Delete: `packaging/android/app/src/main/java/kr/forestmate/app/LauncherActivity.java`
- Test: `packaging/android/app/src/test/java/kr/forestmate/app/state/PhoneStateTest.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/MainActivity.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/state/PhoneState.kt`

- [ ] **Step 1: Write failing phone state test**

Create `PhoneStateTest.kt`:

```kotlin
package kr.forestmate.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneStateTest {
    @Test
    fun selectingCourseResetsHikeProgress() {
        val state = PhoneState(selectedCourseId = "old", activeHikeId = "h1", progress = 0.8)

        val next = state.selectCourse("new")

        assertEquals("new", next.selectedCourseId)
        assertEquals(null, next.activeHikeId)
        assertEquals(0.0, next.progress, 0.01)
    }

    @Test
    fun clearingDeviceTokenDoesNotClearWatchState() {
        val state = PhoneState(deviceToken = "bad", watchPairCode = "123456")

        val next = state.clearDeviceToken()

        assertEquals("", next.deviceToken)
        assertEquals("123456", next.watchPairCode)
        assertFalse(next.hasDeviceToken)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests kr.forestmate.app.state.PhoneStateTest
```

Expected: FAIL because app is not a Kotlin testable native module and `PhoneState` does not exist.

- [ ] **Step 3: Replace app Gradle file**

Replace `packaging/android/app/build.gradle` with:

```groovy
plugins {
    id 'com.android.application'
}

android {
    compileSdkVersion 36
    namespace "kr.forestmate.app"

    defaultConfig {
        applicationId "kr.forestmate.app"
        minSdkVersion 23
        targetSdkVersion 35
        versionCode 6
        versionName "1.1.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

}

dependencies {
    implementation project(':core')
    implementation 'androidx.core:core-ktx:1.17.0'
    implementation 'androidx.activity:activity-ktx:1.9.0'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 4: Replace Android manifest**

Replace `packaging/android/app/src/main/AndroidManifest.xml` with:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/appName"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Add `packaging/android/app/src/main/res/values/styles.xml` if it does not exist:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">#2D6A4F</item>
        <item name="android:navigationBarColor">#0B2417</item>
        <item name="android:statusBarColor">#1B4332</item>
    </style>
</resources>
```

- [ ] **Step 5: Implement state and minimal Activity**

Create `PhoneState.kt`:

```kotlin
package kr.forestmate.app.state

data class PhoneState(
    val deviceToken: String = "",
    val selectedCourseId: String? = null,
    val activeHikeId: String? = null,
    val progress: Double = 0.0,
    val watchPairCode: String? = null,
) {
    val hasDeviceToken: Boolean get() = deviceToken.isNotBlank()

    fun selectCourse(courseId: String): PhoneState =
        copy(selectedCourseId = courseId, activeHikeId = null, progress = 0.0)

    fun clearDeviceToken(): PhoneState =
        copy(deviceToken = "")
}
```

Create `MainActivity.kt`:

```kotlin
package kr.forestmate.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import kr.forestmate.core.api.ApiConfig

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "ForestMate native"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = ApiConfig.DEFAULT_BASE_URL
            textSize = 13f
        })
        setContentView(root)
    }
}
```

Delete the three Java TWA classes.

- [ ] **Step 6: Run tests and dependency check**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests kr.forestmate.app.state.PhoneStateTest
rg -n "androidbrowserhelper|TrustedWebActivity|WebView|LauncherActivity" packaging/android/app
```

Expected: tests PASS; `rg` returns no TWA/WebView dependency in `packaging/android/app`.

- [ ] **Step 7: Commit**

```bash
git add packaging/android/app
git commit -m "feat(android): replace phone TWA with native shell"
```

---

### Task 6: Build Phone Native UI State For Home, Hike, SOS, AI, And My

**Files:**
- Test: `packaging/android/app/src/test/java/kr/forestmate/app/state/NavigationStateTest.kt`
- Test: `packaging/android/app/src/test/java/kr/forestmate/app/state/SosHoldStateTest.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/state/NavigationState.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/state/SosHoldState.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/ui/NativeViews.kt`
- Modify: `packaging/android/app/src/main/java/kr/forestmate/app/MainActivity.kt`

- [ ] **Step 1: Write failing navigation and SOS tests**

Create `NavigationStateTest.kt`:

```kotlin
package kr.forestmate.app.state

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStateTest {
    @Test
    fun onlyKnownTabsCanBeSelected() {
        assertEquals(PhoneTab.HOME, NavigationState().select("missing").selected)
        assertEquals(PhoneTab.AI, NavigationState().select("ai").selected)
    }
}
```

Create `SosHoldStateTest.kt`:

```kotlin
package kr.forestmate.app.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosHoldStateTest {
    @Test
    fun confirmsAfterRequiredHoldTime() {
        val state = SosHoldState(startedAtMs = 1000L, requiredMs = 1800L)

        assertFalse(state.update(nowMs = 2500L).confirmed)
        assertTrue(state.update(nowMs = 2800L).confirmed)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests kr.forestmate.app.state.NavigationStateTest --tests kr.forestmate.app.state.SosHoldStateTest
```

Expected: FAIL because classes do not exist.

- [ ] **Step 3: Implement state classes**

Create `NavigationState.kt`:

```kotlin
package kr.forestmate.app.state

enum class PhoneTab(val id: String, val label: String) {
    HOME("home", "Home"),
    HIKE("hike", "Hike"),
    SOS("sos", "SOS"),
    AI("ai", "AI"),
    MY("my", "My"),
}

data class NavigationState(val selected: PhoneTab = PhoneTab.HOME) {
    fun select(id: String): NavigationState =
        PhoneTab.entries.firstOrNull { it.id == id }?.let { copy(selected = it) } ?: this
}
```

Create `SosHoldState.kt`:

```kotlin
package kr.forestmate.app.state

data class SosHoldState(
    val startedAtMs: Long,
    val requiredMs: Long = 1800L,
    val confirmed: Boolean = false,
) {
    fun update(nowMs: Long): SosHoldState =
        copy(confirmed = nowMs - startedAtMs >= requiredMs)
}
```

- [ ] **Step 4: Add native view factory**

Create `NativeViews.kt`:

```kotlin
package kr.forestmate.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kr.forestmate.app.state.PhoneTab

object NativeViews {
    fun screen(context: Context, title: String, body: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(244, 247, 242))
            addView(TextView(context).apply {
                text = title
                textSize = 24f
                setTextColor(Color.rgb(27, 67, 50))
            })
            addView(TextView(context).apply {
                text = body
                textSize = 15f
                setTextColor(Color.rgb(36, 52, 43))
            })
        }

    fun tabButton(context: Context, tab: PhoneTab, onClick: () -> Unit): Button =
        Button(context).apply {
            text = tab.label
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
}
```

- [ ] **Step 5: Render bottom navigation in MainActivity**

Modify `MainActivity.kt` so it owns `NavigationState` and swaps native screen bodies for the five tabs using `NativeViews.screen`.

Expected minimum screen bodies:

```kotlin
private fun bodyFor(tab: PhoneTab): Pair<String, String> = when (tab) {
    PhoneTab.HOME -> "Today" to "Loading hiking index and recommendations..."
    PhoneTab.HIKE -> "Hike" to "Select a course and start tracking."
    PhoneTab.SOS -> "SOS" to "Hold to send an emergency request."
    PhoneTab.AI -> "AI Companion" to "Ask about weather, hazards, routes, or plants."
    PhoneTab.MY -> "My" to "Hike history and badges."
}
```

- [ ] **Step 6: Run tests**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
```

Expected: tests PASS and native debug APK builds.

- [ ] **Step 7: Commit**

```bash
git add packaging/android/app
git commit -m "feat(android): add native phone navigation"
```

---

### Task 7: Wire Phone App To Core Repository Flows

**Files:**
- Test: `packaging/android/app/src/test/java/kr/forestmate/app/state/HikeFlowStateTest.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/state/HikeFlowState.kt`
- Create: `packaging/android/app/src/main/java/kr/forestmate/app/PhoneStore.kt`
- Modify: `packaging/android/app/src/main/java/kr/forestmate/app/MainActivity.kt`
- Modify: `packaging/android/core/src/main/java/kr/forestmate/core/repo/ForestMateRepository.kt`

- [ ] **Step 1: Write failing hike flow tests**

Create `HikeFlowStateTest.kt`:

```kotlin
package kr.forestmate.app.state

import org.junit.Assert.assertEquals
import org.junit.Test

class HikeFlowStateTest {
    @Test
    fun startingHikeStoresActiveHike() {
        val state = HikeFlowState(selectedCourseId = "c1")

        val next = state.started(hikeId = "h1")

        assertEquals("h1", next.activeHikeId)
        assertEquals(0.0, next.progress, 0.01)
    }

    @Test
    fun progressIsClamped() {
        assertEquals(1.0, HikeFlowState().updateProgress(2.0).progress, 0.01)
        assertEquals(0.0, HikeFlowState().updateProgress(-1.0).progress, 0.01)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests kr.forestmate.app.state.HikeFlowStateTest
```

Expected: FAIL because `HikeFlowState` does not exist.

- [ ] **Step 3: Implement HikeFlowState**

Create `HikeFlowState.kt`:

```kotlin
package kr.forestmate.app.state

data class HikeFlowState(
    val selectedCourseId: String? = null,
    val activeHikeId: String? = null,
    val progress: Double = 0.0,
    val watchPairCode: String? = null,
) {
    fun started(hikeId: String): HikeFlowState =
        copy(activeHikeId = hikeId, progress = 0.0)

    fun updateProgress(value: Double): HikeFlowState =
        copy(progress = value.coerceIn(0.0, 1.0))

    fun paired(code: String): HikeFlowState =
        copy(watchPairCode = code)
}
```

- [ ] **Step 4: Add PhoneStore**

Create `PhoneStore.kt`:

```kotlin
package kr.forestmate.app

import android.content.Context
import kr.forestmate.core.api.ApiConfig

class PhoneStore(context: Context) {
    private val prefs = context.getSharedPreferences("forestmate_phone", Context.MODE_PRIVATE)

    var apiBase: String
        get() = prefs.getString("apiBase", ApiConfig.DEFAULT_BASE_URL) ?: ApiConfig.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("apiBase", value).apply()

    var deviceToken: String
        get() = prefs.getString("deviceToken", "") ?: ""
        set(value) = prefs.edit().putString("deviceToken", value).apply()

    var activeHikeId: String
        get() = prefs.getString("activeHikeId", "") ?: ""
        set(value) = prefs.edit().putString("activeHikeId", value).apply()
}
```

- [ ] **Step 5: Wire MainActivity actions**

Use `ForestMateRepository` from `:core` and `PhoneStore` in `MainActivity`.

Wire these five concrete actions:

- Home tab fetches `/index` and `/courses` on a background thread and displays score/course count.
- Hike tab can register a device, start a hike for the selected course, and show `hikeId`.
- SOS tab posts SOS when there is a device token.
- AI tab posts chat text and shows reply.
- My tab fetches summary and shows total hikes.

Every network call must update UI on the main thread via `runOnUiThread`.

- [ ] **Step 6: Run tests and build**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :core:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
```

Expected: tests PASS and debug APK builds.

- [ ] **Step 7: Commit**

```bash
git add packaging/android/app packaging/android/core
git commit -m "feat(android): wire native phone flows to API"
```

---

### Task 8: Convert Wear API And State Store To Kotlin

**Files:**
- Test: `packaging/android/wear/src/test/java/kr/forestmate/watch/WatchApiTest.kt`
- Test: `packaging/android/wear/src/test/java/kr/forestmate/watch/WatchStateStoreTest.kt`
- Delete: `packaging/android/wear/src/main/java/kr/forestmate/watch/WatchApi.java`
- Create: `packaging/android/wear/src/main/java/kr/forestmate/watch/WatchApi.kt`
- Create: `packaging/android/wear/src/main/java/kr/forestmate/watch/WatchStateStore.kt`
- Modify: `packaging/android/wear/build.gradle`

- [ ] **Step 1: Update wear Gradle for AGP built-in Kotlin and tests**

Modify `packaging/android/wear/build.gradle`:

```groovy
plugins {
    id 'com.android.application'
}
```

Add dependencies:

```groovy
dependencies {
    implementation project(':core')
    implementation 'androidx.core:core-ktx:1.17.0'
    testImplementation 'junit:junit:4.13.2'
}
```

Set Java target to 17. AGP built-in Kotlin uses `compileOptions.targetCompatibility`
for the Kotlin JVM target, so do not add `kotlinOptions`.

```groovy
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
}
```

- [ ] **Step 2: Write failing WatchApi tests**

Create `WatchApiTest.kt`:

```kotlin
package kr.forestmate.watch

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.FakeTransport
import kr.forestmate.core.api.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchApiTest {
    @Test
    fun claimsPairCode() {
        val transport = FakeTransport(
            postResponses = mapOf(
                "https://example.test/api/watch/pair/claim" to HttpResponse(200, """{"watch_token":"w1","hike_id":"h1","course_id":"c1","course_name":"Course","course_km":4.2,"course_elev":836,"route":"Loop"}""")
            )
        )
        val api = WatchApi(ApiConfig("https://example.test/api"), transport)

        val result = api.claim("123456")

        assertEquals("w1", result.watchToken)
        assertEquals("Course", result.courseName)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run:

```bash
./gradlew --no-daemon :wear:testDebugUnitTest --tests kr.forestmate.watch.WatchApiTest
```

Expected: FAIL because `WatchApi` is still Java static methods and does not match the Kotlin API.

- [ ] **Step 4: Implement WatchApi.kt**

Create Kotlin `WatchApi.kt`:

```kotlin
package kr.forestmate.watch

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.HttpTransport
import org.json.JSONObject

data class WatchClaimResult(
    val watchToken: String,
    val hikeId: String,
    val courseId: String,
    val courseName: String,
    val courseKm: Double?,
    val courseElev: Int?,
    val route: String,
)

data class WatchUploadResult(val progress: Double, val distressLevel: Int)

class WatchApi(
    private val config: ApiConfig,
    private val transport: HttpTransport,
) {
    fun claim(code: String): WatchClaimResult {
        val body = JSONObject().put("code", code).toString()
        val response = transport.post(config.url("/watch/pair/claim"), body)
        if (response.statusCode !in 200..299) error("HTTP ${response.statusCode} ${response.body}")
        val json = JSONObject(response.body)
        return WatchClaimResult(
            watchToken = json.getString("watch_token"),
            hikeId = json.optString("hike_id", ""),
            courseId = json.optString("course_id", ""),
            courseName = json.optString("course_name", ""),
            courseKm = if (json.isNull("course_km")) null else json.optDouble("course_km"),
            courseElev = if (json.isNull("course_elev")) null else json.optInt("course_elev"),
            route = json.optString("route", ""),
        )
    }
}
```

Delete `WatchApi.java`.

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
./gradlew --no-daemon :wear:testDebugUnitTest --tests kr.forestmate.watch.WatchApiTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add packaging/android/wear
git commit -m "feat(android): convert wear API client to Kotlin"
```

---

### Task 9: Convert Wear MainActivity And Sensor Service To Kotlin

**Files:**
- Delete: `packaging/android/wear/src/main/java/kr/forestmate/watch/MainActivity.java`
- Delete: `packaging/android/wear/src/main/java/kr/forestmate/watch/WatchSensorService.java`
- Create: `packaging/android/wear/src/main/java/kr/forestmate/watch/MainActivity.kt`
- Create: `packaging/android/wear/src/main/java/kr/forestmate/watch/WatchSensorService.kt`
- Modify: `packaging/android/wear/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write failing service request construction test**

Create `WatchUploadRequestTest.kt`:

```kotlin
package kr.forestmate.watch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchUploadRequestTest {
    @Test
    fun omitsNullOptionalSensorFields() {
        val json = WatchUploadRequest(hr = 88, lat = null, lon = null, alt = 0, acc = 120, battery = 72).toJson()

        assertEquals(88, json.getInt("hr"))
        assertEquals(120, json.getInt("acc"))
        assertEquals(72, json.getInt("battery"))
        assertEquals(false, json.has("lat"))
        assertEquals(false, json.has("lon"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew --no-daemon :wear:testDebugUnitTest --tests kr.forestmate.watch.WatchUploadRequestTest
```

Expected: FAIL because `WatchUploadRequest` does not exist.

- [ ] **Step 3: Implement upload request model**

Add to `WatchApi.kt`:

```kotlin
data class WatchUploadRequest(
    val hr: Int?,
    val lat: Double?,
    val lon: Double?,
    val alt: Int?,
    val acc: Int?,
    val battery: Int?,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("alt", alt ?: 0)
            if (hr != null) put("hr", hr)
            if (lat != null) put("lat", lat)
            if (lon != null) put("lon", lon)
            if (acc != null) put("acc", acc)
            if (battery != null) put("battery", battery)
        }
}
```

Add `upload(token: String, request: WatchUploadRequest): WatchUploadResult` to `WatchApi`.

- [ ] **Step 4: Convert MainActivity.kt**

Create Kotlin `MainActivity.kt` preserving these behaviors from Java:

- Shared preference keys remain byte-for-byte compatible.
- Permission request list covers heart rate, fine location, activity recognition, and notification permission.
- Primary action toggles sensor streaming when paired.
- Secondary action disconnects or toggles backup code entry.
- UI remains programmatic and compact for watch screens.

Keep custom drawing in a nested `WatchFaceView` class or split it into `WatchFaceView.kt` if the file exceeds 500 lines.

- [ ] **Step 5: Convert WatchSensorService.kt**

Create Kotlin `WatchSensorService.kt` preserving these behaviors from Java:

- Starts foreground with health/location service type when available.
- Registers heart rate, accelerometer, rotation vector, GPS, and network location sensors.
- Persists latest heart rate, battery, altitude, accelerometer magnitude, heading, lat/lon, progress, distress level, and upload time.
- Uploads every 5 seconds through `WatchApi.upload`.
- Shows "sent" or "waiting" notification states.

- [ ] **Step 6: Delete Java files and run tests/build**

Run:

```bash
rm packaging/android/wear/src/main/java/kr/forestmate/watch/MainActivity.java
rm packaging/android/wear/src/main/java/kr/forestmate/watch/WatchSensorService.java
./gradlew --no-daemon :wear:testDebugUnitTest
./gradlew --no-daemon :wear:assembleDebug
```

Expected: tests PASS and Wear debug APK builds.

- [ ] **Step 7: Commit**

```bash
git add packaging/android/wear
git commit -m "feat(android): convert wear app to Kotlin"
```

---

### Task 10: Update Release, Docs, And Metadata For Native Android

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `packaging/android/README.md`
- Modify: `.github/workflows/android-release.yml`
- Modify: `README.md`
- Modify: `README.en.md`
- Modify: `fastlane/metadata/android/en-US/full_description.txt`
- Modify: `fastlane/metadata/android/ko-KR/full_description.txt` if present
- Modify: `packaging/android/package.json`

- [ ] **Step 1: Write failing text parity test if README tests cover Android packaging**

Run existing readme parity test first:

```bash
python3 -m pytest server/tests/test_readme_parity.py -q
```

Expected: if Python deps are missing, record the exact missing module and continue with the text edits. If dependencies are installed, use the test output as the RED state for metadata updates.

- [ ] **Step 2: Update architecture text**

Change `docs/ARCHITECTURE.md`:

```markdown
- `packaging/android/`: Kotlin native Android phone app, Kotlin native Wear OS
  companion app, signing scripts, and packaging notes.
```

Replace runtime text that says Android packaging wraps the PWA with:

```markdown
The PWA remains the web install path. The Android APK is a native Kotlin client
that talks to the same FastAPI backend directly.
```

- [ ] **Step 3: Update Android README**

Replace Bubblewrap/TWA build language in `packaging/android/README.md` with:

```markdown
숲길동무 Android 배포는 Kotlin native phone APK와 Kotlin native Wear OS APK를
직접 빌드해 배포한다. 웹 사용자는 별도 APK 없이 PWA 설치 경로를 사용할 수 있다.
```

Use build commands:

```bash
./gradlew --no-daemon :app:assembleRelease :wear:assembleRelease
```

- [ ] **Step 4: Update release workflow**

Remove Node.js, pnpm, Bubblewrap install, and `pnpm run build:apk` steps from `.github/workflows/android-release.yml`. Replace build step with:

```yaml
      - name: Build signed phone and watch APKs
        env:
          BUBBLEWRAP_KEY_PASSWORD: ${{ secrets.BUBBLEWRAP_KEY_PASSWORD }}
          BUBBLEWRAP_KEYSTORE_PASSWORD: ${{ secrets.BUBBLEWRAP_KEYSTORE_PASSWORD }}
        run: |
          ./gradlew --no-daemon :app:assembleRelease :wear:assembleRelease
          mkdir -p dist
          cp app/build/outputs/apk/release/app-release.apk "dist/forestmate-android-${RELEASE_TAG#android-}.apk"
          cp wear/build/outputs/apk/release/wear-release.apk "dist/forestmate-wear-${RELEASE_TAG#android-}.apk"
          shasum -a 256 dist/*.apk > "dist/forestmate-android-${RELEASE_TAG#android-}.sha256"
```

- [ ] **Step 5: Update package metadata**

Change `packaging/android/package.json` description to:

```json
"description": "ForestMate Kotlin native Android and Wear OS APK packaging"
```

Remove Bubblewrap-only scripts: `init:project`, `doctor`, `validate:pwa`. Keep or replace `build:apk` with:

```json
"build:apk": "./gradlew --no-daemon :app:assembleDebug :wear:assembleDebug"
```

- [ ] **Step 6: Run docs checks**

Run:

```bash
rg -n "TWA|Trusted Web Activity|Bubblewrap|androidbrowserhelper|WebView wrapper" README.md README.en.md docs packaging/android fastlane .github
git diff --check
```

Expected: matches are allowed only when they describe the pre-port history or explicitly say the old TWA was replaced. No active packaging docs describe the phone APK as a TWA.

- [ ] **Step 7: Commit**

```bash
git add docs/ARCHITECTURE.md packaging/android/README.md .github/workflows/android-release.yml README.md README.en.md fastlane/metadata/android packaging/android/package.json
git commit -m "docs(android): document native Android distribution"
```

---

### Task 11: Final Verification And Cleanup

**Files:**
- No new files expected unless tests reveal a missing committed fixture.

- [ ] **Step 1: Run Android unit tests**

Run:

```bash
cd /Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port/packaging/android
./gradlew --no-daemon :core:testDebugUnitTest :app:testDebugUnitTest :wear:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build debug APKs**

Run:

```bash
./gradlew --no-daemon :app:assembleDebug :wear:assembleDebug
```

Expected:

- `packaging/android/app/build/outputs/apk/debug/app-debug.apk`
- `packaging/android/wear/build/outputs/apk/debug/wear-debug.apk`

- [ ] **Step 3: Run backend contract tests if Python deps are installed**

Run:

```bash
cd /Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port
python3 -m pytest server/tests/test_services.py server/tests/test_api.py server/tests/test_auth.py -q
```

Expected: PASS. If dependencies are missing, report the exact missing module and do not claim backend tests passed.

- [ ] **Step 4: Verify no TWA/WebView primary path remains**

Run:

```bash
rg -n "androidbrowserhelper|TrustedWebActivity|WebView|LauncherActivity|DelegationService" packaging/android/app packaging/android/build.gradle packaging/android/settings.gradle
```

Expected: no matches.

- [ ] **Step 5: Verify git status and branch**

Run:

```bash
git status --short --branch
git log --oneline --decorate -8
```

Expected: clean working tree on `codex/kotlin-native-port`.

- [ ] **Step 6: Codex worktree retention audit**

Run from the main checkout:

```bash
cd /Users/dong9733/Documents/GitHub/forest-mate
git worktree list --porcelain
git -C /Users/dong9733/.config/superpowers/worktrees/forest-mate/codex-kotlin-native-port status --short --untracked-files=all
```

Expected: worktree is preserved because this is an active feature branch. If the user asks to stop or merge later, make an explicit preserve/remove decision.

---

## Self-Review

- Spec coverage:
  - `:core` shared API and DTOs are covered by Tasks 2-4.
  - Native phone shell and five destinations are covered by Tasks 5-7.
  - Wear Java-to-Kotlin conversion is covered by Tasks 8-9.
  - Documentation and release metadata updates are covered by Task 10.
  - Verification and worktree retention are covered by Task 11.
- Placeholder scan: no forbidden placeholder tokens from the writing-plans skill should appear in executable task steps.
- Type consistency:
  - `ApiConfig`, `ApiResult`, `HttpTransport`, `FakeTransport`, and `ForestMateRepository` names are consistent across tasks.
  - Phone state names are under `kr.forestmate.app.state`.
  - Wear API names are under `kr.forestmate.watch`.
