package com.paisabrain.app.tools

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Product warranty management system.
 *
 * Tracks product warranties, provides expiry reminders, service suggestions,
 * claim guidance, and cost analytics for products under/out of warranty.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Product categories for warranty organization.
 */
enum class ProductCategory(val displayName: String, val icon: String) {
    ELECTRONICS("Electronics", "📱"),
    APPLIANCES("Appliances", "🏠"),
    FURNITURE("Furniture", "🪑"),
    VEHICLE("Vehicle", "🚗"),
    ACCESSORIES("Accessories", "⌚"),
    OTHER("Other", "📦")
}

/**
 * Warranty status based on current date relative to expiry.
 */
enum class WarrantyStatus(val displayName: String, val icon: String) {
    /** Warranty is valid with more than 30 days remaining */
    ACTIVE("Active", "✅"),
    /** Warranty expires within 30 days */
    EXPIRING_SOON("Expiring Soon", "⚠️"),
    /** Warranty has expired */
    EXPIRED("Expired", "❌")
}

/**
 * Represents a product warranty entry.
 *
 * @property id Unique identifier
 * @property productName Name of the product
 * @property category Product category
 * @property purchaseDate Date of purchase
 * @property warrantyMonths Warranty duration in months
 * @property expiryDate Auto-calculated expiry date
 * @property receiptPhotoPath File path to receipt photo
 * @property store Store or seller name
 * @property notes Additional notes (serial number, model, etc.)
 * @property status Current warranty status
 * @property purchaseAmount Amount paid for the product
 * @property brand Product brand/manufacturer
 * @property serialNumber Product serial number (optional)
 */
data class Warranty(
    val id: String = UUID.randomUUID().toString(),
    val productName: String,
    val category: ProductCategory,
    val purchaseDate: LocalDate,
    val warrantyMonths: Int,
    val expiryDate: LocalDate = purchaseDate.plusMonths(warrantyMonths.toLong()),
    val receiptPhotoPath: String = "",
    val store: String = "",
    val notes: String = "",
    val status: WarrantyStatus = WarrantyStatus.ACTIVE,
    val purchaseAmount: Double = 0.0,
    val brand: String = "",
    val serialNumber: String = ""
)

/**
 * Warranty reminder for upcoming expirations.
 */
data class WarrantyReminder(
    val warrantyId: String,
    val productName: String,
    val category: ProductCategory,
    val expiryDate: LocalDate,
    val daysRemaining: Long,
    val message: String,
    val actionSuggestion: String
)

/**
 * Warranty claim guidance steps.
 */
data class ClaimGuide(
    val productName: String,
    val category: ProductCategory,
    val steps: List<String>,
    val documentsNeeded: List<String>,
    val tips: List<String>
)

/**
 * Cost analytics for warranty portfolio.
 */
data class WarrantyCostSummary(
    val totalProductsCost: Double,
    val underWarrantyCost: Double,
    val expiredWarrantyCost: Double,
    val expiringSoonCost: Double,
    val potentialSavings: Double,
    val categoryBreakdown: Map<ProductCategory, CategoryCostInfo>
)

/**
 * Per-category cost information.
 */
