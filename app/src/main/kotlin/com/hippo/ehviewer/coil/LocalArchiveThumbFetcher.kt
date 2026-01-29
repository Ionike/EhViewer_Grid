package com.hippo.ehviewer.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.ehviewer.core.files.openFileDescriptor
import com.hippo.ehviewer.jni.closeArchive
import com.hippo.ehviewer.jni.extractToByteBuffer
import com.hippo.ehviewer.jni.needPassword
import com.hippo.ehviewer.jni.openArchive
import com.hippo.ehviewer.jni.releaseByteBuffer
import com.hippo.ehviewer.local.LocalFileInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.FileSystem
import okio.Path

private val archiveMutex = Mutex()

private suspend fun fetchArchiveThumb(path: Path): FetchResult? {
    return archiveMutex.withLock {
        runCatching {
            path.openFileDescriptor("r").use { pfd ->
                val size = openArchive(pfd.fd, pfd.statSize, true)
                try {
                    if (size <= 0 || needPassword()) {
                        return@runCatching null
                    }
                    val buffer = extractToByteBuffer(0) ?: return@runCatching null
                    try {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val okioBuffer = Buffer().write(bytes)
                        SourceFetchResult(
                            source = ImageSource(okioBuffer, FileSystem.SYSTEM),
                            mimeType = null,
                            dataSource = DataSource.DISK,
                        )
                    } finally {
                        releaseByteBuffer(buffer)
                    }
                } finally {
                    closeArchive()
                }
            }
        }.getOrNull()
    }
}

class LocalArchiveThumbFetcher(
    private val data: LocalFileInfo.Archive,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = fetchArchiveThumb(data.path)

    class Factory : Fetcher.Factory<LocalFileInfo.Archive> {
        override fun create(
            data: LocalFileInfo.Archive,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = LocalArchiveThumbFetcher(data)
    }
}

class MetadataArchiveThumbFetcher(
    private val data: LocalFileInfo.MetadataArchive,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = fetchArchiveThumb(data.path)

    class Factory : Fetcher.Factory<LocalFileInfo.MetadataArchive> {
        override fun create(
            data: LocalFileInfo.MetadataArchive,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = MetadataArchiveThumbFetcher(data)
    }
}
