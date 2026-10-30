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
class PulseDb(context: Context) : SQLiteOpenHelper(context, "pulse.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE hr (day TEXT NOT NULL, slot INTEGER NOT NULL, bpm INTEGER NOT NULL, PRIMARY KEY (day, slot))")
        db.execSQL(
            "CREATE TABLE steps (day TEXT NOT NULL, idx INTEGER NOT NULL, steps INTEGER NOT NULL, " +
                "calories INTEGER NOT NULL, distance INTEGER NOT NULL, PRIMARY KEY (day, idx))",
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createWorkoutTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createWorkoutTables(db)
    }

    private fun createWorkoutTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE workouts (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, start_ms INTEGER NOT NULL, " +
                "end_ms INTEGER NOT NULL, distance REAL NOT NULL, intensity INTEGER NOT NULL)",
        )
        db.execSQL("CREATE TABLE route (workout_id INTEGER NOT NULL, t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)")
        db.execSQL("CREATE TABLE live_hr (workout_id INTEGER NOT NULL, t INTEGER NOT NULL, bpm INTEGER NOT NULL)")
    }

    // ---- workouts ----

    @Synchronized
    fun startWorkout(kind: WorkoutKind, startMs: Long, intensity: Int): Long {
        val values = ContentValues().apply {
            put("kind", kind.name); put("start_ms", startMs); put("end_ms", 0L); put("distance", 0.0); put("intensity", intensity)
        }
        return writableDatabase.insert("workouts", null, values)
    }

    @Synchronized
    fun finishWorkout(id: Long, endMs: Long, distanceM: Double, intensity: Int) {
        val values = ContentValues().apply { put("end_ms", endMs); put("distance", distanceM); put("intensity", intensity) }
        writableDatabase.update("workouts", values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteWorkout(id: Long) {
        val args = arrayOf(id.toString())
        writableDatabase.delete("workouts", "id = ?", args)
        writableDatabase.delete("route", "workout_id = ?", args)
        writableDatabase.delete("live_hr", "workout_id = ?", args)
    }

    private fun readWorkouts(sql: String, args: Array<String>): List<Workout> {
        val out = ArrayList<Workout>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val kind = runCatching { WorkoutKind.valueOf(c.getString(1)) }.getOrNull() ?: continue
                out.add(Workout(c.getLong(0), kind, c.getLong(2), c.getLong(3), c.getDouble(4), c.getInt(5)))
            }
        }
        return out
    }

    @Synchronized
    fun getWorkout(id: Long): Workout? =
        readWorkouts("SELECT id, kind, start_ms, end_ms, distance, intensity FROM workouts WHERE id = ?", arrayOf(id.toString())).firstOrNull()

    /** Finished workouts that started between the two times, newest first. */
    @Synchronized
    fun workoutsBetween(fromMs: Long, toMs: Long): List<Workout> = readWorkouts(
        "SELECT id, kind, start_ms, end_ms, distance, intensity FROM workouts WHERE end_ms > 0 AND start_ms >= ? AND start_ms < ? ORDER BY start_ms DESC",
        arrayOf(fromMs.toString(), toMs.toString()),
    )

    @Synchronized
    fun recentWorkouts(limit: Int): List<Workout> = readWorkouts(
        "SELECT id, kind, start_ms, end_ms, distance, intensity FROM workouts WHERE end_ms > 0 ORDER BY start_ms DESC LIMIT ?",
        arrayOf(limit.toString()),
    )

    @Synchronized
    fun addRoutePoint(workoutId: Long, t: Long, lat: Double, lon: Double) {
        val values = ContentValues().apply { put("workout_id", workoutId); put("t", t); put("lat", lat); put("lon", lon) }
        writableDatabase.insert("route", null, values)
    }

    @Synchronized
    fun routeOf(workoutId: Long): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        readableDatabase.rawQuery("SELECT lat, lon FROM route WHERE workout_id = ? ORDER BY t", arrayOf(workoutId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getDouble(0) to c.getDouble(1))
        }
        return out
    }

    @Synchronized
    fun addLiveHr(workoutId: Long, t: Long, bpm: Int) {
        val values = ContentValues().apply { put("workout_id", workoutId); put("t", t); put("bpm", bpm) }
        writableDatabase.insert("live_hr", null, values)
    }

    @Synchronized
    fun liveHr(workoutId: Long): List<Pair<Long, Int>> {
        val out = ArrayList<Pair<Long, Int>>()
        readableDatabase.rawQuery("SELECT t, bpm FROM live_hr WHERE workout_id = ? ORDER BY t", arrayOf(workoutId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0) to c.getInt(1))
        }
        return out
    }

    @Synchronized
    fun deleteMeta(key: String) {
        writableDatabase.delete("meta", "key = ?", arrayOf(key))
    }

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

    /** Steps per 15-minute block of a day (block 0 = midnight). */
    @Synchronized
    fun stepBlocks(day: LocalDate): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        readableDatabase.rawQuery("SELECT idx, steps FROM steps WHERE day = ?", arrayOf(day.toString())).use { c ->
            while (c.moveToNext()) out[c.getInt(0)] = c.getInt(1)
        }
        return out
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

    /** Forgets the saved band. With [deleteData] it also erases the heart-rate and step history. */
    @Synchronized
    fun removeDevice(deleteData: Boolean) {
        val db = writableDatabase
        for (key in listOf("band_address", "band_name", "battery", "last_sync")) {
            db.delete("meta", "key = ?", arrayOf(key))
        }
        if (deleteData) {
            db.delete("hr", null, null)
            db.delete("steps", null, null)
        }
    }

    /** The owner's profile. Until it is edited it holds 5 ft 11 in and 72.5 kg, with a placeholder age. */
    fun loadProfile(): UserProfile = UserProfile(
        name = getMeta("profile_name") ?: "Vihaan",
        heightCm = getMeta("profile_height_cm")?.toDoubleOrNull() ?: 180.3,
        weightKg = getMeta("profile_weight_kg")?.toDoubleOrNull() ?: 72.5,
        ageYears = getMeta("profile_age")?.toIntOrNull() ?: 25,
        male = (getMeta("profile_male") ?: "true") == "true",
    )

    fun saveProfile(p: UserProfile) {
        setMeta("profile_name", p.name)
        setMeta("profile_height_cm", p.heightCm.toString())
        setMeta("profile_weight_kg", p.weightKg.toString())
        setMeta("profile_age", p.ageYears.toString())
        setMeta("profile_male", p.male.toString())
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
