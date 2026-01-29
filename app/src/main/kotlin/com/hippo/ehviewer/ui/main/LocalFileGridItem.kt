package com.hippo.ehviewer.ui.main

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ehviewer.core.ui.component.ElevatedCard
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.listThumbGenerator
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.local.LocalFileInfo

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope)
fun LocalFileGridItem(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    item: LocalFileInfo,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = ElevatedCard(
    modifier = modifier,
    onClick = onClick,
    onLongClick = onLongClick ?: {},
    interactionSource = interactionSource,
) {
    Column {
        Box {
            when (item) {
                is LocalFileInfo.Folder -> {
                    Box(
                        modifier = Modifier.aspectRatio(DEFAULT_RATIO).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.5f),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is LocalFileInfo.Archive -> {
                    AsyncImage(
                        model = item,
                        contentDescription = null,
                        modifier = Modifier.aspectRatio(DEFAULT_RATIO),
                        contentScale = ContentScale.Fit,
                    )
                }
                is LocalFileInfo.MetadataArchive -> {
                    AsyncImage(
                        model = item,
                        contentDescription = null,
                        modifier = Modifier.aspectRatio(DEFAULT_RATIO),
                        contentScale = ContentScale.Fit,
                    )
                    if (item.comicInfo.pageCount > 0) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd).widthIn(min = 32.dp).height(24.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(text = "${item.comicInfo.pageCount}")
                        }
                    }
                }
                is LocalFileInfo.KnownGallery -> {
                    val info = item.galleryInfo
                    val isHorizontal = info.thumbWidth > info.thumbHeight
                    val ratio = if (isHorizontal) 4f / 3f else DEFAULT_RATIO
                    with(listThumbGenerator) {
                        EhAsyncCropThumb(
                            key = info,
                            modifier = Modifier.aspectRatio(ratio).fillMaxSize(),
                        )
                    }
                    val categoryColor = EhUtils.getCategoryColor(info.category)
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd).widthIn(min = 32.dp).height(24.dp),
                        containerColor = categoryColor,
                        contentColor = EhUtils.getCategoryTextColor(categoryColor),
                    ) {
                        if (info.pages > 0) {
                            val readProgress = if (showProgress) {
                                remember { EhDB.getReadProgressFlow(info.gid) }.collectAsState(0).value
                            } else {
                                0
                            }
                            Text(text = if (readProgress > 0) "${readProgress + 1}/${info.pages}" else "${info.pages}")
                        }
                    }
                }
            }
        }
        val showJpnTitle by Settings.showJpnTitle.collectAsState()
        val displayTitle = when (item) {
            is LocalFileInfo.MetadataArchive -> {
                val comicInfo = item.comicInfo
                if (showJpnTitle) {
                    comicInfo.alternateSeries?.takeIf { it.isNotBlank() } ?: comicInfo.series ?: item.name
                } else {
                    comicInfo.series?.takeIf { it.isNotBlank() } ?: comicInfo.alternateSeries ?: item.name
                }
            }
            else -> item.name
        }
        Text(
            text = displayTitle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(4.dp),
        )
    }
}
