package com.keir.tabwindow

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import javax.swing.JList

class OpenTabRenderer(
    private val project: Project,
    private val window: () -> EditorWindow?,
) : ColoredListCellRenderer<VirtualFile>() {

    override fun customizeCellRenderer(
        list: JList<out VirtualFile>,
        value: VirtualFile,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        val editorWindow = window()
        icon = IconUtil.getIcon(value, Iconable.ICON_FLAG_READ_STATUS, project)

        val color = FileStatusManager.getInstance(project).getStatus(value).color
        val style = if (editorWindow?.selectedFile == value) SimpleTextAttributes.STYLE_BOLD else SimpleTextAttributes.STYLE_PLAIN
        append(EditorTabPresentationUtil.getEditorTabTitle(project, value), SimpleTextAttributes(style, color))

        if (FileDocumentManager.getInstance().isFileModified(value)) {
            append(" *", SimpleTextAttributes(style, color))
        }
        if (editorWindow?.isFilePinned(value) == true) {
            append("  pinned", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
        }
        locationOf(value)?.let { append("  $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }

        toolTipText = value.presentableUrl
    }

    private fun locationOf(file: VirtualFile): String? {
        val parent = file.parent ?: return null
        val basePath = project.basePath
        if (basePath != null) {
            FileUtil.getRelativePath(basePath, parent.path, '/')
                ?.takeUnless { it.startsWith("..") }
                ?.let { return if (it == ".") null else it }
        }
        return FileUtil.getLocationRelativeToUserHome(parent.presentableUrl)
    }
}
