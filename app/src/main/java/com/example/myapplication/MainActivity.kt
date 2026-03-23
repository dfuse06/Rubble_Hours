package com.example.myapplication

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        val buttonClockIn = findViewById<Button>(R.id.buttonClockIn)
        val buttonClockOut = findViewById<Button>(R.id.buttonClockOut)
        val buttonPayCalculator = findViewById<Button>(R.id.buttonPayCalculator)
        val buttonResetWeek = findViewById<Button>(R.id.buttonResetWeek)
        val buttonDailyLog = findViewById<Button>(R.id.buttonDailyLog)

        val textStatus = findViewById<TextView>(R.id.textStatus)
        val textLastShift = findViewById<TextView>(R.id.textLastShift)
        val textWeeklyHours = findViewById<TextView>(R.id.textWeeklyHours)

        updateWeeklyHours(textWeeklyHours)

        val savedClockInTime = sharedPreferences.getLong("clockInTime", 0L)
        if (savedClockInTime != 0L) {
            val formattedTime = formatTime(savedClockInTime)
            textStatus.text = "Clocked in at: $formattedTime"
        }

        buttonClockIn.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            sharedPreferences.edit().putLong("clockInTime", currentTime).apply()

            textStatus.text = "Clocked in at: ${formatTime(currentTime)}"
            Toast.makeText(this, "Clocked in", Toast.LENGTH_SHORT).show()
        }

        buttonClockOut.setOnClickListener {
            val clockInTime = sharedPreferences.getLong("clockInTime", 0L)

            if (clockInTime == 0L) {
                Toast.makeText(this, "You are not clocked in", Toast.LENGTH_SHORT).show()
            } else {
                val clockOutTime = System.currentTimeMillis()
                val workedMillis = clockOutTime - clockInTime
                val workedHours = workedMillis.toDouble() / (1000 * 60 * 60)

                val currentWeeklyHours = sharedPreferences.getFloat("weeklyHours", 0f).toDouble()
                val newWeeklyHours = currentWeeklyHours + workedHours

                val shiftEntry = ShiftEntry(
                    date = formatDate(clockOutTime),
                    clockIn = formatTime(clockInTime),
                    clockOut = formatTime(clockOutTime),
                    hoursWorked = workedHours
                )

                saveShift(shiftEntry)

                sharedPreferences.edit()
                    .putFloat("weeklyHours", newWeeklyHours.toFloat())
                    .remove("clockInTime")
                    .apply()

                textStatus.text = "Not clocked in"
                textLastShift.text = String.format(
                    Locale.US,
                    "Last shift: %.2f hours",
                    workedHours
                )

                updateWeeklyHours(textWeeklyHours)

                Toast.makeText(
                    this,
                    String.format(Locale.US, "Clocked out. Added %.2f hours", workedHours),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        buttonPayCalculator.setOnClickListener {
            val intent = Intent(this, PayCalculatorActivity::class.java)
            startActivity(intent)
        }

        buttonResetWeek.setOnClickListener {
            sharedPreferences.edit().putFloat("weeklyHours", 0f).apply()
            updateWeeklyHours(textWeeklyHours)
            textLastShift.text = "Last shift: 0.00 hours"
            Toast.makeText(this, "Weekly hours reset", Toast.LENGTH_SHORT).show()
        }

        buttonDailyLog.setOnClickListener {
            startActivity(Intent(this, DailyLogActivity::class.java))
        }
    }

    private fun updateWeeklyHours(textView: TextView) {
        val weeklyHours = sharedPreferences.getFloat("weeklyHours", 0f)
        textView.text = String.format(Locale.US, "Weekly hours: %.2f", weeklyHours)
    }

    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        return sdf.format(Date(timeMillis))
    }

    private fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return sdf.format(Date(timeMillis))
    }

    private fun saveShift(shiftEntry: ShiftEntry) {
        val shifts = loadShifts()
        shifts.add(shiftEntry)

        val json = gson.toJson(shifts)
        sharedPreferences.edit().putString("dailyLog", json).apply()
    }

    private fun loadShifts(): MutableList<ShiftEntry> {
        val json = sharedPreferences.getString("dailyLog", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ShiftEntry>>() {}.type
        return gson.fromJson(json, type)
    }
}