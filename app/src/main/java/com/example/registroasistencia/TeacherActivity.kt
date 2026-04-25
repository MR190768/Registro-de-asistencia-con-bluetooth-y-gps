package com.example.registroasistencia

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
            val intent = Intent(this, AttendanceReportActivity::class.java)
            intent.putExtra("COURSE_ID", course.id)
            intent.putExtra("COURSE_NAME", course.name)
            startActivity(intent)
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
                Toast.makeText(this@TeacherActivity, "Error al cargar las clases", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
