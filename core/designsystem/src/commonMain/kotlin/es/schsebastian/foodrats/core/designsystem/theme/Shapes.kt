package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

internal val FoodRatsShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small      = RoundedCornerShape(Radius.sm),
    medium     = RoundedCornerShape(Radius.md),
    large      = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)
