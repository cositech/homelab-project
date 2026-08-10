package com.homelab.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.homelab.app.data.local.AppDatabase
import com.homelab.app.data.local.dao.ServiceDao
import com.homelab.app.data.local.dao.ServiceInstanceDao
import com.homelab.app.security.AndroidKeystoreCredentialStore
import com.homelab.app.security.CredentialEnvelope
import com.homelab.app.security.SecureCredentialStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `service_instances` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `token` TEXT NOT NULL,
                    `username` TEXT,
                    `apiKey` TEXT,
                    `piholePassword` TEXT,
                    `piholeAuthMode` TEXT,
                    `fallbackUrl` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `service_instances`
                    ADD COLUMN `allowSelfSigned` INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `service_instances`
                    ADD COLUMN `password` TEXT DEFAULT NULL
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `service_instances`
                    ADD COLUMN `proxmoxCsrfToken` TEXT DEFAULT NULL
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `service_instances`
                    ADD COLUMN `proxmoxOtp` TEXT DEFAULT NULL
                    """.trimIndent()
                )
            }
        }

        private fun migration6To7(credentialStore: SecureCredentialStore) = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_instances_v7` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `credentialRef` TEXT,
                        `username` TEXT,
                        `piholeAuthMode` TEXT,
                        `fallbackUrl` TEXT,
                        `tlsMode` TEXT NOT NULL DEFAULT 'SYSTEM',
                        `certificatePin` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.query("SELECT * FROM `service_instances`").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val typeIndex = cursor.getColumnIndexOrThrow("type")
                    val labelIndex = cursor.getColumnIndexOrThrow("label")
                    val urlIndex = cursor.getColumnIndexOrThrow("url")
                    val tokenIndex = cursor.getColumnIndexOrThrow("token")
                    val csrfIndex = cursor.getColumnIndexOrThrow("proxmoxCsrfToken")
                    val otpIndex = cursor.getColumnIndexOrThrow("proxmoxOtp")
                    val usernameIndex = cursor.getColumnIndexOrThrow("username")
                    val apiKeyIndex = cursor.getColumnIndexOrThrow("apiKey")
                    val piholePasswordIndex = cursor.getColumnIndexOrThrow("piholePassword")
                    val piholeAuthModeIndex = cursor.getColumnIndexOrThrow("piholeAuthMode")
                    val fallbackUrlIndex = cursor.getColumnIndexOrThrow("fallbackUrl")
                    val allowSelfSignedIndex = cursor.getColumnIndexOrThrow("allowSelfSigned")
                    val passwordIndex = cursor.getColumnIndexOrThrow("password")

                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex)
                        val envelope = CredentialEnvelope(
                            token = cursor.stringOrNull(tokenIndex),
                            proxmoxCsrfToken = cursor.stringOrNull(csrfIndex),
                            proxmoxOtp = cursor.stringOrNull(otpIndex),
                            apiKey = cursor.stringOrNull(apiKeyIndex),
                            piholePassword = cursor.stringOrNull(piholePasswordIndex),
                            password = cursor.stringOrNull(passwordIndex)
                        )
                        val credentialRef = if (envelope.isEmpty) {
                            null
                        } else {
                            "credential:v1:$id:migration".also { reference ->
                                check(credentialStore.put(reference, envelope)) {
                                    "Unable to persist credentials for service instance $id"
                                }
                                check(credentialStore.get(reference) == envelope) {
                                    "Unable to verify credentials for service instance $id"
                                }
                            }
                        }
                        val tlsMode = if (cursor.getInt(allowSelfSignedIndex) == 1) {
                            "INSECURE_COMPATIBILITY"
                        } else {
                            "SYSTEM"
                        }
                        db.execSQL(
                            """
                            INSERT INTO `service_instances_v7` (
                                `id`, `type`, `label`, `url`, `credentialRef`, `username`,
                                `piholeAuthMode`, `fallbackUrl`, `tlsMode`, `certificatePin`
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                            """.trimIndent(),
                            arrayOf(
                                id,
                                cursor.getString(typeIndex),
                                cursor.getString(labelIndex),
                                cursor.getString(urlIndex),
                                credentialRef,
                                cursor.stringOrNull(usernameIndex),
                                cursor.stringOrNull(piholeAuthModeIndex),
                                cursor.stringOrNull(fallbackUrlIndex),
                                tlsMode
                            )
                        )
                    }
                }

                db.execSQL("DROP TABLE `service_instances`")
                db.execSQL("ALTER TABLE `service_instances_v7` RENAME TO `service_instances`")
            }
        }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val credentialStore = AndroidKeystoreCredentialStore(context)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "homelab_database"
        )
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            migration6To7(credentialStore)
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideServiceDao(appDatabase: AppDatabase): ServiceDao {
        return appDatabase.serviceDao()
    }

    @Provides
    @Singleton
    fun provideServiceInstanceDao(appDatabase: AppDatabase): ServiceInstanceDao {
        return appDatabase.serviceInstanceDao()
    }
}

private fun android.database.Cursor.stringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index).takeIf { it.isNotEmpty() }
