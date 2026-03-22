package com.example.myapplication

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DailyLogActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_log)

        supportActionBar?.title = "Daily Log"

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        val listView = findViewById<ListView>(R.id.listViewShifts)

        val shifts = loadShifts().reversed()
        val adapter = ShiftAdapter(this, shifts)
        listView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.daily_log_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                val shareText = buildWeeklyLogText()

                if (shareText.contains("No shifts recorded")) {
                    Toast.makeText(this, "No shifts to share this week", Toast.LENGTH_SHORT).show()
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Weekly Work Log")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }

                startActivity(Intent.createChooser(shareIntent, "Share weekly log"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadShifts(): MutableList<ShiftEntry> {
        val json = sharedPreferences.getString("dailyLog", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ShiftEntry>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun buildWeeklyLogText(): String {
        val weeklyShifts = getCurrentWeekShifts(loadShifts())
        val totalHours = weeklyShifts.sumOf { it.hoursWorked }

        return buildString {
            append("Weekly Work Log\n\n")

            if (weeklyShifts.isEmpty()) {
                append("No shifts recorded for this week.")
            } else {
                weeklyShifts.forEach { shift ->
                    append("${shift.date}\n")
                    append("In: ${shift.clockIn}\n")
                    append("Out: ${shift.clockOut}\n")
                    append(String.format(Locale.US, "Hours: %.2f\n\n", shift.hoursWorked))
                }
                append(String.format(Locale.US, "Total Weekly Hours: %.2f", totalHours))
            }
        }
    }

    private fun getCurrentWeekShifts(allShifts: List<ShiftEntry>): List<ShiftEntry> {
        val inputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
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