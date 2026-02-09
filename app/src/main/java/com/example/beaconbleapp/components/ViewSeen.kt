package com.example.beaconbleapp.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.bluetooth.BluetoothDevice

data class BlDevice(
    val name: String,
    val address: String,
    val bluetoothDevice: BluetoothDevice,
    var isConnected: Boolean = false,
    var isConnecting: Boolean = false,
    val rssi: Int
)

class ViewSeen(
    private val devices: MutableList<BlDevice>,
    private val onDeviceClick: (BlDevice) -> Unit
) : RecyclerView.Adapter<ViewSeen.ViewHolder>() {

//    private val ViewSeen.context: Context

    class ViewHolder(
        view: View, val nameText: TextView,
        val macText: TextView, val statusText: TextView
    ) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 0, 10)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(
                parent.context,
                android.R.drawable.list_selector_background
            )
        }

        val nameText = TextView(parent.context).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
        }

        val macText = TextView(parent.context).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
        }

        val statusText = TextView(parent.context).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }

        root.addView(nameText)
        root.addView(macText)
        root.addView(statusText)

        return ViewHolder(root, nameText, macText, statusText)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]

        holder.nameText.text = "${when {
            device.isConnecting -> "🟡"
            device.isConnected -> "🟢"
            else -> "🔴"
        }} Name: ${device.name}"

        holder.macText.text = "MAC: ${device.address}"

        holder.statusText.setTextColor(
            when {
                device.isConnecting -> Color.parseColor("#DAA520")
                device.isConnected -> Color.GREEN
                else -> Color.RED
            }
        )

        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount(): Int = devices.size
}