data class CategoryCostInfo(
    val totalProducts: Int,
    val totalSpent: Double,
    val activeCount: Int,
    val expiredCount: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// Warranty Tracker Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core warranty tracking and management system.
 *
 * Features:
 * - Add/manage product warranties with receipt photos
 * - Auto-calculate expiry dates and status
 * - Multi-level reminders (30 days, 7 days before expiry)
 * - Service suggestions for electronics/appliances
 * - Generic warranty claim guidance
 * - Cost tracking and analytics
 */
class WarrantyTracker {

    private val warranties = mutableListOf<Warranty>()

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds a new warranty to the tracker.
     *
     * Automatically calculates expiry date and initial status.
     *
     * @param productName Name of the product
     * @param category Product category
     * @param purchaseDate Date when product was purchased
     * @param warrantyMonths Warranty period in months
     * @param receiptPhotoPath Path to receipt photo (optional)
     * @param store Store or seller name (optional)
     * @param notes Additional notes (optional)
     * @param purchaseAmount Purchase price (optional)
     * @param brand Product brand (optional)
     * @param serialNumber Serial number (optional)
     * @return Created Warranty with calculated fields
     */
    fun addWarranty(
        productName: String,
        category: ProductCategory,
        purchaseDate: LocalDate,
        warrantyMonths: Int,
        receiptPhotoPath: String = "",
        store: String = "",
        notes: String = "",
        purchaseAmount: Double = 0.0,
        brand: String = "",
        serialNumber: String = ""
    ): Warranty {
        require(productName.isNotBlank()) { "Product name cannot be empty" }
        require(warrantyMonths > 0) { "Warranty period must be positive" }

        val expiryDate = purchaseDate.plusMonths(warrantyMonths.toLong())
        val status = calculateStatus(expiryDate)

        val warranty = Warranty(
            productName = productName,
            category = category,
            purchaseDate = purchaseDate,
            warrantyMonths = warrantyMonths,
            expiryDate = expiryDate,
            receiptPhotoPath = receiptPhotoPath,
            store = store,
            notes = notes,
            status = status,
            purchaseAmount = purchaseAmount,
            brand = brand,
            serialNumber = serialNumber
        )

        warranties.add(warranty)
        return warranty
    }

    /**
     * Updates an existing warranty.
     *
     * @param id Warranty ID to update
     * @param update Lambda to modify the warranty
     * @return Updated warranty or null if not found
     */
    fun updateWarranty(id: String, update: (Warranty) -> Warranty): Warranty? {
        val index = warranties.indexOfFirst { it.id == id }
        if (index == -1) return null

        val updated = update(warranties[index])
        warranties[index] = updated.copy(status = calculateStatus(updated.expiryDate))
        return warranties[index]
    }

    /**
     * Removes a warranty entry.
     */
    fun deleteWarranty(id: String): Boolean {
        return warranties.removeAll { it.id == id }
    }

    /**
     * Gets a warranty by ID.
     */
    fun getWarranty(id: String): Warranty? {
        return warranties.find { it.id == id }
    }

    /**
     * Gets all warranties with refreshed status.
     */
    fun getAllWarranties(today: LocalDate = LocalDate.now()): List<Warranty> {
        return warranties.map { it.copy(status = calculateStatus(it.expiryDate, today)) }
            .sortedBy { it.expiryDate }
    }

    /**
     * Gets warranties by status.
     */
    fun getByStatus(status: WarrantyStatus, today: LocalDate = LocalDate.now()): List<Warranty> {
        return getAllWarranties(today).filter { it.status == status }
    }

    /**
     * Gets warranties by category.
     */
    fun getByCategory(category: ProductCategory, today: LocalDate = LocalDate.now()): List<Warranty> {
        return getAllWarranties(today).filter { it.category == category }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status & Reminders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates warranty status based on expiry date.
     */
    private fun calculateStatus(expiryDate: LocalDate, today: LocalDate = LocalDate.now()): WarrantyStatus {
        val daysRemaining = ChronoUnit.DAYS.between(today, expiryDate)
        return when {
            daysRemaining < 0 -> WarrantyStatus.EXPIRED
            daysRemaining <= 30 -> WarrantyStatus.EXPIRING_SOON
            else -> WarrantyStatus.ACTIVE
        }
    }

    /**
     * Gets warranty reminders for products expiring soon.
     *
     * Generates reminders at:
     * - 30 days before expiry
     * - 7 days before expiry
     * - On expiry day
     *
     * @param today Reference date
     * @return List of actionable reminders
     */
    fun getReminders(today: LocalDate = LocalDate.now()): List<WarrantyReminder> {
        return warranties
            .mapNotNull { warranty ->
                val daysRemaining = ChronoUnit.DAYS.between(today, warranty.expiryDate)

                // Only remind for warranties expiring within 30 days (and not long expired)
                if (daysRemaining > 30 || daysRemaining < -7) return@mapNotNull null

                val message = when {
                    daysRemaining < 0 ->
                        "⚠️ Warranty for ${warranty.productName} expired ${-daysRemaining} days ago!"
                    daysRemaining == 0L ->
                        "🚨 Warranty for ${warranty.productName} expires TODAY!"
                    daysRemaining <= 7 ->
                        "🔔 Only $daysRemaining days left on ${warranty.productName} warranty!"
                    else ->
                        "📅 ${warranty.productName} warranty expires in $daysRemaining days."
                }

                val actionSuggestion = generateActionSuggestion(warranty, daysRemaining)

                WarrantyReminder(
                    warrantyId = warranty.id,
                    productName = warranty.productName,
                    category = warranty.category,
                    expiryDate = warranty.expiryDate,
                    daysRemaining = daysRemaining,
                    message = message,
                    actionSuggestion = actionSuggestion
                )
            }
            .sortedBy { it.daysRemaining }
    }

    /**
     * Generates context-specific action suggestions.
     */
    private fun generateActionSuggestion(warranty: Warranty, daysRemaining: Long): String {
        if (daysRemaining < 0) {
            return "Consider purchasing extended warranty or insurance for this product."
        }

        return when (warranty.category) {
            ProductCategory.ELECTRONICS -> buildString {
                append("💡 Service suggestion: Get a full checkup/service for your ${warranty.productName} ")
                append("while it's still under warranty. Check for any issues with battery, ")
                append("screen, ports, or performance.")
            }

            ProductCategory.APPLIANCES -> buildString {
                append("💡 Service suggestion: Schedule a preventive maintenance visit for your ")
                append("${warranty.productName}. Check filters, moving parts, and general wear. ")
                append("Any issues found will be covered under warranty.")
            }

            ProductCategory.VEHICLE -> buildString {
                append("💡 Get a complete vehicle inspection at an authorized service center. ")
                append("Report any unusual sounds, vibrations, or performance issues while covered.")
            }

            ProductCategory.FURNITURE -> buildString {
                append("💡 Check for any defects — loose joints, fabric tears, or finish damage. ")
                append("Report before warranty expires for free repair/replacement.")
            }

            ProductCategory.ACCESSORIES -> buildString {
                append("💡 Test all functions of your ${warranty.productName}. ")
                append("Report any malfunctions for warranty service.")
            }

            ProductCategory.OTHER -> buildString {
                append("💡 Review your ${warranty.productName} for any issues. ")
                append("Get them resolved while warranty is active.")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Warranty Claim Guide
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a generic warranty claim guide for a product.
     *
     * Provides step-by-step instructions, required documents, and tips.
     *
     * @param warrantyId ID of the warranty to generate guide for
     * @return Claim guide or null if warranty not found
     */
    fun getClaimGuide(warrantyId: String): ClaimGuide? {
        val warranty = warranties.find { it.id == warrantyId } ?: return null
        return generateClaimGuide(warranty)
    }

    /**
     * Generates claim guide based on product category.
     */
    private fun generateClaimGuide(warranty: Warranty): ClaimGuide {
        val commonSteps = listOf(
            "Locate your purchase receipt/invoice (${if (warranty.receiptPhotoPath.isNotBlank()) "✅ Photo saved" else "⚠️ Not saved — find it"})",
            "Note down the product serial number and model number",
            "Document the issue clearly — take photos/videos of the defect",
            "Contact the seller/store where you purchased (${warranty.store.ifBlank { "not recorded" }})",
            "If seller is unresponsive, contact the manufacturer directly",
            "Request a service ticket/complaint number in writing",
            "Follow up within 3-5 business days if no response"
        )

        val categorySteps = when (warranty.category) {
            ProductCategory.ELECTRONICS -> listOf(
                "Check if the issue is covered (physical damage is usually excluded)",
                "Back up your data before handing over the device",
                "Visit authorized service center or request doorstep pickup",
                "Get a written estimate if they claim it's not covered"
            )

            ProductCategory.APPLIANCES -> listOf(
                "Request doorstep service (most appliance warranties include it)",
                "Be present during service visit",
                "If part replacement needed, ensure genuine parts are used",
                "Get service report signed by technician"
            )

            ProductCategory.VEHICLE -> listOf(
                "Visit authorized service center only (unauthorized service may void warranty)",
                "Maintain service history records",
                "If the claim is disputed, escalate to area service manager",
                "Keep copies of all service records"
            )

            ProductCategory.FURNITURE -> listOf(
                "Take clear photos showing the defect from multiple angles",
                "Check if defect is due to manufacturing or usage",
                "Request on-site inspection for large items"
            )

            else -> listOf(
                "Contact customer support via phone and email both",
                "Keep all communication records"
            )
        }

        val documentsNeeded = listOf(
            "Original purchase receipt/invoice",
            "Warranty card (if provided separately)",
            "Product serial number",
            "Photos/videos of the defect",
            "Your ID proof",
            "Previous service records (if any)"
        )

        val tips = listOf(
            "Always communicate via email/written channels for a paper trail",
            "Note down names of customer support executives you speak with",
            "If warranty claim is rejected, ask for written reason",
            "You can escalate to consumer forum if legitimate claim is denied",
            "Keep the original packaging if possible — some warranties require it",
            "Extended warranty is worth it for expensive electronics (purchase within first year)"
        )

        return ClaimGuide(
            productName = warranty.productName,
            category = warranty.category,
            steps = commonSteps + categorySteps,
            documentsNeeded = documentsNeeded,
            tips = tips
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cost Analytics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates cost summary showing warranty portfolio value.
     *
     * Shows how much you've spent on products still under warranty vs expired,
     * helping understand the "protected" value of your purchases.
     *
     * @param today Reference date
     * @return Cost summary with category breakdown
     */
    fun getCostSummary(today: LocalDate = LocalDate.now()): WarrantyCostSummary {
        val all = getAllWarranties(today).filter { it.purchaseAmount > 0 }

        val totalCost = all.sumOf { it.purchaseAmount }
        val underWarranty = all.filter { it.status == WarrantyStatus.ACTIVE }
        val expiringSoon = all.filter { it.status == WarrantyStatus.EXPIRING_SOON }
        val expired = all.filter { it.status == WarrantyStatus.EXPIRED }

        val underWarrantyCost = underWarranty.sumOf { it.purchaseAmount }
        val expiringSoonCost = expiringSoon.sumOf { it.purchaseAmount }
        val expiredCost = expired.sumOf { it.purchaseAmount }

        // Potential savings = value of products still covered (if they break, warranty covers)
        val potentialSavings = underWarrantyCost + expiringSoonCost

        val categoryBreakdown = all.groupBy { it.category }.mapValues { (_, items) ->
            CategoryCostInfo(
                totalProducts = items.size,
                totalSpent = items.sumOf { it.purchaseAmount },
                activeCount = items.count { it.status != WarrantyStatus.EXPIRED },
                expiredCount = items.count { it.status == WarrantyStatus.EXPIRED }
            )
        }

        return WarrantyCostSummary(
            totalProductsCost = totalCost,
            underWarrantyCost = underWarrantyCost,
            expiredWarrantyCost = expiredCost,
            expiringSoonCost = expiringSoonCost,
            potentialSavings = potentialSavings,
            categoryBreakdown = categoryBreakdown
        )
    }

    /**
     * Formats cost summary as readable string.
     */
    fun formatCostSummary(today: LocalDate = LocalDate.now()): String {
        val summary = getCostSummary(today)
        return buildString {
            appendLine("📦 Warranty Portfolio Summary")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("Total products tracked: ${warranties.size}")
            appendLine("Total spent: ₹${String.format("%,.0f", summary.totalProductsCost)}")
            appendLine()
            appendLine("✅ Under warranty: ₹${String.format("%,.0f", summary.underWarrantyCost)}")
            appendLine("⚠️ Expiring soon: ₹${String.format("%,.0f", summary.expiringSoonCost)}")
            appendLine("❌ Expired: ₹${String.format("%,.0f", summary.expiredWarrantyCost)}")
            appendLine()
            appendLine("🛡️ Protected value: ₹${String.format("%,.0f", summary.potentialSavings)}")
            appendLine("   (Products still covered if something goes wrong)")
            appendLine()

            if (summary.categoryBreakdown.isNotEmpty()) {
                appendLine("By Category:")
                summary.categoryBreakdown.forEach { (category, info) ->
                    appendLine("  ${category.icon} ${category.displayName}: ${info.totalProducts} items, " +
                        "₹${String.format("%,.0f", info.totalSpent)} " +
                        "(${info.activeCount} active, ${info.expiredCount} expired)")
                }
            }
        }.trimEnd()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search & Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Searches warranties by product name, store, brand, or notes.
     */
    fun search(query: String): List<Warranty> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()

        return warranties.filter { warranty ->
            warranty.productName.lowercase().contains(q) ||
                warranty.store.lowercase().contains(q) ||
                warranty.brand.lowercase().contains(q) ||
                warranty.notes.lowercase().contains(q) ||
                warranty.serialNumber.lowercase().contains(q) ||
                warranty.category.displayName.lowercase().contains(q)
        }
    }

    /**
     * Gets products that should be serviced before warranty ends.
     *
     * Suggests servicing electronics and appliances in the last
     * 30-60 days of warranty to catch hidden issues.
     */
    fun getServiceSuggestions(today: LocalDate = LocalDate.now()): List<Warranty> {
        val serviceCategories = setOf(ProductCategory.ELECTRONICS, ProductCategory.APPLIANCES)
        return warranties
            .filter { it.category in serviceCategories }
            .filter { warranty ->
                val days = ChronoUnit.DAYS.between(today, warranty.expiryDate)
                days in 1..60
            }
            .sortedBy { it.expiryDate }
    }

    /**
     * Formats a warranty entry for display.
     */
    fun formatWarranty(warranty: Warranty, today: LocalDate = LocalDate.now()): String {
        val status = calculateStatus(warranty.expiryDate, today)
        val daysRemaining = ChronoUnit.DAYS.between(today, warranty.expiryDate)

        return buildString {
            appendLine("${warranty.category.icon} ${warranty.productName}")
            appendLine("   Status: ${status.icon} ${status.displayName}")
            if (daysRemaining >= 0) {
                appendLine("   Days remaining: $daysRemaining")
            } else {
                appendLine("   Expired: ${-daysRemaining} days ago")
            }
            appendLine("   Purchased: ${warranty.purchaseDate} from ${warranty.store.ifBlank { "N/A" }}")
            appendLine("   Warranty: ${warranty.warrantyMonths} months (until ${warranty.expiryDate})")
            if (warranty.purchaseAmount > 0) {
                appendLine("   Cost: ₹${String.format("%,.0f", warranty.purchaseAmount)}")
            }
            if (warranty.notes.isNotBlank()) {
                appendLine("   Notes: ${warranty.notes}")
            }
        }.trimEnd()
    }
}
