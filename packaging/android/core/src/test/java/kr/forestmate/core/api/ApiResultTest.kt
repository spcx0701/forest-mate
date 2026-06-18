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
