package es.schsebastian.foodrats.core.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

@Composable
fun resolve(key: StringKey): String = stringResource(key.resourceId)

@Composable
fun resolve(key: StringKey, vararg args: Any): String = stringResource(key.resourceId, *args)
