package com.ansh.micr

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.*
import java.io.File

data class ShareItem(
    val name: String,
    val size: String,
    val icon: Drawable?,
    val path: String,
    var isSelected: Boolean = false
)

class SendActivity : AppCompatActivity() {

    private val allItems = mutableListOf<ShareItem>()
    private val selectedItems = mutableListOf<ShareItem>()
    private lateinit var adapter: ShareItemAdapter
    private lateinit var tvSelectedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        val rvItems = findViewById<RecyclerView>(R.id.rvItems)
        rvItems.layoutManager = GridLayoutManager(this, 4)

        adapter = ShareItemAdapter(allItems) { item ->
            if (item.isSelected) {
                selectedItems.add(item)
            } else {
                selectedItems.remove(item)
            }
            tvSelectedCount.text = "${selectedItems.size} SELECTED"
        }
        rvItems.adapter = adapter

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        loadInstalledApps()

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "Please select at least one file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val integrator = IntentIntegrator(this)
            integrator.setPrompt("Scan Receiver QR Code")
            integrator.setBeepEnabled(true)
            integrator.setOrientationLocked(true)
            integrator.initiateScan()
        }
    }

    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<ShareItem>()

            for (app in apps) {
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    val name = pm.getApplicationLabel(app).toString()
                    val icon = pm.getApplicationIcon(app)
                    val file = File(app.sourceDir)
                    val mb = file.length() / (1024.0 * 1024.0)
                    list.add(ShareItem(name, String.format("%.1f MB", mb), icon, file.absolutePath))
                }
            }

            withContext(Dispatchers.Main) {
                allItems.clear()
                allItems.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            Toast.makeText(this, "QR Connected: Starting Transfer...", Toast.LENGTH_LONG).show()

            val serviceIntent = Intent(this, TransferService::class.java).apply {
                putExtra("IS_SENDER", true)
                putExtra("TARGET_IP", "192.168.43.1")
            }
            startService(serviceIntent)
            finish()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    class ShareItemAdapter(
        private val items: List<ShareItem>,
        private val onSelect: (ShareItem) -> Unit
    ) : RecyclerView.Adapter<ShareItemAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.ivIcon)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvSize: TextView = v.findViewById(R.id.tvSize)
            val cbSelect: CheckBox = v.findViewById(R.id.cbSelect)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val currentItem = items[position]
            holder.tvName.text = currentItem.name
            holder.tvSize.text = currentItem.size
            if (currentItem.icon != null) {
                holder.ivIcon.setImageDrawable(currentItem.icon)
            } else {
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            holder.cbSelect.isChecked = currentItem.isSelected

            holder.itemView.setOnClickListener {
                currentItem.isSelected = !currentItem.isSelected
                holder.cbSelect.isChecked = currentItem.isSelected
                onSelect(currentItem)
            }
        }

        override fun getItemCount() = items.size
    }
}
