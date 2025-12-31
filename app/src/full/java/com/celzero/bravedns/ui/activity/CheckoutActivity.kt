/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.service.EncryptedFileManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.togb
import com.celzero.bravedns.util.Utilities.togs
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import java.util.UUID

class CheckoutActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    companion object {
        private const val TOKEN_LENGTH = 32
    }

    private var paymentStatus by mutableStateOf(TcpProxyHelper.getTcpProxyPaymentStatus())
    private var selectedPlan by mutableStateOf(Plan.SIX_MONTH)

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }
        init()

        /*paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)
        "Your backend endpoint/payment-sheet".httpPost().responseJson { _, _, result ->
            if (result is Result.Success) {
                val responseJson = result.get().obj()
                paymentIntentClientSecret = responseJson.getString("paymentIntent")
                customerConfig =
                    PaymentSheet.CustomerConfiguration(
                        responseJson.getString("customer"),
                        responseJson.getString("ephemeralKey")
                    )
                val publishableKey = responseJson.getString("publishableKey")
                PaymentConfiguration.init(this, publishableKey)
            }
        }*/
    }

    private fun init() {
        // create and handle keys if not paid
        if (TcpProxyHelper.getTcpProxyPaymentStatus().isNotPaid()) {
            handleKeys()
        }

        UUID.randomUUID().toString().let { uuid -> Napier.d("UUID: $uuid") }
        generateRandomHexToken(TOKEN_LENGTH).let { token ->
            Napier.d("Token: $token")
        }

        setContent {
            RethinkTheme {
                CheckoutScreen()
            }
        }
    }

    private fun observePaymentStatus() {
        val workManager = WorkManager.getInstance(this.applicationContext)

        // observer for custom download manager worker
        workManager.getWorkInfosByTagLiveData(TcpProxyHelper.PAYMENT_WORKER_TAG).observe(this) {
            workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Napier.i("WorkManager state: ${workInfo.state} for ${TcpProxyHelper.PAYMENT_WORKER_TAG}")
            if (
                WorkInfo.State.ENQUEUED == workInfo.state ||
                    WorkInfo.State.RUNNING == workInfo.state
            ) {
                paymentStatus = TcpProxyHelper.getTcpProxyPaymentStatus()
            } else if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                paymentStatus = TcpProxyHelper.getTcpProxyPaymentStatus()
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                    WorkInfo.State.FAILED == workInfo.state
            ) {
                paymentStatus = TcpProxyHelper.getTcpProxyPaymentStatus()
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(TcpProxyHelper.PAYMENT_WORKER_TAG)
            } else { // state == blocked
                // no-op
            }
        }
    }

    private fun handleKeys() {
        io {
            try {
                val key = TcpProxyHelper.getPublicKey()
                Napier.d("Public Key: $key")
                // if there is a key state, the msgOrExistingState (keyState.msg/keyState.v()) should not be empty
                val keyGenerator = Backend.newPipKeyProvider(key.togb(), "".togs())
                val keyState = keyGenerator.blind()
                // id: use 64 chars as account id
                val id = keyState.msg.opaque()?.s ?: ""
                val accountId = id.substring(0, 64)
                // rest of the keyState values will never be used in kotlin

                // keyState.v() should be retrieved from the file system
                Backend.newPipKeyStateFrom(keyState.v()) // retrieve the key state alone

                Napier.d("Blind: $keyState")
                val path =
                    File(
                        this.filesDir.canonicalPath +
                            File.separator +
                            TcpProxyHelper.TCP_FOLDER_NAME +
                            File.separator +
                            TcpProxyHelper.PIP_KEY_FILE_NAME
                    )
                EncryptedFileManager.writeTcpConfig(this, keyState.v().tos() ?: "", TcpProxyHelper.PIP_KEY_FILE_NAME)
                val content = EncryptedFileManager.read(this, path)
                Napier.d("Content: $content")
            } catch (e: Exception) {
                Napier.e("err in handleKeys: ${e.message}", e)
            }
        }
    }

    @Composable
    private fun CheckoutScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (paymentStatus) {
                TcpProxyHelper.PaymentStatus.NOT_PAID -> PaymentContent()
                TcpProxyHelper.PaymentStatus.INITIATED -> PaymentAwaiting()
                TcpProxyHelper.PaymentStatus.PAID -> PaymentSuccess()
                TcpProxyHelper.PaymentStatus.FAILED -> PaymentFailed()
                else -> PaymentContent()
            }
        }
    }

    @Composable
    private fun PaymentContent() {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            TopBanner()
            Spacer(modifier = Modifier.height(12.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResourceCompat(R.string.checkout_choose_plan),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlanSelector()
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { startPayment() }
            ) {
                Text(text = stringResourceCompat(R.string.checkout_purchase))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navigateToProxy() }
            ) {
                Text(text = stringResourceCompat(R.string.checkout_restore))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResourceCompat(R.string.checkout_terms_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResourceCompat(R.string.checkout_terms_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun TopBanner() {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = stringResourceCompat(R.string.checkout_banner_icon_desc),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResourceCompat(R.string.checkout_app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResourceCompat(R.string.checkout_proxy_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    @Composable
    private fun PlanSelector() {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlanRow(Plan.ONE_MONTH)
            PlanRow(Plan.THREE_MONTH)
            PlanRow(Plan.SIX_MONTH)
        }
    }

    @Composable
    private fun PlanRow(plan: Plan) {
        val context = LocalContext.current
        val accent = Color(fetchColorCompat(context, R.attr.accentGood))
        val subtle = Color(fetchColorCompat(context, R.attr.primaryLightColorText))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedPlan == plan,
                onClick = { selectedPlan = plan }
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                        append(stringResourceCompat(plan.titleRes))
                    }
                    append(" ")
                    withStyle(SpanStyle(color = subtle)) {
                        append(stringResourceCompat(plan.subtitleRes))
                    }
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    @Composable
    private fun PaymentSuccess() {
        StatusScreen(
            title = stringResourceCompat(R.string.checkout_payment_success_title),
            message = stringResourceCompat(R.string.checkout_payment_success_message),
            buttonLabel = stringResourceCompat(R.string.checkout_payment_success_button),
            onButtonClick = { navigateToProxy() }
        )
    }

    @Composable
    private fun PaymentFailed() {
        StatusScreen(
            title = stringResourceCompat(R.string.checkout_payment_failed_title),
            message = stringResourceCompat(R.string.checkout_payment_failed_message),
            buttonLabel = stringResourceCompat(R.string.checkout_payment_failed_button),
            onButtonClick = { navigateToProxy() }
        )
    }

    @Composable
    private fun PaymentAwaiting() {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResourceCompat(R.string.checkout_payment_awaiting_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResourceCompat(R.string.checkout_payment_awaiting_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    @Composable
    private fun StatusScreen(
        title: String,
        message: String,
        buttonLabel: String,
        onButtonClick: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onButtonClick
            ) {
                Text(text = buttonLabel)
            }
        }
    }

    /*fun presentPaymentSheet() {
        paymentSheet.presentWithPaymentIntent(
            paymentIntentClientSecret,
            PaymentSheet.Configuration(
                merchantDisplayName = "My merchant name",
                customer = customerConfig,
                // Set `allowsDelayedPaymentMethods` to true if your business
                // can handle payment methods that complete payment after a delay, like SEPA Debit
                // and Sofort.
                allowsDelayedPaymentMethods = true
            )
        )
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                print("Canceled")
            }
            is PaymentSheetResult.Failed -> {
                print("Error: ${paymentSheetResult.error}")
            }
            is PaymentSheetResult.Completed -> {
                // Display for example, an order confirmation screen
                print("Completed")
            }
        }
    }*/

    private fun startPayment() {
        TcpProxyHelper.initiatePaymentVerification(this)
        observePaymentStatus()
        paymentStatus = TcpProxyHelper.getTcpProxyPaymentStatus()
    }

    private fun navigateToProxy() {
        val intent = Intent(this, TcpProxyMainActivity::class.java)
        startActivity(intent)
        finish()
    }

    // https://stackoverflow.com/a/44227131
    private fun generateRandomHexToken(length: Int): String? {
        val secureRandom = SecureRandom()
        val token = ByteArray(length)
        secureRandom.nextBytes(token)
        return BigInteger(1, token).toString(16) // Hexadecimal encoding
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun fetchColorCompat(context: Context, colorAttr: Int): Int {
        return com.celzero.bravedns.util.UIUtils.fetchColor(context, colorAttr)
    }

    private enum class Plan(val titleRes: Int, val subtitleRes: Int) {
        ONE_MONTH(R.string.checkout_plan_1m_title, R.string.checkout_plan_1m_subtitle),
        THREE_MONTH(R.string.checkout_plan_3m_title, R.string.checkout_plan_3m_subtitle),
        SIX_MONTH(R.string.checkout_plan_6m_title, R.string.checkout_plan_6m_subtitle)
    }

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) {
            context.getString(id, *args)
        } else {
            context.getString(id)
        }
    }
}
