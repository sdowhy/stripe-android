package com.stripe.android.link.model

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.asFlow
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.stripe.android.link.LinkActivityResult
import com.stripe.android.link.LinkActivityResult.Canceled.Reason.BackPressed
import com.stripe.android.link.LinkScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the navigation between screens.
 */
@Singleton
internal class Navigator @Inject constructor() {

    private var launchedDirectly = false
    private var onDismiss: (LinkActivityResult) -> Unit = {}

    @VisibleForTesting
    var navigationController: NavHostController? = null

    var userNavigationEnabled = true

    fun attach(
        navigationController: NavHostController,
        launchedDirectly: Boolean,
        onDismiss: (LinkActivityResult) -> Unit,
    ) {
        this.navigationController = navigationController
        this.launchedDirectly = launchedDirectly
        this.onDismiss = onDismiss
    }

    /**
     * Navigates to the given [LinkScreen], optionally clearing the back stack.
     */
    fun navigateTo(
        target: LinkScreen,
        clearBackStack: Boolean = false
    ) {
        navigationController?.let { navController ->
            navController.navigate(target.route) {
                if (clearBackStack) {
                    popUpTo(navController.backQueue.first().destination.id) {
                        inclusive = true
                    }
                }
            }
        }
    }

    fun setResult(key: String, value: Any) {
        navigationController?.previousBackStackEntry?.savedStateHandle?.set(key, value)
    }

    fun <T> getResultFlow(key: String): Flow<T>? {
        return navigationController
            ?.currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<T>(key)
            ?.asFlow()
    }

    /**
     * Behaves like a back button, popping the back stack and dismissing the Activity if this was
     * the last screen.
     * When [userInitiated] is true, only performs any action if [userNavigationEnabled] is true.
     *
     * @param userInitiated Whether the action was initiated by user interaction.
     */
    fun onBack(userInitiated: Boolean) {
        if (!userInitiated || userNavigationEnabled) {
            navigationController?.let { navController ->
                if (!navController.popBackStack()) {
                    cancel(reason = BackPressed)
                }
            }
        }
    }

    fun complete() {
        onDismiss(LinkActivityResult.Completed(launchedDirectly))
    }

    fun cancel(reason: LinkActivityResult.Canceled.Reason) {
        dismiss(LinkActivityResult.Canceled(reason, launchedDirectly))
    }

    fun fail(error: Throwable) {
        onDismiss(
            LinkActivityResult.Failed(
                error = error,
                launchedDirectly = launchedDirectly,
            )
        )
    }

    private fun dismiss(result: LinkActivityResult) {
        onDismiss(result)
    }

    fun isOnRootScreen(): Boolean? = navigationController?.isOnRootScreen
}


internal val NavController.rootScreenFlow: Flow<Boolean>
    // The Loading screen is always at the bottom of the stack, so a size of 2 means the current
    // screen is at the bottom of the navigation stack.
    get() = currentBackStackEntryFlow.map { this.isOnRootScreen }

private val NavController.isOnRootScreen: Boolean
    // The Loading screen is always at the bottom of the stack, so a size of 2 means the current
    // screen is at the bottom of the navigation stack.
    get() = backQueue.size <= 2
