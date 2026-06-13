package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.share.ShareControllerIos
import es.schsebastian.foodrats.core.domain.share.ShareController
import org.koin.dsl.module

val shareIosModule = module {
    single<ShareController> { ShareControllerIos() }
}
