package com.example.data.local

import com.example.data.model.ProjectFile
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<SavedProject>> = projectDao.getAllProjects()

    suspend fun saveProject(name: String, files: List<ProjectFile>, existingId: Long? = null): Long {
        val json = serializeFiles(files)
        val project = SavedProject(
            id = existingId ?: 0,
            name = name,
            updatedAt = System.currentTimeMillis(),
            filesJson = json
        )
        return projectDao.insertProject(project)
    }

    suspend fun loadProject(id: Long): Pair<SavedProject, List<ProjectFile>>? {
        val project = projectDao.getProjectById(id) ?: return null
        val files = deserializeFiles(project.filesJson)
        return Pair(project, files)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }

    fun serializeFiles(files: List<ProjectFile>): String {
        val jsonArray = JSONArray()
        for (file in files) {
            val obj = JSONObject().apply {
                put("id", file.id)
                put("name", file.name)
                put("path", file.path)
                put("content", file.content)
                put("isFolder", file.isFolder)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    fun deserializeFiles(json: String): List<ProjectFile> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<ProjectFile>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ProjectFile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "untitled"),
                        path = obj.optString("path", obj.optString("name", "untitled")),
                        content = obj.optString("content", ""),
                        isFolder = obj.optBoolean("isFolder", false),
                        isModified = false
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
