package kr.forestmate.core.api

import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val statusCode: Int, val body: String)

interface HttpTransport {
    fun get(url: String, bearerToken: String? = null): HttpResponse
    fun post(url: String, jsonBody: String, bearerToken: String? = null): HttpResponse
}

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 45000,
) : HttpTransport {
    override fun get(url: String, bearerToken: String?): HttpResponse =
        request("GET", url, null, bearerToken)

    override fun post(url: String, jsonBody: String, bearerToken: String?): HttpResponse =
        request("POST", url, jsonBody, bearerToken)

    private fun request(method: String, url: String, body: String?, bearerToken: String?): HttpResponse {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
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
            val statusCode = conn.responseCode
            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(statusCode, text)
        } catch (ex: IOException) {
            HttpResponse(0, "네트워크 연결 실패: ${ex.message ?: ex.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }
}
