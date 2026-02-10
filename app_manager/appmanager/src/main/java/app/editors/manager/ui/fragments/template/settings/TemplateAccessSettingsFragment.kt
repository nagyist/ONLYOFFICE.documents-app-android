package app.editors.manager.ui.fragments.template.settings

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import app.editors.manager.viewModels.main.TemplateSettingsMode
import lib.compose.ui.fragments.ComposeDialogFragment
import lib.compose.ui.theme.ManagerTheme
import lib.toolkit.base.managers.utils.putArgs

class TemplateAccessSettingsFragment : ComposeDialogFragment() {

    private val templateId: String
        get() = arguments?.getString(KEY_TEMPLATE_ID).orEmpty()

    override val fragmentResultKey: String
        get() = KEY_FRAGMENT_RESULT

    @Composable
    override fun Content() {
        ManagerTheme {
            AccessSettingsDestination(
                templateId = templateId,
                modeId = TemplateSettingsMode.MODE_EDIT_TEMPLATE,
                initSettings = null,
                showSnackbar = ::showSnackbar,
                saveSettings = true,
                onSavedSuccessfully = {
                    setResultMsg()
                    dismiss()
                },
                onClose = ::dismiss
            )
        }
    }

    private fun setResultMsg() {
        parentFragmentManager.setFragmentResult(
            KEY_FRAGMENT_RESULT,
            bundleOf()
        )
    }

    companion object {
        private val TAG: String = TemplateAccessSettingsFragment::class.java.simpleName
        private const val KEY_TEMPLATE_ID = "key_template_id"
        const val KEY_FRAGMENT_RESULT = "TemplateAccessSettingsFragmentResult"

        fun show(fragmentManager: FragmentManager, templateId: String) {
            newInstance(templateId).show(fragmentManager, TAG)
        }

        private fun newInstance(templateId: String) =
            TemplateAccessSettingsFragment().putArgs(KEY_TEMPLATE_ID to templateId)
    }
}