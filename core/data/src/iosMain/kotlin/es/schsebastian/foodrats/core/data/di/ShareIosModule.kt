package es.schsebastian.foodrats.core.data.di

import coil3.PlatformContext
import es.schsebastian.foodrats.core.data.share.PlateImageDecoder
import es.schsebastian.foodrats.core.data.share.ShareControllerIos
import es.schsebastian.foodrats.core.data.share.StoryCardRenderer
import es.schsebastian.foodrats.core.data.share.StoryCardRendererIos
import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareControllerImpl
import es.schsebastian.foodrats.core.data.share.StoryShareLauncher
import es.schsebastian.foodrats.core.data.share.StoryShareLauncherIos
import es.schsebastian.foodrats.core.domain.share.ShareController
import org.koin.dsl.module

/**
 * iOS-side Koin module that registers the [ShareController]. The Swift caller in ContentView.swift
 * supplies the share lambda at app startup — see iosApp/ShareBridge.swift, which presents a
 * `UIActivityViewController` from the top-most view controller.
 */
fun shareIosModule(
    share: (String) -> Unit,
) = module {
    single<ShareController> { ShareControllerIos(share) }
}

/**
 * iOS-side Koin module for the shareable story cards (spec 2026-06-14). The renderer + decoder are
 * pure Kotlin/Native; only the share *presentation* needs Swift, so [storyShare] bridges to
 * `StoryShareBridge.swift` (UIPasteboard + `instagram-stories://share`, with a
 * `UIActivityViewController` fallback). The lambda returns a status code: 0 = Instagram, 1 = sheet,
 * 2 = failed. Threaded through ContentView.swift → MainViewController.
 */
fun storyShareIosModule(
    storyShare: (ByteArray) -> Int,
) = module {
    single<StoryCardRenderer> { StoryCardRendererIos(dispatchers = get()) }
    single<StoryShareLauncher> { StoryShareLauncherIos(storyShare) }
    single { PlateImageDecoder(platformContext = PlatformContext.INSTANCE) }
    // The single testable seam feed/stats ViewModels inject (decode → render → launch).
    single<StoryShareController> { StoryShareControllerImpl(decoder = get(), renderer = get(), launcher = get()) }
}
