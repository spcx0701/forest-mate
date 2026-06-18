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
