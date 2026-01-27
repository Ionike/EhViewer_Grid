package com.hippo.ehviewer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.list
import com.ehviewer.core.files.metadataOrNull
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FastScrollLazyVerticalGrid
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.Download
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.local.LocalFileInfo
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.LocalFileGridItem
import com.hippo.ehviewer.ui.main.reorderDense
import com.hippo.ehviewer.ui.navToReader
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import moe.tarsin.navigate
import okio.Path
import okio.Path.Companion.toPath

private val GID_REGEX = Regex("^(\\d+)[-_].*")
private val ARCHIVE_EXTENSIONS = listOf(".cbz", ".zip")

private fun extractGidFromName(name: String): Long? = GID_REGEX.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()

private enum class LocalSortMode {
    TitleAsc,
    TitleDesc,
    DateModifiedAsc,
    DateModifiedDesc,
}

private fun classifyFile(file: Path): LocalFileInfo? {
    val name = file.name
    val lowerName = name.lowercase()
    val lastModified = file.metadataOrNull()?.lastModifiedAtMillis ?: 0
    return when {
        ARCHIVE_EXTENSIONS.any { lowerName.endsWith(it) } -> {
            val gid = extractGidFromName(name)
            LocalFileInfo.Archive(file, name, lastModified, gid)
        }
        file.isDirectory -> {
            val downloadInfo = if (DownloadManager.isInitialized) {
                // First try to extract GID from folder name
                val gid = extractGidFromName(name)
                gid?.let { DownloadManager.getDownloadInfo(it) }
                    // Fallback: search by dirname match
                    ?: DownloadManager.downloadInfoList.find { it.dirname == name }
            } else {
                null
            }
            if (downloadInfo != null) {
                LocalFileInfo.KnownGallery(file, name, lastModified, downloadInfo.galleryInfo)
            } else {
                LocalFileInfo.Folder(file, name, lastModified)
            }
        }
        else -> null
    }
}

