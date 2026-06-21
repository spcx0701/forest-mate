package kr.forestmate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignCopyTest {
    @Test
    fun tabLabelsMatchRedesign() {
        assertEquals(listOf("홈", "산행", "안전", "AI동무", "마이"), DesignCopy.tabLabels)
    }

    @Test
    fun userFacingCopyDoesNotExposeInternalConnectionState() {
        val combined = DesignCopy.userFacingStrings.joinToString("\n")
        val forbidden = listOf("백엔드", "backend", "연결됨", "공공데이터 LIVE", "debug", "Debug")

        forbidden.forEach { phrase ->
            assertFalse("$phrase should not be user-facing", combined.contains(phrase))
        }
    }

    @Test
    fun redesignSectionsArePresent() {
        val combined = DesignCopy.userFacingStrings.joinToString("\n")

        assertTrue(combined.contains("전국 산 검색"))
        assertTrue(combined.contains("안전 브리핑"))
        assertTrue(combined.contains("AI 숲해설사"))
        assertTrue(combined.contains("실시간 안전 이벤트"))
    }
}
