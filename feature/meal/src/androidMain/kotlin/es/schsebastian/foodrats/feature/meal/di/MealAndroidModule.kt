package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.feature.meal.data.upload.MealUploadScheduler
import es.schsebastian.foodrats.feature.meal.data.upload.WorkManagerMealUploadScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mealAndroidModule = module {
    single<MealUploadScheduler> { WorkManagerMealUploadScheduler(androidContext()) }
    // ConnectivityPort is now an app-wide :core:data binding (connectivityAndroidModule),
    // not a meal-feature concern — DraftRetryRunner resolves it from the graph.
}
