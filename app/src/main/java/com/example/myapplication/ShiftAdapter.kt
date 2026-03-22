package com.example.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.util.Locale

class ShiftAdapter(
    context: Context,
    private val shifts: List<ShiftEntry>
) : ArrayAdapter<ShiftEntry>(context, 0, shifts) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_shift, parent, false)

        val shift = shifts[position]

        val textDate = view.findViewById<TextView>(R.id.textDate)
        val textClockIn = view.findViewById<TextView>(R.id.textClockIn)
        val textClockOut = view.findViewById<TextView>(R.id.textClockOut)
        val textHoursWorked = view.findViewById<TextView>(R.id.textHoursWorked)

        textDate.text = shift.date
        textClockIn.text = "In: ${shift.clockIn}"
        textClockOut.text = "Out: ${shift.clockOut}"
        textHoursWorked.text = String.format(Locale.US, "Hours: %.2f", shift.hoursWorked)

        return view
    }
}