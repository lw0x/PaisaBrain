package com.paisabrain.app.emergency

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Step-by-step guide for disputing wrong charges, scams, and fraud.
 *
 * Provides structured guidance for financial disputes including immediate actions,
 * formal complaint procedures, escalation paths, consumer rights information,
 * and auto-generated complaint templates.
 *
 * ## Key Features
 * - Complete dispute workflows for 8 common scenarios
 * - Template complaint messages with fill-in-the-blank fields
 * - Dispute status tracking with deadline reminders
 * - Consumer rights information (regulatory guidelines)
 * - Escalation path guidance (regulator, consumer forum)
 *
 * ## Usage
 * ```kotlin
 * val helper = DisputeHelper(context)
 * val steps = helper.getStepsForDispute(DisputeType.UNAUTHORIZED_TRANSACTION)
 * val complaint = helper.generateComplaintText(myDispute)
 * ```
 *
 * @property context Application context for storage access
 */
class DisputeHelper(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "dispute_helper_prefs"
        private const val KEY_DISPUTES = "active_disputes"

        // Generic helpline numbers (not tied to any specific institution)
        const val BANKING_REGULATOR_HELPLINE = "14448"
        const val CONSUMER_HELPLINE = "1915"
        const val CYBER_CRIME_HELPLINE = "1930"
        const val CYBER_CRIME_PORTAL = "cybercrime.gov.in"
    }

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────────────────────
    // Enums & Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Types of financial disputes supported.
     */
    enum class DisputeType {
        /** Merchant charged a different amount than expected */
        WRONG_AMOUNT_CHARGED,
        /** Same transaction was charged twice */
        DOUBLE_CHARGE,
        /** Transaction you did not authorize (potential fraud) */
        UNAUTHORIZED_TRANSACTION,
        /** Subscription charged after you cancelled */
        SUBSCRIPTION_AFTER_CANCELLATION,
        /** ATM dispensed less cash than debited */
        ATM_LESS_CASH,
        /** UPI payment went to wrong recipient */
        UPI_WRONG_RECIPIENT,
        /** Paid online but product/service not delivered */
        ONLINE_PURCHASE_NOT_DELIVERED,
        /** Refund promised but not received */
        REFUND_NOT_RECEIVED
    }

    /**
     * Status progression of a dispute.
     */
    enum class DisputeStatus {
        /** Dispute drafted but not yet filed */
        DRAFT,
        /** Complaint filed with bank/provider */
        FILED,
        /** Bank/provider acknowledged receipt */
        ACKNOWLEDGED,
        /** Actively being investigated */
        UNDER_REVIEW,
        /** Successfully resolved (money returned, etc.) */
        RESOLVED,
        /** Escalated to regulator/ombudsman/consumer forum */
        ESCALATED,
        /** Dispute closed (either way) */
        CLOSED
    }

    /**
     * Complete dispute record.
     *
     * @property id Unique identifier for this dispute
     * @property type Category of dispute
     * @property amount Disputed amount in rupees
     * @property date Date of the original transaction
     * @property merchantName Name of merchant/payee involved
     * @property transactionId Reference number of the transaction
     * @property status Current status of the dispute
     * @property filedDate When the formal complaint was filed
     * @property resolvedDate When the dispute was resolved (null if ongoing)
     * @property notes User's additional notes
     * @property escalationLevel Current escalation level (0 = bank, 1 = regulator, 2 = consumer forum)
     */
    data class Dispute(
        val id: String = UUID.randomUUID().toString(),
        val type: DisputeType,
        val amount: Double,
        val date: String,
        val merchantName: String = "",
        val transactionId: String = "",
        val status: DisputeStatus = DisputeStatus.DRAFT,
        val filedDate: Long? = null,
        val resolvedDate: Long? = null,
        val notes: String = "",
        val escalationLevel: Int = 0
    )

    /**
     * A single actionable step in the dispute resolution process.
     *
     * @property stepNumber Order of this step
     * @property title Brief title of what to do
     * @property description Detailed instructions
     * @property timeframe When this should be done (e.g., "Within 24 hours")
     * @property isUrgent Whether this is time-sensitive
     * @property contactInfo Relevant phone/email to use for this step
     */
    data class DisputeStep(
        val stepNumber: Int,
        val title: String,
        val description: String,
        val timeframe: String,
        val isUrgent: Boolean = false,
        val contactInfo: String = ""
    )

    /**
     * An escalation option when initial dispute resolution fails.
     *
     * @property level Escalation level (1 = regulator, 2 = ombudsman, 3 = consumer forum)
     * @property title Name of the escalation authority
     * @property description How to file with this authority
     * @property whenToUse Conditions under which to escalate here
     * @property contactInfo How to reach this authority
     * @property timeline Expected resolution timeline
     */
    data class EscalationStep(
        val level: Int,
        val title: String,
        val description: String,
        val whenToUse: String,
        val contactInfo: String,
        val timeline: String
    )

    /**
     * Important information about dispute deadlines and status.
     *
     * @property dispute The tracked dispute
     * @property daysSinceFiled Days elapsed since filing
     * @property deadlineDate When bank must respond
     * @property isOverdue Whether the response deadline has passed
     * @property reminderMessage Human-readable status/reminder message
     */
    data class DisputeReminder(
        val dispute: Dispute,
        val daysSinceFiled: Int,
        val deadlineDate: String,
        val isOverdue: Boolean,
        val reminderMessage: String
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Storage
    // ─────────────────────────────────────────────────────────────────────────────

    private fun getPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Saves a dispute to the tracker.
     *
     * @param dispute The dispute to save or update
     */
    fun saveDispute(dispute: Dispute) {
        val disputes = getAllDisputes().toMutableList()
        val existingIndex = disputes.indexOfFirst { it.id == dispute.id }
        if (existingIndex >= 0) {
            disputes[existingIndex] = dispute
        } else {
            disputes.add(dispute)
        }
        val json = gson.toJson(disputes)
        getPrefs().edit().putString(KEY_DISPUTES, json).apply()
    }

    /**
     * Retrieves all tracked disputes.
     *
     * @return List of all disputes, sorted by filed date (most recent first)
     */
    fun getAllDisputes(): List<Dispute> {
        val json = getPrefs().getString(KEY_DISPUTES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Dispute>>() {}.type
            val disputes: List<Dispute> = gson.fromJson(json, type)
            disputes.sortedByDescending { it.filedDate ?: 0L }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets only active (unresolved) disputes.
     *
     * @return List of disputes that are not yet resolved or closed
     */
    fun getActiveDisputes(): List<Dispute> {
        return getAllDisputes().filter {
            it.status != DisputeStatus.RESOLVED && it.status != DisputeStatus.CLOSED
        }
    }

    /**
     * Updates the status of an existing dispute.
     *
     * @param disputeId The dispute's unique ID
     * @param newStatus The new status to set
     */
    fun updateDisputeStatus(disputeId: String, newStatus: DisputeStatus) {
        val dispute = getAllDisputes().find { it.id == disputeId } ?: return
        val updated = dispute.copy(
            status = newStatus,
            resolvedDate = if (newStatus == DisputeStatus.RESOLVED) System.currentTimeMillis() else dispute.resolvedDate
        )
        saveDispute(updated)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Dispute Steps & Guidance
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns step-by-step instructions for resolving a specific dispute type.
     *
     * Steps are ordered chronologically — immediate actions first, then formal
     * complaints, then escalation triggers.
     *
     * @param type The type of dispute
     * @return Ordered list of [DisputeStep] items
     */
    fun getStepsForDispute(type: DisputeType): List<DisputeStep> {
        return when (type) {
            DisputeType.UNAUTHORIZED_TRANSACTION -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Block your card immediately",
                    description = "Call your bank's 24/7 helpline and request an immediate card block. Most banks also allow blocking through their mobile app. Do this FIRST before anything else.",
                    timeframe = "RIGHT NOW",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Report to bank as unauthorized",
                    description = "Call the bank and explicitly state: 'I want to report an unauthorized transaction.' Ask for a complaint/reference number. Note the name of the person you spoke with.",
                    timeframe = "Within 24 hours",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "File cyber crime report",
                    description = "File a complaint on the national cyber crime portal or call the cyber crime helpline. This creates an official police record which strengthens your bank claim.",
                    timeframe = "Within 24 hours",
                    contactInfo = "Helpline: $CYBER_CRIME_HELPLINE | Portal: $CYBER_CRIME_PORTAL"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "Send written complaint to bank",
                    description = "Email or write to your bank's grievance cell. Include: date, amount, transaction ID, that you did not authorize it, and your complaint reference number from the phone call. Keep a copy.",
                    timeframe = "Within 3 days"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Change passwords and PINs",
                    description = "Change your net banking password, UPI PIN, and debit/credit card PIN. Enable 2-factor authentication if not already active. Check for other suspicious transactions.",
                    timeframe = "Within 24 hours"
                ),
                DisputeStep(
                    stepNumber = 6,
                    title = "Follow up after 10 days",
                    description = "Per regulations, your bank must credit the disputed amount back within 10 working days of reporting (for transactions where customer is not at fault). If not done, call and reference the regulation.",
                    timeframe = "Day 10-15"
                ),
                DisputeStep(
                    stepNumber = 7,
                    title = "Escalate if no response in 30 days",
                    description = "If the bank has not resolved your complaint within 30 days, escalate to the banking regulator's ombudsman scheme. File online at the regulator's complaint portal.",
                    timeframe = "Day 30+",
                    contactInfo = "Banking regulator helpline: $BANKING_REGULATOR_HELPLINE"
                )
            )

            DisputeType.WRONG_AMOUNT_CHARGED -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Gather evidence",
                    description = "Collect the receipt, order confirmation, or price display that shows the correct amount. Take screenshots of the transaction in your bank/payment app showing the wrong amount charged.",
                    timeframe = "Immediately",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Contact the merchant first",
                    description = "Call or visit the merchant. Show them the price discrepancy. Many merchants will reverse the overcharge immediately. Get it in writing if they agree to refund.",
                    timeframe = "Within 24 hours"
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Raise chargeback with bank",
                    description = "If the merchant doesn't cooperate, call your bank and request a 'chargeback' for the excess amount. Provide the evidence of correct price. The bank will initiate the dispute with the merchant's bank.",
                    timeframe = "Within 7 days"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "File written complaint",
                    description = "Send a formal email/letter to your bank's grievance cell with all evidence attached. State the correct amount and excess charged clearly.",
                    timeframe = "Within 7 days"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Wait for resolution",
                    description = "Bank must acknowledge within 48 hours and resolve within 30 days. The chargeback process typically takes 15-45 days. Keep checking your statement.",
                    timeframe = "15-45 days"
                ),
                DisputeStep(
                    stepNumber = 6,
                    title = "Escalate if needed",
                    description = "If unresolved in 30 days, file complaint with banking regulator ombudsman and/or consumer helpline.",
                    timeframe = "Day 30+",
                    contactInfo = "Consumer helpline: $CONSUMER_HELPLINE"
                )
            )

            DisputeType.DOUBLE_CHARGE -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Verify it's actually a double charge",
                    description = "Check your bank statement carefully. Sometimes authorization holds look like charges but drop off in 2-3 days. Wait 48 hours if the second charge just appeared. If it's still there after 48 hours, proceed.",
                    timeframe = "Wait 48 hours first"
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Screenshot both transactions",
                    description = "Take clear screenshots showing both charges — same amount, same merchant, same/similar date. Note both transaction reference numbers.",
                    timeframe = "Immediately"
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Contact merchant",
                    description = "Reach out to the merchant with screenshots of both charges. If it was an online purchase, use their customer support chat (creates a written record). Ask them to reverse the duplicate.",
                    timeframe = "Within 3 days"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "Dispute with bank if merchant unhelpful",
                    description = "Call your bank and report a duplicate charge. They can see both transactions on their end too. Request reversal of one. Get a reference number.",
                    timeframe = "Within 7 days"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Follow up",
                    description = "Double charges are usually resolved within 7-14 days. Check your statement. If not reversed, call bank again with your reference number.",
                    timeframe = "Day 7-14"
                )
            )

            DisputeType.SUBSCRIPTION_AFTER_CANCELLATION -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Find cancellation proof",
                    description = "Look for the cancellation confirmation email, screenshot of 'subscription cancelled' page, or any record showing you cancelled before this charge date. Check your email inbox and spam folder.",
                    timeframe = "Immediately",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Contact the service provider",
                    description = "Email/call the subscription service. Share your cancellation proof. Explicitly request: 1) Immediate refund of wrongful charge, 2) Confirmation that subscription is cancelled, 3) Written assurance no future charges will occur.",
                    timeframe = "Within 24 hours"
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Block future charges",
                    description = "If the service refuses to cooperate, call your bank and request blocking all future charges from this merchant (merchant block). This prevents recurring charges regardless of what the service does.",
                    timeframe = "Within 3 days"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "File chargeback with bank",
                    description = "If refund not processed within 5 days, file a formal chargeback with your bank. Provide your cancellation proof as evidence. The bank will reverse the charge.",
                    timeframe = "Day 5-7"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Consumer complaint if needed",
                    description = "If both merchant and bank are unhelpful, file a consumer complaint. Subscription charging after cancellation is a clear violation of consumer rights.",
                    timeframe = "Day 15+",
                    contactInfo = "Consumer helpline: $CONSUMER_HELPLINE"
                )
            )

            DisputeType.ATM_LESS_CASH -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Do NOT leave the ATM area",
                    description = "Stay near the ATM. Check if the machine is showing any error. If cash is partially stuck in the dispenser, DO NOT pull it — it might tear. Take a photo/video of the ATM screen showing any error message.",
                    timeframe = "RIGHT NOW",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Note ATM details",
                    description = "Write down: ATM ID (displayed on/near the machine), ATM location, exact time of transaction, amount you tried to withdraw, amount actually received (or zero if nothing came out).",
                    timeframe = "RIGHT NOW",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Call your bank immediately",
                    description = "Call your bank's helpline and report 'ATM cash not dispensed' or 'ATM short dispensed'. They will raise a dispute. Get a reference number. The call timestamp itself serves as evidence.",
                    timeframe = "Within 30 minutes",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "SMS/email will confirm debit",
                    description = "You should have received an SMS/email showing the debit. Keep this as evidence. If the ATM did not dispense cash but your account was debited, this is a clear case for automatic reversal.",
                    timeframe = "Check immediately"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Wait for auto-reversal (5 days)",
                    description = "Per regulations, if an ATM transaction fails (cash not dispensed), the bank must reverse the amount within 5 working days. No complaint needed for auto-reversal, but filing one speeds things up.",
                    timeframe = "5 working days"
                ),
                DisputeStep(
                    stepNumber = 6,
                    title = "Escalate if not reversed in 5 days",
                    description = "If amount not reversed in 5 working days, you are entitled to ₹100/day compensation for the delay. Call your bank and reference this regulation. File formal written complaint.",
                    timeframe = "Day 6+",
                    contactInfo = "Banking regulator helpline: $BANKING_REGULATOR_HELPLINE"
                )
            )

            DisputeType.UPI_WRONG_RECIPIENT -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Take screenshots immediately",
                    description = "Screenshot the transaction details showing the wrong UPI ID/number, amount, date, time, and transaction reference number (UTR number). This is your primary evidence.",
                    timeframe = "RIGHT NOW",
                    isUrgent = true
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Request the recipient to return",
                    description = "If you know the person (sent to wrong contact), call them and politely request a return. If sent to unknown UPI ID, skip to next step.",
                    timeframe = "Within 1 hour"
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Raise complaint in payment app",
                    description = "Open the payment app you used → find the transaction → tap 'Raise dispute' or 'Report issue'. Select 'Sent to wrong person/UPI ID'. Provide the UTR number.",
                    timeframe = "Within 24 hours"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "Contact your bank",
                    description = "Call your bank and report 'wrong UPI transfer'. They may be able to contact the beneficiary's bank and request a reversal. However, UPI payments to the correct (but unintended) recipient are hard to reverse without cooperation.",
                    timeframe = "Within 24 hours"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Important: Know your rights",
                    description = "If YOU entered the wrong UPI ID, the bank is not liable — it's not a system error. However, they can try to facilitate return. If the system sent to a different ID than you entered (system error), the bank MUST reverse it.",
                    timeframe = "Understanding"
                ),
                DisputeStep(
                    stepNumber = 6,
                    title = "File police complaint for large amounts",
                    description = "If the amount is significant and the recipient is refusing to return, file a police complaint for 'unjust enrichment'. This is a legal tool to recover money someone received by mistake and refuses to return.",
                    timeframe = "If unresolved in 7 days"
                )
            )

            DisputeType.ONLINE_PURCHASE_NOT_DELIVERED -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Check delivery status thoroughly",
                    description = "Check the order tracking page. Sometimes packages are marked 'delivered' but left with a neighbor/security guard. Check with them first. Also check if it was returned to sender.",
                    timeframe = "Same day"
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Contact the seller/platform",
                    description = "Raise a 'not delivered' complaint through the platform's help section. Most platforms have a window (usually 7-15 days after expected delivery) where you can claim non-delivery and get an automatic refund.",
                    timeframe = "Within the platform's claim window"
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Document everything",
                    description = "Screenshot: order confirmation, tracking page showing delivery/non-delivery, chat with seller, expected delivery date. If platform shows 'delivered' but you didn't receive, say so clearly.",
                    timeframe = "Immediately"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "Raise payment dispute if seller unresponsive",
                    description = "If the platform/seller doesn't respond within 7 days or denies your claim, contact your bank for a chargeback. Provide order details and evidence that item was not received.",
                    timeframe = "Day 7-10"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "Consumer complaint",
                    description = "File complaint on the consumer grievance portal. Include order number, payment proof, and evidence of non-delivery. Sellers are legally required to deliver or refund.",
                    timeframe = "Day 15+",
                    contactInfo = "Consumer helpline: $CONSUMER_HELPLINE"
                )
            )

            DisputeType.REFUND_NOT_RECEIVED -> listOf(
                DisputeStep(
                    stepNumber = 1,
                    title = "Verify refund timeline",
                    description = "Most refunds take 5-10 working days. Check the refund confirmation email for estimated date. Credit card refunds can take up to 2 billing cycles. Don't panic before the stated timeline.",
                    timeframe = "Wait stated period"
                ),
                DisputeStep(
                    stepNumber = 2,
                    title = "Check correct account",
                    description = "Verify the refund is expected in the right account/card. Sometimes refunds go to a different payment method than you expect (e.g., store credit instead of card refund).",
                    timeframe = "After stated period"
                ),
                DisputeStep(
                    stepNumber = 3,
                    title = "Contact merchant with proof",
                    description = "Email/call the merchant with: 1) Order number, 2) Refund confirmation/reference number they gave you, 3) Statement showing no credit received. Ask for the ARN (Acquirer Reference Number) for card refunds.",
                    timeframe = "After stated period + 3 days"
                ),
                DisputeStep(
                    stepNumber = 4,
                    title = "Trace with bank using ARN",
                    description = "If merchant provides the ARN, give it to your bank and ask them to trace the refund. Sometimes refunds are processed by merchant's bank but stuck at your bank's end. ARN helps track it.",
                    timeframe = "Day 10-15"
                ),
                DisputeStep(
                    stepNumber = 5,
                    title = "File chargeback if refund lost",
                    description = "If merchant confirms refund was processed but your bank can't find it (or merchant has gone silent), file a formal chargeback with your bank. This forces a reversal.",
                    timeframe = "Day 15-20"
                ),
                DisputeStep(
                    stepNumber = 6,
                    title = "Regulatory complaint",
                    description = "For persistent non-refund cases, file with both banking regulator (against bank) and consumer forum (against merchant). Entitled to compensation for delayed refunds.",
                    timeframe = "Day 30+",
                    contactInfo = "Consumer helpline: $CONSUMER_HELPLINE | Banking: $BANKING_REGULATOR_HELPLINE"
                )
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Complaint Text Generation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates a formal complaint text that can be sent to the bank/payment provider.
     *
     * The generated text follows standard complaint format with all relevant details
     * filled in from the dispute data. User can copy and send via email/letter.
     *
     * @param dispute The dispute for which to generate complaint text
     * @return Ready-to-send complaint text with all details filled in
     */
    fun generateComplaintText(dispute: Dispute): String {
        val today = dateFormat.format(Date())
        val typeDescription = getDisputeTypeDescription(dispute.type)

        return buildString {
            appendLine("Date: $today")
            appendLine()
            appendLine("To,")
            appendLine("The Grievance Redressal Officer,")
            appendLine("[Your Bank/Payment Provider Name]")
            appendLine()
            appendLine("Subject: Complaint regarding $typeDescription")
            appendLine()
            appendLine("Respected Sir/Madam,")
            appendLine()
            appendLine("I am writing to formally complain about a transaction issue on my account.")
            appendLine()
            appendLine("Transaction Details:")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("• Date of Transaction: ${dispute.date}")
            appendLine("• Amount: ₹${String.format("%,.2f", dispute.amount)}")
            if (dispute.merchantName.isNotEmpty()) {
                appendLine("• Merchant/Payee: ${dispute.merchantName}")
            }
            if (dispute.transactionId.isNotEmpty()) {
                appendLine("• Transaction/Reference ID: ${dispute.transactionId}")
            }
            appendLine("• Nature of Dispute: $typeDescription")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            // Type-specific complaint body
            append(getComplaintBodyForType(dispute.type, dispute))

            appendLine()
            appendLine("I request you to:")
            appendLine("1. Investigate this matter immediately")
            appendLine("2. Reverse/refund the disputed amount of ₹${String.format("%,.2f", dispute.amount)}")
            appendLine("3. Provide me a written response within the stipulated timeframe")
            appendLine()
            appendLine("As per regulatory guidelines, I understand that the bank must resolve this complaint within 30 days. If I do not receive a satisfactory response, I reserve the right to escalate to the Banking Ombudsman.")
            appendLine()
            appendLine("Please acknowledge receipt of this complaint and provide a reference number.")
            appendLine()
            appendLine("Thanking you,")
            appendLine("[Your Name]")
            appendLine("[Your Account Number/Customer ID]")
            appendLine("[Your Registered Mobile Number]")
            appendLine("[Your Email Address]")
        }
    }

    /**
     * Gets a human-readable description of a dispute type.
     */
    private fun getDisputeTypeDescription(type: DisputeType): String {
        return when (type) {
            DisputeType.WRONG_AMOUNT_CHARGED -> "Wrong amount charged (overcharge)"
            DisputeType.DOUBLE_CHARGE -> "Duplicate/double charge"
            DisputeType.UNAUTHORIZED_TRANSACTION -> "Unauthorized transaction (fraud)"
            DisputeType.SUBSCRIPTION_AFTER_CANCELLATION -> "Subscription charged after cancellation"
            DisputeType.ATM_LESS_CASH -> "ATM cash not dispensed / short dispensed"
            DisputeType.UPI_WRONG_RECIPIENT -> "UPI payment sent to wrong recipient"
            DisputeType.ONLINE_PURCHASE_NOT_DELIVERED -> "Online purchase not delivered"
            DisputeType.REFUND_NOT_RECEIVED -> "Refund not received"
        }
    }

    /**
     * Generates type-specific complaint body paragraphs.
     */
    private fun getComplaintBodyForType(type: DisputeType, dispute: Dispute): String {
        return when (type) {
            DisputeType.UNAUTHORIZED_TRANSACTION -> buildString {
                appendLine("I wish to inform you that the above transaction was NOT authorized by me. I did not make this transaction, nor did I share my card details, OTP, PIN, or password with anyone.")
                appendLine()
                appendLine("I have already reported this to the cyber crime helpline (${CYBER_CRIME_HELPLINE}) and request that my card be blocked immediately if not already done.")
                appendLine()
                appendLine("As per regulatory circular on 'Limiting Liability of Customers in Unauthorized Electronic Banking Transactions', since I have reported this within 3 working days, my liability should be limited to zero (for third-party breach) or a maximum of ₹10,000.")
            }

            DisputeType.ATM_LESS_CASH -> buildString {
                appendLine("I attempted to withdraw cash from the ATM on ${dispute.date}. My account was debited ₹${String.format("%,.2f", dispute.amount)} but the ATM either dispensed less cash or did not dispense any cash.")
                appendLine()
                appendLine("As per regulatory guidelines, the bank must auto-reverse failed ATM transactions within 5 working days (T+5). If the reversal is delayed beyond this, I am entitled to compensation of ₹100 per day of delay.")
            }

            DisputeType.DOUBLE_CHARGE -> buildString {
                appendLine("I was charged twice for the same transaction. The duplicate charge of ₹${String.format("%,.2f", dispute.amount)} appeared on my statement for the same merchant (${dispute.merchantName}) on the same date.")
                appendLine()
                appendLine("I made only ONE purchase/transaction. The second charge is erroneous and must be reversed immediately.")
            }

            DisputeType.SUBSCRIPTION_AFTER_CANCELLATION -> buildString {
                appendLine("I cancelled my subscription with ${dispute.merchantName.ifEmpty { "[Service Provider]" }} prior to the charge date. Despite cancellation, I was charged ₹${String.format("%,.2f", dispute.amount)}.")
                appendLine()
                appendLine("I have proof of cancellation (confirmation email/screenshot) and request immediate reversal of this wrongful charge. I also request that all future recurring charges from this merchant be blocked on my account.")
            }

            else -> buildString {
                appendLine("The above transaction of ₹${String.format("%,.2f", dispute.amount)} is disputed for the reason mentioned above.")
                if (dispute.notes.isNotEmpty()) {
                    appendLine()
                    appendLine("Additional details: ${dispute.notes}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Escalation Path
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns the full escalation path for a dispute.
     *
     * Escalation levels:
     * 1. Bank's internal grievance cell
     * 2. Banking Regulator Ombudsman
     * 3. Consumer Disputes Redressal Forum
     *
     * @param dispute The dispute to get escalation options for
     * @return Ordered list of [EscalationStep] from lowest to highest authority
     */
    fun getEscalationPath(dispute: Dispute): List<EscalationStep> {
        return listOf(
            EscalationStep(
                level = 1,
                title = "Bank's Internal Grievance Redressal",
                description = "First, exhaust the bank's internal complaint mechanism. File with their Principal Nodal Officer if first-level support hasn't helped.",
                whenToUse = "Bank frontline staff hasn't resolved in 7-10 days",
                contactInfo = "Check bank's website for Grievance Redressal Officer details",
                timeline = "Must respond within 30 days"
            ),
            EscalationStep(
                level = 2,
                title = "Banking Regulator Ombudsman",
                description = "If bank doesn't respond in 30 days OR response is unsatisfactory, file with the banking regulator's ombudsman scheme. This is free and can be done online. The ombudsman's decision is binding on the bank.",
                whenToUse = "Bank hasn't resolved complaint within 30 days, OR rejected your complaint without valid reason",
                contactInfo = "Helpline: $BANKING_REGULATOR_HELPLINE | File online at regulator's CMS portal",
                timeline = "Typically resolved within 30-45 days of filing"
            ),
            EscalationStep(
                level = 3,
                title = "Consumer Disputes Redressal Forum",
                description = "For amounts up to ₹50 lakhs, file in District Consumer Forum. For ₹50 lakhs to ₹2 crores, State Commission. Can claim compensation for mental agony, loss of interest, etc. in addition to the disputed amount.",
                whenToUse = "Ombudsman decision not satisfactory, or for claiming additional compensation",
                contactInfo = "Consumer helpline: $CONSUMER_HELPLINE | File on e-daakhil portal",
                timeline = "3-12 months (varies by case complexity)"
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Consumer Rights
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns relevant consumer rights and regulatory protections for a dispute type.
     *
     * These are based on general banking regulations and consumer protection laws.
     * Users should verify current applicability with official sources.
     *
     * @param type The dispute type
     * @return List of rights/protections applicable to this situation
     */
    fun getConsumerRights(type: DisputeType): List<String> {
        val commonRights = listOf(
            "You have the right to file a complaint and receive a reference number.",
            "Bank must acknowledge your complaint within 48 hours.",
            "Bank must resolve or respond within 30 days of complaint.",
            "If bank fails to resolve in 30 days, you can escalate to the Banking Ombudsman at no cost.",
            "You are entitled to compensation for delays beyond stipulated timelines.",
            "You can file in Consumer Forum for additional compensation (mental agony, interest loss, etc.)."
        )

        val specificRights = when (type) {
            DisputeType.UNAUTHORIZED_TRANSACTION -> listOf(
                "Zero liability: If fraud is due to bank's system/third-party breach and you report within 3 days, your liability is ZERO.",
                "Limited liability (₹10,000 max): If reported within 4-7 days of receiving transaction alert.",
                "Bank must credit the disputed amount within 10 working days of receiving your complaint (provisional credit).",
                "The burden of proof is on the bank to show you authorized the transaction, not on you to prove you didn't.",
                "Bank cannot deny your claim solely because OTP was used — SIM swap fraud and phishing are recognized attack vectors."
            )

            DisputeType.ATM_LESS_CASH -> listOf(
                "Auto-reversal mandatory: Bank must reverse failed ATM transactions within 5 working days (T+5).",
                "Compensation: ₹100/day for each day of delay beyond 5 working days.",
                "This applies even without filing a complaint — it should be automatic.",
                "If ATM belongs to different bank, the bank reconciliation should still complete within 5 days.",
                "You do NOT need to visit the branch — phone complaint is sufficient."
            )

            DisputeType.SUBSCRIPTION_AFTER_CANCELLATION -> listOf(
                "Charging after cancellation is an unauthorized transaction — same protections apply.",
                "You can request your bank to block all future charges from a specific merchant.",
                "Under consumer protection law, this may constitute 'unfair trade practice'.",
                "You can claim refund plus compensation for harassment."
            )

            DisputeType.DOUBLE_CHARGE -> listOf(
                "Duplicate charges must be reversed once reported — there is no ambiguity in such cases.",
                "Bank should be able to verify the duplicate from their transaction logs.",
                "If both charges are legitimate but one should be reversed, merchant's bank will handle it via chargeback.",
                "You are entitled to interest on the wrongly held amount."
            )

            DisputeType.UPI_WRONG_RECIPIENT -> listOf(
                "If error is on the system side (you entered correct ID but money went elsewhere), bank MUST reverse.",
                "If you entered wrong ID yourself, bank is not liable but can facilitate recovery.",
                "Recipient holding money received by mistake is 'unjust enrichment' under law — you can file police complaint.",
                "Payment service provider must have a dispute resolution mechanism."
            )

            DisputeType.ONLINE_PURCHASE_NOT_DELIVERED -> listOf(
                "Non-delivery of paid goods is a 'deficiency in service' under consumer protection law.",
                "You can claim: full refund + compensation for inconvenience + cost of filing complaint.",
                "E-commerce platforms are jointly liable with sellers for non-delivery.",
                "Complaint must be filed within 2 years of the cause of action."
            )

            DisputeType.REFUND_NOT_RECEIVED -> listOf(
                "Merchant is obligated to process refund within the timeline they communicated.",
                "For card transactions, refund should reflect within 5-7 working days of merchant processing it.",
                "If merchant processed but bank hasn't credited, bank is liable for the delay.",
                "You can ask merchant for ARN (Acquirer Reference Number) to trace the refund with your bank."
            )

            else -> emptyList()
        }

        return specificRights + commonRights
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Deadline Reminders
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Checks all active disputes for deadline-related reminders.
     *
     * Returns reminders for disputes that are:
     * - Approaching the 30-day response deadline
     * - Past the 30-day deadline (should escalate)
     * - Near other important milestones
     *
     * @return List of [DisputeReminder] for disputes needing attention
     */
    fun getDeadlineReminders(): List<DisputeReminder> {
        val reminders = mutableListOf<DisputeReminder>()
        val now = System.currentTimeMillis()

        for (dispute in getActiveDisputes()) {
            val filedDate = dispute.filedDate ?: continue
            val daysSinceFiled = TimeUnit.MILLISECONDS.toDays(now - filedDate).toInt()

            val deadlineMs = filedDate + TimeUnit.DAYS.toMillis(30)
            val deadlineDateStr = dateFormat.format(Date(deadlineMs))
            val isOverdue = now > deadlineMs

            val message = when {
                isOverdue -> {
                    val overdueDays = daysSinceFiled - 30
                    "⚠️ This dispute is $overdueDays days past the 30-day response deadline! " +
                            "Time to escalate to the Banking Ombudsman."
                }
                daysSinceFiled >= 25 -> {
                    val daysLeft = 30 - daysSinceFiled
                    "⏰ You filed this dispute $daysSinceFiled days ago. " +
                            "If no response by day 30 ($daysLeft days from now), you can escalate."
                }
                daysSinceFiled >= 15 && dispute.status == DisputeStatus.FILED -> {
                    "📋 It's been $daysSinceFiled days since filing. " +
                            "Consider following up with the bank for a status update."
                }
                daysSinceFiled >= 5 && dispute.type == DisputeType.ATM_LESS_CASH -> {
                    "🏧 ATM disputes should auto-reverse within 5 working days. " +
                            "It's been $daysSinceFiled days. If not reversed, you're owed ₹100/day compensation."
                }
                else -> continue
            }

            reminders.add(
                DisputeReminder(
                    dispute = dispute,
                    daysSinceFiled = daysSinceFiled,
                    deadlineDate = deadlineDateStr,
                    isOverdue = isOverdue,
                    reminderMessage = message
                )
            )
        }

        return reminders
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Important Contacts
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns a list of important helpline numbers for financial disputes.
     *
     * @return Map of description to phone number
     */
    fun getImportantHelplines(): Map<String, String> {
        return mapOf(
            "Banking Regulator Helpline" to BANKING_REGULATOR_HELPLINE,
            "Consumer Helpline" to CONSUMER_HELPLINE,
            "Cyber Crime Helpline" to CYBER_CRIME_HELPLINE,
            "Cyber Crime Online Portal" to CYBER_CRIME_PORTAL
        )
    }
}
