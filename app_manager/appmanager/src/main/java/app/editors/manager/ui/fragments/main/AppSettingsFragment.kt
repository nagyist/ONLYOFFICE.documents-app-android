package app.editors.manager.ui.fragments.main

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.managers.tools.PreferenceTool
import app.editors.manager.ui.activities.main.AboutScreen
import app.editors.manager.ui.activities.main.IMainActivity
import app.editors.manager.ui.compose.locale.AppLocalePickerScreen
import app.editors.manager.ui.compose.passcode.PasscodeMainScreen
import app.editors.manager.ui.fragments.onboarding.WhatsNewDialog
import app.editors.manager.viewModels.main.AppSettingsEffect
import app.editors.manager.viewModels.main.AppSettingsState
import app.editors.manager.viewModels.main.AppSettingsViewModel
import app.editors.manager.viewModels.main.AppSettingsViewModelFactory
import app.editors.manager.viewModels.main.PasscodeViewModel
import app.editors.manager.viewModels.main.PasscodeViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import lib.compose.ui.theme.ManagerTheme
import lib.compose.ui.utils.popBackStackWhenResumed
import lib.compose.ui.views.AppArrowItem
import lib.compose.ui.views.AppDivider
import lib.compose.ui.views.AppHeaderItem
import lib.compose.ui.views.AppListItem
import lib.compose.ui.views.AppScaffold
import lib.compose.ui.views.AppSelectItem
import lib.compose.ui.views.AppSwitchItem
import lib.compose.ui.views.PlaceholderView
import lib.toolkit.base.managers.tools.ResourcesProvider
import lib.toolkit.base.managers.tools.ThemePreferencesTools
import lib.toolkit.base.managers.utils.ActivitiesUtils
import lib.toolkit.base.managers.utils.FontPicker
import lib.toolkit.base.managers.utils.StringUtils
import lib.toolkit.base.managers.utils.UiUtils
import lib.toolkit.base.managers.utils.capitalize
import lib.toolkit.base.managers.utils.suspendLaunchAfterResume
import lib.toolkit.base.ui.dialogs.common.CommonDialog
import lib.toolkit.base.ui.fragments.base.BaseFragment
import java.io.File
import javax.inject.Inject
import kotlin.properties.Delegates

private data class ClearCacheMessage(
    val title: String?,
    val message: String
)

private sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Theme : Screen("theme")
    data object Passcode : Screen("passcode")
    data object LocalePicker : Screen("locale")
    data object About : Screen("about")
    data object Fonts : Screen("fonts")
}

class AppSettingsFragment : BaseFragment() {

    companion object {
        val TAG: String = AppSettingsFragment::class.java.simpleName
        private const val DIALOG_CANCEL_TAG = "DIALOG_CANCEL_TAG"

        fun newInstance(): AppSettingsFragment = AppSettingsFragment()
    }

    @Inject
    lateinit var preferenceTool: PreferenceTool

    private var addMenuItem: MenuItem? = null
    private var clearMenuItem: MenuItem? = null

    private var navController: NavHostController by Delegates.notNull()

    private val viewModel by viewModels<AppSettingsViewModel> {
        AppSettingsViewModelFactory(
            themePrefs = ThemePreferencesTools(requireContext()),
            resourcesProvider = ResourcesProvider(requireContext()),
            preferenceTool = preferenceTool
        )
    }

    private val passcodeViewModel by viewModels<PasscodeViewModel> {
        PasscodeViewModelFactory(preferenceTool = preferenceTool)
    }

    private val clearCacheMessage: ClearCacheMessage
        get() {
            val string = requireContext().getString(R.string.dialog_clear_cache)
            return try {
                val (title, message) = string.split("\n")
                ClearCacheMessage(title, message)
            } catch (_: Exception) {
                ClearCacheMessage(null, string)
            }
        }

    init {
        App.getApp().appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_settings_fonts, menu)
        clearMenuItem = menu.findItem(R.id.clear_fonts)
        addMenuItem = menu.findItem(R.id.add_font)
        clearMenuItem?.let { UiUtils.setMenuItemTint(requireContext(), it, lib.toolkit.base.R.color.colorPrimary) }
        addMenuItem?.let { UiUtils.setMenuItemTint(requireContext(), it, lib.toolkit.base.R.color.colorPrimary) }

