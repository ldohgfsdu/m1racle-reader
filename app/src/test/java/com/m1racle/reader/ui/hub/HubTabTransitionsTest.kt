package com.m1racle.reader.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubTabTransitionsTest {

    @Test
    fun `direction follows dock order and ignores reader routes`() {
        assertEquals(1, HubTabTransitions.direction("home", "library"))
        assertEquals(1, HubTabTransitions.direction("library", "settings"))
        assertEquals(-1, HubTabTransitions.direction("settings", "home"))
        assertEquals(0, HubTabTransitions.direction("bookmarks", "bookmarks"))
        assertNull(HubTabTransitions.direction("home", "pdf/abc"))
        assertNull(HubTabTransitions.direction("viewer/x", "home"))
        assertNull(HubTabTransitions.direction(null, "library"))
    }

    @Test
    fun `hub pairs produce enter and exit while reader routes leave motion to the host`() {
        assertNotNull(HubTabTransitions.enter("home", "library"))
        assertNotNull(HubTabTransitions.exit("home", "library"))
        assertNotNull(HubTabTransitions.enter("settings", "home"))
        assertNull(HubTabTransitions.enter("home", "pdf/abc"))
        assertNull(HubTabTransitions.exit("pdf/abc", "home"))
    }

    @Test
    fun `hub pages switch directly without adding a decorative transition`() {
        assertEquals(androidx.compose.animation.EnterTransition.None, HubTabTransitions.enter("home", "library"))
        assertEquals(androidx.compose.animation.ExitTransition.None, HubTabTransitions.exit("home", "library"))
        assertEquals(androidx.compose.animation.EnterTransition.None, HubTabTransitions.enter("settings", "home"))
    }
}
