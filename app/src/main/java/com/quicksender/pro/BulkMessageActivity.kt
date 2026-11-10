package com.quicksender.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.quicksender.pro.databinding.ActivityBulkMessageBinding
import kotlin.math.max
import kotlin.random.Random

class BulkMessageActivity : AppCompatActivity() {

    private lateinit var b: ActivityBulkMessageBinding
    private var group: GroupItem? = null
    private var selectedMediaUri: Uri? = null
    private var mediaMimeType: String? = null

    private var activeQueue = false
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            showPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBulkMessageBinding.inflate(layoutInflater)
        setContentView(b.root)

        val groupId = intent.getStringExtra("groupId") ?: return finish()
        group = GroupStore.loadGroups(this).find { it.id == groupId } ?: return finish()

        b.toolbarBulk.setNavigationOnClickListener { finish() }

        // Attach buttons
        b.btnAttachImage.setOnClickListener {
            mediaMimeType = "image/*"
            mediaPicker.launch("image/*")
        }
        b.btnAttachVideo.setOnClickListener {
            mediaMimeType = "video/*"
            mediaPicker.launch("video/*")
        }
        b.btnAttachAudio.setOnClickListener {
            mediaMimeType = "audio/*"
            mediaPicker.launch("audio/*")
        }
        b.btnRemoveMedia.setOnClickListener {
            selectedMediaUri = null
            mediaMimeType = null
            b.previewContainer.visibility = View.GONE
            b.tvCaptionLabel.text = "Message"
        }

        b.sliderDelay.addOnChangeListener { _, value, _ ->
            b.tvDelayLabel.text = "Anti-Ban Delay: ${value.toInt()}s (±2s random jitter)"
        }

        b.btnSendAll.setOnClickListener {
            if (activeQueue) {
                stopQueue()
            } else {
                startQueue()
            }
        }
    }

    private fun showPreview() {
        b.previewContainer.visibility = View.VISIBLE
        b.tvCaptionLabel.text = "Caption (Optional)"
        if (mediaMimeType?.startsWith("image") == true) {
            b.imgPreview.setImageURI(selectedMediaUri)
            b.imgPreview.visibility = View.VISIBLE
            b.tvMediaStatus.text = ""
        } else {
            b.imgPreview.visibility = View.GONE
            b.tvMediaStatus.text = "Attached: ${mediaMimeType?.substringBefore('/')?.uppercase()}"
        }
    }

    private fun startQueue() {
        val members = group?.members ?: return
        if (members.isEmpty()) return

        val text = b.etMessage.text.toString().trim()
        if (text.isEmpty() && selectedMediaUri == null) {
            Toast.makeText(this, "Enter a message or attach media", Toast.LENGTH_SHORT).show()
            return
        }

        activeQueue = true
        currentIndex = 0
        b.btnSendAll.text = "Pause Dispatch"
        b.progressSend.visibility = View.VISIBLE
        b.tvProgressStatus.visibility = View.VISIBLE
        b.progressSend.max = members.size
        b.progressSend.progress = 0

        dispatchNext()
    }

    private fun stopQueue() {
        activeQueue = false
        b.btnSendAll.text = "Resume Dispatch"
    }

    private fun dispatchNext() {
        val members = group?.members ?: return
        if (!activeQueue || currentIndex >= members.size) {
            activeQueue = false
            b.btnSendAll.text = "Completed!"
            return
        }

        val item = members[currentIndex]
        val pkg = if (b.rbBusiness.isChecked) "com.whatsapp.w4b" else "com.whatsapp"

        var msg = b.etMessage.text.toString()
        msg = Spintax.resolve(msg).replace("{name}", item.name).replace("{phone}", item.phone)

        val cleanPhone = if (item.phone.length <= 10) "91${item.phone}" else item.phone

        if (selectedMediaUri != null) {
            // Share media via Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mediaMimeType ?: "*/*"
                setPackage(pkg)
                putExtra(Intent.EXTRA_STREAM, selectedMediaUri)
                if (msg.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, msg)
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(shareIntent)
            } catch (_: Exception) {
                // Fallback to URL
                openViaUrl(cleanPhone, msg, pkg)
            }
        } else {
            openViaUrl(cleanPhone, msg, pkg)
        }

        currentIndex++
        b.progressSend.progress = currentIndex
        b.tvProgressStatus.text = "Sending: $currentIndex / ${members.size}"

        val baseDelay = b.sliderDelay.value.toInt() * 1000L
        val jitter = Random.nextLong(-2000L, 2001L)
        val delay = max(2000L, baseDelay + jitter)

        handler.postDelayed({ dispatchNext() }, delay)
    }

    private fun openViaUrl(phone: String, msg: String, pkg: String) {
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(msg)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }
}