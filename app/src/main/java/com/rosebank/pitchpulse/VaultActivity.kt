package com.rosebank.pitchpulse

import android.os.Bundle
import com.rosebank.pitchpulse.databinding.ActivityVaultBinding

/** Shows the hashed password, document database and blob files for the demo video. */
class VaultActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val email = Prefs.email(this)
        val account = if (email.isBlank()) null else DocumentStore.get(this, "accounts", email.lowercase())
        val predictions = DocumentStore.list(this, "predictions")
        val blobs = BlobStore.list(this)
        val hash = account?.optString("passwordHash").orEmpty()
        val provider = account?.optString("provider").orEmpty()
        val text = buildString {
            appendLine("AUTHENTICATION SERVICE")
            appendLine("email: ${account?.optString("email") ?: email.ifBlank { "guest" }}")
            appendLine("name: ${account?.optString("name") ?: Prefs.name(this@VaultActivity)}")
            appendLine("provider: ${provider.ifBlank { Prefs.mode(this@VaultActivity) }}")
            val firebaseUid = account?.optString("firebaseUid").orEmpty()
            if (firebaseUid.isNotBlank() && provider == "email") {
                appendLine("password: hashed and salted by Firebase Authentication (not readable by the app)")
            } else if (hash.isNotBlank()) {
                appendLine("algorithm: SHA-256")
                appendLine("salt: ${account?.optString("salt")}")
                appendLine("passwordHash:")
                appendLine(hash)
                appendLine("plain password: not stored")
            } else {
                appendLine("password: none — single sign-on or guest")
            }
            appendLine()
            appendLine("ONLINE SERVICES")
            appendLine(Cloud.status(this@VaultActivity))
            appendLine("REST API: ${Api.baseUrl(this@VaultActivity).ifEmpty { "TheSportsDB (public, direct)" }}")
            appendLine()
            appendLine("DOCUMENT DATABASE")
            if (predictions.isEmpty()) appendLine("predictions: empty")
            predictions.forEach { appendLine(it.toString()) }
            appendLine()
            appendLine("BLOB STORAGE")
            if (blobs.isEmpty()) appendLine("no files")
            blobs.forEach { appendLine("${it.name} · ${it.length()} bytes") }
        }
        binding.body.text = text
    }
}
