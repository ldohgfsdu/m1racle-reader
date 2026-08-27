package com.m1racle.reader.ui.hub

/**
 * Hub 顶层目的地（Dock 四入口）。route 与 NavGraph.Routes 一一对应；
 * 阅读器路由（viewer/pdf）不属于 Hub，Dock 不在其上显示。
 */
enum class HubDestination(val route: String) {
    HOME("home"),
    LIBRARY("library"),
    BOOKMARKS("bookmarks"),
    SETTINGS("settings"),
    ;

    companion object {
        fun fromRoute(route: String?): HubDestination? =
            entries.firstOrNull { it.route == route }

        /** Dock 是否应显示在该路由上（四个顶层 Hub 目的地）。 */
        fun isTopLevelRoute(route: String?): Boolean = fromRoute(route) != null
    }
}
