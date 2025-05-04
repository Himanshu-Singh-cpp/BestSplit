package com.example.bestsplit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Utility class for handling UPI QR code scanning and payments
 */
object UpiPaymentUtils {

    /**
     * Parse UPI QR code content to extract UPI ID and other details
     */
    fun parseUpiQrCode(qrContent: String): UpiDetails? {
        return try {
            // Check if this is a UPI QR code
            if (qrContent.startsWith("upi://pay")) {
                // Parse the UPI URI to extract parameters
                val uri = Uri.parse(qrContent)

                // Extract UPI ID (pa - payee address)
                val upiId = uri.getQueryParameter("pa")

                // Extract other optional parameters
                val name = uri.getQueryParameter("pn")  // payee name
                val amountStr = uri.getQueryParameter("am")  // amount
                val note = uri.getQueryParameter("tn")  // transaction note

                // Convert amount string to Double if present
                val amount = amountStr?.toDoubleOrNull()

                // Return parsed details only if UPI ID is available
                if (!upiId.isNullOrEmpty()) {
                    UpiDetails(upiId, name, amount, note)
                } else {
                    null
                }
            } else {
                // Try to extract UPI ID from non-standard QR codes
                // Common format: just the UPI ID by itself (e.g. "name@upi")
                val upiIdPattern = Regex("[a-zA-Z0-9_.\\-]+@[a-zA-Z0-9]+")
                val matchResult = upiIdPattern.find(qrContent)

                if (matchResult != null) {
                    val extractedUpiId = matchResult.value
                    Log.d("UpiPaymentUtils", "Extracted non-standard UPI ID: $extractedUpiId")
                    UpiDetails(extractedUpiId, null, null, null)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("UpiPaymentUtils", "Error parsing UPI QR code", e)
            null
        }
    }

    /**
     * Initiate UPI payment with given details
     */
    fun initiateUpiPayment(
        context: Context,
        upiId: String,
        amount: Double,
        description: String,
        transactionRef: String? = null
    ) {
        try {
            // Log the input amount
            Log.d("UpiPaymentUtils", "Initiating payment with amount: $amount")

            // Format amount properly with 2 decimal places
            val formattedAmount = String.format("%.2f", amount)
            Log.d("UpiPaymentUtils", "Formatted amount: $formattedAmount")

            // Create UPI payment URI with all required parameters
            val uri = Uri.parse("upi://pay")
                .buildUpon()
                .appendQueryParameter("pa", upiId)  // payee address (UPI ID)
                .appendQueryParameter("pn", "BestSplit Payment")  // payee name
                .appendQueryParameter(
                    "tn",
                    description.ifEmpty { "Settlement payment" })  // transaction note
                .appendQueryParameter("am", formattedAmount)  // amount
                .appendQueryParameter("cu", "INR")  // currency
                .appendQueryParameter("mc", "")  // merchant code (optional)
                .appendQueryParameter(
                    "tr",
                    transactionRef ?: "BestSplit${System.currentTimeMillis()}"
                )  // transaction reference ID
                .build()

            Log.d("UpiPayment", "Payment URI: $uri")

            val upiPayIntent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                // Ensure URI is not modified by the app
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }

            // Check if there are apps that can handle this intent
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(
                upiPayIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            if (activities.isNotEmpty()) {
                // Show payment apps chooser
                val chooser = Intent.createChooser(upiPayIntent, "Pay with...")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                // Toast to confirm payment initiation
                Toast.makeText(
                    context,
                    "Payment of ₹$formattedAmount initiated",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "No UPI apps found on device",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e("UpiPayment", "Error initiating UPI payment", e)
            Toast.makeText(
                context,
                "Error initiating payment: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * Data class to hold UPI information parsed from QR code
 */
data class UpiDetails(
    val upiId: String,
    val name: String? = null,
    val amount: Double? = null,
    val note: String? = null
)