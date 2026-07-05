package kr.forestmate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignCopyTest {
    @Test
    fun tabLabelsMatchRedesign() {
        assertEquals(listOf("홈", "산행", "안전", "AI동무", "마이"), DesignCopy.korean.tabLabels)
        assertEquals(listOf("Home", "Hike", "Safety", "AI Guide", "My"), DesignCopy.english.tabLabels)
    }

    @Test
    fun userFacingCopyDoesNotExposeInternalConnectionState() {
        val combined = listOf(DesignCopy.korean, DesignCopy.english)
            .flatMap { it.userFacingStrings }
            .joinToString("\n")
        val forbidden = listOf("백엔드", "backend", "연결됨", "공공데이터 LIVE", "debug", "Debug")

        forbidden.forEach { phrase ->
            assertFalse("$phrase should not be user-facing", combined.contains(phrase))
        }
    }

    @Test
    fun redesignSectionsArePresent() {
        val combined = DesignCopy.korean.userFacingStrings.joinToString("\n")

        assertTrue(combined.contains("전국 산 검색"))
        assertTrue(combined.contains("안전 브리핑"))
        assertTrue(combined.contains("AI 숲해설사"))
        assertTrue(combined.contains("실시간 안전 이벤트"))
    }

    @Test
    fun englishSectionsArePresentWithoutHangul() {
        val combined = (DesignCopy.english.tabLabels + DesignCopy.english.userFacingStrings).joinToString("\n")

        assertTrue(combined.contains("Search mountains nationwide"))
        assertTrue(combined.contains("Safety Briefing"))
        assertTrue(combined.contains("AI forest guide"))
        assertTrue(combined.contains("Live safety events"))
        assertFalse("English copy should not contain Hangul", Regex("[가-힣]").containsMatchIn(combined))
    }

    @Test
    fun languageIsResolvedFromDeviceTagsAndStoredValues() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStoredValue("en"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromLanguageTag("ko-KR"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromStoredValue("ko"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromLanguageTag("ja-JP"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromStoredValue(""))
    }

    @Test
    fun localCatalogProvidesEnglishCourseCopy() {
        assertEquals(LocalCatalog.courses.map { it.id }, LocalCatalog.coursesFor(AppLanguage.ENGLISH).map { it.id })

        val combined = LocalCatalog.coursesFor(AppLanguage.ENGLISH)
            .flatMap { course ->
                listOf(course.name, course.route, course.level, course.crowd, course.peak, course.rescuePoint, course.fireStation) +
                    course.hazards.flatMap { listOf(it.type, it.grade, it.note) }
            }
            .joinToString("\n")

        assertTrue(combined.contains("Bukhansan Baegundae Route"))
        assertTrue(combined.contains("Landslide grade 1"))
        assertFalse("English local catalog should not contain Hangul", Regex("[가-힣]").containsMatchIn(combined))
    }
}
