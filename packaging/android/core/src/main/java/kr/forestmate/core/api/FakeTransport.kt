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
