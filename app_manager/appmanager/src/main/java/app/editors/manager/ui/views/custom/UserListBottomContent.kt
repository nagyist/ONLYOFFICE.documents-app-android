package app.editors.manager.ui.views.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lib.compose.ui.theme.colorTextSecondary
import lib.compose.ui.views.AppDivider
import lib.compose.ui.views.AppTextButton
import lib.toolkit.base.R

@Composable
fun UserListBottomContent(
    nextButtonTitle: Int,
    count: Int? = null,
    access: Int? = null,
    accessList: List<Int> = emptyList(),
    onAccess: (Int) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onNext: () -> Unit
) {
    AppDivider()
    Row(
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        onDelete?.let {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                    contentDescription = null,
                    tint = MaterialTheme.colors.colorTextSecondary
                )
            }
        }
        count?.let {
            Text(text = "$count", style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)
        }
        access?.let {
            AccessIconButton(
                access = access,
                enabled = true,
                accessList = accessList,
                onAccess = onAccess::invoke
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AppTextButton(
            enabled = count?.let { count > 0 } ?: true,
            title = nextButtonTitle,
            onClick = onNext
        )
    }
}