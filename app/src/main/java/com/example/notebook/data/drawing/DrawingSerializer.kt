package com.example.notebook.data.drawing

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * DrawingData 的 JSON 序列化 / 反序列化 + 文件存取
 */
object DrawingSerializer {

    fun toJson(data: DrawingData): String {
        val json = JSONObject()
        json.put("width", data.width)
        json.put("height", data.height)
        val strokesArr = JSONArray()
        for (stroke in data.strokes) {
            val sj = JSONObject()
            sj.put("color", stroke.color)
            sj.put("strokeWidth", stroke.strokeWidth.toDouble())
            sj.put("isEraser", stroke.isEraser)
            val pointsArr = JSONArray()
            for (p in stroke.points) {
                val pj = JSONObject()
                pj.put("x", p.x.toDouble())
                pj.put("y", p.y.toDouble())
                pointsArr.put(pj)
            }
            sj.put("points", pointsArr)
            strokesArr.put(sj)
        }
        json.put("strokes", strokesArr)
        return json.toString()
    }

    fun fromJson(json: String): DrawingData {
        val obj = JSONObject(json)
        val w = obj.getInt("width")
        val h = obj.getInt("height")
        val arr = obj.getJSONArray("strokes")
        val strokes = mutableListOf<StrokeData>()
        for (i in 0 until arr.length()) {
            val sj = arr.getJSONObject(i)
            val pArr = sj.getJSONArray("points")
            val points = mutableListOf<PointData>()
            for (j in 0 until pArr.length()) {
                val pj = pArr.getJSONObject(j)
                points.add(PointData(pj.getDouble("x").toFloat(), pj.getDouble("y").toFloat()))
            }
            strokes.add(
                StrokeData(
                    points = points,
                    color = sj.getInt("color"),
                    strokeWidth = sj.getDouble("strokeWidth").toFloat(),
                    isEraser = sj.getBoolean("isEraser")
                )
            )
        }
        return DrawingData(strokes, w, h)
    }

    /** 保存绘画数据到 JSON 文件，返回文件路径 */
    fun saveToFile(context: Context, id: Long, data: DrawingData): String {
        val dir = File(context.filesDir, "drawings")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "drawing_${id}.json")
        file.writeText(toJson(data))
        return file.absolutePath
    }

    /** 从文件加载绘画数据 */
    fun loadFromFile(path: String): DrawingData? {
        return try {
            val file = File(path)
            if (file.exists()) fromJson(file.readText()) else null
        } catch (_: Exception) {
            null
        }
    }

    /** 保存缩略图 PNG，返回文件路径 */
    fun saveThumbnail(context: Context, id: Long, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "drawings")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "thumb_${id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 80, it) }
        return file.absolutePath
    }

    /** 删除旧文件（如果存在） */
    fun deleteFile(path: String?) {
        path?.let { File(it).delete() }
    }
}

