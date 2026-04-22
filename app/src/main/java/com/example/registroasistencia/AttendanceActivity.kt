package com.example.registroasistencia

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.registroasistencia.databinding.ActivityAttendanceBinding
import com.example.registroasistencia.models.Attendance
import com.example.registroasistencia.models.Course
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    private var course: Course? = null
    private var isLocationOk = false
    private var isTimeOk = false
    private var isBluetoothOk = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                // In a real app, you might match the teacher's MAC address or Name
                // For this demo, we assume any nearby device during the window is enough or just detecting any signal
                isBluetoothOk = true
                checkAllConditions()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val courseId = intent.getStringExtra("COURSE_ID") ?: return
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        loadCourseData(courseId)

        binding.btnMarkAttendance.setOnClickListener {
            markAsPresent()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun loadCourseData(courseId: String) {
        FirebaseDatabase.getInstance().getReference("courses").child(courseId).get()
            .addOnSuccessListener { snapshot ->
                course = snapshot.getValue(Course::class.java)
                course?.let {
                    binding.tvAttendanceCourseName.text = it.name
                    checkConditions(it)
                }
            }
    }

    private fun checkConditions(course: Course) {
        // 1. Check Time
        val now = System.currentTimeMillis()
        isTimeOk = now in course.startTime..course.endTime
        
        // 2. Check Location
        checkLocation(course)

        // 3. Start Bluetooth Scan
        startBluetoothScan()
        
        checkAllConditions()
    }

    private fun checkLocation(course: Course) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val results = FloatArray(1)
                binding.textView4.text=location.latitude.toString()
                binding.textView5.text=location.longitude.toString()

                Location.distanceBetween(location.latitude, location.longitude, course.latitude, course.longitude, results)
                isLocationOk = results[0] <= course.radius
                binding.textView6.text=results[0].toString()

                checkAllConditions()
            }
        }
    }

    private fun startBluetoothScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), 200)
            return
        }

        bluetoothAdapter?.startDiscovery()
    }

    private fun checkAllConditions() {
        var status = ""
        if (!isTimeOk) status += "Not in scheduled time.\n"
        if (!isLocationOk) status += "Outside of allowed radius.\n"
        if (!isBluetoothOk) status += "Teacher signal not detected.\n"

        if (isTimeOk && isLocationOk && isBluetoothOk) {
            binding.tvStatus.text = "All conditions met!"
            binding.pbChecking.visibility = View.GONE
            binding.btnMarkAttendance.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = if (status.isEmpty()) "Checking..." else status
        }
    }

    private fun markAsPresent() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val attendance = Attendance(uid, course?.id ?: "", System.currentTimeMillis())
        FirebaseDatabase.getInstance().getReference("attendance")
            .child(course?.id ?: "").child(uid).setValue(attendance)
            .addOnSuccessListener {
                Toast.makeText(this, "Attendance Registered!", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
