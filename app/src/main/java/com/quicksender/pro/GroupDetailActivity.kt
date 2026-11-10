package com.quicksender.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicksender.pro.databinding.ActivityGroupDetailBinding

class GroupDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityGroupDetailBinding
    private var group: GroupItem? = null
    private var allGroups = mutableListOf<GroupItem>()

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        val groupId = intent.getStringExtra("groupId") ?: return finish()
        allGroups = GroupStore.loadGroups(this)
        group = allGroups.find { it.id == groupId } ?: return finish()

        b.toolbarDetail.title = group?.name
        b.toolbarDetail.setNavigationOnClickListener { finish() }

        b.rvMembers.layoutManager = LinearLayoutManager(this)
        updateUI()

        b.btnAddContacts.setOnClickListener { showAddOptions() }
        b.btnRenameGroup.setOnClickListener { promptRename() }
        b.btnDeleteGroup.setOnClickListener { promptDelete() }

        b.btnCreateBulk.setOnClickListener {
            if (group?.members.isNullOrEmpty()) {
                Toast.makeText(this, "Add contacts first before creating message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, BulkMessageActivity::class.java).apply {
                putExtra("groupId", group?.id)
            }
            startActivity(intent)
        }
    }

    private fun updateUI() {
        val members = group?.members ?: mutableListOf()
        b.tvCountInfo.text = "Members (${members.size} / 400)"
        b.rvMembers.adapter = MemberAdapter(members)
    }

    private fun showAddOptions() {
        val options = arrayOf("Import CSV / Excel", "Paste Numbers Manually")
        AlertDialog.Builder(this)
            .setTitle("Add Members")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> csvPicker.launch("*/*")
                    1 -> promptPasteNumbers()
                }
            }
            .show()
    }

    private fun promptPasteNumbers() {
        val input = EditText(this).apply {
            hint = "Format (one per line):\n9876543210\nJohn, 9876543210"
            minLines = 6
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Paste Contacts")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                parseAndAdd(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importCsv(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            parseAndAdd(text)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseAndAdd(raw: String) {
        val target = group ?: return
        var added = 0
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(',', ';', '\t').map { it.trim().trim('"') }
            var phone = ""
            var name = "Friend"

            if (parts.size >= 2) {
                if (parts[0].any { it.isDigit() } && parts[0].filter { it.isDigit() }.length >= 10) {
                    phone = parts[0].filter { it.isDigit() }
                    name = parts[1].ifEmpty { "Friend" }
                } else {
                    name = parts[0].ifEmpty { "Friend" }
                    phone = parts[1].filter { it.isDigit() }
                }
            } else if (parts.isNotEmpty()) {
                phone = parts[0].filter { it.isDigit() }
            }

            if (phone.length >= 10 && target.members.size < 400) {
                if (!target.members.any { it.phone == phone }) {
                    target.members.add(MemberItem(name, phone))
                    added++
                }
            }
        }
        GroupStore.saveGroups(this, allGroups)
        updateUI()
        Toast.makeText(this, "Added $added member(s)", Toast.LENGTH_SHORT).show()
    }

    private fun promptRename() {
        val input = EditText(this).apply {
            setText(group?.name)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Group")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    group?.name = newName
                    b.toolbarDetail.title = newName
                    GroupStore.saveGroups(this, allGroups)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete this group?")
            .setPositiveButton("Delete") { _, _ ->
                allGroups.removeAll { it.id == group?.id }
                GroupStore.saveGroups(this, allGroups)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class MemberAdapter(private val members: List<MemberItem>) : RecyclerView.Adapter<MemberAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(android.R.id.text1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = members[position]
            holder.text.text = "${m.name} (+${m.phone})"
            holder.text.setTextColor(0xFFFFFFFF.toInt())
        }
        override fun getItemCount(): Int = members.size
    }
}