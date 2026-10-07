package com.keir.tabwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBList
import com.intellij.ui.content.Content
import com.intellij.util.Alarm
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/** Lists the tabs of the current (last active) editor split. */
class OpenTabsPanel(
    private val project: Project,
    private val content: Content,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val model = CollectionListModel<VirtualFile>()
    val list = JBList(model)

    var window: EditorWindow? = null
        private set

    private var snapshot: List<Any?> = emptyList()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.emptyText.text = "No open tabs"
        list.cellRenderer = OpenTabRenderer(project) { window }
        TreeUIHelper.getInstance().installListSpeedSearch(list) { it.presentableName }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val file = fileAt(e) ?: return
                when {
                    SwingUtilities.isMiddleMouseButton(e) -> closeFiles(listOf(file))
                    SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger -> open(file, requestFocus = e.clickCount >= 2)
                }
            }
        })
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val file = list.selectedValue ?: return
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> { open(file, requestFocus = true); e.consume() }
                    KeyEvent.VK_DELETE -> { closeFiles(listOf(file)); e.consume() }
                }
            }
        })
        PopupHandler.installPopupMenu(list, createPopupGroup(), "OpenTabsPopup")

        setContent(ScrollPaneFactory.createScrollPane(list, true))
        subscribe()
        refresh()
        schedulePoll()
    }

    private fun subscribe() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) = refreshLater()
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) = refreshLater()
            override fun selectionChanged(event: FileEditorManagerEvent) = refreshLater()
        })
        connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { refreshLater() })
        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusesChanged() = list.repaint()
            override fun fileStatusChanged(virtualFile: VirtualFile) = list.repaint()
        }, this)
    }

    // No events exist for split focus changes, tab reordering or modified markers, so poll cheaply while visible.
    private fun schedulePoll() {
        alarm.addRequest({
            if (isShowing) refresh()
            schedulePoll()
        }, POLL_INTERVAL_MS)
    }

    private fun refreshLater() {
        ApplicationManager.getApplication().invokeLater({ refresh() }, project.disposed)
    }

    private fun refresh() {
        if (project.isDisposed) return
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val windows = manager.windows
        val current = manager.currentWindow?.takeIf { it in windows } ?: windows.firstOrNull()
        val files = current?.fileList ?: emptyList()
        val selected = current?.selectedFile
        val documentManager = FileDocumentManager.getInstance()

        val newSnapshot = listOf(
            current, windows.size, selected, files,
            files.map { it.name }, files.map { current?.isFilePinned(it) }, files.map { documentManager.isFileModified(it) },
        )
        if (newSnapshot == snapshot) return
        snapshot = newSnapshot
        window = current

        if (model.items != files) model.replaceAll(files)
        if (selected != null) {
            list.setSelectedValue(selected, true)
        } else {
            list.clearSelection()
        }
        content.displayName = if (windows.size > 1 && current != null) "Split ${windows.indexOf(current) + 1} of ${windows.size}" else null
        list.repaint()
    }

    private fun liveWindow(): EditorWindow? =
        window?.takeIf { it in FileEditorManagerEx.getInstanceEx(project).windows }

    private fun fileAt(e: MouseEvent): VirtualFile? {
        val index = list.locationToIndex(e.point)
        if (index < 0 || !list.getCellBounds(index, index).contains(e.point)) return null
        return model.getElementAt(index)
    }

    private fun open(file: VirtualFile, requestFocus: Boolean) {
        val target = liveWindow() ?: return
        FileEditorManagerEx.getInstanceEx(project).openFile(file, target, FileEditorOpenOptions(requestFocus = requestFocus))
    }

    private fun closeFiles(files: List<VirtualFile>) {
        val target = liveWindow() ?: return
        files.forEach { target.closeFile(it) }
        refresh()
    }

    private fun closableFiles(except: VirtualFile?): List<VirtualFile> {
        val target = window ?: return emptyList()
        return model.items.filter { it != except && !target.isFilePinned(it) }
    }

    private fun createPopupGroup() = DefaultActionGroup(
        listAction("Close Tab", AllIcons.Actions.Close) { closeFiles(listOf(it)) },
        listAction("Close Other Tabs") { closeFiles(closableFiles(except = it)) },
        DumbAwareAction.create("Close All Tabs") { closeFiles(closableFiles(except = null)) },
        object : DumbAwareAction("Pin Tab", null, AllIcons.General.Pin_tab) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                val file = list.selectedValue
                e.presentation.isEnabled = file != null
                e.presentation.text = if (file != null && window?.isFilePinned(file) == true) "Unpin Tab" else "Pin Tab"
            }

            override fun actionPerformed(e: AnActionEvent) {
                val file = list.selectedValue ?: return
                val target = window ?: return
                target.setFilePinned(file, !target.isFilePinned(file))
                refresh()
            }
        },
    )

    private fun listAction(text: String, icon: javax.swing.Icon? = null, perform: (VirtualFile) -> Unit) =
        object : DumbAwareAction(text, null, icon) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = list.selectedValue != null }
            override fun actionPerformed(e: AnActionEvent) { list.selectedValue?.let(perform) }
        }

    override fun dispose() = Unit

    private companion object {
        const val POLL_INTERVAL_MS = 300
    }
}
