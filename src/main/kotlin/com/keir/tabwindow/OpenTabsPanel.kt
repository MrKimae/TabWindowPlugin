package com.keir.tabwindow

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
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
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DropMode
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/** Lists the tabs of one editor split. */
class OpenTabsPanel(
    private val project: Project,
    val window: EditorWindow,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val model = CollectionListModel<VirtualFile>()
    // Always fit the window width so long names are clipped and the pin stays at the right edge.
    val list: JBList<VirtualFile> = object : JBList<VirtualFile>(model), UiDataProvider {
        override fun getScrollableTracksViewportWidth() = true

        // Same data the editor tab label provides, so Rider's own tab popup actions work on the row.
        override fun uiDataSnapshot(sink: DataSink) {
            sink[CommonDataKeys.PROJECT] = project
            if (!isLive()) return
            sink[EditorWindow.DATA_KEY] = window
            val file = selectedValue ?: return
            window.findCompositeAndTab(file)?.second?.component?.let { DataSink.uiDataSnapshot(sink, it) }
            sink[CommonDataKeys.VIRTUAL_FILE] = file
        }
    }

    private var snapshot: List<Any?> = emptyList()

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.setExpandableItemsEnabled(false)
        list.emptyText.text = "No open tabs"
        list.cellRenderer = OpenTabRenderer(project) { window }
        TreeUIHelper.getInstance().installListSpeedSearch(list) { it.presentableName }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val file = fileAt(e) ?: return
                when {
                    SwingUtilities.isMiddleMouseButton(e) -> closeFiles(listOf(file))
                    SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger -> open(file, requestFocus = true)
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
        list.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val index = list.locationToIndex(Point(x, y))
                if (index < 0 || !list.getCellBounds(index, index).contains(x, y)) return
                list.selectedIndex = index
                val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP) as? ActionGroup ?: return
                ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_TAB_POPUP, group)
                    .apply { setTargetComponent(list) }
                    .component.show(comp, x, y)
            }
        })

        list.dragEnabled = true
        list.dropMode = DropMode.INSERT
        list.transferHandler = TabReorderTransferHandler(::moveTab)

        setContent(
            ScrollPaneFactory.createScrollPane(
                list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            ).apply { border = null },
        )
        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusesChanged() = list.repaint()
            override fun fileStatusChanged(virtualFile: VirtualFile) = list.repaint()
        }, this)
        refresh()
    }

    fun refresh() {
        if (project.isDisposed || !isLive()) return
        val files = window.fileList
        val selected = window.selectedFile
        val documentManager = FileDocumentManager.getInstance()

        val newSnapshot = listOf(
            selected, files, files.map { it.name }, files.map { window.isFilePinned(it) }, files.map { documentManager.isFileModified(it) },
        )
        if (newSnapshot == snapshot) return
        snapshot = newSnapshot

        if (model.items != files) model.replaceAll(files)
        if (selected != null) {
            list.setSelectedValue(selected, true)
        } else {
            list.clearSelection()
        }
        list.repaint()
    }

    private fun isLive() = window in FileEditorManagerEx.getInstanceEx(project).windows

    private fun liveWindow(): EditorWindow? = window.takeIf { isLive() }

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

    /** Moves the tab at [from] so it is inserted before the tab currently at [dropIndex]. */
    private fun moveTab(from: Int, dropIndex: Int) {
        val target = liveWindow() ?: return
        val file = model.items.getOrNull(from) ?: return
        val newIndex = if (dropIndex > from) dropIndex - 1 else dropIndex
        if (newIndex == from) return
        val tab = target.findCompositeAndTab(file)?.second ?: return
        if (!TabReorder.reorder(target.tabbedPane.tabs, tab, newIndex)) return
        refresh()
        list.setSelectedValue(file, true)
    }

    override fun dispose() = Unit
}
