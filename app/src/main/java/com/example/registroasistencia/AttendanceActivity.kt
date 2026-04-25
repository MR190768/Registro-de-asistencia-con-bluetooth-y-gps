package com.example.registroasistencia

import android.Manifest
import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.registroasistencia.databinding.ActivityAttendanceBinding
import com.example.registroasistencia.models.Attendance
import com.example.registroasistencia.models.Course
import com.example.registroasistencia.models.User
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var course: Course? = null
    private var isLocationOk = false
    private var isTimeOk = false
    private var isBluetoothOk = false
    private var bluetoothaddress = ""

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { updateLocationStatus(it) }
        }
    }

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startBluetoothScan()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        if (it.name == bluetoothaddress || it.address == bluetoothaddress) {
                            isBluetoothOk = true
                            checkAllConditions()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (!isBluetoothOk) startBluetoothScan()
                }
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

        startTimeCheckLoop()
    }

    private fun startTimeCheckLoop() {
        lifecycleScope.launch {
            while (true) {
                course?.let {
                    val now = System.currentTimeMillis()
                    val newIsTimeOk = now in it.startTime..it.endTime
                    if (newIsTimeOk != isTimeOk) {
                        isTimeOk = newIsTimeOk
                        checkAllConditions()
                    }
                }
                delay(1000)
            }
        }
    }

    private fun loadCourseData(courseId: String) {
        FirebaseDatabase.getInstance().getReference("courses").child(courseId).get()
            .addOnSuccessListener { snapshot ->
                course = snapshot.getValue(Course::class.java)
                course?.let {
                    binding.tvAttendanceCourseName.text = it.name
                    bluetoothaddress = it.bluetoothAddress
                    startRealTimeChecks()
                    calculateStudentAttendancePercentage(it.id)
                }
            }
    }

    private fun calculateStudentAttendancePercentage(courseId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(courseId)
        
        attendanceRef.get().addOnSuccessListener { snapshot ->
            var totalDays = snapshot.childrenCount.toDouble()
            var attendedDays = 0
            
            for (daySnapshot in snapshot.children) {
                if (daySnapshot.hasChild(uid)) {
                    attendedDays++
                }
            }
            
            if (totalDays > 0) {
                val percentage = (attendedDays / totalDays) * 100
                binding.textView6.text = "Tu asistencia: ${String.format("%.1f", percentage)}%"
            } else {
                binding.textView6.text = "Asistencia: 0%"
            }
        }
    }

    private fun startRealTimeChecks() {
        startLocationUpdates()
        startBluetoothScan()
        checkAllConditions()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(10000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun updateLocationStatus(location: Location) {
        course?.let {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, it.latitude, it.longitude, results)
            isLocationOk = results[0] <= it.radius
            checkAllConditions()
        }
    }

    private fun startBluetoothScan() {
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 200)
                return
            }
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
            return
        }

        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
    }

    private fun checkAllConditions() {
        var status = ""
        if (!isTimeOk) status += "Fuera de horario...\n"
        if (!isLocationOk) status += "Fuera del radio permitido...\n"
        if (!isBluetoothOk) status += "Señal del profesor no detectada...\n"

        if (isTimeOk && isLocationOk && isBluetoothOk) {
            binding.tvStatus.text = "¡Condiciones cumplidas!"
            binding.pbChecking.visibility = View.GONE
            binding.btnMarkAttendance.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = if (status.isEmpty()) "Verificando..." else status
            binding.btnMarkAttendance.visibility = View.GONE
            binding.pbChecking.visibility = View.VISIBLE
        }
    }

    private fun markAsPresent() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val courseId = course?.id ?: ""
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Obtenemos el nombre del estudiante antes de guardar
        FirebaseDatabase.getInstance().getReference("users").child(uid).get()
            .addOnSuccessListener { userSnapshot ->
                val user = userSnapshot.getValue(User::class.java)
                val studentName = user?.name ?: "Estudiante"
                
                val attendance = Attendance(uid, studentName, courseId, System.currentTimeMillis())
                
                FirebaseDatabase.getInstance().getReference("attendance")
                    .child(courseId)
                    .child(date)
                    .child(uid)
                    .setValue(attendance)
                    .addOnSuccessListener {
                        Toast.makeText(this, "¡Asistencia registrada!", Toast.LENGTH_SHORT).show()
                        calculateStudentAttendancePercentage(courseId)
                        finish()
                    }
            }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0)
    }

    override fun onStop() {
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try {
            unregisterReceiver(receiver)
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) { }
    }
}
