package com.paisabrain.app.emergency

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * "In Case of Emergency" Vault — a separate secure section that a trusted person can access.
 *
 * The ICE Vault is designed to be accessed by a designated trusted person (spouse, parent, child)
 * in the event that the user is incapacitated or passes away. It contains critical financial
 * and legal information needed to handle the user's affairs.
 *
 * ## Security Model
 * - Completely separate encryption from the main app
 * - Uses its own AES-256-GCM key derived from a dedicated ICE password
 * - The ICE password is different from the app's main password/PIN
 * - Trusted person only sees ICE vault data — NOT spending data, transactions, or personal vault
 * - The app cannot decrypt ICE vault without the ICE password (zero-knowledge)
 *
 * ## Contents
 * - Bank accounts (name, number, branch, nominee)
 * - Insurance policies (type, number, nominee, maturity)
 * - Loan details (outstanding, EMI, tenure)
 * - Investment accounts
 * - Property document locations
 * - Important contacts (CA, lawyer, advisor)
 * - Digital asset information
 * - Personal letter/instructions to loved ones
 * - Auto-generated action checklist
 *
 * ## Usage
 * ```kotlin
 * val vault = IceVault(context)
 * vault.setupIcePassword("secure_password_here")
 * vault.saveIceVaultData("secure_password_here", myVaultData)
 *
 * // Trusted person access:
 * if (vault.verifyIcePassword("their_password_attempt")) {
 *     val data = vault.getIceVaultData("their_password_attempt")
 * }
 * ```
 *
 * @property context Application context for storage access
 */
