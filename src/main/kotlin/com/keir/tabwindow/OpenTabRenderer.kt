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
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JList

/** Renders a row like the editor tab: same title, icon, colors and pin. */
class OpenTabRenderer(
    private val project: Project,
    private val window: () -> EditorWindow?,
) : ColoredListCellRenderer<VirtualFile>() {

    private var pinIcon: Icon? = null

    override fun customizeCellRenderer(
        list: JList<out VirtualFile>,
        value: VirtualFile,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        val editorWindow = window()
        val tab = editorWindow?.findCompositeAndTab(value)?.second
        val isActive = editorWindow?.selectedFile == value

        icon = tab?.icon ?: IconUtil.getIcon(value, Iconable.ICON_FLAG_READ_STATUS, project)
        if (tab != null) appendTabTitle(tab, isActive) else appendFallbackTitle(value, isActive)

        if (!selected) tab?.tabColor?.let { background = it }
        pinIcon = if (tab?.isPinned == true || editorWindow?.isFilePinned(value) == true) AllIcons.Actions.PinTab else null
        toolTipText = value.presentableUrl
    }

    private fun appendTabTitle(tab: TabInfo, bold: Boolean) {
        val texts = tab.coloredText.texts
        val attributes = tab.coloredText.attributes
        if (texts.isEmpty()) {
            append(tab.text, SimpleTextAttributes(styleOf(SimpleTextAttributes.STYLE_PLAIN, bold), tab.defaultForeground))
            return
        }
        texts.forEachIndexed { i, text ->
            val attr = attributes.getOrNull(i) ?: SimpleTextAttributes.REGULAR_ATTRIBUTES
            val foreground = attr.fgColor ?: tab.defaultForeground
            append(text, SimpleTextAttributes(attr.bgColor, foreground, attr.waveColor, styleOf(attr.style, bold)))
        }
    }

    private fun appendFallbackTitle(file: VirtualFile, bold: Boolean) {
        val color = FileStatusManager.getInstance(project).getStatus(file).color
        val style = styleOf(SimpleTextAttributes.STYLE_PLAIN, bold)
        append(EditorTabPresentationUtil.getEditorTabTitle(project, file), SimpleTextAttributes(style, color))
        if (FileDocumentManager.getInstance().isFileModified(file)) append(" *", SimpleTextAttributes(style, color))
    }

    private fun styleOf(style: Int, bold: Boolean) = if (bold) style or SimpleTextAttributes.STYLE_BOLD else style

    // Painted directly so the pin sits at the row's right edge, like the tab's close/pin button;
    // text is clipped before it so long names never run underneath.
    override fun paintComponent(g: Graphics) {
        val icon = pinIcon
        if (icon == null) {
            super.paintComponent(g)
            return
        }
        val pinLeft = width - icon.iconWidth - JBUI.scale(PIN_GAP)
        if (isOpaque) {
            g.color = background
            g.fillRect(0, 0, width, height)
        }
        val textGraphics = g.create()
        try {
            textGraphics.clipRect(0, 0, pinLeft - JBUI.scale(PIN_GAP), height)
            super.paintComponent(textGraphics)
        } finally {
            textGraphics.dispose()
        }
        icon.paintIcon(this, g, pinLeft, (height - icon.iconHeight) / 2)
    }

    private companion object {
        const val PIN_GAP = 4
    }
}
