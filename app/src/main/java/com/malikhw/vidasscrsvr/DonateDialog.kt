package com.malikhw.vidasscrsvr

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.malikhw.vidasscrsvr.billing.DonateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object DonateDialog {

    fun show(activity: Activity) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_donate)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val contentContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.donateContent)
        val btnInfo = dialog.findViewById<android.widget.ImageButton>(R.id.btnDonateInfo)
        val btnMaybeLater = dialog.findViewById<MaterialButton>(R.id.btnDonateMaybeLater)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var purchasing = false
        lateinit var render: () -> Unit

        val helper = DonateHelper(activity, onPurchaseSuccess = {
            dialog.dismiss()
            Toast.makeText(activity, activity.getString(R.string.donate_success_toast), Toast.LENGTH_LONG).show()
        }, onPurchaseError = { message ->
            purchasing = false
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            render()
        })

        render = {
            contentContainer.removeAllViews()
            val inflater = LayoutInflater.from(activity)
            val state = helper.state.value
            val products = helper.products.value

            when {
                purchasing -> {
                    contentContainer.addView(ProgressBar(activity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    })
                    contentContainer.addView(TextView(activity).apply {
                        text = activity.getString(R.string.donate_waiting_payment)
                        setTextColor(Color.GRAY)
                        textSize = 12f
                        setPadding(0, 16, 0, 0)
                    })
                }

                state is DonateHelper.State.Error -> {
                    contentContainer.addView(TextView(activity).apply {
                        text = state.message
                        setTextColor(Color.parseColor("#CF6679"))
                        textSize = 13f
                        gravity = android.view.Gravity.CENTER
                    })
                }

                products.isEmpty() -> {
                    contentContainer.addView(ProgressBar(activity))
                    contentContainer.addView(TextView(activity).apply {
                        text = activity.getString(R.string.donate_loading_tiers)
                        setTextColor(Color.GRAY)
                        textSize = 12f
                        setPadding(0, 16, 0, 0)
                    })
                }

                else -> {
                    contentContainer.addView(TextView(activity).apply {
                        text = activity.getString(R.string.donate_how_much)
                        setTextColor(Color.GRAY)
                        textSize = 14f
                        setPadding(0, 0, 0, 12)
                    })
                    products.forEach { product ->
                        val name = product.name
                        val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "\u2014"

                        val tierBtn =
                            inflater.inflate(R.layout.item_donate_tier, contentContainer, false) as MaterialButton
                        tierBtn.text = "$name        $price"
                        tierBtn.setOnClickListener {
                            purchasing = true
                            render()
                            val result = helper.launchPurchase(activity, product)
                            if (result.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                                purchasing = false
                                render()
                                Toast.makeText(
                                    activity,
                                    result.debugMessage.ifBlank { activity.getString(R.string.donate_payment_unavailable) },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            // don't dismiss here, wait for onPurchaseSuccess so
                            // the consume/ack round trip completes first
                        }
                        contentContainer.addView(tierBtn)
                    }
                }
            }
        }

        scope.launch {
            combine(helper.state, helper.products) { s, p -> s to p }.collect { (_, _) ->
                render()
            }
        }

        btnInfo.setOnClickListener {
            AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.donate_info_message))
                .setPositiveButton(activity.getString(R.string.btn_ok), null)
                .show()
        }

        btnMaybeLater.setOnClickListener { dialog.dismiss() }

        dialog.setOnDismissListener {
            helper.disconnect()
            scope.cancel()
        }

        render()
        helper.connect(scope)
        dialog.show()
    }
}
