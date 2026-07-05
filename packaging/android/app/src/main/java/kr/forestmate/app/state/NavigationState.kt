package kr.forestmate.app.state

enum class PhoneTab(val id: String) {
    HOME("home"),
    HIKE("hike"),
    SOS("sos"),
    AI("ai"),
    MY("my"),
}

data class NavigationState(val selected: PhoneTab = PhoneTab.HOME) {
    fun select(id: String): NavigationState =
        PhoneTab.entries.firstOrNull { it.id == id }?.let { copy(selected = it) } ?: this
}
