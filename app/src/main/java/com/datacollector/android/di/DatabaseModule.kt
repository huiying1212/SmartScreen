package com.datacollector.android.di

import android.content.Context
import androidx.room.Room
import com.datacollector.android.data.local.AppDatabase
import com.datacollector.android.data.local.dao.AchievementDao
import com.datacollector.android.data.local.dao.ContextSummaryDao
import com.datacollector.android.data.local.dao.EsmDao
import com.datacollector.android.data.local.dao.GoalDao
import com.datacollector.android.data.local.dao.JournalDao
import com.datacollector.android.data.local.dao.ReflectionMessageDao
import com.datacollector.android.data.local.dao.ScoreDao
import com.datacollector.android.data.local.dao.WallpaperRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun scoreDao(db: AppDatabase): ScoreDao = db.scoreDao()
    @Provides fun contextSummaryDao(db: AppDatabase): ContextSummaryDao = db.contextSummaryDao()
    @Provides fun esmDao(db: AppDatabase): EsmDao = db.esmDao()
    @Provides fun journalDao(db: AppDatabase): JournalDao = db.journalDao()
    @Provides fun goalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun achievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
    @Provides fun reflectionMessageDao(db: AppDatabase): ReflectionMessageDao =
        db.reflectionMessageDao()
    @Provides fun wallpaperRecordDao(db: AppDatabase): WallpaperRecordDao =
        db.wallpaperRecordDao()
}
