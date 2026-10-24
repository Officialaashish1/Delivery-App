package `in`.localrush.customer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import `in`.localrush.core.designsystem.theme.LocalRushTheme
import `in`.localrush.customer.data.repo.PaymentResult
import `in`.localrush.customer.data.repo.PaymentResultBus
import `in`.localrush.customer.navigation.AppNavHost
import `in`.localrush.customer.navigation.DeepLinkHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity. Razorpay Checkout calls back here; results are forwarded to the checkout ViewModel via [PaymentResultBus]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    @Inject lateinit var deepLinks: DeepLinkHandler
    @Inject lateinit var payments: PaymentResultBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Checkout.preload(applicationContext)
        deepLinks.offer(intent)
        setContent {
            LocalRushTheme {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinks.offer(intent)
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        val orderId = data?.orderId; val paymentId = razorpayPaymentId ?: data?.paymentId; val signature = data?.signature
        if (orderId.isNullOrBlank() || paymentId.isNullOrBlank() || signature.isNullOrBlank()) {
            payments.publish(PaymentResult.Failure(-1, getString(R.string.payment_incomplete_response)))
        } else {
            payments.publish(PaymentResult.Success(orderId, paymentId, signature))
        }
    }

    override fun onPaymentError(code: Int, description: String?, data: PaymentData?) {
        val message = when (code) {
            Checkout.PAYMENT_CANCELED -> getString(R.string.payment_cancelled)
            Checkout.NETWORK_ERROR -> getString(R.string.payment_network_error)
            else -> description?.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") } ?: getString(R.string.payment_failed)
        }
        payments.publish(PaymentResult.Failure(code, message))
    }
}