        suspendLaunchAfterResume {
            navController.currentBackStackEntryFlow.collect { entry ->
                if (entry.destination.route == Screen.Fonts.route) {
                    clearMenuItem?.isVisible = viewModel.settingsState.value.fonts.isNotEmpty()
                    addMenuItem?.isVisible = true
                } else {
                    clearMenuItem?.isVisible = false
                    addMenuItem?.isVisible = false
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_fonts -> {
                UiUtils.showQuestionDialog(
                    context = requireContext(),
                    title = getString(R.string.dialogs_question_delete_all_fonts),
                    description = resources.getQuantityString(R.plurals.dialogs_question_message_delete, 2),
                    acceptListener = viewModel::clearFonts
                )
            }

            R.id.add_font -> {
                FontPicker(
                    activityResultRegistry = requireActivity().activityResultRegistry,
                    callback = viewModel::addFont
                ).pickFonts()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext())
    }

    override fun onCancelClick(dialogs: CommonDialog.Dialogs?, tag: String?) {
        when (tag) {
            DIALOG_CANCEL_TAG -> {
                viewModel.cancelJob()
                hideDialog()
            }

            else -> super.onCancelClick(dialogs, tag)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).setContent {
            val settingsState by viewModel.settingsState.collectAsState()
            navController = rememberNavController()

            LaunchedEffect(Unit) {
                viewModel.effect.collect {
                    when (it) {
                        is AppSettingsEffect.Error -> showSnackBar(it.message)
                        is AppSettingsEffect.Progress -> updateProgressDialog(100, it.value)
                        AppSettingsEffect.HideDialog -> hideDialog()
                        AppSettingsEffect.ShowDialog -> {
                            showProgressDialog(
                                title = getString(R.string.dialogs_wait_title),
                                isHideButton = false,
                                cancelTitle = getString(R.string.dialogs_common_cancel_button),
                                tag = DIALOG_CANCEL_TAG
                            )
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.message.collectLatest { message ->
                    showSnackBar(message)
                }
            }

            LaunchedEffect(Unit) {
                navController.addOnDestinationChangedListener { _, destination, _ ->
                    initToolbar(destination.route)
                }
            }

            ManagerTheme {
                AppScaffold {
                    NavHost(navController = navController, startDestination = Screen.Main.route) {
                        composable(Screen.Main.route) {
                            SettingsScreen(
                                context = requireContext(),
                                settingsState = settingsState,
                                onWifiState = viewModel::setWifiState,
                                onAnalytics = viewModel::setAnalytic,
                                onCacheClear = viewModel::clearCache,
                                onThemeClick = { navController.navigate(Screen.Theme.route) },
                                onPasscodeClick = { navController.navigate(Screen.Passcode.route) },
                                onLocaleClick = { navController.navigate(Screen.LocalePicker.route) },
                                onAboutClick = { navController.navigate(Screen.About.route) },
                                onFontsClick = { navController.navigate(Screen.Fonts.route) }
                            )
                        }
                        composable(Screen.Theme.route) {
                            ThemeScreen()
                        }
                        composable(Screen.Passcode.route) {
                            PasscodeMainScreen(
                                viewModel = passcodeViewModel,
                                enterPasscodeKey = false,
                                onBackClick = navController::popBackStack,
                                onSuccess = navController::popBackStack
                            )
                        }
                        composable(Screen.LocalePicker.route) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                with(App.getApp().appComponent.appLocaleHelper) {
                                    AppLocalePickerScreen(
                                        locales = locales,
                                        current = currentLocale,
                                        onBackListener = navController::popBackStack,
                                        onChangeLocale = ::changeLocale
                                    )
                                }
                            }
                        }
                        composable(Screen.About.route) {
                            AboutScreen(
                                onBackClick = navController::popBackStack,
                                onShowBrowser = { url -> showUrlInBrowser(getString(url)) }
                            )
                        }
                        composable(Screen.Fonts.route) {
                            LaunchedEffect(settingsState.fonts.size) {
                                clearMenuItem?.isVisible = settingsState.fonts.isNotEmpty()
                            }

                            FontsScreen(
                                fonts = settingsState.fonts,
                                onBack = navController::popBackStackWhenResumed
                            ) { font ->
                                UiUtils.showQuestionDialog(
                                    context = requireContext(),
                                    title = getString(R.string.dialogs_question_delete_font),
                                    description = resources.getQuantityString(
                                        R.plurals.dialogs_question_message_delete,
                                        1
                                    ),
                                    acceptListener = { viewModel.deleteFont(font) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initToolbar(screen: String?) {
        (activity as? IMainActivity)?.apply {
            val title = when (screen) {
                Screen.Theme.route -> R.string.app_settings_color_theme
                Screen.Passcode.route -> R.string.app_settings_passcode
                Screen.LocalePicker.route -> R.string.settings_language
                Screen.About.route -> R.string.about_title
                Screen.Fonts.route -> lib.toolkit.base.R.string.settings_fonts_title
                else -> R.string.settings_item_title
            }

            setAppBarStates(false)
            showNavigationButton(screen != Screen.Main.route)
            showActionButton(false)
            setActionBarTitle(getString(title))
        }
    }

    @Composable
    fun SettingsScreen(
        context: Context,
        settingsState: AppSettingsState,
        onThemeClick: () -> Unit,
        onPasscodeClick: () -> Unit,
        onLocaleClick: () -> Unit,
        onAboutClick: () -> Unit,
        onWifiState: (Boolean) -> Unit,
        onAnalytics: (Boolean) -> Unit,
        onCacheClear: () -> Unit,
        onFontsClick: () -> Unit
    ) {
        Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AppHeaderItem(title = R.string.app_settings_analytic_title)
                AppSwitchItem(
                    title = R.string.app_settings_analytic,
                    checked = settingsState.analytics,
                    onCheck = onAnalytics,
                    dividerVisible = false
                )
                AppDivider()
                AppHeaderItem(title = R.string.setting_title_wifi)
                AppSwitchItem(
                    title = R.string.setting_wifi,
                    checked = settingsState.wifi,
                    onCheck = onWifiState,
                    dividerVisible = false
                )
                AppDivider()
                AppHeaderItem(title = R.string.app_settings_security)
                AppArrowItem(
                    title = R.string.app_settings_passcode,
                    arrowVisible = true,
                    dividerVisible = false,
                    onClick = onPasscodeClick
                )
                AppDivider()
                AppHeaderItem(title = R.string.settings_title_common)
                AppArrowItem(
                    title = R.string.settings_clear_cache,
                    option = StringUtils.getFormattedSize(context, settingsState.cache),
                    dividerVisible = false,
                    enabled = settingsState.cache > 0
                ) {
                    val message = clearCacheMessage
                    UiUtils.showQuestionDialog(
                        context,
                        title = message.title.orEmpty(),
                        description = message.message,
                        acceptListener = onCacheClear
                    )
                }
                AppArrowItem(
                    title = lib.toolkit.base.R.string.settings_fonts_title,
                    dividerVisible = false,
                    onClick = onFontsClick,
                    option = settingsState.fonts.size.toString()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    with(requireContext().getSystemService(LocaleManager::class.java)) {
                        val currentAppLocale = applicationLocales.get(0) ?: systemLocales.get(0)
                        AppArrowItem(
                            title = R.string.settings_language,
                            option = currentAppLocale.displayLanguage.capitalize(currentAppLocale),
                            dividerVisible = false,
                            onClick = onLocaleClick
                        )
                    }
                }
                AppArrowItem(
                    title = R.string.app_settings_color_theme,
                    option = getThemeString(settingsState.themeMode)?.let { stringResource(id = it) },
                    dividerVisible = false,
                    onClick = onThemeClick
                )
                AppArrowItem(
                    title = R.string.about_title,
                    arrowVisible = true,
                    dividerVisible = false,
                    onClick = onAboutClick
                )
                AppArrowItem(
                    title = R.string.whats_new_title,
                    arrowVisible = true,
                    dividerVisible = false,
                    onClick = {
                        WhatsNewDialog.show(requireActivity())
                    }
                )
                AppArrowItem(
                    title = R.string.app_settings_main_help,
                    dividerVisible = false,
                    arrowVisible = false
                ) {
                    showUrlInBrowser(getString(R.string.app_url_help))
                }
                AppArrowItem(
                    title = lib.toolkit.base.R.string.about_feedback,
                    dividerVisible = false,
                    arrowVisible = false
                ) {
                    ActivitiesUtils.sendFeedbackEmail(context, "")
                }
            }
        }
    }

    @Composable
    fun ThemeScreen() {
        val state by viewModel.settingsState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colors.background)
        ) {
            AppSelectItem(
                title = stringResource(id = R.string.app_settings_follow_the_system),
                selected = state.themeMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                dividerVisible = false,
                onClick = {
                    viewModel.setThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            )
            AppSelectItem(
                title = stringResource(id = R.string.app_settings_light_theme),
                selected = state.themeMode == AppCompatDelegate.MODE_NIGHT_NO,
                dividerVisible = false,
                onClick = {
                    viewModel.setThemeMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            )
            AppSelectItem(
                title = stringResource(id = R.string.app_settings_dark_theme),
                selected = state.themeMode == AppCompatDelegate.MODE_NIGHT_YES,
                dividerVisible = false,
                onClick = {
                    viewModel.setThemeMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            )
        }
    }

    private fun getThemeString(mode: Int) = when (mode) {
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> R.string.app_settings_follow_the_system
        AppCompatDelegate.MODE_NIGHT_NO -> R.string.app_settings_light_theme
        AppCompatDelegate.MODE_NIGHT_YES -> R.string.app_settings_dark_theme
        else -> null
    }

    //    @Previews.All
    @Composable
    private fun Preview() {
        ManagerTheme {
            SettingsScreen(
                context = LocalContext.current,
                settingsState = AppSettingsState(
                    cache = 100000L,
                    themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                    analytics = false,
                    wifi = true,
                    passcodeEnabled = true
                ),
                onThemeClick = {},
                onWifiState = {},
                onAnalytics = {},
                onPasscodeClick = {},
                onLocaleClick = {},
                onAboutClick = {},
                onCacheClear = {},
                onFontsClick = {}
            )
        }
    }
}

@Composable
private fun FontsScreen(fonts: List<File>, onBack: () -> Unit, onFontClick: (File) -> Unit = {}) {
    AppScaffold {
        if (fonts.isEmpty()) {
            PlaceholderView(
                image = lib.toolkit.base.R.drawable.placeholder_empty_folder,
                title = stringResource(id = R.string.placeholder_no_fonts),
                subtitle = ""
            )
        } else {
            LazyColumn {
                items(items = fonts, key = { it.name }) { font ->
                    AppListItem(
                        modifier = Modifier.animateItem(),
                        title = font.nameWithoutExtension,
                        dividerVisible = false,
                        onClick = { onFontClick.invoke(font) }
                    )
                }
            }
        }
    }
}