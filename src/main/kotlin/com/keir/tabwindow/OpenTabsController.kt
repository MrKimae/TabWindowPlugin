package com.keir.tabwindow

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Alarm

/** Keeps one tool window content tab per editor split and selects the tab of the active split. */
class OpenTabsController(
    private val project: Project,
    private val toolWindow: ToolWindow,
) : Disposable {

    private val contents = LinkedHashMap<EditorWindow, Content>()
    private var lastCurrent: EditorWindow? = null
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) = syncLater()
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) = syncLater()
            override fun selectionChanged(event: FileEditorManagerEvent) = syncLater()
        })
        connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { syncLater() })
        sync()
        schedulePoll()
    }

    // No events exist for split creation/focus, tab reordering or tab color/icon updates, so poll cheaply.
    private fun schedulePoll() {
        alarm.addRequest({
            if (toolWindow.isVisible) sync(repaint = true)
            schedulePoll()
        }, POLL_INTERVAL_MS)
    }

    private fun syncLater() {
        ApplicationManager.getApplication().invokeLater({ sync() }, project.disposed)
    }

    private fun sync(repaint: Boolean = false) {
        if (project.isDisposed || toolWindow.isDisposed) return
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val windows = ordered(manager.windows.toList())
        val contentManager = toolWindow.contentManager

        (contents.keys - windows.toSet()).forEach { removed ->
            contents.remove(removed)?.let { contentManager.removeContent(it, true) }
        }
        windows.forEachIndexed { index, window ->
            val content = contents.getOrPut(window) { createContent(window) }
            if (contentManager.getIndexOfContent(content) != index) {
                if (contentManager.getIndexOfContent(content) >= 0) contentManager.removeContent(content, false)
                contentManager.addContent(content, index)
            }
            content.displayName = if (windows.size > 1) "Split ${index + 1}" else null
            val panel = content.component as OpenTabsPanel
            panel.refresh()
            if (repaint) panel.list.repaint()
        }

        // Only follow the editor when the active split changes, so a split tab chosen by hand stays selected.
        val current = manager.currentWindow?.takeIf { it in contents }
        if (current != null && current != lastCurrent) {
            lastCurrent = current
            contents[current]?.let { if (contentManager.selectedContent != it) contentManager.setSelectedContent(it, false) }
        }
    }

    private fun createContent(window: EditorWindow): Content {
        val panel = OpenTabsPanel(project, window)
        return ContentFactory.getInstance().createContent(panel, null, false).apply {
            preferredFocusableComponent = panel.list
            setDisposer(panel)
            isCloseable = false
        }
    }

    // Order splits as laid out on screen: top to bottom, then left to right.
    private fun ordered(windows: List<EditorWindow>): List<EditorWindow> {
        val positions = windows.associateWith { window ->
            val component = window.tabbedPane.tabs.component
            if (component.isShowing) component.locationOnScreen else null
        }
        if (positions.values.any { it == null }) {
            val known = contents.keys.toList()
            return windows.sortedBy { known.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        }
        return windows.sortedWith(compareBy<EditorWindow>({ positions.getValue(it)!!.y }, { positions.getValue(it)!!.x }))
    }

    override fun dispose() {
        contents.clear()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 300
    }
}
