package com.example.registroasistencia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.registroasistencia.models.Course

class CourseAdapter(private val courses: List<Course>, private val onClick: (Course) -> Unit) :
    RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvItemCourseName)
        val tvCode: TextView = view.findViewById(R.id.tvItemCourseCode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]
        holder.tvName.text = course.name
        holder.tvCode.text = "Code: ${course.code}"
        holder.itemView.setOnClickListener { onClick(course) }
    }

    override fun getItemCount() = courses.size
}
