package com.example.registroasistencia

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.registroasistencia.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            checkUserRole(currentUser.uid)
        }
    }

    private fun checkUserRole(uid: String) {
        database.child(uid).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                val intent = if (user.role == "teacher") {
                    Intent(this, TeacherActivity::class.java)
                } else {
                    Intent(this, StudentActivity::class.java)
                }
                startActivity(intent)
                finish()
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }.addOnFailureListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
