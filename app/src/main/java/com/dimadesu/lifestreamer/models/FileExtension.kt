package com.dimadesu.lifestreamer.models

enum class FileExtension(val extension: String) {
    TS(".ts"),
    FLV(".flv"),
    MP4(".mp4"),
    WEBM(".webm"),
    OGG(".ogg"),
    THREEGP(".3gp");

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
    }
}