package app.editors.manager.ui.adapters.holders.explorer

import android.view.View
import androidx.core.view.isVisible
import app.editors.manager.R
import app.editors.manager.databinding.LayoutExplorerListFooterBinding
import app.editors.manager.mvp.models.list.Footer
import app.editors.manager.ui.adapters.ExplorerAdapter
import app.editors.manager.ui.adapters.holders.BaseViewHolderExplorer

class ListFooterViewHolder(parent: View, adapter: ExplorerAdapter)
    : BaseViewHolderExplorer<Footer>(parent, adapter) {

    private val viewBinding = LayoutExplorerListFooterBinding.bind(parent)

    override fun bind(element: Footer) {
        viewBinding.listExplorerFooterLayout.isVisible = adapter.isFooter
    }

    override fun getCachedIcon(): View? = null

    companion object {

        val LAYOUT: Int = R.layout.layout_explorer_list_footer
    }
}