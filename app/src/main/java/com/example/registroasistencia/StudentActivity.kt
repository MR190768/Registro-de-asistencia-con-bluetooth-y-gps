package com.example.registroasistencia

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.registroasistencia.databinding.ActivityStudentBinding
import com.example.registroasistencia.models.Course
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class StudentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentBinding
    private val auth = FirebaseAuth.getInstance()
    private val dbCourses = FirebaseDatabase.getInstance().getReference("courses")
    private val dbEnrollments = FirebaseDatabase.getInstance().getReference("enrollments")
    private val courses = mutableListOf<Course>()
    private lateinit var adapter: CourseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CourseAdapter(courses) { course ->
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("COURSE_ID", course.id)
            startActivity(intent)
        }

        binding.rvCoursesStudent.layoutManager = LinearLayoutManager(this)
        binding.rvCoursesStudent.adapter = adapter

        binding.btnEnroll.setOnClickListener {
            val code = binding.etEnrollCode.text.toString()
            if (code.isNotEmpty()) enrollInCourse(code)
        }

        binding.btnLogoutStudent.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadEnrolledCourses()
    }

    private fun enrollInCourse(code: String) {
        dbCourses.orderByChild("code").equalTo(code).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val course = snapshot.children.first().getValue(Course::class.java)
                    if (course != null) {
                        val uid = auth.currentUser?.uid ?: ""
                        dbEnrollments.child(uid).child(course.id).setValue(true).addOnCompleteListener {
                            if (it.isSuccessful) {
                                Toast.makeText(this@StudentActivity, "Se ha inscrito a ${course.name} exitosamente", Toast.LENGTH_SHORT).show()
                                binding.etEnrollCode.text.clear()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@StudentActivity, "Codigo invalido", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadEnrolledCourses() {
        val uid = auth.currentUser?.uid ?: ""
        dbEnrollments.child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                courses.clear()
                val courseIds = snapshot.children.mapNotNull { it.key }
                if (courseIds.isEmpty()) {
                    adapter.notifyDataSetChanged()
                    return
                }
                
                var loadedCount = 0
                for (id in courseIds) {
                    dbCourses.child(id).get().addOnSuccessListener { courseSnap ->
                        courseSnap.getValue(Course::class.java)?.let { courses.add(it) }
                        loadedCount++
                        if (loadedCount == courseIds.size) {
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
