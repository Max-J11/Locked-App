package com.max.locked.domain

/** One element on screen: its view id (without the "package:id/" prefix) and text. */
data class ScreenNode(val id: String?, val text: String?)

/** Pages that could switch Locked off. Strict mode closes them while locked. */
enum class ProtectedScreen { APP_INFO, OWN_SETTINGS_PAGE, CONFIRM_DIALOG, ACCESSIBILITY_APPS, UNINSTALL }

/** One UI's Accessibility › Installed apps page, the usual way to reach Locked's switch. */
private const val ACCESSIBILITY_APPS_TITLE = "Installed apps"

const val SETTINGS_PACKAGE = "com.android.settings"
val PACKAGE_INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")

/** Apps whose screens strict mode looks at. */
val STRICT_MODE_PACKAGES = PACKAGE_INSTALLERS + SETTINGS_PACKAGE

/**
 * Which protected page, if any, is showing. Matched against One UI 8.5's layouts:
 * - App info: header title is [appLabel] and there's a Force stop button.
 * - Locked's own page (Accessibility switch, notification access): page title is [appLabel] and
 *   there's an on/off switch bar at the top. Lists that merely contain a "Locked" row don't match.
 * - A Settings dialog naming [appLabel], like "Turn off Locked?" from that switch.
 * - Accessibility › Installed apps, when it lists [appLabel]. Closing it too means three quick
 *   checks to beat (list, page, dialog) instead of two. Other pages that list Locked don't match.
 * - Uninstall dialog: the package installer names [appLabel].
 */
fun protectedScreen(packageName: String, nodes: List<ScreenNode>, appLabel: String): ProtectedScreen? {
    fun has(id: String, text: String? = null) =
        nodes.any { it.id == id && (text == null || it.text == text) }

    return when (packageName) {
        SETTINGS_PACKAGE -> when {
            has("entity_header_title", appLabel) && has("forcestop_button") -> ProtectedScreen.APP_INFO
            has("collapsing_appbar_extended_title", appLabel) && has("sesl_switchbar_switch") ->
                ProtectedScreen.OWN_SETTINGS_PAGE
            nodes.any { it.id == "message" && it.text?.contains(appLabel) == true } && has("button1") ->
                ProtectedScreen.CONFIRM_DIALOG
            has("collapsing_appbar_extended_title", ACCESSIBILITY_APPS_TITLE) && has("title", appLabel) ->
                ProtectedScreen.ACCESSIBILITY_APPS
            else -> null
        }
        in PACKAGE_INSTALLERS -> if (has("app_label", appLabel)) ProtectedScreen.UNINSTALL else null
        else -> null
    }
}
