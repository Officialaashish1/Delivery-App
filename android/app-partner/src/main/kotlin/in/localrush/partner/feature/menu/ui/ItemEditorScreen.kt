package `in`.localrush.partner.feature.menu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.LabeledSwitch
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.menu.vm.AddonGroupForm
import `in`.localrush.partner.feature.menu.vm.AddonOptionForm
import `in`.localrush.partner.feature.menu.vm.ItemEditorEvent
import `in`.localrush.partner.feature.menu.vm.ItemEditorViewModel
import `in`.localrush.partner.feature.menu.vm.ItemErrors
import `in`.localrush.partner.feature.menu.vm.ItemForm
import `in`.localrush.partner.feature.menu.vm.VariantGroupForm
import `in`.localrush.partner.feature.menu.vm.VariantOptionForm
import `in`.localrush.partner.feature.registration.ui.ImagePickBox

val FOOD_TYPES = listOf("veg", "non_veg", "egg")

@Composable
private fun errorText(errors: Map<String, String>, key: String): String? = when (errors[key]) {
    null -> null
    ItemErrors.NAME_REQUIRED -> stringResource(R.string.error_required)
    ItemErrors.INVALID_AMOUNT -> stringResource(R.string.error_amount)
    ItemErrors.DISCOUNT_NOT_LOWER -> stringResource(R.string.error_discount_not_lower)
    ItemErrors.PREP_RANGE -> stringResource(R.string.error_prep_item)
    ItemErrors.STOCK_INVALID -> stringResource(R.string.error_stock)
    ItemErrors.MIN_GT_MAX -> stringResource(R.string.error_min_gt_max)
    ItemErrors.MAX_GT_OPTIONS -> stringResource(R.string.error_max_gt_options)
    ItemErrors.NO_OPTIONS -> stringResource(R.string.error_no_options)
    ItemErrors.MULTIPLE_DEFAULTS -> stringResource(R.string.error_multiple_defaults)
    else -> errors[key]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: ItemEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e -> when (e) { is ItemEditorEvent.Saved -> onSaved(); is ItemEditorEvent.Message -> snackbar.showSnackbar(e.text) } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setImage) }
    val data = (state as? `in`.localrush.core.common.util.UiState.Success)?.data

    Scaffold(
        topBar = { PartnerTopBar(stringResource(if (viewModel.itemId == null) R.string.item_add else R.string.item_edit), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (data != null) LRFooter { LRButton(stringResource(R.string.action_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = data.saving, enabled = online) } },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            val f = d.form
            val errors = d.errors
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImagePickBox(d.newImage?.toString() ?: d.existingImage, stringResource(R.string.item_photo), Modifier.width(110.dp).height(110.dp)) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LRTextField(f.name, { v -> viewModel.edit { it.copy(name = v) } }, label = stringResource(R.string.item_name), errorText = errorText(errors, ItemErrors.NAME))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FOOD_TYPES.forEach { t ->
                                FilterChip(selected = f.foodType == t, onClick = { viewModel.edit { it.copy(foodType = t) } }, label = { Text(foodTypeLabel(t)) }, leadingIcon = { VegNonVegDot(t) })
                            }
                        }
                    }
                }
                LRTextField(f.description, { v -> viewModel.edit { it.copy(description = v) } }, label = stringResource(R.string.item_description), singleLine = false, maxLines = 4)
                SectionTitle(stringResource(R.string.item_category))
                errorText(errors, ItemErrors.CATEGORY)?.let { InlineError(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.categories.forEach { c -> FilterChip(selected = f.categoryId == c.id, onClick = { viewModel.edit { it.copy(categoryId = c.id) } }, label = { Text(c.name) }) }
                }
                SectionTitle(stringResource(R.string.item_pricing))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRTextField(f.price, { v -> viewModel.edit { it.copy(price = v) } }, label = stringResource(R.string.item_price), prefix = "₹", errorText = errorText(errors, ItemErrors.PRICE), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    LRTextField(f.discountedPrice, { v -> viewModel.edit { it.copy(discountedPrice = v) } }, label = stringResource(R.string.item_discounted_price), prefix = "₹", errorText = errorText(errors, ItemErrors.DISCOUNT), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRTextField(f.packagingCharge, { v -> viewModel.edit { it.copy(packagingCharge = v) } }, label = stringResource(R.string.item_packaging), prefix = "₹", errorText = errorText(errors, ItemErrors.PACKAGING), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    LRTextField(f.preparationMinutes, { v -> viewModel.edit { it.copy(preparationMinutes = v.filter { c -> c.isDigit() }.take(3)) } }, label = stringResource(R.string.item_prep_minutes), errorText = errorText(errors, ItemErrors.PREP), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                SectionTitle(stringResource(R.string.item_availability))
                LabeledSwitch(stringResource(R.string.item_available), f.isAvailable, { v -> viewModel.edit { it.copy(isAvailable = v) } })
                LabeledSwitch(stringResource(R.string.item_limited_stock), f.stockLimited, { v -> viewModel.edit { it.copy(stockLimited = v) } }, description = stringResource(R.string.item_limited_stock_hint))
                if (f.stockLimited) LRTextField(f.stockQuantity, { v -> viewModel.edit { it.copy(stockQuantity = v.filter { c -> c.isDigit() }) } }, label = stringResource(R.string.item_stock_quantity), errorText = errorText(errors, ItemErrors.STOCK), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LabeledSwitch(stringResource(R.string.item_recommended), f.isRecommended, { v -> viewModel.edit { it.copy(isRecommended = v) } })
                LabeledSwitch(stringResource(R.string.item_bestseller), f.isBestseller, { v -> viewModel.edit { it.copy(isBestseller = v) } })
                SectionTitle(stringResource(R.string.item_spice))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..3).forEach { lvl -> FilterChip(selected = f.spiceLevel == lvl, onClick = { viewModel.edit { it.copy(spiceLevel = lvl) } }, label = { Text(if (lvl == 0) stringResource(R.string.item_spice_none) else "🌶".repeat(lvl)) }) }
                }
                LRTextField(f.tags, { v -> viewModel.edit { it.copy(tags = v) } }, label = stringResource(R.string.item_tags), helperText = stringResource(R.string.item_tags_hint))

                SectionTitle(stringResource(R.string.item_variants)) {
                    LRButton(stringResource(R.string.item_add_group), onClick = { viewModel.edit { it.copy(variantGroups = it.variantGroups + VariantGroupForm()) } }, style = LRButtonStyle.Text, leadingIcon = Icons.Filled.Add)
                }
                Text(stringResource(R.string.item_variants_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                f.variantGroups.forEachIndexed { gi, g -> VariantGroupEditor(gi, g, errors, viewModel::edit) }

                SectionTitle(stringResource(R.string.item_addons)) {
                    LRButton(stringResource(R.string.item_add_group), onClick = { viewModel.edit { it.copy(addonGroups = it.addonGroups + AddonGroupForm()) } }, style = LRButtonStyle.Text, leadingIcon = Icons.Filled.Add)
                }
                Text(stringResource(R.string.item_addons_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                f.addonGroups.forEachIndexed { gi, g -> AddonGroupEditor(gi, g, errors, viewModel::edit) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun foodTypeLabel(t: String): String = stringResource(when (t) { "veg" -> R.string.food_veg; "egg" -> R.string.food_egg; else -> R.string.food_non_veg })

@Composable
private fun VariantGroupEditor(gi: Int, g: VariantGroupForm, errors: Map<String, String>, edit: ((ItemForm) -> ItemForm) -> Unit) {
    fun updateGroup(t: (VariantGroupForm) -> VariantGroupForm) = edit { f -> f.copy(variantGroups = f.variantGroups.mapIndexed { i, x -> if (i == gi) t(x) else x }) }
    LRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LRTextField(g.name, { v -> updateGroup { it.copy(name = v) } }, label = stringResource(R.string.group_name_variant), modifier = Modifier.weight(1f), errorText = errorText(errors, "variant.$gi"))
            IconButton(onClick = { edit { f -> f.copy(variantGroups = f.variantGroups.filterIndexed { i, _ -> i != gi }) } }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
        LabeledSwitch(stringResource(R.string.group_required), g.isRequired, { v -> updateGroup { it.copy(isRequired = v) } })
        Text(stringResource(R.string.group_options_default_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        g.options.forEachIndexed { oi, o ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RadioButton(selected = o.isDefault, onClick = { updateGroup { grp -> grp.copy(options = grp.options.mapIndexed { i, x -> x.copy(isDefault = i == oi) }) } })
                LRTextField(o.name, { v -> updateGroup { grp -> grp.copy(options = grp.options.mapIndexed { i, x -> if (i == oi) x.copy(name = v) else x }) } }, label = stringResource(R.string.option_name), modifier = Modifier.weight(1.4f), errorText = errorText(errors, "variant.$gi.$oi"))
                LRTextField(o.price, { v -> updateGroup { grp -> grp.copy(options = grp.options.mapIndexed { i, x -> if (i == oi) x.copy(price = v) else x }) } }, label = stringResource(R.string.option_price), prefix = "₹", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                IconButton(onClick = { updateGroup { grp -> grp.copy(options = grp.options.filterIndexed { i, _ -> i != oi }) } }, enabled = g.options.size > 1) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        }
        LRButton(stringResource(R.string.option_add), onClick = { updateGroup { it.copy(options = it.options + VariantOptionForm()) } }, style = LRButtonStyle.Text, leadingIcon = Icons.Filled.Add)
    }
}

@Composable
private fun AddonGroupEditor(gi: Int, g: AddonGroupForm, errors: Map<String, String>, edit: ((ItemForm) -> ItemForm) -> Unit) {
    fun updateGroup(t: (AddonGroupForm) -> AddonGroupForm) = edit { f -> f.copy(addonGroups = f.addonGroups.mapIndexed { i, x -> if (i == gi) t(x) else x }) }
    LRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LRTextField(g.name, { v -> updateGroup { it.copy(name = v) } }, label = stringResource(R.string.group_name_addon), modifier = Modifier.weight(1f), errorText = errorText(errors, "addon.$gi"))
            IconButton(onClick = { edit { f -> f.copy(addonGroups = f.addonGroups.filterIndexed { i, _ -> i != gi }) } }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LRTextField(g.minSelect, { v -> updateGroup { it.copy(minSelect = v.filter { c -> c.isDigit() }) } }, label = stringResource(R.string.group_min_select), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            LRTextField(g.maxSelect, { v -> updateGroup { it.copy(maxSelect = v.filter { c -> c.isDigit() }) } }, label = stringResource(R.string.group_max_select), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        g.options.forEachIndexed { oi, o ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Checkbox(checked = o.foodType != "veg", onCheckedChange = { nv -> updateGroup { grp -> grp.copy(options = grp.options.mapIndexed { i, x -> if (i == oi) x.copy(foodType = if (nv) "non_veg" else "veg") else x }) } })
                VegNonVegDot(o.foodType)
                LRTextField(o.name, { v -> updateGroup { grp -> grp.copy(options = grp.options.mapIndexed { i, x -> if (i == oi) x.copy(name = v) else x }) } }, label = stringResource(R.string.option_name), modifier = Modifier.weight(1.4f), errorText = errorText(errors, "addon.$gi.$oi"))
                LRTextField(o.price, { v -> updateGroup { grp -> grp.copy(options = grp.options.mapIndexed { i, x -> if (i == oi) x.copy(price = v) else x }) } }, label = stringResource(R.string.option_price), prefix = "₹", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                IconButton(onClick = { updateGroup { grp -> grp.copy(options = grp.options.filterIndexed { i, _ -> i != oi }) } }, enabled = g.options.size > 1) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        }
        Text(stringResource(R.string.group_nonveg_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LRButton(stringResource(R.string.option_add), onClick = { updateGroup { it.copy(options = it.options + AddonOptionForm()) } }, style = LRButtonStyle.Text, leadingIcon = Icons.Filled.Add)
    }
}
