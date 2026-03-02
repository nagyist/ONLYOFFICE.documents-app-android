package lib.compose.ui.views

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lib.compose.ui.theme.ManagerTheme
import lib.compose.ui.theme.colorTextPrimary
import lib.toolkit.base.R


@Composable
fun AppStepperItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    titleColor: Color = MaterialTheme.colors.colorTextPrimary,
    enabled: Boolean = true,
    dividerVisible: Boolean = true,
    buttonDownEnabled: Boolean = true,
    buttonUpEnabled: Boolean = true,
    onDownClick: () -> Unit,
    onUpClick: () -> Unit,
    onValueClick: (() -> Unit)? = null
) {
    AppListItem(
        modifier = modifier,
        title = title,
        titleColor = titleColor,
        dividerVisible = dividerVisible,
        enabled = enabled,
        paddingEnd = 0.dp,
        endContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.primary
                ) {
                    IconButton(
                        onClick = onDownClick,
                        enabled = enabled && buttonDownEnabled
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_minus_small),
                            contentDescription = null,
                        )
                    }
                    Text(
                        modifier = Modifier
                            .widthIn(min = 56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorResource(R.color.colorBackdrop))
                            .clickable(
                                enabled = onValueClick != null,
                                onClick = { onValueClick?.invoke() }
                            )
                            .padding(vertical = 2.dp, horizontal = 8.dp),
                        text = value,
                        color = titleColor,
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = onUpClick,
                        enabled = enabled && buttonUpEnabled
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_add_small),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun AppStepperItem(
    @StringRes title: Int,
    value: String,
    onDownClick: () -> Unit,
    onUpClick: () -> Unit
) {
    AppStepperItem(
        title = stringResource(id = title),
        value = value,
        onDownClick = onDownClick,
        onUpClick = onUpClick
    )
}

@Preview
@Composable
private fun AppStepperItemPreview() {
    ManagerTheme {
        Surface {
            Column {
                AppStepperItem(
                    title = "Stepper Item",
                    onDownClick = { },
                    onUpClick = { },
                    value = "123",
                    buttonUpEnabled = false
                )
                AppStepperItem(
                    title = "Stepper Item",
                    value = "123",
                    enabled = false,
                    onDownClick = { },
                    onUpClick = { }
                )
            }
        }
    }
}