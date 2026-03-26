package com.example.myapplication

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var textStatus: TextView
    private lateinit var textLastShift: TextView
    private lateinit var textWeeklyHours: TextView
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        val buttonClockIn = findViewById<Button>(R.id.buttonClockIn)
        val buttonClockOut = findViewById<Button>(R.id.buttonClockOut)
        val buttonPayCalculator = findViewById<Button>(R.id.buttonPayCalculator)
        val buttonResetWeek = findViewById<Button>(R.id.buttonResetWeek)
        val buttonDailyLog = findViewById<Button>(R.id.buttonDailyLog)

        textStatus = findViewById(R.id.textStatus)
        textLastShift = findViewById(R.id.textLastShift)
        textWeeklyHours = findViewById(R.id.textWeeklyHours)

        showFirstRunHelpDialog()
        refreshMainScreen()

        buttonClockIn.setOnClickListener {
            clockIn()
        }

        buttonClockOut.setOnClickListener {
            clockOut()
        }

        buttonPayCalculator.setOnClickListener {
            startActivity(Intent(this, PayCalculatorActivity::class.java))
        }

        buttonResetWeek.setOnClickListener {
            sharedPreferences.edit()
                .remove("dailyLog")
                .remove("clockInTime")
                .apply()

            refreshMainScreen()
            refreshQuickSettingsTile()

            Toast.makeText(this, "Weekly hours reset", Toast.LENGTH_SHORT).show()
        }

        buttonDailyLog.setOnClickListener {
            startActivity(Intent(this, DailyLogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMainScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            refreshMainScreen()
        }
    }

    private fun showFirstRunHelpDialog() {
        val hasSeenHelp = sharedPreferences.getBoolean("hasSeenHelpDialog", false)

        if (!hasSeenHelp) {
            val builder = AlertDialog.Builder(this)
                .setTitle("Welcome")
                .setMessage(
                    "How to use this app:\n\n" +
                            "• Tap Clock In to start your shift.\n" +
                            "• Tap Clock Out to end your shift and save your hours.\n" +
                            "• Open Daily Log to view saved shifts.\n" +
                            "• Save Daily Log exports a CSV file to Downloads/Rubble Hours.\n" +
                            "• Use Pay Calculator to estimate your pay.\n" +
                            "• You can also add a Quick Settings tile for fast clock in/out."
                )
                .setPositiveButton("Got It") { dialog, _ ->
                    sharedPreferences.edit()
                        .putBoolean("hasSeenHelpDialog", true)
                        .apply()
                    dialog.dismiss()
                }
                .setCancelable(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setNeutralButton("Add Quick Tile") { _, _ ->
                    sharedPreferences.edit()
                        .putBoolean("hasSeenHelpDialog", true)
                        .apply()
                    requestAddQuickTile()
                }
            }

            builder.show()
        }
    }

    private fun clockIn() {
        val currentTime = System.currentTimeMillis()

        sharedPreferences.edit()
            .putLong("clockInTime", currentTime)
            .apply()

        refreshMainScreen()
        refreshQuickSettingsTile()

        Toast.makeText(this, "Clocked in", Toast.LENGTH_SHORT).show()
    }

    private fun clockOut() {
        val clockInTime = sharedPreferences.getLong("clockInTime", 0L)

        if (clockInTime == 0L) {
            Toast.makeText(this, "You are not clocked in", Toast.LENGTH_SHORT).show()
            return
        }

        val clockOutTime = System.currentTimeMillis()
        val workedMillis = clockOutTime - clockInTime
        val workedHours = workedMillis.toDouble() / (1000 * 60 * 60)

        val shiftEntry = ShiftEntry(
            date = formatDate(clockOutTime),
            clockIn = formatTime(clockInTime),
            clockOut = formatTime(clockOutTime),
            hoursWorked = workedHours
        )

        saveShift(shiftEntry)

        sharedPreferences.edit()
            .remove("clockInTime")
            .apply()

        refreshMainScreen()
        refreshQuickSettingsTile()

        Toast.makeText(
            this,
            String.format(Locale.US, "Clocked out. Added %.2f hours", workedHours),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshMainScreen() {
        val weeklyShifts = getCurrentWeekShifts(loadShifts())
        val weeklyHours = weeklyShifts.sumOf { it.hoursWorked }

        textWeeklyHours.text = String.format(Locale.US, "Weekly hours: %.2f", weeklyHours)

        val savedClockInTime = sharedPreferences.getLong("clockInTime", 0L)
        if (savedClockInTime != 0L) {
            textStatus.text = "Clocked in at: ${formatTime(savedClockInTime)}"
        } else {
            textStatus.text = "Not clocked in"
        }

        val lastShift = weeklyShifts.lastOrNull()
        if (lastShift != null) {
            textLastShift.text = String.format(
                Locale.US,
                "Last shift: %.2f hours",
                lastShift.hoursWorked
            )
        } else {
            textLastShift.text = "Last shift: 0.00 hours"
        }
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

    private fun requestAddQuickTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            val componentName = ComponentName(this, QuickClockTileService::class.java)

            statusBarManager.requestAddTileService(
                componentName,
                "Clock In/Out",
                Icon.createWithResource(this, R.drawable.ic_qs_clock),
                mainExecutor
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                        Toast.makeText(this, "Quick tile added", Toast.LENGTH_SHORT).show()
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                        Toast.makeText(this, "Quick tile already added", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Quick tile not added", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(
                this,
                "Add the tile manually from Quick Settings edit",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun refreshQuickSettingsTile() {
        TileService.requestListeningState(
            this,
            ComponentName(this, QuickClockTileService::class.java)
        )
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
        return gson.fromJson(json, type) ?: mutableListOf()
    }
}