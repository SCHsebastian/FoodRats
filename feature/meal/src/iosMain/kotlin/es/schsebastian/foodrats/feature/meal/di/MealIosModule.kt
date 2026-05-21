package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.feature.meal.data.upload.InProcessMealUploadScheduler
import es.schsebastian.foodrats.feature.meal.data.upload.MealUploadScheduler
import org.koin.dsl.module

val mealIosModule = module {
    single<MealUploadScheduler> { InProcessMealUploadScheduler() }
}
