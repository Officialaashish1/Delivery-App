package `in`.localrush.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Brand vector icons as Compose ImageVectors (24dp grid). Material icons re-exported for convenience
 * so features import one object.
 */
object LRIcons {
    val Search: ImageVector = Icons.Filled.Search
    val Bell: ImageVector = Icons.Outlined.Notifications
    val Close: ImageVector = Icons.Filled.Close

    /** Delivery scooter (side view, facing right). */
    val Scooter: ImageVector by lazy {
        ImageVector.Builder("Scooter", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // Body / deck
                moveTo(19f, 7f); horizontalLineToRelative(-1.5f); verticalLineToRelative(-2f); horizontalLineToRelative(-3.5f)
                verticalLineToRelative(1.5f); horizontalLineToRelative(2f); lineTo(16f, 10.5f); lineTo(10f, 10.5f)
                lineTo(8.6f, 7.5f); lineTo(3f, 7.5f); verticalLineToRelative(1.5f); horizontalLineToRelative(4.6f)
                lineTo(9f, 12f); lineTo(7.5f, 14f); horizontalLineToRelative(7f)
                curveTo(14.6f, 13.7f, 14.5f, 13.4f, 14.5f, 13f)
                curveTo(14.5f, 11f, 16.1f, 9.5f, 18f, 9.5f); horizontalLineToRelative(1f); close()
                // Rear wheel
                moveTo(5.5f, 14f); curveTo(3.6f, 14f, 2f, 15.6f, 2f, 17.5f); reflectiveCurveTo(3.6f, 21f, 5.5f, 21f)
                reflectiveCurveTo(9f, 19.4f, 9f, 17.5f); reflectiveCurveTo(7.4f, 14f, 5.5f, 14f); close()
                moveTo(5.5f, 19f); curveTo(4.7f, 19f, 4f, 18.3f, 4f, 17.5f); reflectiveCurveTo(4.7f, 16f, 5.5f, 16f)
                reflectiveCurveTo(7f, 16.7f, 7f, 17.5f); reflectiveCurveTo(6.3f, 19f, 5.5f, 19f); close()
                // Front wheel
                moveTo(18.5f, 14f); curveTo(16.6f, 14f, 15f, 15.6f, 15f, 17.5f); reflectiveCurveTo(16.6f, 21f, 18.5f, 21f)
                reflectiveCurveTo(22f, 19.4f, 22f, 17.5f); reflectiveCurveTo(20.4f, 14f, 18.5f, 14f); close()
                moveTo(18.5f, 19f); curveTo(17.7f, 19f, 17f, 18.3f, 17f, 17.5f); reflectiveCurveTo(17.7f, 16f, 18.5f, 16f)
                reflectiveCurveTo(20f, 16.7f, 20f, 17.5f); reflectiveCurveTo(19.3f, 19f, 18.5f, 19f); close()
                // Delivery box
                moveTo(3f, 3f); horizontalLineToRelative(5f); verticalLineToRelative(3.5f); horizontalLineTo(3f); close()
            }
        }.build()
    }

    /** Map pin with a fork & knife cut-out (restaurant). */
    val RestaurantPin: ImageVector by lazy {
        ImageVector.Builder("RestaurantPin", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                pin()
                // fork
                moveTo(9.2f, 5.5f); horizontalLineToRelative(1f); verticalLineToRelative(2.3f); horizontalLineToRelative(0.6f); verticalLineToRelative(-2.3f)
                horizontalLineToRelative(1f); verticalLineToRelative(2.3f); horizontalLineToRelative(0.6f); verticalLineToRelative(-2.3f); horizontalLineToRelative(1f)
                verticalLineToRelative(3f); curveToRelative(0f, 0.9f, -0.6f, 1.6f, -1.5f, 1.8f); verticalLineTo(14f); horizontalLineToRelative(-1.2f)
                verticalLineTo(10.3f); curveTo(9.8f, 10.1f, 9.2f, 9.4f, 9.2f, 8.5f); close()
                // knife
                moveTo(14.8f, 5.5f); curveToRelative(0.9f, 0.6f, 1.4f, 2f, 1.4f, 3.3f); curveToRelative(0f, 0.9f, -0.5f, 1.4f, -1.2f, 1.6f)
                verticalLineTo(14f); horizontalLineToRelative(-1.2f); verticalLineTo(5.5f); close()
            }
        }.build()
    }

    /** Map pin with a house cut-out (delivery address). */
    val HomePin: ImageVector by lazy {
        ImageVector.Builder("HomePin", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                pin()
                moveTo(12f, 5.8f); lineTo(7.8f, 9.4f); horizontalLineToRelative(1.2f); verticalLineTo(13.5f); horizontalLineToRelative(2.1f)
                verticalLineTo(11f); horizontalLineToRelative(1.8f); verticalLineTo(13.5f); horizontalLineToRelative(2.1f); verticalLineTo(9.4f)
                horizontalLineToRelative(1.2f); close()
            }
        }.build()
    }

    /** Plain location pin with a dot. */
    val LocationPin: ImageVector by lazy {
        ImageVector.Builder("LocationPin", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                pin()
                moveTo(12f, 7f); curveToRelative(-1.5f, 0f, -2.7f, 1.2f, -2.7f, 2.7f); reflectiveCurveTo(10.5f, 12.4f, 12f, 12.4f)
                reflectiveCurveToRelative(2.7f, -1.2f, 2.7f, -2.7f); reflectiveCurveTo(13.5f, 7f, 12f, 7f); close()
            }
        }.build()
    }

    /** Rider helmet-and-shoulders avatar placeholder. */
    val Rider: ImageVector by lazy {
        ImageVector.Builder("Rider", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f); curveTo(8.1f, 2f, 5f, 5.1f, 5f, 9f); verticalLineToRelative(1.5f); horizontalLineToRelative(14f)
                verticalLineTo(9f); curveTo(19f, 5.1f, 15.9f, 2f, 12f, 2f); close()
                moveTo(6f, 11.5f); horizontalLineToRelative(12f); verticalLineToRelative(1f); curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineTo(8f); curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f); close()
                moveTo(4f, 22f); curveToRelative(0f, -3.3f, 3.6f, -5.5f, 8f, -5.5f); reflectiveCurveToRelative(8f, 2.2f, 8f, 5.5f); close()
            }
        }.build()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.pin() {
        moveTo(12f, 2f)
        curveTo(7.6f, 2f, 4f, 5.6f, 4f, 10f)
        curveToRelative(0f, 5.5f, 8f, 12f, 8f, 12f)
        reflectiveCurveToRelative(8f, -6.5f, 8f, -12f)
        curveTo(20f, 5.6f, 16.4f, 2f, 12f, 2f)
        close()
    }
}
