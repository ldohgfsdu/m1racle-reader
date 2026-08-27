package com.markleaf.app.ui.navigation

import com.markleaf.app.ui.hub.HubDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2.2 Phase 2 Dock 导航状态契约：四入口与路由一一对应、未知/阅读器路由不属
 * Hub（Dock 不显示）、入口顺序固定为 Home→Library→Bookmarks→Settings。
 */
class NavigationStateTest {

    @Test
    fun `dock has exactly four entries in fixed order`() {
        assertEquals(
            listOf("home", "library", "bookmarks", "settings"),
            HubDestination.entries.map { it.route },
        )
        assertEquals(HubDestination.HOME, HubDestination.entries.first())
        assertEquals(HubDestination.SETTINGS, HubDestination.entries.last())
    }

    @Test
    fun `routes round trip through fromRoute`() {
        for (dest in HubDestination.entries) {
            assertEquals(dest, HubDestination.fromRoute(dest.route))
        }
    }

    @Test
    fun `unknown and viewer routes resolve to null`() {
        assertNull(HubDestination.fromRoute(null))
        assertNull(HubDestination.fromRoute(""))
        assertNull(HubDestination.fromRoute("viewer/abc"))
        assertNull(HubDestination.fromRoute("pdf/xyz"))
        assertNull(HubDestination.fromRoute("unknown"))
    }

    @Test
    fun `dock visibility follows top level routes only`() {
        for (dest in HubDestination.entries) {
            assertTrue(HubDestination.isTopLevelRoute(dest.route))
        }
        assertFalse(HubDestination.isTopLevelRoute("viewer/abc"))
        assertFalse(HubDestination.isTopLevelRoute("pdf/xyz"))
        assertFalse(HubDestination.isTopLevelRoute(null))
    }

    @Test
    fun `nav graph route constants match hub destinations`() {
        assertEquals(Routes.HOME, HubDestination.HOME.route)
        assertEquals(Routes.LIBRARY, HubDestination.LIBRARY.route)
        assertEquals(Routes.BOOKMARKS, HubDestination.BOOKMARKS.route)
        assertEquals(Routes.SETTINGS, HubDestination.SETTINGS.route)
    }
}
