package com.example.registroasistencia

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.registroasistencia.databinding.ActivityCreateCourseBinding
import com.example.registroasistencia.models.Course
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class CreateCourseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateCourseBinding
    private var startTime: Calendar = Calendar.getInstance()
    private var endTime: Calendar = Calendar.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("courses")
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var bluetoothaddress = ""
    private var editingCourseId: String? = null
    private var currentCourse: Course? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Verificar si estamos editando
        editingCourseId = intent.getStringExtra("COURSE_ID")
        if (editingCourseId != null) {
            setupEditMode()
        }

        binding.btnStartTime.setOnClickListener {
            showTimePicker { cal ->
                startTime = cal
                binding.btnStartTime.text = "Inicio: ${cal.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", cal.get(Calendar.MINUTE))}"
            }
        }

        binding.btnEndTime.setOnClickListener {
            showTimePicker { cal ->
                endTime = cal
                binding.btnEndTime.text = "Fin: ${cal.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", cal.get(Calendar.MINUTE))}"
            }
        }

        binding.btnSaveCourse.setOnClickListener {
            saveCourse()
        }

        binding.btnGetlocation.setOnClickListener {
            getLocation()
        }

        setupBluetooth()
    }

    private fun setupEditMode() {
        binding.btnSaveCourse.text = "Actualizar Curso"
        database.child(editingCourseId!!).get().addOnSuccessListener { snapshot ->
            currentCourse = snapshot.getValue(Course::class.java)
            currentCourse?.let {
                binding.etCourseName.setText(it.name)
                binding.etLatitude.setText(it.latitude.toString())
                binding.etLongitude.setText(it.longitude.toString())
                binding.etRadius.setText(it.radius.toString())
                
                startTime.timeInMillis = it.startTime
                endTime.timeInMillis = it.endTime
                
                binding.btnStartTime.text = "Inicio: ${startTime.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", startTime.get(Calendar.MINUTE))}"
                binding.btnEndTime.text = "Fin: ${endTime.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", endTime.get(Calendar.MINUTE))}"
            }
        }
    }

    private fun setupBluetooth() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.getAdapter()

        if (bluetoothAdapter?.isEnabled==false){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }

        bluetoothaddress = bluetoothAdapter?.name ?: bluetoothAdapter?.address ?: "Sin Bluetooth"
    }

    private fun showTimePicker(onTimeSelected: (Calendar) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val result = Calendar.getInstance()
            result.set(Calendar.HOUR_OF_DAY, hour)
            result.set(Calendar.MINUTE, minute)
            onTimeSelected(result)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun saveCourse() {
        val name = binding.etCourseName.text.toString()
        val lat = binding.etLatitude.text.toString().toDoubleOrNull() ?: 0.0
        val lon = binding.etLongitude.text.toString().toDoubleOrNull() ?: 0.0
        val radius = binding.etRadius.text.toString().toFloatOrNull() ?: 0f


        if(lat == 0.0 || lon == 0.0 || name.isEmpty() || radius == 0f){
            Toast.makeText(this, "Por favor llene todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        if(bluetoothaddress=="Sin Bluetooth"){
            Toast.makeText(this, "Por favor habilite Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if(radius !in 1.0..15.0){
            Toast.makeText(this, "El radio debe estar entre 1 y 15", Toast.LENGTH_SHORT).show()
            return
        }


        val teacherId = auth.currentUser?.uid ?: ""
        
        val id = editingCourseId ?: database.push().key ?: ""
        val code = currentCourse?.code ?: (100000..999999).random().toString()

        if (name.isNotEmpty()) {
            val course = Course(
                id = id,
                name = name,
                teacherId = teacherId,
                code = code,
                latitude = lat,
                longitude = lon,
                radius = radius,
                startTime = startTime.timeInMillis,
                endTime = endTime.timeInMillis,
                bluetoothAddress = bluetoothaddress
            )

            database.child(id).setValue(course).addOnCompleteListener {
                if (it.isSuccessful) {
                    val msg = if (editingCourseId != null) "Curso Actualizado" else "Curso Creado! Código: $code"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun getLocation(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                binding.etLatitude.setText(location.latitude.toString())
                binding.etLongitude.setText(location.longitude.toString())
            }
        }
    }
}
