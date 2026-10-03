package nl.giejay.android.tv.immich.shared.util

import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.api.model.AssetFace
import nl.giejay.mediaslider.model.FocusArea
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceFocusAreaProviderTest {
    private val originalFetch = FaceFocusAreaProvider.fetchFaces

    @After
    fun tearDown() {
        FaceFocusAreaProvider.fetchFaces = originalFetch
    }

    @Test
    fun `maps face bounding boxes to fractions of the image`() = runBlocking {
        FaceFocusAreaProvider.fetchFaces = { listOf(face(100, 50, 300, 250, 1000, 500)).right() }

        val areas = FaceFocusAreaProvider("a1").getFocusAreas()

        assertEquals(listOf(FocusArea(0.1f, 0.1f, 0.3f, 0.5f)), areas)
    }

    @Test
    fun `clamps boxes to the image and drops invalid faces`() {
        val areas = listOf(
            face(-10, -10, 200, 200, 1000, 1000),
            face(10, 10, 20, 20, 0, 0),
            face(50, 50, 50, 80, 1000, 1000)
        ).toFocusAreas()

        assertEquals(listOf(FocusArea(0f, 0f, 0.2f, 0.2f)), areas)
    }

    @Test
    fun `returns no focus areas when faces cannot be loaded`() = runBlocking {
        FaceFocusAreaProvider.fetchFaces = { "forbidden".left() }

        assertTrue(FaceFocusAreaProvider("a1").getFocusAreas().isEmpty())
    }

    private fun face(x1: Int, y1: Int, x2: Int, y2: Int, width: Int, height: Int) =
        AssetFace("f", width, height, x1, y1, x2, y2)
}
