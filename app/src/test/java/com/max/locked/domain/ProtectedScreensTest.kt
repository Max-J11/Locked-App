package com.max.locked.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Screens as captured from One UI 8.5 on the A54. */
class ProtectedScreensTest {

    private val label = "Locked"
    private val installer = "com.google.android.packageinstaller"

    private fun appInfo(app: String) = listOf(
        ScreenNode("collapsing_appbar_extended_title", "App info"),
        ScreenNode("entity_header_title", app),
        ScreenNode("entity_header_summary", "Installed"),
        ScreenNode("title", "Notifications"),
        ScreenNode("open_button", null),
        ScreenNode("uninstall_button", null),
        ScreenNode("forcestop_button", null),
    )

    private val ownAccessibilityPage = listOf(
        ScreenNode("collapsing_appbar_extended_title", "Locked"),
        ScreenNode("sesl_switchbar_container", "Locked"),
        ScreenNode("sesl_switchbar_switch", null),
        ScreenNode("title", "Locked shortcut"),
        ScreenNode("title", "App info"),
    )

    private val installedAppsList = listOf(
        ScreenNode("collapsing_appbar_extended_title", "Installed apps"),
        ScreenNode("title", "Bitwarden"),
        ScreenNode("title", "Locked"),
        ScreenNode("switch_widget", null),
    )

    private fun uninstallDialog(app: String) = listOf(
        ScreenNode("alertTitle", "Uninstall this app?"),
        ScreenNode("app_label", app),
        ScreenNode("button2", "Cancel"),
        ScreenNode("button1", "Uninstall"),
    )

    @Test
    fun `Locked's App info is protected`() {
        assertEquals(ProtectedScreen.APP_INFO, protectedScreen(SETTINGS_PACKAGE, appInfo("Locked"), label))
    }

    @Test
    fun `another app's App info is not`() {
        assertNull(protectedScreen(SETTINGS_PACKAGE, appInfo("Chrome"), label))
    }

    @Test
    fun `Locked's Accessibility page is protected`() {
        assertEquals(
            ProtectedScreen.OWN_SETTINGS_PAGE,
            protectedScreen(SETTINGS_PACKAGE, ownAccessibilityPage, label),
        )
    }

    @Test
    fun `the Accessibility Installed apps list is protected when it lists Locked`() {
        assertEquals(
            ProtectedScreen.ACCESSIBILITY_APPS,
            protectedScreen(SETTINGS_PACKAGE, installedAppsList, label),
        )
    }

    @Test
    fun `other lists that show Locked are not`() {
        val appsList = listOf(
            ScreenNode("collapsing_appbar_extended_title", "Apps"),
            ScreenNode("title", "Chrome"),
            ScreenNode("title", "Locked"),
        )
        assertNull(protectedScreen(SETTINGS_PACKAGE, appsList, label))
    }

    @Test
    fun `uninstalling Locked is protected, other apps are not`() {
        assertEquals(ProtectedScreen.UNINSTALL, protectedScreen(installer, uninstallDialog("Locked"), label))
        assertNull(protectedScreen(installer, uninstallDialog("Chrome"), label))
    }

    private fun confirmDialog(message: String) = listOf(
        ScreenNode("message", message),
        ScreenNode("button2", "Cancel"),
        ScreenNode("button1", "Turn off"),
    )

    @Test
    fun `the Turn off Locked dialog is protected`() {
        assertEquals(
            ProtectedScreen.CONFIRM_DIALOG,
            protectedScreen(SETTINGS_PACKAGE, confirmDialog("Turn off Locked?"), label),
        )
    }

    @Test
    fun `turning off another service is not`() {
        assertNull(protectedScreen(SETTINGS_PACKAGE, confirmDialog("Turn off Bitwarden?"), label))
    }

    @Test
    fun `the same layout in another app is ignored`() {
        assertNull(protectedScreen("com.example.other", appInfo("Locked"), label))
    }
}
