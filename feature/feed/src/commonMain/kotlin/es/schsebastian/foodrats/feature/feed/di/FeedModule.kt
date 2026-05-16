package es.schsebastian.foodrats.feature.feed.di

import org.koin.dsl.module

val feedModule = module {
    // Scaffold only. Feed consumes :core:domain MealReadPort (already bound by :feature:meal).
}
