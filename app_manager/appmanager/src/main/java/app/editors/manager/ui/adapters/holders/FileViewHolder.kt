package app.editors.manager.ui.adapters.holders

import android.view.View
import androidx.core.view.isVisible
import app.documents.core.network.manager.models.explorer.CloudFile
import app.editors.manager.R
import app.editors.manager.databinding.ListExplorerFilesBinding
import app.editors.manager.ui.adapters.ExplorerAdapter
import lib.toolkit.base.managers.utils.StringUtils
import lib.toolkit.base.managers.utils.TimeUtils

class FileViewHolder(itemView: View, adapter: ExplorerAdapter) :
    BaseViewHolderExplorer<CloudFile>(itemView, adapter) {

    private var viewBinding = ListExplorerFilesBinding.bind(itemView)

    init {
        viewBinding.listExplorerFileLayout.setOnClickListener{ view ->
            adapter.mOnItemClickListener?.onItemClick(view, layoutPosition)
        }

        viewBinding.listExplorerFileLayout.setOnLongClickListener { view ->
            adapter.mOnItemLongClickListener?.onItemLongClick(view, layoutPosition)
            false
        }

        viewBinding.listExplorerFileContext.setOnClickListener {
            adapter.mOnItemContextListener?.onItemContextClick(layoutPosition)
        }
    }

    override fun bind(file: CloudFile) {
        // Get file info
        val filesInfo: String = StringUtils.getHtmlString(file.createdBy.displayName) + PLACEHOLDER_POINT
            .takeIf { file.createdBy.displayName.isNotEmpty() }.orEmpty() +
                TimeUtils.getWeekDate(file.updated) + PLACEHOLDER_POINT +
                StringUtils.getFormattedSize(adapter.context, file.pureContentLength)

        if (adapter.preferenceTool.selfId.equals(file.createdBy.id, ignoreCase = true)) {
            if (!adapter.isSectionMy) {
                filesInfo + PLACEHOLDER_POINT + adapter.context.getString(R.string.item_owner_self)
            }
        } else if (file.createdBy.title.isNotEmpty()) {
            filesInfo + PLACEHOLDER_POINT + file.createdBy.displayName
        }

        with(viewBinding) {
            listExplorerFileName.text = file.title
            listExplorerFileInfo.text = filesInfo
            listExplorerFileContext.isVisible = true
            listExplorerFileFavorite.isVisible = file.favorite

            viewIconSelectableLayout.item = file
            viewIconSelectableLayout.selectMode = adapter.isSelectMode
            viewIconSelectableLayout.itemSelected = file.isSelected
        }
    }

    companion object {
        val LAYOUT: Int = R.layout.list_explorer_files
    }
}