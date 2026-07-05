package kr.forestmate.app

import kr.forestmate.core.model.Course
import kr.forestmate.core.model.Hazard

object LocalCatalog {
    private data class CourseShape(
        val id: String,
        val km: Double,
        val minutes: Int,
        val levelN: Int,
        val view: Int,
        val hazardPositions: List<Double>,
        val gps: String,
        val elevation: List<Int>,
    )

    private data class CourseText(
        val id: String,
        val name: String,
        val route: String,
        val level: String,
        val crowd: String,
        val peak: String,
        val gridNo: String,
        val rescuePoint: String,
        val fireStation: String,
        val hazards: List<HazardText>,
    )

    private data class HazardText(val type: String, val grade: String, val note: String)

    private val courseShapes = shapeTable(
        """
        bukhansan|4.2|190|2|4|0.62,0.38|37.6584,126.9778|120,180,260,390,480,542,650,770,836
        inwangsan|2.8|100|1|3|0.55|37.5772,126.9610|60,95,140,180,210,196,170,150,130
        achasan|3.5|140|1|5|0.70|37.5713,127.1030|40,80,130,170,210,240,262,280,287
        dobong|6.4|280|3|5|0.78,0.45|37.6987,127.0114|110,190,300,420,510,600,660,700,726
        """,
    )

    val courses: List<Course> = coursesFrom(
        """
        bukhansan|북한산 백운대 코스|백운대탐방지원센터 → 백운대 정상|중|보통|백운대 836m|다사 5683 2741|백운산장 헬기장 620m|서울 종로소방서 산악구조대|낙석주의~산사태 1등급~최근 2주 강우 누적 — 우회로 권장;급경사~사고다발 구간~스틱 사용·심박 주의
        inwangsan|인왕산 자락길 둘레|사직공원 → 수성동계곡|하|낮음|인왕산 338m|다사 5421 2856|황학정 진입로 280m|서울 종로소방서|혼잡구간~주말 정체~성곽길 합류 — 추월 자제
        achasan|아차산 해맞이 능선|아차산생태공원 → 해맞이광장|하|보통|아차산 287m|마바 1043 1822|해맞이광장 헬기포인트|구리소방서|암릉구간~주의~우천 시 미끄럼 — 난간 이용
        dobong|도봉산 신선대 코스|도봉탐방지원센터 → 신선대|상|높음|신선대 726m|다사 6122 3354|도봉대피소 410m|도봉소방서 산악구조대|Y계곡 암릉~사고다발~강풍 시 우회 권장;낙석주의~산사태 2등급~헬멧 권장 구간
        """,
    )
    val courseIds: Set<String> = courseShapes.map { it.id }.toSet()

    private val englishCourses: List<Course> = coursesFrom(
        """
        bukhansan|Bukhansan Baegundae Route|Baegundae Trail Support Center → Baegundae summit|Medium|Moderate|Baegundae 836m|Dasa 5683 2741|Baegunsanjang helipad 620m|Seoul Jongno Fire Station mountain rescue team|Rockfall caution~Landslide grade 1~Two-week rainfall buildup — detour recommended;Steep slope~Frequent-accident segment~Use poles and watch heart rate
        inwangsan|Inwangsan Foothill Loop|Sajik Park → Suseongdong Valley|Easy|Low|Inwangsan 338m|Dasa 5421 2856|Hwanghakjeong access road 280m|Seoul Jongno Fire Station|Crowded segment~Weekend congestion~Fortress path merge — avoid passing
        achasan|Achasan Sunrise Ridge|Achasan Ecological Park → Sunrise Plaza|Easy|Moderate|Achasan 287m|Maba 1043 1822|Sunrise Plaza heli point|Guri Fire Station|Rocky ridge~Caution~Slippery in rain — use handrails
        dobong|Dobongsan Sinseondae Route|Dobong Trail Support Center → Sinseondae|Hard|High|Sinseondae 726m|Dasa 6122 3354|Dobong shelter 410m|Dobong Fire Station mountain rescue team|Y Valley rocky ridge~Frequent accidents~Detour recommended in strong wind;Rockfall caution~Landslide grade 2~Helmet recommended segment
        """,
    )

    fun coursesFor(language: AppLanguage): List<Course> =
        if (language == AppLanguage.ENGLISH) englishCourses else courses

    private fun coursesFrom(raw: String): List<Course> {
        val textById = courseTexts(raw).associateBy { it.id }
        return courseShapes.map { shape ->
            val text = textById.getValue(shape.id)
            require(text.hazards.size == shape.hazardPositions.size) {
                "Course ${shape.id} has ${text.hazards.size} hazard labels for ${shape.hazardPositions.size} positions"
            }
            Course(
                id = shape.id,
                name = text.name,
                km = shape.km,
                minutes = shape.minutes,
                route = text.route,
                hazards = text.hazards.zip(shape.hazardPositions).map { (hazard, at) ->
                    Hazard(type = hazard.type, grade = hazard.grade, at = at, note = hazard.note)
                },
                level = text.level,
                levelN = shape.levelN,
                crowd = text.crowd,
                view = shape.view,
                peak = text.peak,
                gridNo = text.gridNo,
                gps = shape.gps,
                rescuePoint = text.rescuePoint,
                fireStation = text.fireStation,
                elevation = shape.elevation,
            )
        }
    }

    private fun courseTexts(raw: String): List<CourseText> =
        raw.trimIndent()
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { row ->
                val fields = row.split('|')
                require(fields.size == 10) { "Invalid course row: $row" }
                CourseText(
                    id = fields[0],
                    name = fields[1],
                    route = fields[2],
                    level = fields[3],
                    crowd = fields[4],
                    peak = fields[5],
                    gridNo = fields[6],
                    rescuePoint = fields[7],
                    fireStation = fields[8],
                    hazards = hazardTexts(fields[9]),
                )
            }
            .toList()

    private fun shapeTable(raw: String): List<CourseShape> =
        raw.trimIndent()
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { row ->
                val fields = row.split('|')
                require(fields.size == 8) { "Invalid course shape row: $row" }
                CourseShape(
                    id = fields[0],
                    km = fields[1].toDouble(),
                    minutes = fields[2].toInt(),
                    levelN = fields[3].toInt(),
                    view = fields[4].toInt(),
                    hazardPositions = fields[5].split(',').map { it.toDouble() },
                    gps = fields[6],
                    elevation = fields[7].split(',').map { it.toInt() },
                )
            }
            .toList()

    private fun hazardTexts(raw: String): List<HazardText> =
        raw.split(';').map { entry ->
            val fields = entry.split('~')
            require(fields.size == 3) { "Invalid hazard row: $entry" }
            HazardText(type = fields[0], grade = fields[1], note = fields[2])
        }
}
