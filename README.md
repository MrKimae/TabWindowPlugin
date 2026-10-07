# Open Tabs Window

A [Rider](https://www.jetbrains.com/rider/) / IntelliJ Platform plugin that adds an **Open Tabs** tool
window listing the open editor tabs — like the Switcher (`Ctrl+Tab`), but docked as a regular tool
window next to Explorer and Bookmarks.

## What it does

- Lists the tabs of the **current editor split** in tab order. With split editors, the list follows the
  split that was last active, and the tool window title shows e.g. `Split 2 of 3`.
- Shows file icon, VCS status colour, modified marker (`*`), pinned state and parent folder; the active
  tab is bold.
- Click selects the tab in the editor (focus stays in the list); double-click or `Enter` also focuses
  the editor.
- Middle-click or `Delete` closes a tab. Context menu: Close Tab, Close Other Tabs, Close All Tabs,
  Pin/Unpin Tab.
- Speed search: just start typing.

## Requirements

- Rider 2026.2 or later (see `pluginSinceBuild` in `gradle.properties`)
- JDK 17 to build (auto-provisioned via the Gradle Foojay toolchain resolver); Gradle itself can run on
  Rider's bundled JBR

## Building

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\Rider\jbr"
.\gradlew.bat buildPlugin
```

The plugin zip is produced at `build\distributions\tab-window-<version>.zip`.

## Installing

In Rider: **Settings → Plugins → gear icon → Install Plugin from Disk...**, then select the zip.
Open the window via **View → Tool Windows → Open Tabs**.

## Running / debugging

```powershell
.\gradlew.bat runIde
```

## Project structure

| Path | Description |
|------|-------------|
| `src/main/kotlin/com/keir/tabwindow/OpenTabsToolWindowFactory.kt` | Tool window registration |
| `src/main/kotlin/com/keir/tabwindow/OpenTabsPanel.kt` | List, tracking of the current split, actions |
| `src/main/kotlin/com/keir/tabwindow/OpenTabRenderer.kt` | Cell rendering |
| `src/main/resources/META-INF/plugin.xml` | Plugin manifest |
