package com.example.myapplication

import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class QuickClockTileService : TileService() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onStartListening() {
        super.onStartListening()
        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        sharedPreferences = getSharedPreferences("WorkAppPrefs", MODE_PRIVATE)

        val clockInTime = sharedPreferences.getLong("clockInTime", 0L)

        if (clockInTime == 0L) {
            val currentTime = System.currentTimeMillis()
            sharedPreferences.edit()
                .putLong("clockInTime", currentTime)
                .apply()

            Toast.makeText(this, "Clocked in", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(
                this,
                String.format("Clocked out. Added %.2f hours", workedHours),
                Toast.LENGTH_SHORT
            ).show()
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val clockInTime = sharedPreferences.getLong("clockInTime", 0L)

        if (clockInTime == 0L) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Clock In"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Not clocked in"
            }
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Clock Out"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Currently clocked in"
            }
        }

        tile.updateTile()
    }

    private fun formatTime(timeMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        return sdf.format(java.util.Date(timeMillis))
    }

    private fun formatDate(timeMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        return sdf.format(java.util.Date(timeMillis))
    }

    private fun saveShift(shiftEntry: ShiftEntry) {
        val gson = com.google.gson.Gson()
        val shifts = loadShifts()
        shifts.add(shiftEntry)

        val json = gson.toJson(shifts)
        sharedPreferences.edit().putString("dailyLog", json).apply()
    }

    private fun loadShifts(): MutableList<ShiftEntry> {
        val json = sharedPreferences.getString("dailyLog", null) ?: return mutableListOf()
        val type = object : com.google.gson.reflect.TypeToken<MutableList<ShiftEntry>>() {}.type
        return com.google.gson.Gson().fromJson(json, type) ?: mutableListOf()
    }
}