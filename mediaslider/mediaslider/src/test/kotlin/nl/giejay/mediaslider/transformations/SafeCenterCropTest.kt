package nl.giejay.mediaslider.transformations

import nl.giejay.mediaslider.model.FocusArea
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

    @Test
    fun movesCropWindowToFace() {
        // portrait photo, face near the top: a centered crop would cut it off
        val face = FocusArea(0.4f, 0.15f, 0.6f, 0.3f)
        val crop = SafeCenterCrop.calculateCrop(3024, 4032, 1920, 1080, 100, 100, listOf(face))
        assertTrue(crop.isFullCrop)
        assertEquals(1701, crop.height)
        assertEquals(227, crop.y)
        assertFaceInside(crop, face, 3024, 4032)
    }

    @Test
    fun keepsCropWindowInsideImageForFaceAtEdge() {
        val face = FocusArea(0.9f, 0.3f, 0.97f, 0.6f)
        val crop = SafeCenterCrop.calculateCrop(6000, 1500, 1920, 1080, 100, 100, listOf(face))
        assertTrue(crop.isFullCrop)
        assertEquals(6000 - crop.width, crop.x)
        assertFaceInside(crop, face, 6000, 1500)
    }

    @Test
    fun cropsLessInsteadOfCuttingOffFaces() {
        val top = FocusArea(0.4f, 0.05f, 0.6f, 0.15f)
        val bottom = FocusArea(0.4f, 0.8f, 0.6f, 0.9f)
        val crop = SafeCenterCrop.calculateCrop(3024, 4032, 1920, 1080, 100, 100, listOf(top, bottom))
        assertFalse(crop.isFullCrop)
        assertTrue(crop.height > 1701)
        assertFaceInside(crop, top, 3024, 4032)
        assertFaceInside(crop, bottom, 3024, 4032)
    }

    @Test
    fun respectsMaxCutOffWithFaces() {
        val face = FocusArea(0.4f, 0.0f, 0.6f, 0.1f)
        val crop = SafeCenterCrop.calculateCrop(4032, 3024, 1920, 1080, 20, 20, listOf(face))
        assertFalse(crop.isFullCrop)
        assertEquals(2419, crop.height)
        assertEquals(0, crop.y)
    }

    @Test
    fun ignoresEmptyFocusAreas() {
        val crop = SafeCenterCrop.calculateCrop(4032, 3024, 1920, 1080, 20, 20, listOf(FocusArea(0.5f, 0.5f, 0.5f, 0.5f)))
        assertEquals(SafeCenterCrop.calculateCrop(4032, 3024, 1920, 1080, 20, 20), crop)
    }

    private fun assertFaceInside(crop: SafeCenterCrop.Crop, face: FocusArea, width: Int, height: Int) {
        assertTrue(crop.x <= face.left * width && crop.x + crop.width >= face.right * width)
        assertTrue(crop.y <= face.top * height && crop.y + crop.height >= face.bottom * height)
    }
}
