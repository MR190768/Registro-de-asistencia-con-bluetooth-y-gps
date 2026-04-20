package com.example.registroasistencia

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.registroasistencia.databinding.ActivityTeacherBinding
import com.example.registroasistencia.models.Course
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TeacherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherBinding
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("courses")
    private val courses = mutableListOf<Course>()
    private lateinit var adapter: CourseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CourseAdapter(courses) { course ->
            // Teacher clicks on course: Option to activate Bluetooth for attendance
            activateBluetooth()
        }

        binding.rvCoursesTeacher.layoutManager = LinearLayoutManager(this)
        binding.rvCoursesTeacher.adapter = adapter

        binding.btnCreateCourse.setOnClickListener {
            startActivity(Intent(this, CreateCourseActivity::class.java))
        }

        binding.btnLogoutTeacher.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadCourses()
    }

    private fun loadCourses() {
        val uid = auth.currentUser?.uid ?: ""
        database.orderByChild("teacherId").equalTo(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                courses.clear()
                for (child in snapshot.children) {
                    val course = child.getValue(Course::class.java)
                    if (course != null) courses.add(course)
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@TeacherActivity, "Error loading courses", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun activateBluetooth() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        } else {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            startActivity(discoverableIntent)
            Toast.makeText(this, "Bluetooth discoverable for 5 minutes", Toast.LENGTH_SHORT).show()
        }
    }
}
