package com.quicksender.pro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GroupItem(
    val id: String,
    var name: String,
    val members: MutableList<MemberItem> = mutableListOf()
)

data class MemberItem(
    val name: String,
    val phone: String
)

object GroupStore {
    private const val PREFS_NAME = "quick_groups_prefs"
    private const val KEY_GROUPS = "groups_json"

    fun loadGroups(context: Context): MutableList<GroupItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_GROUPS, null) ?: return mutableListOf()
        val list = mutableListOf<GroupItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val members = mutableListOf<MemberItem>()
                val memArr = obj.optJSONArray("members") ?: JSONArray()
                for (j in 0 until memArr.length()) {
                    val mObj = memArr.getJSONObject(j)
                    members.add(MemberItem(mObj.getString("name"), mObj.getString("phone")))
                }
                list.add(GroupItem(id, name, members))
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveGroups(context: Context, groups: List<GroupItem>) {
        val arr = JSONArray()
        for (g in groups) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            val memArr = JSONArray()
            for (m in g.members) {
                val mObj = JSONObject()
                mObj.put("name", m.name)
                mObj.put("phone", m.phone)
                memArr.put(mObj)
            }
            obj.put("members", memArr)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GROUPS, arr.toString())
            .apply()
    }
}