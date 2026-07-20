package es.schsebastian.foodrats.feature.stats.presentation.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrim
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfIsoWeek
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfMonth
import es.schsebastian.foodrats.feature.stats.domain.model.MealCalendarMonth
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import es.schsebastian.foodrats.feature.stats.presentation.components.formatOneDecimal
import es.schsebastian.foodrats.feature.stats.presentation.toStringKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel

/**
 * "My plates" — a Monday-first month grid of the signed-in member's own meals in the active crew.
 * Days with meals show the first meal's thumbnail; tapping a day lists that day's meals below the
 * grid; tapping a meal opens the existing meal detail. Structural scaffold mirrors
 * [es.schsebastian.foodrats.feature.achievements.presentation.AchievementsScreen] (media floor +
 * floating glass chrome).
 */
@Composable
fun MealCalendarScreen(
    onBack: () -> Unit,
    onMealClick: (mealId: String, dayIso: String) -> Unit,
    vm: MealCalendarViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, dim = 0.32f, scrim = FrScrimStyle.Even)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 56.dp)
                .verticalScroll(rememberScrollState())
                .frSafeHorizontalPadding()
                .frContentWidth(Breakpoints.contentMax)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val error = state.error
            when {
                error != null -> ErrorTile(
                    message = resolve(error.toStringKey()),
                    onRetry = { vm.onIntent(MealCalendarIntent.Retry) },
                )
                else -> {
                    val month = state.month
                    if (month != null) {
                        MonthHeader(
                            month = month,
                            today = state.today,
                            onPrevious = { vm.onIntent(MealCalendarIntent.PreviousMonth) },
                            onNext = { vm.onIntent(MealCalendarIntent.NextMonth) },
                        )
                        WeekdayHeader()
                        val calendar = state.calendar
                        if (calendar == null) {
                            MonthGridSkeleton(month)
                        } else {
                            MonthGrid(
                                month = month,
                                calendar = calendar,
                                selectedDay = state.selectedDay,
                                onDaySelected = { vm.onIntent(MealCalendarIntent.DaySelected(it)) },
                            )
                            if (calendar.mealsByDay.isEmpty()) {
                                EmptyMonth()
                            }
                            val dayMeals = state.selectedDay?.let { calendar.mealsByDay[it] }.orEmpty()
                            dayMeals.forEach { meal ->
                                CalendarMealRow(
                                    meal = meal,
                                    onClick = { onMealClick(meal.meal.id.value, meal.meal.day.toKey()) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(Spacing.xl))
        }

        // Floating chrome — back (left) + centered title (mirrors AchievementsScreen).
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().frSafeHorizontalPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrGlassCircleButton(
                icon = FrIcons.Back,
                onClick = onBack,
                contentDescription = resolve(StatsStringKey.CalendarBackCta),
            )
            FrText(
                text = resolve(StatsStringKey.CalendarTitle),
                style = StructuralType.titleMd,
                color = StructuralColors.foreground,
            )
            Spacer(Modifier.size(44.dp))
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Month header + weekday initials
// ----------------------------------------------------------------------------------------------

@Composable
private fun MonthHeader(
    month: LocalDate,
    today: LocalDate?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val canGoNext = today != null && month < startOfMonth(today)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrGlassCircleButton(
            icon = FrIcons.ChevronLeft,
            onClick = onPrevious,
            contentDescription = resolve(StatsStringKey.CalendarPrevMonthCta),
            size = 36.dp,
        )
        FrText(
            text = resolve(StatsStringKey.CalendarMonthTitleFormat, resolve(month.month.toStringKey()), month.year),
            style = StructuralType.titleLg,
            color = StructuralColors.foreground,
        )
        FrGlassCircleButton(
            icon = FrIcons.ChevronRight,
            onClick = onNext,
            contentDescription = resolve(StatsStringKey.CalendarNextMonthCta),
            size = 36.dp,
            enabled = canGoNext,
        )
    }
}

@Composable
private fun WeekdayHeader() {
    val initials = listOf(
        StatsStringKey.CalendarWeekdayMonday,
        StatsStringKey.CalendarWeekdayTuesday,
        StatsStringKey.CalendarWeekdayWednesday,
        StatsStringKey.CalendarWeekdayThursday,
        StatsStringKey.CalendarWeekdayFriday,
        StatsStringKey.CalendarWeekdaySaturday,
        StatsStringKey.CalendarWeekdaySunday,
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        initials.forEach { key ->
            FrText(
                text = resolve(key),
                style = StructuralType.micro.copy(textAlign = TextAlign.Center),
                color = StructuralColors.foreground.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Month grid
// ----------------------------------------------------------------------------------------------

/** The month's days padded with leading/trailing nulls to full Monday-first weeks of 7. */
private fun monthCells(month: LocalDate): List<List<LocalDate?>> {
    val leadingBlanks = startOfIsoWeek(month).daysUntil(month)
    val dayCount = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val cells: List<LocalDate?> =
        List(leadingBlanks) { null } + List(dayCount) { month.plus(DatePeriod(days = it)) }
    return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    calendar: MealCalendarMonth,
    selectedDay: LocalDate?,
    onDaySelected: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        monthCells(month).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(
                            day = day,
                            meals = calendar.mealsByDay[day].orEmpty(),
                            selected = day == selectedDay,
                            onClick = { onDaySelected(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    meals: List<MealWithRatings>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.sm)
    val hasMeals = meals.isNotEmpty()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        meals.firstOrNull()?.let { first ->
            AsyncImage(
                model = first.meal.thumbnailUrl.ifBlank { first.meal.photoUrl },
                contentDescription = resolve(StatsStringKey.PlatePhotoFormat, first.meal.dish.value),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            FrScrim(style = FrScrimStyle.Photo)
        }
        FrText(
            text = day.day.toString(),
            style = StructuralType.micro,
            color = if (hasMeals) StructuralColors.onMedia else StructuralColors.foreground.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xxs),
        )
    }
}

/** Loading skeleton mirroring [MonthGrid]'s silhouette — shimmering day cells, same week rows. */
@Composable
private fun MonthGridSkeleton(month: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        monthCells(month).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        FrShimmerBox(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = RoundedCornerShape(Radius.sm),
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Selected-day meal list + empty / error states
// ----------------------------------------------------------------------------------------------

@Composable
private fun CalendarMealRow(meal: MealWithRatings, onClick: () -> Unit) {
    FrGlassTile(depth = FrTileDepth.Default, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AsyncImage(
                model = meal.meal.thumbnailUrl.ifBlank { meal.meal.photoUrl },
                contentDescription = resolve(StatsStringKey.PlatePhotoFormat, meal.meal.dish.value),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                FrText(
                    text = meal.meal.dish.value,
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                meal.averageScore?.let { score ->
                    FrText(
                        text = resolve(StatsStringKey.CalendarScoreFormat, formatOneDecimal(score.toFloat())),
                        style = StructuralType.micro,
                        color = StructuralColors.foreground.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMonth() {
    FrText(
        text = resolve(StatsStringKey.CalendarEmptyMonth),
        style = StructuralType.body.copy(textAlign = TextAlign.Center),
        color = StructuralColors.foreground.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
    )
}

@Composable
private fun ErrorTile(message: String, onRetry: () -> Unit) {
    FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
        FrText(text = message, style = StructuralType.body, color = StructuralColors.foreground)
        Spacer(Modifier.height(Spacing.md))
        FrGlassButton(label = resolve(StatsStringKey.Retry), onClick = onRetry, tone = FrButtonTone.Primary)
    }
}

/** Exhaustive month-name mapping — every calendar month has a localized [StatsStringKey]. */
private fun Month.toStringKey(): StatsStringKey = when (this) {
    Month.JANUARY   -> StatsStringKey.CalendarMonthJanuary
    Month.FEBRUARY  -> StatsStringKey.CalendarMonthFebruary
    Month.MARCH     -> StatsStringKey.CalendarMonthMarch
    Month.APRIL     -> StatsStringKey.CalendarMonthApril
    Month.MAY       -> StatsStringKey.CalendarMonthMay
    Month.JUNE      -> StatsStringKey.CalendarMonthJune
    Month.JULY      -> StatsStringKey.CalendarMonthJuly
    Month.AUGUST    -> StatsStringKey.CalendarMonthAugust
    Month.SEPTEMBER -> StatsStringKey.CalendarMonthSeptember
    Month.OCTOBER   -> StatsStringKey.CalendarMonthOctober
    Month.NOVEMBER  -> StatsStringKey.CalendarMonthNovember
    Month.DECEMBER  -> StatsStringKey.CalendarMonthDecember
}
