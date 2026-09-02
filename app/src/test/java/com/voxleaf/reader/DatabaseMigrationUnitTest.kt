package com.voxleaf.reader

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.core.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationUnitTest {
    @Test
    fun `migration 8 to 9 preserves section and adds structure metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration-unit-${System.nanoTime()}.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE sections (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, chapterNumber INTEGER NOT NULL, title TEXT NOT NULL, estimatedMinutes INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            db.execSQL("INSERT INTO sections VALUES ('s', 'b', 1, 'Chapter One', 2)")
            DatabaseModule.MIGRATION_8_9.migrate(db)
            db.query("SELECT title, detectionSource, detectionConfidence, isManuallyEdited FROM sections WHERE id = 's'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Chapter One", cursor.getString(0))
                assertEquals("LEGACY", cursor.getString(1))
                assertEquals(0.5f, cursor.getFloat(2), 0.001f)
                assertEquals(0, cursor.getInt(3))
            }
        }
        helper.close()
    }
}
