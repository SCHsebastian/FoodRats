package es.schsebastian.foodrats.feature.feed.presentation.components

/**
 * Holds the Google Static Maps API key. Provided via Koin by the Android app module
 * (from `BuildConfig.MAPS_API_KEY`, sourced from the `googleMapsApiKey` Gradle property).
 * iOS renders the location with MapKit (Apple Maps) and does NOT need this — the iOS
 * actual of [FrLocationMap] never resolves it, so no iOS binding is required.
 */
data class MapsApiKey(val value: String)
