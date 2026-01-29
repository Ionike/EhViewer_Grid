package com.hippo.ehviewer.local

import com.ehviewer.core.model.BaseGalleryInfo
import com.hippo.ehviewer.spider.ComicInfo
import okio.Path

sealed class LocalFileInfo {
    abstract val path: Path
    abstract val name: String
    abstract val lastModified: Long

    data class Folder(
        override val path: Path,
        override val name: String,
        override val lastModified: Long = 0,
    ) : LocalFileInfo()

    data class Archive(
        override val path: Path,
        override val name: String,
        override val lastModified: Long = 0,
        val gid: Long? = null,
    ) : LocalFileInfo()

    data class MetadataArchive(
        override val path: Path,
        override val name: String,
        override val lastModified: Long = 0,
        val comicInfo: ComicInfo,
    ) : LocalFileInfo()

    data class KnownGallery(
        override val path: Path,
        override val name: String,
        override val lastModified: Long = 0,
        val galleryInfo: BaseGalleryInfo,
    ) : LocalFileInfo()
}
