package es.schsebastian.foodrats.core.domain.share

/**
 * Shares arbitrary text via the platform's native share sheet. Per-platform
 * adapters live in `:core:data`; consumers only see this port.
 */
interface ShareController {
    fun shareText(text: String)
}
