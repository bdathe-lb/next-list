package com.example.nextlist.core.navigation

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val glyph: String,
) {
    GROUPS(
        route = "home/groups",
        label = "小组",
        glyph = "组",
    ),
    ACTIVITY_FEED(
        route = "home/feed",
        label = "动态",
        glyph = "动",
    ),
    PROFILE(
        route = "home/profile",
        label = "我的",
        glyph = "我",
    ),
}
