package com.example.myapplication

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PayCalculatorActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_pay_calculator)

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        val editRate = findViewById<EditText>(R.id.editRate)
        val textSavedHours = findViewById<TextView>(R.id.textSavedHours)
        val textPayResult = findViewById<TextView>(R.id.textPayResult)
        val buttonCalculatePay = findViewById<Button>(R.id.buttonCalculatePay)

        updateHoursDisplay(textSavedHours)

        buttonCalculatePay.setOnClickListener {
            val rateText = editRate.text.toString().trim()

            if (rateText.isEmpty()) {
                Toast.makeText(this, "Enter hourly rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rate = rateText.toDoubleOrNull()
            if (rate == null) {
                Toast.makeText(this, "Enter a valid hourly rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val weeklyHours = getWeeklyHoursFromLog()
            val pay = weeklyHours * rate

            textSavedHours.text = String.format(Locale.US, "Saved Hours: %.2f", weeklyHours)
            textPayResult.text = String.format(Locale.US, "Estimated Pay: $%.2f", pay)
        }
    }

    override fun onResume() {
        super.onResume()
        val textSavedHours = findViewById<TextView>(R.id.textSavedHours)
        updateHoursDisplay(textSavedHours)
    }

    private fun updateHoursDisplay(textSavedHours: TextView) {
        val weeklyHours = getWeeklyHoursFromLog()
        textSavedHours.text = String.format(Locale.US, "Saved Hours: %.2f", weeklyHours)
    }

    private fun getWeeklyHoursFromLog(): Double {
        return getCurrentWeekShifts(loadShifts()).sumOf { it.hoursWorked }
    }

    private fun loadShifts(): MutableList<ShiftEntry> {
        val json = sharedPreferences.getString("dailyLog", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ShiftEntry>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
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