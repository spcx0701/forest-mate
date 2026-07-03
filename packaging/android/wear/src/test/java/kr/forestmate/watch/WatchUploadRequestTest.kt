package kr.forestmate.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchUploadRequestTest {
    @Test
    fun omitsNullOptionalSensorFields() {
        val json = WatchUploadRequest(
            hr = 88,
            lat = null,
            lon = null,
            alt = 0,
            acc = 120,
            battery = 72,
        ).toJson()

        assertEquals(88, json.getInt("hr"))
        assertEquals(120, json.getInt("acc"))
        assertEquals(72, json.getInt("battery"))
        assertEquals(false, json.has("lat"))
        assertEquals(false, json.has("lon"))
    }
}
