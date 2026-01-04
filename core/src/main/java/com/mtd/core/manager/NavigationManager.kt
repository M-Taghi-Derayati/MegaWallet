package com.mtd.core.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت متمرکز Navigation در کل اپلیکیشن
 */
@Singleton
class NavigationManager @Inject constructor() {
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>(replay = 0)
    val navigationEvents = _navigationEvents.asSharedFlow()

    /**
     * Navigate به یک destination
     */
    suspend fun navigate(
        destination: NavigationDestination,
        options: NavigationOptions = NavigationOptions()
    ) {
        _navigationEvents.emit(
            NavigationEvent.Navigate(destination, options)
        )
    }

    /**
     * Navigate back
     */
    suspend fun navigateBack(result: Any? = null) {
        _navigationEvents.emit(
            NavigationEvent.Back(result)
        )
    }

    /**
     * Pop up to a specific destination
     */
    suspend fun popUpTo(
        destination: String,
        inclusive: Boolean = false
    ) {
        _navigationEvents.emit(
            NavigationEvent.PopUpTo(destination, inclusive)
        )
    }
}

/**
 * رویدادهای Navigation
 */
sealed class NavigationEvent {
    data class Navigate(
        val destination: NavigationDestination,
        val options: NavigationOptions
    ) : NavigationEvent()
    
    data class Back(val result: Any? = null) : NavigationEvent()
    
    data class PopUpTo(
        val destination: String,
        val inclusive: Boolean
    ) : NavigationEvent()
}

/**
 * Navigation destinations
 */
sealed class NavigationDestination {
    data class Screen(
        val route: String,
        val args: Map<String, Any> = emptyMap()
    ) : NavigationDestination()
    
    data class DeepLink(val url: String) : NavigationDestination()
}

/**
 * Navigation options
 */
data class NavigationOptions(
    val popUpTo: String? = null,
    val inclusive: Boolean = false,
    val saveState: Boolean = false,
    val restoreState: Boolean = false,
    val singleTop: Boolean = false
)

