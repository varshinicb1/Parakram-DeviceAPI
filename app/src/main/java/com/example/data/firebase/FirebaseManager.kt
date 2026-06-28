package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.AuditLog
import com.example.data.AutomationWorkflow
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager(private val context: Context) {
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    
    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable

    private val _currentUserFlow = MutableStateFlow<UserSession?>(null)
    val currentUserFlow: StateFlow<UserSession?> = _currentUserFlow

    init {
        try {
            // Check if Firebase is initialized. The google-services plugin might not have
            // found a google-services.json, in which case FirebaseApp won't be initialized.
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                _isFirebaseAvailable.value = true
                
                auth?.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    _currentUserFlow.value = user?.toUserSession()
                }
                Log.d("FirebaseManager", "Firebase successfully initialized and available.")
            } else {
                Log.w("FirebaseManager", "FirebaseApp is not initialized. Using Sandbox mode.")
                _isFirebaseAvailable.value = false
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Failed to initialize Firebase: ${e.message}. Using Sandbox mode.")
            _isFirebaseAvailable.value = false
        }
    }

    // High-fidelity local state for Sandbox Mode fallback
    private var sandboxUser: UserSession? = null
    private val sandboxLogs = mutableListOf<AuditLog>()
    private val sandboxWorkflows = mutableListOf<AutomationWorkflow>()

    fun signInWithEmail(email: String, name: String) {
        if (_isFirebaseAvailable.value && auth != null) {
            // Create or sign in user. To make it extremely easy to test,
            // we do a passwordless flow or simple sign in if exists, otherwise create.
            auth?.signInWithEmailAndPassword(email, "devapi1234")
                ?.addOnFailureListener {
                    // Try to create the user if they don't exist
                    auth?.createUserWithEmailAndPassword(email, "devapi1234")
                        ?.addOnSuccessListener { result ->
                            // Update display name
                            val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                                displayName = name
                            }
                            result.user?.updateProfile(profileUpdates)
                        }
                }
        } else {
            // Local fallback session
            sandboxUser = UserSession(
                uid = "sandbox_user_123",
                email = email,
                displayName = name,
                photoUrl = "https://lh3.googleusercontent.com/a/default-user=s96-c",
                isSandbox = true
            )
            _currentUserFlow.value = sandboxUser
        }
    }

    fun signInWithGoogle(idToken: String, email: String, name: String, photoUrl: String) {
        if (_isFirebaseAvailable.value && auth != null) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth?.signInWithCredential(credential)
                ?.addOnFailureListener {
                    // Fall back to sign in with email if credential fails
                    signInWithEmail(email, name)
                }
        } else {
            sandboxUser = UserSession(
                uid = "google_oauth_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8),
                email = email,
                displayName = name,
                photoUrl = photoUrl.ifEmpty { "https://lh3.googleusercontent.com/a/default-user=s96-c" },
                isSandbox = true
            )
            _currentUserFlow.value = sandboxUser
        }
    }

    fun signOut() {
        if (_isFirebaseAvailable.value && auth != null) {
            auth?.signOut()
        } else {
            sandboxUser = null
            _currentUserFlow.value = null
        }
    }

    // Logs synchronization
    suspend fun saveAuditLog(log: AuditLog) {
        val user = _currentUserFlow.value ?: return
        if (_isFirebaseAvailable.value && firestore != null) {
            try {
                firestore!!.collection("users")
                    .document(user.uid)
                    .collection("logs")
                    .document(log.id)
                    .set(log)
                    .await()
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error saving log to Firestore: ${e.message}")
            }
        } else {
            sandboxLogs.add(0, log) // Add to top
        }
    }

    suspend fun getAuditLogs(): List<AuditLog> {
        val user = _currentUserFlow.value ?: return emptyList()
        if (_isFirebaseAvailable.value && firestore != null) {
            return try {
                val snapshot = firestore!!.collection("users")
                    .document(user.uid)
                    .collection("logs")
                    .orderBy("timestamp")
                    .get()
                    .await()
                snapshot.toObjects(AuditLog::class.java)
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error loading logs: ${e.message}")
                emptyList()
            }
        } else {
            return sandboxLogs
        }
    }

    // Workflows synchronization
    suspend fun saveWorkflow(workflow: AutomationWorkflow) {
        val user = _currentUserFlow.value ?: return
        if (_isFirebaseAvailable.value && firestore != null) {
            try {
                firestore!!.collection("users")
                    .document(user.uid)
                    .collection("workflows")
                    .document(workflow.id)
                    .set(workflow)
                    .await()
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error saving workflow to Firestore: ${e.message}")
            }
        } else {
            val index = sandboxWorkflows.indexOfFirst { it.id == workflow.id }
            if (index >= 0) {
                sandboxWorkflows[index] = workflow
            } else {
                sandboxWorkflows.add(workflow)
            }
        }
    }

    suspend fun getWorkflows(): List<AutomationWorkflow> {
        val user = _currentUserFlow.value ?: return emptyList()
        if (_isFirebaseAvailable.value && firestore != null) {
            return try {
                val snapshot = firestore!!.collection("users")
                    .document(user.uid)
                    .collection("workflows")
                    .get()
                    .await()
                val list = snapshot.toObjects(AutomationWorkflow::class.java)
                if (list.isEmpty()) {
                    // Populate default workflows
                    getDefaultWorkflows().forEach { saveWorkflow(it) }
                    getDefaultWorkflows()
                } else {
                    list
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error loading workflows: ${e.message}")
                emptyList()
            }
        } else {
            if (sandboxWorkflows.isEmpty()) {
                sandboxWorkflows.addAll(getDefaultWorkflows())
            }
            return sandboxWorkflows
        }
    }

    private fun getDefaultWorkflows(): List<AutomationWorkflow> {
        return listOf(
            AutomationWorkflow("w1", "Clipboard Synchronizer", "Automatically mirror desktop copy operations to smartphone clipboard", "On Desktop Copy", "Write Local Clipboard", true),
            AutomationWorkflow("w2", "Proximity Camera Capture", "Activate camera stream when desktop comes within Bluetooth range", "On Proximity Trigger", "Start Camera Stream", false),
            AutomationWorkflow("w3", "Energy Saver Mode", "Notify desktop to lower streaming frames when smartphone battery is low", "Battery < 20%", "Limit Frame Rate (15 FPS)", true),
            AutomationWorkflow("w4", "Biometric Desktop Unlock", "Use phone fingerprint reader to unlock paired desktop computer", "On Desktop Lockscreen Request", "Request Biometric Challenge", false)
        )
    }

    private var sandboxAdminProfile: AdminProfile? = null

    suspend fun saveAdminProfile(profile: AdminProfile) {
        val user = _currentUserFlow.value ?: return
        if (_isFirebaseAvailable.value && firestore != null) {
            try {
                firestore!!.collection("users")
                    .document(user.uid)
                    .collection("profile")
                    .document("admin")
                    .set(profile)
                    .await()
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error saving admin profile to Firestore: ${e.message}")
            }
        } else {
            sandboxAdminProfile = profile
        }
    }

    suspend fun getAdminProfile(): AdminProfile {
        val user = _currentUserFlow.value ?: return AdminProfile()
        if (_isFirebaseAvailable.value && firestore != null) {
            return try {
                val document = firestore!!.collection("users")
                    .document(user.uid)
                    .collection("profile")
                    .document("admin")
                    .get()
                    .await()
                document.toObject(AdminProfile::class.java) ?: AdminProfile(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    apiKey = "pk_live_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                )
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error getting admin profile: ${e.message}")
                AdminProfile(uid = user.uid, displayName = user.displayName, email = user.email)
            }
        } else {
            if (sandboxAdminProfile == null) {
                sandboxAdminProfile = AdminProfile(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    apiKey = "pk_sandbox_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                )
            }
            return sandboxAdminProfile!!
        }
    }

    private fun FirebaseUser.toUserSession() = UserSession(
        uid = uid,
        email = email ?: "",
        displayName = displayName ?: "Companion Developer",
        photoUrl = photoUrl?.toString() ?: "https://lh3.googleusercontent.com/a/default-user=s96-c",
        isSandbox = false
    )
}

data class UserSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isSandbox: Boolean = false
)

data class AdminProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val organization: String = "Enterprise Core",
    val developerRole: String = "Lead Systems Architect",
    val apiKey: String = "",
    val maxDevices: Int = 10,
    val securityLevel: String = "ECC-384 + TLS 1.3"
)
