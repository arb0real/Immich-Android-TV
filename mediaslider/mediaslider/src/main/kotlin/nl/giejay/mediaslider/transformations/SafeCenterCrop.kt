package nl.giejay.mediaslider.transformations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import nl.giejay.mediaslider.model.FocusArea
import timber.log.Timber
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crops the image to fill the target (screen) aspect ratio, but never cuts off more than
 * [maxCutOffWidth]% of the image width or [maxCutOffHeight]% of the image height.
 * If a full crop would exceed the limit, the image is cropped up to the limit and the remaining
 * difference is letterboxed by the ImageView (so black bars are as small as allowed).
 *
 * When [focusAreas] (e.g. faces) are given, the crop window is moved to contain them instead of
 * being centered, and is widened if needed so no focus area gets cut off.
 */
class SafeCenterCrop(private val maxCutOffHeight: Int,
                     private val maxCutOffWidth: Int,
                     private val focusAreas: List<FocusArea> = emptyList()) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val crop = calculateCrop(toTransform.width, toTransform.height, outWidth, outHeight, maxCutOffWidth, maxCutOffHeight, focusAreas)
        if (crop.isFullCrop) {
            Timber.i("Safe cropping fully, cutting off ${crop.cutOffPercent}% (max width: $maxCutOffWidth%, max height: $maxCutOffHeight%, focus areas: ${focusAreas.size})")
            if (focusAreas.isEmpty()) {
                return TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight)
            }
            return cropAndScale(pool, toTransform, crop, outWidth, outHeight)
        }
        if (crop.width == toTransform.width && crop.height == toTransform.height) {
            return toTransform
        }
        Timber.i("Partially safe cropping to ${crop.width}x${crop.height} from ${toTransform.width}x${toTransform.height}, full crop would cut off ${crop.cutOffPercent}%")
        return Bitmap.createBitmap(toTransform, crop.x, crop.y, crop.width, crop.height)
    }

    private fun cropAndScale(pool: BitmapPool, src: Bitmap, crop: Crop, outWidth: Int, outHeight: Int): Bitmap {
        val result = pool.get(outWidth, outHeight, src.config ?: Bitmap.Config.ARGB_8888)
        TransformationUtils.setAlpha(src, result)
        Canvas(result).drawBitmap(
            src,
            Rect(crop.x, crop.y, crop.x + crop.width, crop.y + crop.height),
            Rect(0, 0, outWidth, outHeight),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        )
        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is SafeCenterCrop && other.maxCutOffHeight == maxCutOffHeight &&
                other.maxCutOffWidth == maxCutOffWidth && other.focusAreas == focusAreas
    }

    override fun hashCode(): Int {
        return ((ID.hashCode() * 31 + maxCutOffHeight) * 31 + maxCutOffWidth) * 31 + focusAreas.hashCode()
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("$ID-$maxCutOffHeight-$maxCutOffWidth-$focusAreas".toByteArray(CHARSET))
    }

    data class Crop(val x: Int, val y: Int, val width: Int, val height: Int, val isFullCrop: Boolean, val cutOffPercent: Int)

    companion object {
        private const val ID = "nl.giejay.mediaslider.transformations.SafeCenterCrop"

        /** Extra room around each focus area (fraction of its size), so e.g. hair and chin are kept. */
        private const val FOCUS_PADDING = 0.25

        /** Where the focus center should land vertically: slightly above the middle looks more natural. */
        private const val VERTICAL_FOCUS_POSITION = 0.4

        fun calculateCrop(srcWidth: Int, srcHeight: Int,
                          outWidth: Int, outHeight: Int,
                          maxCutOffWidth: Int, maxCutOffHeight: Int,
                          focusAreas: List<FocusArea> = emptyList()): Crop {
            if (srcWidth <= 0 || srcHeight <= 0 || outWidth <= 0 || outHeight <= 0) {
                return Crop(0, 0, srcWidth, srcHeight, false, 0)
            }
            val srcAspect = srcWidth.toDouble() / srcHeight
            val outAspect = outWidth.toDouble() / outHeight
            return if (srcAspect > outAspect) {
                // image is wider than the screen: cut off left and right
                val window = calculateWindow(srcWidth, srcHeight * outAspect, maxCutOffWidth,
                    focusAreas.map { it.left.toDouble() to it.right.toDouble() }, 0.5)
                Crop(window.start, 0, window.size, srcHeight, window.isFull, window.cutOffPercent)
            } else {
                // image is taller than the screen: cut off top and bottom
                val window = calculateWindow(srcHeight, srcWidth / outAspect, maxCutOffHeight,
                    focusAreas.map { it.top.toDouble() to it.bottom.toDouble() }, VERTICAL_FOCUS_POSITION)
                Crop(0, window.start, srcWidth, window.size, window.isFull, window.cutOffPercent)
            }
        }

        private data class Window(val start: Int, val size: Int, val isFull: Boolean, val cutOffPercent: Int)

        /**
         * Calculates the crop window along the one axis that needs cropping.
         * @param length image length along this axis
         * @param fillLength window length needed to fill the screen
         * @param focus focus areas along this axis as fractions (start to end)
         * @param focusPosition where in the window the center of the focus areas should be (0..1)
         */
        private fun calculateWindow(length: Int,
                                    fillLength: Double,
                                    maxCutOff: Int,
                                    focus: List<Pair<Double, Double>>,
                                    focusPosition: Double): Window {
            val cutOffPercent = ((1 - fillLength / length) * 100).roundToInt()
            var size = if (cutOffPercent <= maxCutOff) fillLength else max(fillLength, length * (1 - maxCutOff / 100.0))

            val validFocus = focus.filter { (start, end) -> end > start }
            if (validFocus.isEmpty()) {
                val windowSize = size.roundToInt().coerceIn(1, length)
                return Window((length - windowSize) / 2, windowSize, cutOffPercent <= maxCutOff, cutOffPercent)
            }

            // the part of the image that must stay visible: all focus areas plus some padding
            val required = validFocus.map { (start, end) ->
                val padding = (end - start) * FOCUS_PADDING
                (start - padding).coerceAtLeast(0.0) * length to (end + padding).coerceAtMost(1.0) * length
            }
            val requiredStart = required.minOf { it.first }
            val requiredEnd = required.maxOf { it.second }
            // widen the window (crop less) rather than cutting off a face
            size = max(size, requiredEnd - requiredStart)
            val windowSize = size.roundToInt().coerceIn(1, length)

            val focusCenter = (requiredStart + requiredEnd) / 2
            // keep the required part inside the window, then keep the window inside the image
            val start = (focusCenter - windowSize * focusPosition)
                .coerceIn(min(requiredEnd - windowSize, requiredStart), requiredStart)
                .coerceIn(0.0, (length - windowSize).toDouble())
            val isFull = windowSize <= fillLength.roundToInt()
            return Window(start.roundToInt().coerceIn(0, length - windowSize), windowSize, isFull, cutOffPercent)
        }
    }
}
