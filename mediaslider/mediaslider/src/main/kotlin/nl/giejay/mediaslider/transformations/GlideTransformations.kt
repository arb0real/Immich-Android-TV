package nl.giejay.mediaslider.transformations

import android.content.Context
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CenterInside
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.FocusArea

enum class GlideTransformations(val usesFocusAreas: Boolean = false,
                                val transform: (Context, MediaSliderConfiguration, List<FocusArea>) -> BitmapTransformation) {
    CENTER_CROP(transform = { _, _, _ -> CenterCrop() }),
    CENTER_INSIDE(transform = { _, _, _ -> CenterInside() }),
    SAFE_CENTER_CROP(transform = { _, config, _ -> SafeCenterCrop(config.maxCutOffHeight, config.maxCutOffWidth) }),
    SMART_CROP(usesFocusAreas = true, transform = { _, config, focusAreas -> SafeCenterCrop(config.maxCutOffHeight, config.maxCutOffWidth, focusAreas) });

    companion object {
        fun valueOfSafe(name: String, default: GlideTransformations): GlideTransformations {
            return entries.find { it.toString() == name } ?: default
        }
    }

}
