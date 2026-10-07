package com.keir.tabwindow

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.lang.reflect.Method
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.TransferHandler

internal object TabReorder {
    private val log = logger<TabReorder>()

    // JBTabsImpl.reorderTab is what editor tab dragging uses, but it is protected.
    private val reorderMethod: Method? by lazy {
        runCatching {
            JBTabsImpl::class.java.getDeclaredMethod("reorderTab", TabInfo::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.onFailure { log.warn("JBTabsImpl.reorderTab not available; tab reordering disabled", it) }.getOrNull()
    }

    fun reorder(tabs: JBTabs, tab: TabInfo, newIndex: Int): Boolean {
        val impl = tabs as? JBTabsImpl ?: return false
        val method = reorderMethod ?: return false
        return runCatching {
            method.invoke(impl, tab, newIndex)
            impl.fireTabsMoved()
        }
            .onFailure { log.warn("Failed to reorder editor tab", it) }
            .isSuccess
    }
}

internal class TabReorderTransferHandler(private val move: (from: Int, dropIndex: Int) -> Unit) : TransferHandler() {

    override fun getSourceActions(c: JComponent) = MOVE

    override fun createTransferable(c: JComponent): Transferable? {
        val index = (c as? JList<*>)?.selectedIndex?.takeIf { it >= 0 } ?: return null
        return IndexTransferable(index)
    }

    override fun canImport(support: TransferSupport) =
        support.isDrop && support.isDataFlavorSupported(FLAVOR)

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        val location = support.dropLocation as? JList.DropLocation ?: return false
        val from = support.transferable.getTransferData(FLAVOR) as Int
        move(from, location.index)
        return true
    }

    private class IndexTransferable(private val index: Int) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(FLAVOR)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == FLAVOR
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != FLAVOR) throw UnsupportedFlavorException(flavor)
            return index
        }
    }

    private companion object {
        val FLAVOR = DataFlavor("${DataFlavor.javaJVMLocalObjectMimeType};class=java.lang.Integer", "Open tab index")
    }
}
