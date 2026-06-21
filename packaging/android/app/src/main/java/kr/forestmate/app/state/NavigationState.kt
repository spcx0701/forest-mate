package kr.forestmate.app.state

enum class PhoneTab(val id: String, val label: String) {
    HOME("home", "홈"),
    HIKE("hike", "산행"),
    SOS("sos", "안전"),
    AI("ai", "AI동무"),
    MY("my", "마이"),
}

data class NavigationState(val selected: PhoneTab = PhoneTab.HOME) {
    fun select(id: String): NavigationState =
        PhoneTab.entries.firstOrNull { it.id == id }?.let { copy(selected = it) } ?: this
}
