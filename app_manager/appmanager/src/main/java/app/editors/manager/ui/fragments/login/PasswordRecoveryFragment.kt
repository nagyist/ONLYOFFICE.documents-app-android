package app.editors.manager.ui.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import app.editors.manager.BuildConfig
import app.editors.manager.R
import app.editors.manager.databinding.FragmentLoginPasswordRecoveryBinding
import app.editors.manager.mvp.presenters.login.PasswordRecoveryPresenter
import app.editors.manager.mvp.views.login.PasswordRecoveryView
import app.editors.manager.ui.fragments.base.BaseAppFragment
import app.editors.manager.ui.views.edits.BaseWatcher
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import lib.toolkit.base.managers.utils.StringUtils.isEmailValid
import lib.toolkit.base.managers.utils.putArgs
import moxy.presenter.InjectPresenter

class PasswordRecoveryFragment : BaseAppFragment(), PasswordRecoveryView {

    companion object {
        var TAG: String = PasswordRecoveryFragment::class.java.simpleName

        private const val KEY_EMAIL = "KEY_EMAIL"
        private const val KEY_PORTAL = "KEY_PORTAL"
        private const val KEY_LDAP = "KEY_LDAP"

        fun newInstance(email: String?, portal: String?, ldap: Boolean = false): PasswordRecoveryFragment {
            return PasswordRecoveryFragment().putArgs(
                KEY_EMAIL to email,
                KEY_PORTAL to portal,
                KEY_LDAP to ldap
            )
        }
    }

    @InjectPresenter
    lateinit var presenter: PasswordRecoveryPresenter

    private var viewBinding: FragmentLoginPasswordRecoveryBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        viewBinding = FragmentLoginPasswordRecoveryBinding.inflate(inflater)
        return viewBinding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    private fun init() {
        viewBinding?.apply {
            if (arguments?.getBoolean(KEY_LDAP) == true) {
                loginPasswordRecoveryEmailLayout.setHint(R.string.profile_username_title)
            }
            loginPasswordRecoveryEmailEdit.apply {
                setText(arguments?.getString(KEY_EMAIL))
                addTextChangedListener(FieldsWatcher())
                setOnEditorActionListener { _, actionId, _ ->
                    actionKeyPress(actionId)
                }
            }
            loginPasswordRecoveryButton.apply {
                if (arguments?.getString(KEY_EMAIL)?.isEmpty() == true) {
                    this.isEnabled = false
                }
                setOnClickListener {
                    onRecoverButtonClick()
                }
            }
            loginPasswordReturnButton.setOnClickListener { activity?.onBackPressed() }
        }
        setActionBarTitle(context?.getString(R.string.login_password_recovery_toolbar_title))
    }

    private fun onRecoverButtonClick() {
        val email = viewBinding?.loginPasswordRecoveryEmailEdit?.text.toString().trim()

        if (!isEmailValid(email)) {
            onEmailError()
            return
        }

        verifyWithHCaptcha(email)
    }

    private fun verifyWithHCaptcha(email: String) {
        val hCaptcha = HCaptcha.getClient(requireActivity())

        hCaptcha
            .addOnSuccessListener { result ->
                if (!result.tokenResult.isNullOrEmpty()) {
                    presenter.recoverPassword(email, result.tokenResult)
                } else {
                    onError(getString(R.string.errors_unknown_error))
                }
            }
            .addOnFailureListener { error ->
                onError(error.message)
            }

        val siteKey = if (arguments?.getString(KEY_PORTAL)?.endsWith(".info") == true) {
            BuildConfig.CAPTCHA_PUBLIC_KEY_INFO
        } else {
            BuildConfig.CAPTCHA_PUBLIC_KEY_COM
        }

        val config = HCaptchaConfig.builder()
            .siteKey(siteKey)
            .loading(true)
            .build()

        hCaptcha.setup(config).verifyWithHCaptcha()
    }

    override fun onPasswordRecoverySuccess(email: String) {
        viewBinding?.apply {
            loginPasswordRecoveryEmailLayout.visibility = View.INVISIBLE
            loginPasswordRecoveryHint.text = getString(R.string.login_password_recovery_success_hint, email)
            loginPasswordRecoveryImage.isVisible = true
            loginPasswordRecoveryButton.isVisible = false
            loginPasswordReturnButton.isVisible = true
        }
    }

    override fun onEmailError() {
        hideDialog()
        viewBinding?.loginPasswordRecoveryEmailLayout?.error =
            context?.getString(R.string.errors_email_syntax_error)
    }

    override fun onError(message: String?) {
        message?.let { showSnackBar(it) }
    }

    fun actionKeyPress(actionId: Int): Boolean {
        if (actionId == EditorInfo.IME_ACTION_NEXT) {
            onRecoverButtonClick()
            hideKeyboard()
            return true
        }
        return false
    }

    private inner class FieldsWatcher : BaseWatcher() {
        override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
            viewBinding?.loginPasswordRecoveryEmailLayout?.isErrorEnabled = false
            val email = viewBinding?.loginPasswordRecoveryEmailEdit?.text.toString()
            viewBinding?.loginPasswordRecoveryButton?.isEnabled = "" != email
        }
    }
}