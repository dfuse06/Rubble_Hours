package com.example.myapplication

import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DailyLogActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var listView: ListView
    private lateinit var adapter: ShiftAdapter
    private lateinit var fabSaveCsv: FloatingActionButton
    private lateinit var fabClearWeek: FloatingActionButton
    private lateinit var buttonShareWeeklyLog: ImageButton
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_daily_log)

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        listView = findViewById(R.id.listViewShifts)
        fabSaveCsv = findViewById(R.id.fabSaveCsv)
        fabClearWeek = findViewById(R.id.fabClearWeek)
        buttonShareWeeklyLog = findViewById(R.id.buttonShareWeeklyLog)

        loadList()

        buttonShareWeeklyLog.setOnClickListener {
            shareWeeklyLogAsText()
        }

        fabSaveCsv.setOnClickListener {
            saveWeeklyLogAsCsvOnly()
        }

        fabClearWeek.setOnClickListener {
            confirmClearWeek()
        }
    }

    private fun confirmClearWeek() {
        val weeklyShifts = getCurrentWeekShifts(loadShifts())

        if (weeklyShifts.isEmpty()) {
            Toast.makeText(this, "No shifts to clear", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear Week")
            .setMessage("This will permanently clear this week's hours and log entries. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearWeeklyData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadList() {
        val shifts = getCurrentWeekShifts(loadShifts()).reversed()
        adapter = ShiftAdapter(this, shifts.toMutableList())
        listView.adapter = adapter
    }

    private fun loadShifts(): MutableList<ShiftEntry> {
        val json = sharedPreferences.getString("dailyLog", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ShiftEntry>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun shareWeeklyLogAsText() {
        val weeklyShifts = getCurrentWeekShifts(loadShifts())

        if (weeklyShifts.isEmpty()) {
            Toast.makeText(this, "No shifts to share", Toast.LENGTH_SHORT).show()
            return
        }

        val totalHours = weeklyShifts.sumOf { it.hoursWorked }

        val shareText = buildString {
            append("Weekly Work Log\n\n")

            weeklyShifts.forEach { shift ->
                append("${shift.date}\n")
                append("In: ${shift.clockIn}\n")
                append("Out: ${shift.clockOut}\n")
                append(String.format(Locale.US, "Hours: %.2f\n\n", shift.hoursWorked))
            }

            append(String.format(Locale.US, "Total Weekly Hours: %.2f", totalHours))
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Weekly Work Log")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(shareIntent, "Share weekly log"))
    }

    private fun saveWeeklyLogAsCsvOnly() {
        val weeklyShifts = getCurrentWeekShifts(loadShifts())

        if (weeklyShifts.isEmpty()) {
            Toast.makeText(this, "No shifts to save", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "RubbleHours_WeekOf_${getStartOfWeekFileName()}.csv"
        val csvText = buildWeeklyCsv(weeklyShifts)

        try {
            val resolver = contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + "Rubble Hours"
                )
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri == null) {
                Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write(csvText)
                    writer.flush()
                }
            } ?: run {
                Toast.makeText(this, "Failed to write CSV file", Toast.LENGTH_SHORT).show()
                return
            }

            Toast.makeText(this, "CSV saved to Downloads/Rubble Hours", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildWeeklyCsv(shifts: List<ShiftEntry>): String {
        val totalHours = shifts.sumOf { it.hoursWorked }

        return buildString {
            append("Date,Clock In,Clock Out,Hours Worked\n")

            shifts.forEach { shift ->
                append(csvField(shift.date)).append(",")
                append(csvField(shift.clockIn)).append(",")
                append(csvField(shift.clockOut)).append(",")
                append(String.format(Locale.US, "%.2f", shift.hoursWorked)).append("\n")
            }

            append("\n")
            append("Total Weekly Hours,,,")
            append(String.format(Locale.US, "%.2f", totalHours))
        }
    }

    private fun csvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun getStartOfWeekFileName(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(calendar.time)
    }

    private fun clearWeeklyData() {
        sharedPreferences.edit()
            .remove("dailyLog")
            .remove("weeklyHours")
            .remove("clockInTime")
            .remove("lastShiftHours")
            .apply()

        loadList()

        Toast.makeText(this, "Week cleared", Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentWeekShifts(allShifts: List<ShiftEntry>): List<ShiftEntry> {
        val inputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis

        return allShifts.filter { shift ->
            try {
                val parsedDate = inputFormat.parse(shift.date)
                parsedDate != null && parsedDate.time in startOfWeek..endOfWeek
            } catch (_: Exception) {
                false
            }
        }
    }
}