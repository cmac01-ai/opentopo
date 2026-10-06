package org.opentopo.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.opentopo.app.ntrip.NtripProfile
import org.opentopo.app.ntrip.NtripProfileDao

@Database(
    entities = [ProjectEntity::class, PointEntity::class, TrigPointCacheEntity::class, NtripProfile::class],
    version = 11,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pointDao(): PointDao
    abstract fun trigPointCacheDao(): TrigPointCacheDao
    abstract fun ntripProfileDao(): NtripProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN antennaHeight REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN photoPath TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN layerType TEXT NOT NULL DEFAULT 'point'")
                db.execSQL("ALTER TABLE points ADD COLUMN featureId INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN geoidSeparation REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN orthometricHeight REAL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trig_points_cache (
                        gysId TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        elevation REAL,
                        status TEXT,
                        pointOrder INTEGER NOT NULL DEFAULT 0,
                        distanceM REAL,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trig_points_cache ADD COLUMN egsa87Easting REAL")
                db.execSQL("ALTER TABLE trig_points_cache ADD COLUMN egsa87Northing REAL")
                db.execSQL("ALTER TABLE trig_points_cache ADD COLUMN egsa87Z REAL")
            }
        }

        /**
         * v7 → v8: add the NTRIP profiles table. Seeding is done at runtime
         * by [org.opentopo.app.ntrip.NtripProfileRepository.seedIfEmpty] so
         * the legacy DataStore-backed NTRIP config can migrate in.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ntrip_profile (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        code TEXT NOT NULL,
                        tintColor INTEGER NOT NULL,
                        badgeFgColor INTEGER NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        useTls INTEGER NOT NULL DEFAULT 0,
                        username TEXT NOT NULL DEFAULT '',
                        password TEXT NOT NULL DEFAULT '',
                        mountpoint TEXT NOT NULL DEFAULT '',
                        sendGga INTEGER NOT NULL DEFAULT 1,
                        rtcm_preference TEXT NOT NULL DEFAULT 'ANY',
                        isActive INTEGER NOT NULL DEFAULT 0,
                        lastUsedAt INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v8 → v9: persist the coordinate reference system per project.
         *
         * Existing projects pre-date SIRGAS support and therefore remain EGSA87.
         * New projects are created as SIRGAS2000_UTM by ProjectEntity defaults/UI.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN coordinateSystem TEXT NOT NULL DEFAULT 'EGSA87'")
                db.execSQL("ALTER TABLE projects ADD COLUMN utmZone INTEGER")
            }
        }

        /**
         * v9 → v10: add UTM hemisphere at project level and persist the
         * resolved CRS metadata with each observed point.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN utmHemisphere TEXT")
                db.execSQL("ALTER TABLE points ADD COLUMN crsEpsg INTEGER")
                db.execSQL("ALTER TABLE points ADD COLUMN utmZone INTEGER")
                db.execSQL("ALTER TABLE points ADD COLUMN utmHemisphere TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN attribute TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE points ADD COLUMN heightModel TEXT")
                db.execSQL("ALTER TABLE points ADD COLUMN heightUncertainty REAL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opentopo.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11,
                    )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
