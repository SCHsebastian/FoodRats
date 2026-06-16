package es.schsebastian.foodrats.core.data.share

/**
 * Pure (host-testable) helpers for the share-card cache files. Factored out of the platform
 * launchers so the file-naming + FileProvider-authority logic can be unit-tested on the JVM (the
 * real rasterization + share sheet are platform-only and verified on device, spec §12).
 */
object ShareCardFiles {

    /** Sub-directory under the OS cache dir where share PNGs are written. */
    const val CACHE_SUBDIR: String = "share_cards"

    /** Content type for the exported card image. */
    const val PNG_MIME: String = "image/png"

    /**
     * Stable file name for a rendered card. We reuse a single name so the cache never grows
     * unbounded — each share overwrites the previous PNG (the URI is granted per-share and the
     * pixels are the only payload, spec §9). Includes the `.png` suffix.
     */
    fun fileName(): String = "share_card.png"

    /**
     * The `FileProvider` authority for a given application id, e.g. `es.schsebastian.foodrats` →
     * `es.schsebastian.foodrats.fileprovider`. Must match the `<provider>` `android:authorities`
     * in the manifest (spec §6.1).
     */
    fun fileProviderAuthority(packageName: String): String = "$packageName.fileprovider"
}