class IceVault(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "ice_vault_config"
        private const val KEY_PASSWORD_HASH = "ice_pwd_hash"
        private const val KEY_SALT = "ice_salt"
        private const val KEY_ENCRYPTED_DATA = "ice_encrypted_data"
        private const val KEY_IV = "ice_iv"
        private const val KEY_LAST_UPDATED = "ice_last_updated"
        private const val KEY_TRUSTED_PERSON_NAME = "ice_trusted_person"
        private const val KEY_IS_CONFIGURED = "ice_configured"

        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val SALT_LENGTH_BYTES = 32

        /** Remind to update if vault is older than this (in milliseconds) — 6 months */
        private const val UPDATE_REMINDER_THRESHOLD_MS = 180L * 24 * 60 * 60 * 1000
    }

    private val gson = Gson()
    private val secureRandom = SecureRandom()

    // ─────────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Bank account information for the ICE vault.
     *
     * @property bankName Name of the bank (user-entered, no branding)
     * @property accountNumber Full account number
     * @property ifscCode IFSC code of the branch
     * @property branch Branch name/address
     * @property accountType Type of account (Savings, Current, FD, etc.)
     * @property nominee Name of nominee registered on this account
     */
    data class BankAccountInfo(
        val bankName: String,
        val accountNumber: String,
        val ifscCode: String = "",
        val branch: String = "",
        val accountType: String = "Savings",
        val nominee: String = ""
    )

    /**
     * Insurance policy information.
     *
     * @property policyType Type (Life, Health, Motor, Home, etc.)
     * @property policyNumber Policy number
     * @property providerName Insurance provider (user-entered)
     * @property nominee Named nominee for claims
     * @property sumAssured Coverage amount
     * @property premiumAmount Premium payment amount
     * @property premiumFrequency How often premium is paid (Monthly, Quarterly, Annual)
     * @property maturityDate When the policy matures (if applicable)
     * @property helplineNumber Provider's claim helpline
     */
    data class InsuranceInfo(
        val policyType: String,
        val policyNumber: String,
        val providerName: String = "",
        val nominee: String = "",
        val sumAssured: Double = 0.0,
        val premiumAmount: Double = 0.0,
        val premiumFrequency: String = "Annual",
        val maturityDate: String = "",
        val helplineNumber: String = ""
    )

    /**
     * Loan/liability information.
     *
     * @property loanType Type (Home Loan, Personal Loan, Car Loan, Education Loan, Credit Card, etc.)
     * @property lenderName Lender/bank name (user-entered)
     * @property outstandingAmount Current outstanding balance
     * @property emiAmount Monthly EMI amount
     * @property remainingMonths Number of EMIs remaining
     * @property accountNumber Loan account number
     * @property hasInsurance Whether loan protection insurance is active
     * @property insuranceDetails Details of loan protection (if any)
     */
    data class LoanInfo(
        val loanType: String,
        val lenderName: String,
        val outstandingAmount: Double,
        val emiAmount: Double,
        val remainingMonths: Int,
        val accountNumber: String = "",
        val hasInsurance: Boolean = false,
        val insuranceDetails: String = ""
    )

    /**
     * Investment account information.
     *
     * @property type Type (Mutual Funds, Stocks, PPF, NPS, FD, Gold, etc.)
     * @property accountId Account/Folio number
     * @property providerName Where the investment is held
     * @property approximateValue Last known approximate value
     * @property nominee Named nominee
     * @property notes Additional notes (login info hints, where documents are, etc.)
     */
    data class InvestmentInfo(
        val type: String,
        val accountId: String,
        val providerName: String = "",
        val approximateValue: Double = 0.0,
        val nominee: String = "",
        val notes: String = ""
    )

    /**
     * Important contact (professional advisors, etc.)
     *
     * @property name Contact's name
     * @property role Their role (CA, Lawyer, Financial Advisor, etc.)
     * @property phone Phone number
     * @property email Email address
     * @property notes Additional context
     */
    data class Contact(
        val name: String,
        val role: String,
        val phone: String,
        val email: String = "",
        val notes: String = ""
    )

    /**
     * Complete ICE Vault data structure.
     *
     * @property bankAccounts All bank accounts information
     * @property insurancePolicies All insurance policies
     * @property loans All outstanding loans/liabilities
     * @property investments All investment accounts
     * @property importantContacts Professional contacts (CA, lawyer, etc.)
     * @property digitalAssets Notes about digital accounts, crypto wallets, etc.
     * @property propertyDocumentsLocation Where physical property documents are kept
     * @property safeDepositBoxDetails Details of any safe deposit boxes
     * @property letterToLovedOnes Personal instructions/letter from the user
     * @property additionalNotes Any other critical information
     * @property lastUpdated Timestamp of last update
     */
    data class IceVaultData(
        val bankAccounts: List<BankAccountInfo> = emptyList(),
        val insurancePolicies: List<InsuranceInfo> = emptyList(),
        val loans: List<LoanInfo> = emptyList(),
        val investments: List<InvestmentInfo> = emptyList(),
        val importantContacts: List<Contact> = emptyList(),
        val digitalAssets: List<String> = emptyList(),
        val propertyDocumentsLocation: String = "",
        val safeDepositBoxDetails: String = "",
        val letterToLovedOnes: String = "",
        val additionalNotes: String = "",
        val lastUpdated: Long = System.currentTimeMillis()
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Configuration & Storage (non-encrypted metadata)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Checks if the ICE vault has been set up (password configured).
     *
     * @return true if ICE vault is configured and ready to use
     */
    fun isConfigured(): Boolean {
        return getPrefs().getBoolean(KEY_IS_CONFIGURED, false)
    }

    /**
     * Gets the name of the designated trusted person.
     *
     * @return Trusted person's name, or empty string if not set
     */
    fun getTrustedPersonName(): String {
        return getPrefs().getString(KEY_TRUSTED_PERSON_NAME, "") ?: ""
    }

    /**
     * Sets the trusted person's name (displayed on the ICE vault access screen).
     *
     * @param name Name of the trusted person
     */
    fun setTrustedPersonName(name: String) {
        getPrefs().edit().putString(KEY_TRUSTED_PERSON_NAME, name).apply()
    }

    /**
     * Gets the timestamp when ICE vault data was last updated.
     *
     * @return Timestamp in milliseconds, or 0 if never updated
     */
    fun getLastUpdatedTime(): Long {
        return getPrefs().getLong(KEY_LAST_UPDATED, 0L)
    }

    /**
     * Checks if the vault needs updating (older than 6 months).
     *
     * @return true if the vault should be reviewed and updated
     */
    fun needsUpdate(): Boolean {
        val lastUpdated = getLastUpdatedTime()
        if (lastUpdated == 0L) return false // Never configured = not overdue
        return (System.currentTimeMillis() - lastUpdated) > UPDATE_REMINDER_THRESHOLD_MS
    }

    /**
     * Gets a human-readable string of when the vault was last updated.
     *
     * @return Formatted date string, or "Never" if not configured
     */
    fun getLastUpdatedDisplay(): String {
        val lastUpdated = getLastUpdatedTime()
        if (lastUpdated == 0L) return "Never"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(lastUpdated))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Password & Encryption Setup
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sets up the ICE vault password for the first time.
     *
     * This generates a unique salt, derives a key from the password, and stores
     * the password hash for verification. The actual encryption key is derived
     * fresh each time from the password + salt (never stored).
     *
     * @param password The ICE vault password (should be different from app password)
     * @return true if setup was successful
     */
    fun setupIcePassword(password: String): Boolean {
        if (password.length < 6) return false

        try {
            // Generate random salt
            val salt = ByteArray(SALT_LENGTH_BYTES)
            secureRandom.nextBytes(salt)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            // Derive password hash for verification (using same PBKDF2 but with extra iterations)
            val verificationHash = deriveVerificationHash(password, salt)
            val hashBase64 = Base64.encodeToString(verificationHash, Base64.NO_WRAP)

            getPrefs().edit()
                .putString(KEY_SALT, saltBase64)
                .putString(KEY_PASSWORD_HASH, hashBase64)
                .putBoolean(KEY_IS_CONFIGURED, true)
                .apply()

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Changes the ICE vault password.
     *
     * Decrypts data with old password, re-encrypts with new password.
     *
     * @param oldPassword Current ICE password
     * @param newPassword New ICE password to set
     * @return true if password was changed successfully
     */
    fun changeIcePassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyIcePassword(oldPassword)) return false
        if (newPassword.length < 6) return false

        // Retrieve existing data
        val existingData = getIceVaultData(oldPassword)

        // Setup new password
        if (!setupIcePassword(newPassword)) return false

        // Re-encrypt data with new password
        if (existingData != null) {
            return saveIceVaultData(newPassword, existingData)
        }
        return true
    }

    /**
     * Verifies an ICE password attempt.
     *
     * @param password The password to verify
     * @return true if the password is correct
     */
    fun verifyIcePassword(password: String): Boolean {
        val prefs = getPrefs()
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
        val storedHashBase64 = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false

        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val attemptHash = deriveVerificationHash(password, salt)
            val attemptHashBase64 = Base64.encodeToString(attemptHash, Base64.NO_WRAP)

            // Constant-time comparison to prevent timing attacks
            constantTimeEquals(storedHashBase64, attemptHashBase64)
        } catch (e: Exception) {
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Data Access (encrypted)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves and decrypts the ICE vault data.
     *
     * The trusted person calls this after successful password verification
     * to access the vault contents.
     *
     * @param password The verified ICE password
     * @return Decrypted [IceVaultData] or null if decryption fails or no data exists
     */
    fun getIceVaultData(password: String): IceVaultData? {
        if (!verifyIcePassword(password)) return null

        val prefs = getPrefs()
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_DATA, null) ?: return null
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return null
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return null

        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val encryptedData = Base64.decode(encryptedBase64, Base64.NO_WRAP)

            val key = deriveEncryptionKey(password, salt)
            val decryptedJson = decrypt(encryptedData, key, iv)

            gson.fromJson(decryptedJson, IceVaultData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypts and saves ICE vault data.
     *
     * @param password The ICE password (for key derivation)
     * @param data The vault data to encrypt and store
     * @return true if saved successfully
     */
    fun saveIceVaultData(password: String, data: IceVaultData): Boolean {
        if (!verifyIcePassword(password)) return false

        return try {
            val prefs = getPrefs()
            val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)

            val key = deriveEncryptionKey(password, salt)
            val iv = generateIv()

            val dataWithTimestamp = data.copy(lastUpdated = System.currentTimeMillis())
            val json = gson.toJson(dataWithTimestamp)
            val encrypted = encrypt(json, key, iv)

            prefs.edit()
                .putString(KEY_ENCRYPTED_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply()

            true
        } catch (e: Exception) {
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Action Checklist Generator
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Auto-generates a "What to do" action checklist for the trusted person.
     *
     * The checklist is customized based on what information the user has stored
     * in the vault. It provides a prioritized list of actions the trusted person
     * should take.
     *
     * @return Ordered list of action items, or empty list if vault is not configured
     */
    fun generateActionChecklist(password: String): List<String> {
        val data = getIceVaultData(password) ?: return emptyList()
        val checklist = mutableListOf<String>()

        // Priority 1: Immediate actions
        checklist.add("📋 IMMEDIATE ACTIONS (within 24 hours):")

        if (data.importantContacts.isNotEmpty()) {
            val firstContact = data.importantContacts.first()
            checklist.add("  ☐ Contact ${firstContact.name} (${firstContact.role}) at ${firstContact.phone}")
        }

        if (data.bankAccounts.isNotEmpty()) {
            checklist.add("  ☐ Inform all banks about the situation — they may freeze accounts for security")
            data.bankAccounts.forEach { account ->
                checklist.add("    → ${account.bankName} — A/c: ${account.accountNumber} (${account.branch})")
            }
        }

        // Priority 2: Insurance claims
        if (data.insurancePolicies.isNotEmpty()) {
            checklist.add("")
            checklist.add("📋 INSURANCE CLAIMS (within 7 days):")
            data.insurancePolicies.forEach { policy ->
                checklist.add("  ☐ File claim for ${policy.policyType} — Policy: ${policy.policyNumber}")
                if (policy.helplineNumber.isNotEmpty()) {
                    checklist.add("    → Call: ${policy.helplineNumber}")
                }
                if (policy.nominee.isNotEmpty()) {
                    checklist.add("    → Nominee: ${policy.nominee}")
                }
            }
        }

        // Priority 3: Loan management
        if (data.loans.isNotEmpty()) {
            checklist.add("")
            checklist.add("📋 LOANS & LIABILITIES:")
            data.loans.forEach { loan ->
                checklist.add("  ☐ Inform ${loan.lenderName} about ${loan.loanType}")
                checklist.add("    → Outstanding: ₹${String.format("%,.0f", loan.outstandingAmount)} | EMI: ₹${String.format("%,.0f", loan.emiAmount)}/month")
                if (loan.hasInsurance) {
                    checklist.add("    → ⚡ Has loan protection insurance — file claim to clear loan")
                }
            }
            checklist.add("  ☐ Check if any loans have protection insurance that covers this situation")
        }

        // Priority 4: Investments
        if (data.investments.isNotEmpty()) {
            checklist.add("")
            checklist.add("📋 INVESTMENTS (can be done within 30 days):")
            data.investments.forEach { investment ->
                checklist.add("  ☐ ${investment.type} at ${investment.providerName}")
                checklist.add("    → Account/Folio: ${investment.accountId}")
                if (investment.nominee.isNotEmpty()) {
                    checklist.add("    → Nominee: ${investment.nominee}")
                }
            }
            checklist.add("  ☐ Claim investments — nominee can claim with death certificate + nominee proof")
        }

        // Priority 5: Subscriptions and recurring payments
        checklist.add("")
        checklist.add("📋 SUBSCRIPTIONS & RECURRING PAYMENTS:")
        checklist.add("  ☐ Cancel all non-essential subscriptions linked to bank accounts")
        checklist.add("  ☐ Stop/redirect any auto-pay mandates")
        checklist.add("  ☐ Inform utility providers (electricity, water, gas, internet)")

        // Priority 6: Documents
        if (data.propertyDocumentsLocation.isNotEmpty()) {
            checklist.add("")
            checklist.add("📋 IMPORTANT DOCUMENTS:")
            checklist.add("  ☐ Property documents location: ${data.propertyDocumentsLocation}")
        }

        if (data.safeDepositBoxDetails.isNotEmpty()) {
            checklist.add("  ☐ Safe deposit box: ${data.safeDepositBoxDetails}")
        }

        // Priority 7: Digital assets
        if (data.digitalAssets.isNotEmpty()) {
            checklist.add("")
            checklist.add("📋 DIGITAL ACCOUNTS & ASSETS:")
            data.digitalAssets.forEach { asset ->
                checklist.add("  ☐ $asset")
            }
        }

        // Priority 8: Professional contacts
        if (data.importantContacts.size > 1) {
            checklist.add("")
            checklist.add("📋 PROFESSIONAL CONTACTS TO REACH:")
            data.importantContacts.forEach { contact ->
                checklist.add("  ☐ ${contact.name} (${contact.role}) — ${contact.phone}")
                if (contact.notes.isNotEmpty()) {
                    checklist.add("    Note: ${contact.notes}")
                }
            }
        }

        // General reminders
        checklist.add("")
        checklist.add("📋 GENERAL REMINDERS:")
        checklist.add("  ☐ Obtain multiple copies of death certificate (needed for almost everything)")
        checklist.add("  ☐ Inform employer/pension provider")
        checklist.add("  ☐ Update voter ID, driving license records")
        checklist.add("  ☐ Check for any pending tax filings")
        checklist.add("  ☐ Claim provident fund / pension benefits")

        return checklist
    }

    /**
     * Returns a compassionate update reminder message if the vault needs updating.
     *
     * @return Reminder message, or null if no update needed
     */
    fun getUpdateReminderMessage(): String? {
        if (!needsUpdate()) return null

        val lastUpdated = getLastUpdatedDisplay()
        val monthsSince = ((System.currentTimeMillis() - getLastUpdatedTime()) /
                (30L * 24 * 60 * 60 * 1000)).toInt()

        return buildString {
            appendLine("🔔 Your ICE Vault was last updated $lastUpdated ($monthsSince months ago).")
            appendLine()
            appendLine("Life changes — accounts open and close, loans get paid off, ")
            appendLine("investments grow. A quick review ensures your loved ones have ")
            appendLine("accurate information when they need it most.")
            appendLine()
            appendLine("Take 10 minutes to review and update? 💙")
        }
    }

    /**
     * Gets the user's personal letter/instructions to loved ones.
     * This is a lightweight accessor that doesn't require decrypting the full vault.
     * (Actually, it does require the full vault for security — but provides a focused view.)
     *
     * @param password ICE password for decryption
     * @return The letter text, or null if not set or invalid password
     */
    fun getLetterToLovedOnes(password: String): String? {
        val data = getIceVaultData(password) ?: return null
        return data.letterToLovedOnes.ifEmpty { null }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Cryptographic Utilities
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Derives the verification hash from password and salt.
     * Uses PBKDF2 with extra iterations for the stored hash (compared to encryption key).
     */
    private fun deriveVerificationHash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS * 2, // Double iterations for verification hash
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Derives the AES-256 encryption key from password and salt.
     * Different derivation parameters than verification hash to ensure they're independent.
     */
    private fun deriveEncryptionKey(password: String, salt: ByteArray): SecretKeySpec {
        // Use a modified salt for encryption key derivation (XOR with constant)
        val encSalt = salt.clone()
        for (i in encSalt.indices) {
            encSalt[i] = (encSalt[i].toInt() xor 0x5A).toByte()
        }

        val spec = PBEKeySpec(
            password.toCharArray(),
            encSalt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext with AES-256-GCM.
     */
    private fun encrypt(plaintext: String, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        return cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    }

    /**
     * Decrypts ciphertext with AES-256-GCM.
     */
    private fun decrypt(ciphertext: ByteArray, key: SecretKeySpec, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Generates a random IV for AES-GCM.
     */
    private fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Vault Summary (non-sensitive)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns a non-sensitive summary of what's in the vault (for the owner's reference).
     *
     * Does NOT expose actual data — just counts and categories. Useful for the
     * owner to verify completeness without fully decrypting.
     *
     * @param password ICE password for access
     * @return Summary text, or null if access denied
     */
    fun getVaultSummary(password: String): String? {
        val data = getIceVaultData(password) ?: return null

        return buildString {
            appendLine("📦 ICE Vault Summary")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🏦 Bank Accounts: ${data.bankAccounts.size}")
            appendLine("📋 Insurance Policies: ${data.insurancePolicies.size}")
            appendLine("💳 Loans/Liabilities: ${data.loans.size}")
            appendLine("📈 Investments: ${data.investments.size}")
            appendLine("👥 Important Contacts: ${data.importantContacts.size}")
            appendLine("💻 Digital Assets: ${data.digitalAssets.size}")
            appendLine("📝 Personal Letter: ${if (data.letterToLovedOnes.isNotEmpty()) "Written ✓" else "Not written"}")
            appendLine("📍 Property Docs Location: ${if (data.propertyDocumentsLocation.isNotEmpty()) "Set ✓" else "Not set"}")
            appendLine("🔐 Safe Deposit Box: ${if (data.safeDepositBoxDetails.isNotEmpty()) "Noted ✓" else "Not set"}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Last Updated: ${getLastUpdatedDisplay()}")
            appendLine("Trusted Person: ${getTrustedPersonName().ifEmpty { "Not designated" }}")
        }
    }

    /**
     * Completely wipes the ICE vault — removes all encrypted data and configuration.
     *
     * ⚠️ This is irreversible. All ICE vault data will be permanently lost.
     *
     * @param password Current ICE password (required to confirm deletion)
     * @return true if wiped successfully, false if password is wrong
     */
    fun wipeVault(password: String): Boolean {
        if (!verifyIcePassword(password)) return false

        getPrefs().edit().clear().apply()
        return true
    }
}
