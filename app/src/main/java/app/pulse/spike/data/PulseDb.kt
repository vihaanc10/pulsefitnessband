package app.pulse.spike.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** One 15-minute block of activity from the band. [day] is "YYYY-MM-DD" as the band reports it. */
data class StepRecord(val day: String, val index: Int, val steps: Int, val calories: Int, val distanceM: Int)

/**
 * Everything the band has told us, stored on the phone in a plain SQLite file (built into Android,
 * no library needed). Nothing leaves the phone.
 *  - hr: one heart-rate reading per 5-minute slot of a day (slot 0 = midnight)
 *  - steps: activity blocks of 15 minutes
 *  - meta: small facts such as the saved band, its battery and the last sync time
 */
class PulseDb(context: Context) : SQLiteOpenHelper(context, "pulse.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE hr (day TEXT NOT NULL, slot INTEGER NOT NULL, bpm INTEGER NOT NULL, PRIMARY KEY (day, slot))")
        db.execSQL(
            "CREATE TABLE steps (day TEXT NOT NULL, idx INTEGER NOT NULL, steps INTEGER NOT NULL, " +
                "calories INTEGER NOT NULL, distance INTEGER NOT NULL, PRIMARY KEY (day, idx))",
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Saves the readings of one day. Readings outside 30-220 bpm (empty slots, glitches) are skipped. */
    @Synchronized
    fun saveHeartRate(day: LocalDate, readings: IntArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            readings.forEachIndexed { slot, bpm ->
                if (bpm in 30..220) {
                    val values = ContentValues().apply {
                        put("day", day.toString())
                        put("slot", slot)
                        put("bpm", bpm)
                    }
                    db.insertWithOnConflict("hr", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun saveSteps(records: List<StepRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in records) {
                val values = ContentValues().apply {
                    put("day", r.day)
                    put("idx", r.index)
                    put("steps", r.steps)
                    put("calories", r.calories)
                    put("distance", r.distanceM)
                }
                db.insertWithOnConflict("steps", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun heartRate(day: LocalDate): List<HrPoint> {
        val out = ArrayList<HrPoint>()
        readableDatabase.rawQuery("SELECT slot, bpm FROM hr WHERE day = ? ORDER BY slot", arrayOf(day.toString())).use { c ->
            while (c.moveToNext()) out.add(HrPoint(c.getInt(0) * 5, c.getInt(1)))
        }
        return out
    }

    /** Steps, calories (the band's raw units) and distance in metres for one day. */
    @Synchronized
    fun stepTotals(day: LocalDate): Triple<Int, Int, Int> {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(steps), 0), COALESCE(SUM(calories), 0), COALESCE(SUM(distance), 0) FROM steps WHERE day = ?",
            arrayOf(day.toString()),
        ).use { c ->
            return if (c.moveToFirst()) Triple(c.getInt(0), c.getInt(1), c.getInt(2)) else Triple(0, 0, 0)
        }
    }

    @Synchronized
    fun hasAnyData(): Boolean =
        DatabaseUtils.queryNumEntries(readableDatabase, "hr") > 0 || DatabaseUtils.queryNumEntries(readableDatabase, "steps") > 0

    @Synchronized
    fun getMeta(key: String): String? {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    @Synchronized
    fun setMeta(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
