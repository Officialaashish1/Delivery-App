package `in`.localrush.rider.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether any activity of the app is in the foreground (used to suppress duplicate offer notifications). */
@Singleton
class AppForeground @Inject constructor() : DefaultLifecycleObserver {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground

    fun install() { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { _isForeground.value = true }
    override fun onStop(owner: LifecycleOwner) { _isForeground.value = false }
}
