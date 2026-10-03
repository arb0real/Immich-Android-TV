package nl.giejay.android.tv.immich.shared.util

import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.AssetFace
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.model.FocusArea
import nl.giejay.mediaslider.model.FocusAreaProvider
import timber.log.Timber

/**
 * Lazily loads the faces Immich detected in an asset ([GET /faces?id=]) so the slider can crop
 * around them. Search results don't reliably contain face bounding boxes across Immich versions.
 */
class FaceFocusAreaProvider(private val assetId: String) : FocusAreaProvider {

    override suspend fun getFocusAreas(): List<FocusArea> {
        return fetchFaces(assetId).fold(
            { error ->
                Timber.w("Could not load faces of asset $assetId: $error")
                emptyList()
            },
            { faces -> faces.toFocusAreas() }
        )
    }

    companion object {
        /** Test seam — production uses [ApiClient.getFaces]. */
        @Volatile
        var fetchFaces: suspend (String) -> Either<String, List<AssetFace>> = { assetId ->
            ApiClient.getClient(
                ApiClientConfig(
                    PreferenceManager.hostName,
                    PreferenceManager.get(API_KEY),
                    PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                    PreferenceManager.get(DEBUG_MODE)
                )
            ).getFaces(assetId)
        }
    }
}

internal fun List<AssetFace>.toFocusAreas(): List<FocusArea> {
    return filter { it.imageWidth > 0 && it.imageHeight > 0 }
        .map { face ->
            val width = face.imageWidth.toFloat()
            val height = face.imageHeight.toFloat()
            FocusArea(
                (minOf(face.boundingBoxX1, face.boundingBoxX2) / width).coerceIn(0f, 1f),
                (minOf(face.boundingBoxY1, face.boundingBoxY2) / height).coerceIn(0f, 1f),
                (maxOf(face.boundingBoxX1, face.boundingBoxX2) / width).coerceIn(0f, 1f),
                (maxOf(face.boundingBoxY1, face.boundingBoxY2) / height).coerceIn(0f, 1f)
            )
        }
        .filter { it.right > it.left && it.bottom > it.top }
}
