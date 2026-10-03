package nl.giejay.mediaslider.transformations

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import timber.log.Timber
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Center crops the image to fill the target (screen) aspect ratio, but never cuts off more than
 * [maxCutOffWidth]% of the image width or [maxCutOffHeight]% of the image height.
 * If a full crop would exceed the limit, the image is cropped up to the limit and the remaining
 * difference is letterboxed by the ImageView (so black bars are as small as allowed).
 */
class SafeCenterCrop(private val maxCutOffHeight: Int,
                     private val maxCutOffWidth: Int) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val crop = calculateCrop(toTransform.width, toTransform.height, outWidth, outHeight, maxCutOffWidth, maxCutOffHeight)
        if (crop.isFullCrop) {
            Timber.i("Safe cropping fully, cutting off ${crop.cutOffPercent}% (max width: $maxCutOffWidth%, max height: $maxCutOffHeight%)")
            return TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight)
        }
        if (crop.width == toTransform.width && crop.height == toTransform.height) {
            return toTransform
        }
        Timber.i("Partially safe cropping to ${crop.width}x${crop.height} from ${toTransform.width}x${toTransform.height}, full crop would cut off ${crop.cutOffPercent}%")
        return Bitmap.createBitmap(toTransform, crop.x, crop.y, crop.width, crop.height)
    }

    override fun equals(other: Any?): Boolean {
        return other is SafeCenterCrop && other.maxCutOffHeight == maxCutOffHeight && other.maxCutOffWidth == maxCutOffWidth
    }

    override fun hashCode(): Int {
        return ID.hashCode() * 31 * 31 + maxCutOffHeight * 31 + maxCutOffWidth
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("$ID-$maxCutOffHeight-$maxCutOffWidth".toByteArray(CHARSET))
    }

    data class Crop(val x: Int, val y: Int, val width: Int, val height: Int, val isFullCrop: Boolean, val cutOffPercent: Int)

    companion object {
        private const val ID = "nl.giejay.mediaslider.transformations.SafeCenterCrop"

        fun calculateCrop(srcWidth: Int, srcHeight: Int,
                          outWidth: Int, outHeight: Int,
                          maxCutOffWidth: Int, maxCutOffHeight: Int): Crop {
            if (srcWidth <= 0 || srcHeight <= 0 || outWidth <= 0 || outHeight <= 0) {
                return Crop(0, 0, srcWidth, srcHeight, false, 0)
            }
            val srcAspect = srcWidth.toDouble() / srcHeight
            val outAspect = outWidth.toDouble() / outHeight
            return if (srcAspect > outAspect) {
                // image is wider than the screen: cut off left and right
                val fillWidth = srcHeight * outAspect
                val cutOff = ((1 - fillWidth / srcWidth) * 100).roundToInt()
                if (cutOff <= maxCutOffWidth) {
                    Crop(0, 0, srcWidth, srcHeight, true, cutOff)
                } else {
                    val width = max(fillWidth, srcWidth * (1 - maxCutOffWidth / 100.0)).roundToInt().coerceIn(1, srcWidth)
                    Crop((srcWidth - width) / 2, 0, width, srcHeight, false, cutOff)
                }
            } else {
                // image is taller than the screen: cut off top and bottom
                val fillHeight = srcWidth / outAspect
                val cutOff = ((1 - fillHeight / srcHeight) * 100).roundToInt()
                if (cutOff <= maxCutOffHeight) {
                    Crop(0, 0, srcWidth, srcHeight, true, cutOff)
                } else {
                    val height = max(fillHeight, srcHeight * (1 - maxCutOffHeight / 100.0)).roundToInt().coerceIn(1, srcHeight)
                    Crop(0, (srcHeight - height) / 2, srcWidth, height, false, cutOff)
                }
            }
        }
    }
}
