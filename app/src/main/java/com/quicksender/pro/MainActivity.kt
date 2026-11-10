package com.quicksender.pro

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicksender.pro.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val groups = mutableListOf<GroupItem>()
    private lateinit var adapter: GroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Left 3-Dots Settings Menu
        b.btnMenuLeft.setOnClickListener { showSettingsMenu(it) }

        // RecyclerView setup
        b.rvGroups.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter(groups) { group ->
            val intent = Intent(this, GroupDetailActivity::class.java).apply {
                putExtra("groupId", group.id)
            }
            startActivity(intent)
        }
        b.rvGroups.adapter = adapter

        // Plus buttons (Center for empty state, Bottom for populated state)
        b.fabCenter.setOnClickListener { promptCreateGroup() }
        b.fabBottom.setOnClickListener { promptCreateGroup() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        groups.clear()
        groups.addAll(GroupStore.loadGroups(this))
        adapter.notifyDataSetChanged()

        if (groups.isEmpty()) {
            b.emptyStateView.visibility = View.VISIBLE
            b.rvGroups.visibility = View.GONE
            b.fabBottom.visibility = View.GONE
        } else {
            b.emptyStateView.visibility = View.GONE
            b.rvGroups.visibility = View.VISIBLE
            b.fabBottom.visibility = View.VISIBLE
        }
    }

    private fun promptCreateGroup() {
        val input = EditText(this).apply {
            hint = "Enter group name (e.g., VIP Customers)"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Create New Group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newGroup = GroupItem(UUID.randomUUID().toString(), name)
                    groups.add(0, newGroup)
                    GroupStore.saveGroups(this, groups)
                    refreshList()
                    // Navigate directly to the new group
                    val intent = Intent(this, GroupDetailActivity::class.java).apply {
                        putExtra("groupId", newGroup.id)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Accessibility Permission")
        popup.menu.add(0, 2, 1, "Share App")
        popup.menu.add(0, 3, 2, "About QuickSender")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                2 -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "QuickSender Pro - Automated WhatsApp Bulk Messaging App")
                    }
                    startActivity(Intent.createChooser(intent, "Share via"))
                }
                3 -> {
                    AlertDialog.Builder(this)
                        .setTitle("QuickSender Pro")
                        .setMessage("Minimalist bulk campaign sender for WhatsApp & WhatsApp Business.\n\nAlways adhere to WhatsApp terms of service.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    class GroupAdapter(
        private val items: List<GroupItem>,
        private val onClick: (GroupItem) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvGroupName)
            val count: TextView = v.findViewById(R.id.tvMemberCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.count.text = "${item.members.size} / 400 members"
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}