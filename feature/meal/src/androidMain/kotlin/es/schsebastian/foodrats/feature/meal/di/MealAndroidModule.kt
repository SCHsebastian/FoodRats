package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.feature.meal.data.upload.MealUploadScheduler
import es.schsebastian.foodrats.feature.meal.data.upload.WorkManagerMealUploadScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mealAndroidModule = module {
    single<MealUploadScheduler> { WorkManagerMealUploadScheduler(androidContext()) }
}
