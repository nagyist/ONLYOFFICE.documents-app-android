package app.editors.manager.ui.adapters.holders.explorer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import app.documents.core.model.cloud.Recent
import app.editors.manager.R
import app.editors.manager.databinding.LayoutExplorerGridFileBinding
import app.editors.manager.managers.utils.ManagerUiUtils.getFileThumbnail
import app.editors.manager.mvp.models.ui.RecentUI
import app.editors.manager.mvp.models.ui.toRecent
import lib.toolkit.base.managers.utils.StringUtils.getExtensionFromPath
import lib.toolkit.base.ui.adapters.holder.BaseViewHolder
import lib.toolkit.base.ui.adapters.holder.ViewType
import java.util.Locale

class GridRecentViewHolder(
    view: View,
    private val itemListener: ((recent: Recent) -> Unit)? = null,
    private val contextListener: ((recent: Recent, position: Int, thumbnail: Bitmap) -> Unit)? = null
) : BaseViewHolder<ViewType>(view) {

    private val binding = LayoutExplorerGridFileBinding.bind(view)

    override fun bind(item: ViewType) {
        if (item is RecentUI) {
            val icon = getFileThumbnail(getExtensionFromPath(item.name.lowercase(Locale.ROOT)), true)
            with(binding) {
                title.text = item.name
                subtitle.text = item.source ?: view.context.getString(R.string.this_device)
                image.setImageResource(icon)
                root.setCardBackgroundColor(null)
                root.setOnClickListener { itemListener?.invoke(item.toRecent()) }
                root.setOnLongClickListener {
                    val bitmap = Bitmap.createBitmap(
                        image.measuredWidth,
                        image.measuredHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    image.draw(canvas)
                    contextListener?.invoke(item.toRecent(), layoutPosition, bitmap)
                    return@setOnLongClickListener false
                }
            }
        }
    }
}