package app.editors.manager.ui.fragments.template.createroom

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.editors.manager.ui.fragments.template.settings.TemplateSettingsScreen
import app.editors.manager.viewModels.main.RoomFromTemplateViewModel
import app.editors.manager.viewModels.main.TemplateSettingsMode
import app.editors.manager.viewModels.main.TemplateSettingsViewModel
import kotlinx.serialization.Serializable
import lib.compose.ui.fragments.ComposeDialogFragment
import lib.compose.ui.theme.ManagerTheme
import lib.compose.ui.utils.popBackStackWhenResumed
import lib.toolkit.base.managers.utils.putArgs

class RoomFromTemplateFragment : ComposeDialogFragment() {

    private val templateId: String?
        get() = arguments?.getString(KEY_TEMPLATE_ID)

    override val fragmentResultKey: String
        get() = KEY_FRAGMENT_RESULT

    @Composable
    override fun Content() {
        ManagerTheme {
            val navController = rememberNavController()
            val startDestination = templateId?.let { Screens.Creation(it) }
                ?: Screens.TemplatesList

            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable<Screens.TemplatesList> {
                    val viewModel = viewModel<RoomFromTemplateViewModel>(
                        factory = RoomFromTemplateViewModel.Factory
                    )
                    RoomFromTemplateScreen(
                        viewModel = viewModel,
                        onTemplateClick = { id -> navController.navigate(Screens.Creation(id)) },
                        onBack = ::dismiss
                    )
                }

                composable<Screens.Creation> { backStackEntry ->
                    val id = backStackEntry.toRoute<Screens.Creation>().templateId
                    val viewModel = viewModel<TemplateSettingsViewModel>(
                        factory = TemplateSettingsViewModel.factory(
                            templateId = id,
                            modeId = TemplateSettingsMode.MODE_CREATE_ROOM
                        )
                    )

                    TemplateSettingsScreen(
                        viewModel = viewModel,
                        showSnackbar = ::showSnackbar,
                        navigateToAccessSettings = {},
                        navigateToCreated = { roomId, roomType, _ ->
                            setResult(roomId, roomType)
                            dismiss()
                        },
                        onBack = {
                            if (templateId == null) {
                                navController.popBackStackWhenResumed()
                            } else {
                                dismiss()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun setResult(id: String?, roomType: Int?) {
        parentFragmentManager.setFragmentResult(
            KEY_FRAGMENT_RESULT,
            bundleOf(
                KEY_SAVED_ID to id,
                KEY_SAVED_ROOM_TYPE to roomType
            )
        )
    }

    sealed interface Screens {
        @Serializable
        data object TemplatesList : Screens

        @Serializable
        data class Creation(val templateId: String) : Screens
    }

    companion object {
        private val TAG: String = RoomFromTemplateFragment::class.java.simpleName
        private const val KEY_TEMPLATE_ID = "key_template_id"
        const val KEY_FRAGMENT_RESULT = "RoomFromTemplateFragmentResult"
        const val KEY_SAVED_ID = "key_saved_room_id"
        const val KEY_SAVED_ROOM_TYPE = "key_saved_room_type"

        fun show(fragmentManager: FragmentManager, templateId: String?) {
            newInstance(templateId).show(fragmentManager, TAG)
        }

        private fun newInstance(templateId: String?) =
            RoomFromTemplateFragment().putArgs(KEY_TEMPLATE_ID to templateId)
    }
}