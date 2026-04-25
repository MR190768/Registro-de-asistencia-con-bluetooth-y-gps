package com.example.registroasistencia

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.registroasistencia.databinding.ActivityAttendanceReportBinding
import com.example.registroasistencia.models.Attendance
import com.example.registroasistencia.models.User
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AttendanceReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceReportBinding
    private var courseId: String = ""
    private var selectedDate: String = ""
    private val studentsList = mutableListOf<StudentAttendance>()
    private lateinit var adapter: StudentAttendanceAdapter

    data class StudentAttendance(val id: String?, val name: String?, val status: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        courseId = intent.getStringExtra("COURSE_ID") ?: ""
        binding.tvReportCourseName.text = intent.getStringExtra("COURSE_NAME") ?: "Curso"

        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        binding.tvSelectedDate.text = "Fecha: $selectedDate"

        adapter = StudentAttendanceAdapter(studentsList)
        binding.rvAttendanceList.layoutManager = LinearLayoutManager(this)
        binding.rvAttendanceList.adapter = adapter

        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnExportPdf.setOnClickListener {
            exportToPdf()
        }

        binding.btnEditCourse.setOnClickListener {
            val intent = Intent(this, CreateCourseActivity::class.java)
            intent.putExtra("COURSE_ID", courseId)
            startActivity(intent)
        }

        binding.btnDeleteCourse.setOnClickListener {
            showDeleteConfirmation()
        }

        loadAttendanceData()
        calculateGeneralPercentage()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, day)
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            binding.tvSelectedDate.text = "Fecha: $selectedDate"
            loadAttendanceData()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadAttendanceData() {
        val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance")
            .child(courseId).child(selectedDate)

        // 1. Obtener quiénes marcaron asistencia hoy
        attendanceRef.get().addOnSuccessListener { attendanceSnapshot ->
            val presentIds = attendanceSnapshot.children.mapNotNull { it.key }

            // 2. Obtener la lista de inscritos en este curso
            FirebaseDatabase.getInstance().getReference("enrollments").get().addOnSuccessListener { enrollmentsSnapshot ->
                val enrolledStudentIds = mutableListOf<String>()

                // Estructura de enrollments: enrollments/{studentId}/{courseId} = true
                for (studentSnap in enrollmentsSnapshot.children) {
                    if (studentSnap.hasChild(courseId)) {
                        studentSnap.key?.let { enrolledStudentIds.add(it) }
                    }
                }

                // 3. Obtener los nombres de los inscritos
                FirebaseDatabase.getInstance().getReference("users").get().addOnSuccessListener { usersSnapshot ->
                    studentsList.clear()
                    for (id in enrolledStudentIds) {
                        val user = usersSnapshot.child(id).getValue(User::class.java)
                        if (user != null) {
                            val status = if (presentIds.contains(id)) "Presente" else "Ausente"
                            studentsList.add(StudentAttendance(id, user.name, status))
                        }
                    }
                    studentsList.sortBy { it.name }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun calculateGeneralPercentage() {
        val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(courseId)
        
        attendanceRef.get().addOnSuccessListener { snapshot ->
            var totalActualAttendances = 0L
            val daysCount = snapshot.childrenCount

            FirebaseDatabase.getInstance().getReference("enrollments").get().addOnSuccessListener { enrollmentsSnap ->
                var studentsCount = enrollmentsSnap.children.count{
                    it.hasChild(courseId)
                }
                val totalPossibleAttendances = daysCount * studentsCount
                for (daySnap in snapshot.children) {
                    totalActualAttendances += daySnap.childrenCount
                }

                if (totalPossibleAttendances > 0) {
                    val percentage = (totalActualAttendances.toDouble() / totalPossibleAttendances) * 100
                    binding.tvTotalPercentage.text = "Asistencia General del Curso: ${String.format("%.1f", percentage)}%"
                } else {
                    binding.tvTotalPercentage.text = "Asistencia General del Curso: 0%"
                }
            }

        }
    }

    private fun exportToPdf() {
        if (studentsList.isEmpty()) {
            Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        
        var y = 25f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Reporte de Asistencia - $selectedDate", 10f, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        canvas.drawText("Curso: ${binding.tvReportCourseName.text}", 10f, y, paint)
        y += 30f
        
        paint.textSize = 10f
        for (student in studentsList) {
            canvas.drawText("${student.name}: ${student.status}", 10f, y, paint)
            y += 15f
            if (y > 580) break
        }

        pdfDocument.finishPage(page)

        val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Asistencia_$selectedDate.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(filePath))
            Toast.makeText(this, "PDF guardado en: ${filePath.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al crear PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        pdfDocument.close()
    }

    class StudentAttendanceAdapter(private val list: List<StudentAttendance>) :
        RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val tvStatus: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name
            holder.tvStatus.text = item.status
            holder.tvStatus.setTextColor(if (item.status == "Presente") 0xFF008800.toInt() else 0xFFFF0000.toInt())
        }

        override fun getItemCount() = list.size
    }

    private fun showDeleteConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Eliminar Curso")
            .setMessage("¿Estás seguro de que deseas eliminar este curso? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                FirebaseDatabase.getInstance().getReference("courses").child(courseId).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Curso eliminado", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
