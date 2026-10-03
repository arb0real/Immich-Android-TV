package nl.giejay.mediaslider.transformations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeCenterCropTest {

    @Test
    fun fullyCropsWhenCutOffIsWithinLimit() {
        // 4:3 photo on a 16:9 screen loses 25% of its height
        val crop = SafeCenterCrop.calculateCrop(4032, 3024, 1920, 1080, maxCutOffWidth = 30, maxCutOffHeight = 30)
        assertTrue(crop.isFullCrop)
        assertEquals(25, crop.cutOffPercent)
    }

    @Test
    fun partiallyCropsHeightUpToLimit() {
        val crop = SafeCenterCrop.calculateCrop(4032, 3024, 1920, 1080, maxCutOffWidth = 20, maxCutOffHeight = 20)
        assertFalse(crop.isFullCrop)
        assertEquals(4032, crop.width)
        assertEquals(2419, crop.height)
        assertEquals((3024 - 2419) / 2, crop.y)
        assertEquals(0, crop.x)
    }

    @Test
    fun partiallyCropsWidthOfPanoramaUpToLimit() {
        val crop = SafeCenterCrop.calculateCrop(6000, 1500, 1920, 1080, maxCutOffWidth = 20, maxCutOffHeight = 0)
        assertFalse(crop.isFullCrop)
        assertEquals(4800, crop.width)
        assertEquals(1500, crop.height)
        assertEquals(600, crop.x)
    }

    @Test
    fun doesNotCropWhenAspectRatioMatches() {
        val crop = SafeCenterCrop.calculateCrop(3840, 2160, 1920, 1080, maxCutOffWidth = 0, maxCutOffHeight = 0)
        assertTrue(crop.isFullCrop)
        assertEquals(0, crop.cutOffPercent)
    }

    @Test
    fun keepsImageWhenTargetSizeIsUnknown() {
        val crop = SafeCenterCrop.calculateCrop(4032, 3024, Int.MIN_VALUE, Int.MIN_VALUE, 20, 20)
        assertFalse(crop.isFullCrop)
        assertEquals(4032, crop.width)
        assertEquals(3024, crop.height)
    }
}
