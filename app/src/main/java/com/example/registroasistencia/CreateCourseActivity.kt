package com.example.registroasistencia

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.registroasistencia.databinding.ActivityCreateCourseBinding
import com.example.registroasistencia.models.Course
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class CreateCourseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateCourseBinding
    private var startTime: Calendar = Calendar.getInstance()
    private var endTime: Calendar = Calendar.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("courses")
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartTime.setOnClickListener {
            showTimePicker { cal ->
                startTime = cal
                binding.btnStartTime.text = "Start: ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}"
            }
        }

        binding.btnEndTime.setOnClickListener {
            showTimePicker { cal ->
                endTime = cal
                binding.btnEndTime.text = "End: ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}"
            }
        }

        binding.btnSaveCourse.setOnClickListener {
            saveCourse()
        }
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
        val teacherId = auth.currentUser?.uid ?: ""
        val courseId = database.push().key ?: ""
        val code = (100000..999999).random().toString()

        if (name.isNotEmpty()) {
            val course = Course(
                id = courseId,
                name = name,
                teacherId = teacherId,
                code = code,
                latitude = lat,
                longitude = lon,
                radius = radius,
                startTime = startTime.timeInMillis,
                endTime = endTime.timeInMillis
            )

            database.child(courseId).setValue(course).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Course Created! Code: $code", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}
