package es.schsebastian.foodrats.core.data.share

/**
 * Fixed export pixel sizes for the share cards (spec §5). We render at a fixed pixel size rather
 * than at the on-screen density so the exported PNG is identical across devices.
 *
 * - Story: 1080 × 1920 (Meta's recommended Instagram-Stories asset, 9:16).
 * - Square: 1080 × 1080 (the square / in-app-preview variant, 1:1).
 *
 * These live next to [StoryCardRenderer] per the design-system handoff (the card composable is
 * ratio-locked; the platform renderer owns the pixel size).
 */
const val STORY_WIDTH_PX: Int = 1080
const val STORY_HEIGHT_PX: Int = 1920
const val SQUARE_SIDE_PX: Int = 1080
