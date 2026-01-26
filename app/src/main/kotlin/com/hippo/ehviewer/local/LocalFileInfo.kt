package com.hippo.ehviewer.local

import com.ehviewer.core.model.BaseGalleryInfo
import okio.Path

sealed class LocalFileInfo {
    abstract val path: Path
    abstract val name: String

    data class Folder(
        override val path: Path,
        override val name: String,
    ) : LocalFileInfo()

    data class Archive(
        override val path: Path,
        override val name: String,
        val gid: Long? = null,
    ) : LocalFileInfo()

    data class KnownGallery(
        override val path: Path,
        override val name: String,
        val galleryInfo: BaseGalleryInfo,
    ) : LocalFileInfo()
}
