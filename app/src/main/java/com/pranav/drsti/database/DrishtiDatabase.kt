package com.pranav.drsti.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        AppSettingsEntity::class,
        ConversationStateEntity::class,
        CalibrationStatsEntity::class
    ],
    version = 4,
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
    abstract fun conversationStateDao(): ConversationStateDao
    abstract fun calibrationStatsDao(): CalibrationStatsDao

    companion object {
        @Volatile private var instance: DrishtiDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversation_state` (
                        `conversationId` INTEGER NOT NULL, 
                        `activeTopic` TEXT, 
                        `activeDecisionId` INTEGER, 
                        `languagePreference` TEXT NOT NULL, 
                        `detailLevel` TEXT NOT NULL, 
                        `lastFactsSnapshot` TEXT, 
                        `updatedAt` TEXT NOT NULL, 
                        PRIMARY KEY(`conversationId`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `calibration_stats` (
                        `id` INTEGER NOT NULL, 
                        `decisionsAnalyzed` INTEGER NOT NULL, 
                        `outcomesRecorded` INTEGER NOT NULL, 
                        `directionallyCorrect` INTEGER NOT NULL, 
                        `overconfidenceCount` INTEGER NOT NULL, 
                        `underconfidenceCount` INTEGER NOT NULL, 
                        `updatedAt` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                // Add new columns to conversation_state if they don't exist
                runCatching { db.execSQL("ALTER TABLE conversation_state ADD COLUMN lastIntent TEXT") }
                runCatching { db.execSQL("ALTER TABLE conversation_state ADD COLUMN lastActiveEntityId TEXT") }
            }
        }

        fun getInstance(context: Context): DrishtiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DrishtiDatabase::class.java,
                    "drsti.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                 .build().also { instance = it }
            }
    }
}
