package com.dimadesu.lifestreamer.models

import io.github.thibaultbee.streampack.core.elements.endpoints.MediaContainerType

enum class FileExtension(
    val extension: String,
    val mimeType: String,
    val containerType: MediaContainerType
) {
    TS(".ts", "video/mp2ts", MediaContainerType.TS),
    FLV(".flv", "video/x-flv", MediaContainerType.FLV),
    MP4(".mp4", "video/mp4", MediaContainerType.MP4),
    WEBM(".webm", "video/webm", MediaContainerType.WEBM),
    OGG(".ogg", "video/ogg", MediaContainerType.OGG),
    THREEGP(".3gp", "video/3gpp", MediaContainerType.THREEGP);

    companion object {
        fun fromEndpointType(endpointType: EndpointType): FileExtension {
            return when (endpointType) {
                EndpointType.TS_FILE -> TS
                EndpointType.FLV_FILE -> FLV
                EndpointType.MP4_FILE -> MP4
                EndpointType.WEBM_FILE -> WEBM
                EndpointType.OGG_FILE -> OGG
                EndpointType.THREEGP_FILE -> THREEGP
                else -> throw IllegalArgumentException("Unknown extension: $endpointType")
            }
        }

        /** Removes a trailing file extension that this app adds, e.g. "video.ts" -> "video". */
        fun stripKnownExtension(fileName: String): String {
            val extension = entries.firstOrNull { fileName.endsWith(it.extension) }?.extension
            return if (extension != null) fileName.removeSuffix(extension) else fileName
        }

        fun fromContainerType(containerType: MediaContainerType): FileExtension {
            return entries.first { it.containerType == containerType }
        }
    }
}
