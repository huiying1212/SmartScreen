package com.datacollector.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.datacollector.android.views.MoodFaceView

/**
 * Compose wrapper around the existing [MoodFaceView]. Re-uses the
 * stable Java rendering / animation code without forcing a rewrite.
 */
@Composable
fun MoodFace(
    score: Int,
    style: String = "CLASSIC",
    sizeDp: Int = 200,
    modifier: Modifier = Modifier,
) {
    val stress = (score.coerceIn(0, 100) / 100f)
    AndroidView(
        modifier = modifier.size(sizeDp.dp),
        factory = { ctx ->
            MoodFaceView(ctx).apply {
                setFaceStyle(MoodFaceView.FaceStyle.fromName(style))
                setStressImmediate(stress)
            }
        },
        update = { v ->
            v.setFaceStyle(MoodFaceView.FaceStyle.fromName(style))
            v.setStress(stress)
        },
    )
}
