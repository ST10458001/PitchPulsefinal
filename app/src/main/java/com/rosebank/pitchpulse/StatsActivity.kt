package com.rosebank.pitchpulse

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rosebank.pitchpulse.databinding.ActivityStatsBinding
import java.io.File

/** Full-screen stat card. Share writes a PNG and opens the Android Sharesheet (FR-004). */
class StatsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.isReady()) Repository.init(this)
        val binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getStringExtra("id").orEmpty()
        val card = intent.getStringExtra("card") ?: "possession"
        val match = Repository.match(id) ?: return finish()
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }
        fun paint(away: Boolean) {
            val (kicker, value, detail) = when (card) {
                "shots" -> Triple(
                    "SHOTS ON TARGET",
                    if (away) "${match.sotAway}" else "${match.sotHome} – ${match.sotAway}",
                    "${match.shotsHome} attempts home, ${match.shotsAway} away"
                )
                "xg" -> Triple(
                    "EXPECTED GOALS",
                    if (away) "${"%.2f".format(match.xgAway)}" else "${"%.2f".format(match.xgHome)} – ${"%.2f".format(match.xgAway)}",
                    match.pulse
                )
                "momentum" -> Triple("MOMENTUM", match.momentum.replace(",", " · "), "${match.home} vs ${match.away}")
                else -> Triple(
                    "POSSESSION",
                    if (away) "${100 - match.possessionHome}%" else "${match.possessionHome}%",
                    if (away) match.away else match.home
                )
            }
            binding.kicker.text = kicker
            binding.value.text = value
            binding.detail.text = detail
        }
        paint(false)
        binding.compare.setOnCheckedChangeListener { _, checked -> paint(checked) }
        binding.share.setOnClickListener {
            val bitmap = Bitmap.createBitmap(binding.card.width, binding.card.height, Bitmap.Config.ARGB_8888)
            binding.card.draw(Canvas(bitmap))
            val file = File(cacheDir, "pitchpulse-stat.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_card)))
            Toast.makeText(this, "Stat card ready to share", Toast.LENGTH_SHORT).show()
        }
    }
}
