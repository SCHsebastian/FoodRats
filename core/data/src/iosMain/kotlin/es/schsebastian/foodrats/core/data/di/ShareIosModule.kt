package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.share.ShareController
import es.schsebastian.foodrats.core.data.share.ShareControllerIos
import org.koin.dsl.module

val shareIosModule = module {
    single<ShareController> { ShareControllerIos() }
}
