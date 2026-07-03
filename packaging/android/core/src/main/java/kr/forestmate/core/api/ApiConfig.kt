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
