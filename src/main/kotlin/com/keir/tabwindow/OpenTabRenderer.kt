package com.keir.tabwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class OpenTabRenderer(
    private val project: Project,
    private val window: () -> EditorWindow?,
) : ListCellRenderer<VirtualFile> {

    private val text = TextRenderer()
    private val pin = JLabel().apply { border = JBUI.Borders.emptyRight(4) }
    private val panel = JPanel(BorderLayout()).apply {
        add(text, BorderLayout.CENTER)
        add(pin, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out VirtualFile>,
        value: VirtualFile,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ): Component {
        text.getListCellRendererComponent(list, value, index, selected, hasFocus)
        pin.icon = if (window()?.isFilePinned(value) == true) AllIcons.Actions.PinTab else null
        panel.background = text.background
        panel.toolTipText = value.presentableUrl
        return panel
    }

    private inner class TextRenderer : ColoredListCellRenderer<VirtualFile>() {
        override fun customizeCellRenderer(
            list: JList<out VirtualFile>,
            value: VirtualFile,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            icon = IconUtil.getIcon(value, Iconable.ICON_FLAG_READ_STATUS, project)

            val color = FileStatusManager.getInstance(project).getStatus(value).color
            val style = if (window()?.selectedFile == value) SimpleTextAttributes.STYLE_BOLD else SimpleTextAttributes.STYLE_PLAIN
            append(EditorTabPresentationUtil.getEditorTabTitle(project, value), SimpleTextAttributes(style, color))

            if (FileDocumentManager.getInstance().isFileModified(value)) {
                append(" *", SimpleTextAttributes(style, color))
            }
        }
    }
}
