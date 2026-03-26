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
    private lateinit var editRate: EditText
    private lateinit var textSavedHours: TextView
    private lateinit var textPayResult: TextView
    private lateinit var buttonCalculatePay: Button

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_pay_calculator)

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        editRate = findViewById(R.id.editRate)
        textSavedHours = findViewById(R.id.textSavedHours)
        textPayResult = findViewById(R.id.textPayResult)
        buttonCalculatePay = findViewById(R.id.buttonCalculatePay)

        updateHoursDisplay()

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

            val totalHours = getWeeklyHoursFromLog()
            val regularHours = minOf(totalHours, 40.0)
            val overtimeHours = maxOf(0.0, totalHours - 40.0)

            val regularPay = regularHours * rate
            val overtimePay = overtimeHours * rate * 1.5
            val totalPay = regularPay + overtimePay

            textSavedHours.text = String.format(
                Locale.US,
                "Saved Hours: %.2f",
                totalHours
            )

            textPayResult.text = String.format(
                Locale.US,
                "Estimated Pay: $%.2f\n\nRegular Hours: %.2f\nOvertime Hours: %.2f\nRegular Pay: $%.2f\nOvertime Pay: $%.2f",
                totalPay,
                regularHours,
                overtimeHours,
                regularPay,
                overtimePay
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateHoursDisplay()
    }

    private fun updateHoursDisplay() {
        val weeklyHours = getWeeklyHoursFromLog()
        textSavedHours.text = String.format(Locale.US, "Saved Hours: %.2f", weeklyHours)
        textPayResult.text = "Estimated Pay: $0.00"
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