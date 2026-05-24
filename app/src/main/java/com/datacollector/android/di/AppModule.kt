package com.datacollector.android.di

import android.content.Context
import com.datacollector.android.api.DeepSeekApiClient
import com.datacollector.android.utils.CollectionConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCollectionConfig(@ApplicationContext context: Context): CollectionConfig =
        CollectionConfig.getInstance(context)

    @Provides
    @Singleton
    fun provideDeepSeekClient(@ApplicationContext context: Context): DeepSeekApiClient =
        DeepSeekApiClient(context)
}
