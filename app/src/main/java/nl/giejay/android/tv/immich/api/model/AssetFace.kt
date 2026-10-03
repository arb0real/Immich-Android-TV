package nl.giejay.android.tv.immich.api.model

/**
 * A face detected by Immich. Bounding box coordinates are in pixels of an image of size
 * [imageWidth] x [imageHeight].
 */
data class AssetFace(
    val id: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val boundingBoxX1: Int,
    val boundingBoxY1: Int,
    val boundingBoxX2: Int,
    val boundingBoxY2: Int
)
