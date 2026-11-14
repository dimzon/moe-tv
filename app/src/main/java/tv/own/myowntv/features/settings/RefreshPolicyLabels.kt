package tv.own.myowntv.features.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.settings.DataRefreshPolicy

/** The one label/description pair shown wherever the shared refresh choice is offered. */
@StringRes
fun DataRefreshPolicy.labelRes(): Int = when (this) {
    DataRefreshPolicy.EVERY_6_HOURS -> R.string.refresh_policy_6h
    DataRefreshPolicy.STARTUP -> R.string.refresh_policy_startup
    DataRefreshPolicy.ONLY_FIRST -> R.string.refresh_policy_first
}

@StringRes
fun DataRefreshPolicy.descriptionRes(): Int = when (this) {
    DataRefreshPolicy.EVERY_6_HOURS -> R.string.refresh_policy_6h_description
    DataRefreshPolicy.STARTUP -> R.string.refresh_policy_startup_description
    DataRefreshPolicy.ONLY_FIRST -> R.string.refresh_policy_first_description
}

@Composable
fun dataRefreshPolicyLabel(policy: DataRefreshPolicy): String = stringResource(policy.labelRes())
