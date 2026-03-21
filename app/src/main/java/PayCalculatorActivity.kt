package com.example.myapplication

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.Locale

class PayCalculatorActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pay_calculator)

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        val editRate = findViewById<EditText>(R.id.editRate)
        val textSavedHours = findViewById<TextView>(R.id.textSavedHours)
        val textPayResult = findViewById<TextView>(R.id.textPayResult)
        val buttonCalculatePay = findViewById<Button>(R.id.buttonCalculatePay)

        val weeklyHours = sharedPreferences.getFloat("weeklyHours", 0f)
        textSavedHours.text = String.format(Locale.US, "Saved weekly hours: %.2f", weeklyHours)

        buttonCalculatePay.setOnClickListener {
            val rateText = editRate.text.toString()
            val rate = rateText.toDoubleOrNull()

            if (rate == null) {
                textPayResult.text = "Please enter a valid hourly rate"
            } else {
                val totalPay = weeklyHours * rate
                textPayResult.text = String.format(Locale.US, "Total Pay: $%.2f", totalPay)
            }
        }
    }
}