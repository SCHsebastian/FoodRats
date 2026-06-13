package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.share.ShareControllerIos
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
