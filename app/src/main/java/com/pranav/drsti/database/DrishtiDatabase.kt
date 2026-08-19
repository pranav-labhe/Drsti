package com.pranav.drsti.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pranav.drsti.database.dao.*
import com.pranav.drsti.database.entity.*

@Database(
    entities = [
        PersonEntity::class,
        PlaceEntity::class,
        ConversationEntity::class,
        ConversationMessageEntity::class,
        KundaliEntity::class,
        DashaEntity::class,
        PanchangEntity::class,
        PlanetaryPositionEntity::class,
        TransitAnalysisEntity::class,
        DecisionEntity::class,
        DecisionAnalysisEntity::class,
        DecisionOutcomeEntity::class,
        OutcomeAnalysisEntity::class,
        AIRequestLogEntity::class,
        AppSettingsEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DrishtiDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun placeDao(): PlaceDao
    abstract fun conversationDao(): ConversationDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun kundaliDao(): KundaliDao
    abstract fun dashaDao(): DashaDao
    abstract fun panchangDao(): PanchangDao
    abstract fun planetaryPositionDao(): PlanetaryPositionDao
    abstract fun transitAnalysisDao(): TransitAnalysisDao
    abstract fun decisionDao(): DecisionDao
    abstract fun decisionAnalysisDao(): DecisionAnalysisDao
    abstract fun decisionOutcomeDao(): DecisionOutcomeDao
    abstract fun outcomeAnalysisDao(): OutcomeAnalysisDao
    abstract fun aiRequestLogDao(): AIRequestLogDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile private var instance: DrishtiDatabase? = null

        fun getInstance(context: Context): DrishtiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DrishtiDatabase::class.java,
                    "drsti.db"
                ).fallbackToDestructiveMigration()
                 .build().also { instance = it }
            }
    }
}
