package com.dualboot.dsu.model

import android.net.Uri

object DSUConstants {
    const val DEFAULT_IMAGE_SIZE = -1L
    const val DEFAULT_USERDATA = 8L * 1024L * 1024L * 1024L // 8 GB default
}

open class ImagePartition(
    var partitionName: String,
    var uri: Uri = Uri.EMPTY,
    var fileSize: Long = DSUConstants.DEFAULT_IMAGE_SIZE,
)

enum class SourceType {
    NONE, SINGLE_SYSTEM_IMAGE, DSU_PACKAGE
}

open class DSUInstallationSource(
    open var type: SourceType = SourceType.NONE,
    open var uri: Uri = Uri.EMPTY,
    open var fileSize: Long = DSUConstants.DEFAULT_IMAGE_SIZE,
    open val images: List<ImagePartition> = listOf(),
) {
    data class SingleSystemImage(
        override var uri: Uri = Uri.EMPTY,
        override var fileSize: Long = DSUConstants.DEFAULT_IMAGE_SIZE,
        override var type: SourceType = SourceType.SINGLE_SYSTEM_IMAGE,
    ) : DSUInstallationSource(SourceType.SINGLE_SYSTEM_IMAGE)

    data class DsuPackage(
        override var uri: Uri = Uri.EMPTY,
        override var type: SourceType = SourceType.DSU_PACKAGE,
    ) : DSUInstallationSource(SourceType.DSU_PACKAGE)
}
