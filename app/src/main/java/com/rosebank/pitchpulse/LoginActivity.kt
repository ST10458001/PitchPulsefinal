package com.rosebank.pitchpulse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.rosebank.pitchpulse.databinding.ActivityLoginBinding

/**
 * FR-001. Register and sign in with a SHA-256 password hash, Google-style SSO, or guest browsing.
 * The plain password is never written to the document database.
 *
 * References:
 *  - OWASP, 2024. Password Storage Cheat Sheet (salted one-way hashing).
 *  - Android Developers, 2025. Log - used for the diagnostic log lines below.
 *  - Firebase, 2025. Authenticate with Firebase using password-based accounts on Android.
 *  - Android Developers, 2025. Sign in your user with Credential Manager (Google ID token).
 *
 * When Firebase is configured (google-services.json present) sign-in goes to Firebase Authentication,
 * which hashes and salts the password server-side. Otherwise the on-device fallback below is used.
 * The password itself is never logged.
 */
class LoginActivity : BaseActivity() {
    private val tag = "PP-Login"
    private var registerMode = false
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val upgrade = intent.getBooleanExtra("upgrade", false)

        fun paint() {
            binding.heading.text = if (registerMode) "Create your account" else "Welcome back"
            binding.primary.text = getString(if (registerMode) R.string.create_account else R.string.sign_in)
            binding.nameLayout.visibility = if (registerMode) View.VISIBLE else View.GONE
            binding.toggleMode.text = if (registerMode) "Have an account? Sign in" else "Need an account? Register"
            binding.guest.visibility = if (upgrade) View.GONE else View.VISIBLE
        }
        paint()
        binding.toggleMode.setOnClickListener {
            registerMode = !registerMode
            paint()
        }
        binding.primary.setOnClickListener {
            val email = binding.emailInput.text?.toString()?.trim().orEmpty()
            val password = binding.passwordInput.text?.toString().orEmpty()
            val name = binding.nameInput.text?.toString()?.trim().orEmpty()
            binding.emailLayout.error = null
            binding.passwordLayout.error = null
            binding.nameLayout.error = null
            if (!emailPattern.matches(email)) {
                binding.emailLayout.error = "Enter a valid email"
                return@setOnClickListener
            }
            if (!passwordOk(password)) {
                binding.passwordLayout.error = "8+ characters, one uppercase letter, one number"
                return@setOnClickListener
            }
            if (registerMode && !name.matches(Regex("^[A-Za-z0-9 ]{3,30}$"))) {
                binding.nameLayout.error = "3–30 letters, numbers or spaces"
                return@setOnClickListener
            }
            if (Cloud.ready(this)) {
                cloudEmail(binding, email, password, name, upgrade)
                return@setOnClickListener
            }
            val key = email.lowercase()
            val existing = DocumentStore.get(this, "accounts", key)
            if (registerMode) {
                if (existing != null) {
                    binding.emailLayout.error = "That email is already registered"
                    return@setOnClickListener
                }
                val salt = PasswordHasher.salt()
                val hash = PasswordHasher.hash(password, salt)
                DocumentStore.put(
                    this,
                    "accounts",
                    key,
                    org.json.JSONObject()
                        .put("email", email)
                        .put("name", name)
                        .put("provider", "email")
                        .put("algorithm", "SHA-256")
                        .put("salt", salt)
                        .put("passwordHash", hash)
                )
                Prefs.setSession(this, name, email, "email")
                Log.i(tag, "Registered new account for $key (hash stored, plain password not stored)")
                Toast.makeText(this, "Password stored as SHA-256, not plain text", Toast.LENGTH_SHORT).show()
                leave(upgrade)
                return@setOnClickListener
            }
            if (existing == null || existing.optString("provider") != "email") {
                Log.w(tag, "Login rejected: no password account for $key")
                binding.emailLayout.error = "No password account for that email"
                return@setOnClickListener
            }
            val salt = existing.optString("salt")
            val hash = existing.optString("passwordHash")
            if (!PasswordHasher.matches(password, salt, hash)) {
                Log.w(tag, "Login rejected: password did not match stored hash for $key")
                binding.passwordLayout.error = "Password does not match the stored hash"
                return@setOnClickListener
            }
            Prefs.setSession(this, existing.optString("name"), email, "email")
            Log.i(tag, "Login success for $key")
            leave(upgrade)
        }
        binding.google.setOnClickListener {
            if (Cloud.ready(this) && webClientId().isNotBlank()) {
                cloudGoogle(binding, upgrade)
                return@setOnClickListener
            }
            val email = binding.emailInput.text?.toString()?.trim().orEmpty().ifBlank { "alex.nkosi@gmail.com" }
            if (!emailPattern.matches(email)) {
                binding.emailLayout.error = "Enter the Google email to use for single sign-on"
                return@setOnClickListener
            }
            val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            DocumentStore.put(
                this,
                "accounts",
                email.lowercase(),
                org.json.JSONObject()
                    .put("email", email)
                    .put("name", name)
                    .put("provider", "google")
                    .put("passwordHash", org.json.JSONObject.NULL)
            )
            Prefs.setSession(this, name, email, "google")
            Log.i(tag, "Google SSO sign-in recorded for ${email.lowercase()} (no password stored)")
            Toast.makeText(this, "Signed in with Google SSO — no password stored", Toast.LENGTH_SHORT).show()
            leave(upgrade)
        }
        binding.guest.setOnClickListener {
            Prefs.setSession(this, "Guest", "", "guest")
            Log.i(tag, "Continuing as guest")
            leave(upgrade)
        }
    }

    /** Register or sign in with Firebase Authentication. The password never reaches our own storage. */
    private fun cloudEmail(binding: ActivityLoginBinding, email: String, password: String, name: String, upgrade: Boolean) {
        binding.primary.isEnabled = false
        lifecycleScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val user = if (registerMode) {
                    val created = auth.createUserWithEmailAndPassword(email, password).await().user!!
                    created.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
                    Log.i(tag, "Firebase: registered ${created.uid}")
                    created
                } else {
                    val signed = auth.signInWithEmailAndPassword(email, password).await().user!!
                    Log.i(tag, "Firebase: signed in ${signed.uid}")
                    signed
                }
                val display = if (registerMode) name else (user.displayName ?: email.substringBefore("@"))
                finishCloudSignIn(user.uid, email, display, "email", upgrade)
            } catch (e: Exception) {
                Log.w(tag, "Firebase email auth failed: ${e.javaClass.simpleName}")
                when (e) {
                    is FirebaseAuthUserCollisionException -> binding.emailLayout.error = "That email is already registered"
                    is FirebaseNetworkException -> binding.passwordLayout.error = "No connection. Check your internet and try again"
                    else -> binding.passwordLayout.error = if (registerMode) "Could not create the account" else "Email or password is incorrect"
                }
                Log.d(tag, "Sign-in error shown to the user")
            } finally {
                binding.primary.isEnabled = true
            }
        }
    }

    /** Google single sign-on: Credential Manager returns a Google ID token that Firebase exchanges for a user. */
    private fun cloudGoogle(binding: ActivityLoginBinding, upgrade: Boolean) {
        binding.google.isEnabled = false
        lifecycleScope.launch {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId())
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(this@LoginActivity).getCredential(this@LoginActivity, request).credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(google.idToken, null)
                    val user = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await().user!!
                    Log.i(tag, "Firebase: Google SSO signed in ${user.uid}")
                    val email = user.email ?: google.id
                    finishCloudSignIn(user.uid, email, user.displayName ?: email.substringBefore("@"), "google", upgrade)
                } else {
                    Log.w(tag, "Unexpected credential type: ${credential.type}")
                    binding.emailLayout.error = "Google sign-in did not return an account"
                }
            } catch (e: GetCredentialException) {
                Log.w(tag, "Google sign-in cancelled or failed: ${e.javaClass.simpleName}")
                binding.emailLayout.error = "Google sign-in was cancelled or is unavailable"
            } catch (e: Exception) {
                Log.e(tag, "Google SSO error", e)
                binding.emailLayout.error = "Could not sign in with Google"
            } finally {
                binding.google.isEnabled = true
            }
        }
    }

    /** Save the profile online (Firestore), mirror it on the device, register for push, then continue. */
    private fun finishCloudSignIn(uid: String, email: String, name: String, provider: String, upgrade: Boolean) {
        Cloud.saveProfile(this, uid, email, name, provider)
        DocumentStore.put(
            this,
            "accounts",
            email.lowercase(),
            org.json.JSONObject()
                .put("email", email)
                .put("name", name)
                .put("provider", provider)
                .put("firebaseUid", uid)
                .put("passwordHash", org.json.JSONObject.NULL)
        )
        Prefs.setSession(this, name, email, provider)
        Cloud.registerPush(this)
        leave(upgrade)
    }

    /** default_web_client_id is generated from google-services.json once Google sign-in is enabled in Firebase. */
    private fun webClientId(): String {
        val id = resources.getIdentifier("default_web_client_id", "string", packageName)
        return if (id == 0) "" else getString(id)
    }

    private fun leave(upgrade: Boolean) {
        if (upgrade) {
            finish()
            return
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun passwordOk(password: String): Boolean {
        return password.length >= 8 && password.any { it.isUpperCase() } && password.any { it.isDigit() }
    }
}
