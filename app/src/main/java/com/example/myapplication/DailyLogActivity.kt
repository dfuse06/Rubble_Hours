package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
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
    private lateinit var fabAddManualEntry: FloatingActionButton
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
        fabAddManualEntry = findViewById(R.id.fabAddManualEntry)
        buttonShareWeeklyLog = findViewById(R.id.buttonShareWeeklyLog)

        loadList()

        fabAddManualEntry.setOnClickListener {
            showManualEntryDialog()
        }

        fabSaveCsv.setOnClickListener {
            saveWeeklyLogAsCsvOnly()
        }

        fabClearWeek.setOnClickListener {
            confirmClearWeek()
        }

        buttonShareWeeklyLog.setOnClickListener {
            shareWeeklyLogAsText()
        }
    }

    private fun loadList() {
        val shifts = getCurrentWeekShifts(loadShifts()).reversed().toMutableList()

        adapter = ShiftAdapter(this, shifts) { selectedShift ->
            showEditEntryDialog(selectedShift)
        }

        listView.adapter = adapter
    }

    private fun loadShifts(): MutableList<ShiftEntry> {
        val json = sharedPreferences.getString("dailyLog", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ShiftEntry>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveShifts(shifts: MutableList<ShiftEntry>) {
        val json = gson.toJson(shifts)
        sharedPreferences.edit().putString("dailyLog", json).apply()
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

    private fun showManualEntryDialog() {
        showEntryDialog(existingShift = null)
    }

    private fun showEditEntryDialog(shiftToEdit: ShiftEntry) {
        showEntryDialog(existingShift = shiftToEdit)
    }

    private fun showEntryDialog(existingShift: ShiftEntry?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_entry, null)

        val editDate = dialogView.findViewById<EditText>(R.id.editDate)
        val editClockIn = dialogView.findViewById<EditText>(R.id.editClockIn)
        val editClockOut = dialogView.findViewById<EditText>(R.id.editClockOut)
        val editHours = dialogView.findViewById<EditText>(R.id.editHours)

        val selectedDate = Calendar.getInstance()
        var clockInHour = -1
        var clockInMinute = -1
        var clockOutHour = -1
        var clockOutMinute = -1

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

        if (existingShift != null) {
            editDate.setText(existingShift.date)
            editClockIn.setText(existingShift.clockIn)
            editClockOut.setText(existingShift.clockOut)
            editHours.setText(String.format(Locale.US, "%.2f", existingShift.hoursWorked))
        } else {
            editDate.setText(dateFormat.format(selectedDate.time))
        }

        editDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedDate.set(Calendar.YEAR, year)
                    selectedDate.set(Calendar.MONTH, month)
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    editDate.setText(dateFormat.format(selectedDate.time))
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        editClockIn.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    clockInHour = hourOfDay
                    clockInMinute = minute

                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)

                    editClockIn.setText(timeFormat.format(cal.time))
                    autoFillHours(editHours, clockInHour, clockInMinute, clockOutHour, clockOutMinute)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                false
            ).show()
        }

        editClockOut.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    clockOutHour = hourOfDay
                    clockOutMinute = minute

                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)

                    editClockOut.setText(timeFormat.format(cal.time))
                    autoFillHours(editHours, clockInHour, clockInMinute, clockOutHour, clockOutMinute)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                false
            ).show()
        }

        val title = if (existingShift == null) "Add Manual Entry" else "Edit Entry"

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)

        if (existingShift != null) {
            builder.setNeutralButton("Delete", null)
        }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val date = editDate.text.toString().trim()
            val clockIn = editClockIn.text.toString().trim()
            val clockOut = editClockOut.text.toString().trim()
            val hoursText = editHours.text.toString().trim()

            if (date.isEmpty() || clockIn.isEmpty() || clockOut.isEmpty() || hoursText.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hours = hoursText.toDoubleOrNull()
            if (hours == null) {
                Toast.makeText(this, "Enter valid hours", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val shifts = loadShifts()

            if (existingShift == null) {
                shifts.add(
                    ShiftEntry(
                        date = date,
                        clockIn = clockIn,
                        clockOut = clockOut,
                        hoursWorked = hours
                    )
                )

                saveShifts(shifts)
                loadList()
                Toast.makeText(this, "Manual entry added", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                val index = shifts.indexOfFirst {
                    it.date == existingShift.date &&
                            it.clockIn == existingShift.clockIn &&
                            it.clockOut == existingShift.clockOut &&
                            it.hoursWorked == existingShift.hoursWorked
                }

                if (index == -1) {
                    Toast.makeText(this, "Could not find entry to update", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                shifts[index] = ShiftEntry(
                    date = date,
                    clockIn = clockIn,
                    clockOut = clockOut,
                    hoursWorked = hours
                )

                saveShifts(shifts)
                loadList()
                Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        if (existingShift != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val shifts = loadShifts()

                val removed = shifts.removeAll {
                    it.date == existingShift.date &&
                            it.clockIn == existingShift.clockIn &&
                            it.clockOut == existingShift.clockOut &&
                            it.hoursWorked == existingShift.hoursWorked
                }

                if (removed) {
                    saveShifts(shifts)
                    loadList()
                    Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Could not find entry to delete", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun autoFillHours(
        editHours: EditText,
        clockInHour: Int,
        clockInMinute: Int,
        clockOutHour: Int,
        clockOutMinute: Int
    ) {
        if (clockInHour == -1 || clockOutHour == -1) return

        val start = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, clockInHour)
        start.set(Calendar.MINUTE, clockInMinute)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)

        val end = Calendar.getInstance()
        end.set(Calendar.HOUR_OF_DAY, clockOutHour)
        end.set(Calendar.MINUTE, clockOutMinute)
        end.set(Calendar.SECOND, 0)
        end.set(Calendar.MILLISECOND, 0)

        if (end.before(start)) {
            end.add(Calendar.DAY_OF_MONTH, 1)
        }

        val diffMillis = end.timeInMillis - start.timeInMillis
        val hours = diffMillis.toDouble() / (1000 * 60 * 60)

        editHours.setText(String.format(Locale.US, "%.2f", hours))
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