private suspend fun loadFiles(path: Path, sortMode: LocalSortMode): List<LocalFileInfo> {
    return withIOContext {
        path.list()
            .mapNotNull { classifyFile(it) }
            .sortedWith(
                compareBy<LocalFileInfo> {
                    when (it) {
                        is LocalFileInfo.Folder -> 0
                        is LocalFileInfo.KnownGallery -> 1
                        is LocalFileInfo.Archive -> 2
                    }
                }.then(
                    when (sortMode) {
                        LocalSortMode.TitleAsc -> compareBy { it.name.lowercase() }
                        LocalSortMode.TitleDesc -> compareByDescending { it.name.lowercase() }
                        LocalSortMode.DateModifiedAsc -> compareBy { it.lastModified }
                        LocalSortMode.DateModifiedDesc -> compareByDescending { it.lastModified }
                    },
                ),
            )
    }
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.LocalReaderScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val title = stringResource(id = R.string.local_reader)
    val hint = stringResource(R.string.search_bar_hint, title)
    val animateItems by Settings.animateItems.collectAsState()
    val showProgress by Settings.showReadingProgress.collectAsState()

    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by remember { mutableIntStateOf(0) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var sortModeOrdinal by rememberSaveable { mutableIntStateOf(LocalSortMode.DateModifiedDesc.ordinal) }
    val sortMode = LocalSortMode.entries[sortModeOrdinal]
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val rootPath = remember { downloadLocation.toString() }
    var currentPath by rememberSaveable { mutableStateOf(rootPath) }
    val pathStack = remember { mutableStateListOf<String>() }
    val files = remember { mutableStateListOf<LocalFileInfo>() }

    DrawerHandle(!searchBarExpanded)

    BackHandler(enabled = pathStack.isNotEmpty() || currentPath != rootPath) {
        if (pathStack.isNotEmpty()) {
            currentPath = pathStack.removeAt(pathStack.lastIndex)
        } else if (currentPath != rootPath) {
            currentPath = rootPath
        }
    }

    LaunchedEffect(currentPath, keyword, sortMode) {
        val path = currentPath.toPath()
        val loaded = loadFiles(path, sortMode)
        files.clear()
        if (keyword.isEmpty()) {
            files.addAll(loaded)
        } else {
            files.addAll(loaded.filter { it.name.contains(keyword, ignoreCase = true) })
        }
    }

    val density = LocalDensity.current

    fun onItemClick(item: LocalFileInfo) {
        when (item) {
            is LocalFileInfo.Folder -> {
                pathStack.add(currentPath)
                currentPath = item.path.toString()
            }
            is LocalFileInfo.Archive -> {
                navToReader(item.path.toString())
            }
            is LocalFileInfo.KnownGallery -> {
                launchIO { EhDB.putHistoryInfo(item.galleryInfo) }
                navToReader(item.galleryInfo)
            }
        }
    }

    fun onItemLongClick(item: LocalFileInfo) {
        when (item) {
            is LocalFileInfo.KnownGallery -> {
                navigate(item.galleryInfo.asDst())
            }
            else -> {
                // No action for non-database items
            }
        }
    }

    SearchBarScreen(
        onApplySearch = { keyword = it },
        expanded = searchBarExpanded,
        onExpandedChange = { searchBarExpanded = it },
        title = title,
        searchFieldHint = hint,
        searchBarOffsetY = { searchBarOffsetY },
        trailingIcon = {
            IconButton(onClick = { sortMenuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(imageVector = Icons.AutoMirrored.Default.Sort, contentDescription = null)
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.title_asc)) },
                    onClick = {
                        sortMenuExpanded = false
                        sortModeOrdinal = LocalSortMode.TitleAsc.ordinal
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.title_desc)) },
                    onClick = {
                        sortMenuExpanded = false
                        sortModeOrdinal = LocalSortMode.TitleDesc.ordinal
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = "Date modified (ascending)") },
                    onClick = {
                        sortMenuExpanded = false
                        sortModeOrdinal = LocalSortMode.DateModifiedAsc.ordinal
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = "Date modified (descending)") },
                    onClick = {
                        sortMenuExpanded = false
                        sortModeOrdinal = LocalSortMode.DateModifiedDesc.ordinal
                    },
                )
            }
        },
    ) { contentPadding ->
        val gridInterval = dimensionResource(com.hippo.ehviewer.R.dimen.gallery_grid_interval)
        val thumbColumns by Settings.thumbColumns.collectAsState()
        val realThumbColumns = maxOf(2, thumbColumns)
        val realPadding = contentPadding + PaddingValues(
            dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_list_margin_h),
            dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_list_margin_v),
        )
        val searchBarConnection = remember {
            val topPaddingPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = -consumed.y
                    searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                    return Offset.Zero
                }
            }
        }

        if (files.isEmpty()) {
            Column(
                modifier = Modifier.padding(realPadding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = EhIcons.Big.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(id = R.string.no_download_info),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        } else {
            val denseList by remember(files.toList(), realThumbColumns) {
                derivedStateOf {
                    val indices = reorderDense(files, realThumbColumns) { item ->
                        when (item) {
                            is LocalFileInfo.KnownGallery -> {
                                val isHorizontal = item.galleryInfo.thumbWidth > item.galleryInfo.thumbHeight
                                if (isHorizontal) 2 else 1
                            }
                            else -> 1
                        }
                    }
                    indices.map { files[it] }
                }
            }

            FastScrollLazyVerticalGrid(
                columns = GridCells.Fixed(realThumbColumns),
                modifier = Modifier.nestedScroll(searchBarConnection).fillMaxSize(),
                contentPadding = realPadding,
                verticalArrangement = Arrangement.spacedBy(gridInterval),
                horizontalArrangement = Arrangement.spacedBy(gridInterval),
            ) {
                items(
                    items = denseList,
                    key = { it.path.toString() },
                    span = { item ->
                        when (item) {
                            is LocalFileInfo.KnownGallery -> {
                                val isHorizontal = item.galleryInfo.thumbWidth > item.galleryInfo.thumbHeight
                                if (isHorizontal) GridItemSpan(2) else GridItemSpan(1)
                            }
                            else -> GridItemSpan(1)
                        }
                    },
                ) { item ->
                    LocalFileGridItem(
                        onClick = { onItemClick(item) },
                        onLongClick = when (item) {
                            is LocalFileInfo.KnownGallery -> ({ onItemLongClick(item) })
                            else -> null
                        },
                        item = item,
                        showProgress = showProgress,
                        modifier = Modifier.thenIf(animateItems) { animateItem() },
                    )
                }
            }
        }
    }
}
