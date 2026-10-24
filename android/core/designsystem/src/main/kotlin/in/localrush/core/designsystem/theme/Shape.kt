package `in`.localrush.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Rounded 16–20dp cards per the brand. */
val LRShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object LRRadius {
    val card = 16.dp
    val cardLarge = 20.dp
    val button = 12.dp
    val chip = 999.dp
    val sheet = 24.dp
}
