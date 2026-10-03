package nl.giejay.mediaslider.model

/**
 * A region of interest (e.g. a detected face) in an image, as fractions (0..1) of the image
 * width and height, in display orientation.
 */
data class FocusArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

interface FocusAreaProvider {
    suspend fun getFocusAreas(): List<FocusArea>
}
