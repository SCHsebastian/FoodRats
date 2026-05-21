package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.location.IosLocationProvider
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import org.koin.dsl.module

val locationIosModule = module {
    single<LocationProvider> { IosLocationProvider() }
}
