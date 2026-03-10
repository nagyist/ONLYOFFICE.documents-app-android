package app.editors.manager.mvp.presenters.login

import app.documents.core.network.common.NetworkResult
import app.editors.manager.app.App
import app.editors.manager.mvp.views.login.PasswordRecoveryView
import kotlinx.coroutines.launch
import moxy.InjectViewState
import moxy.presenterScope

@InjectViewState
class PasswordRecoveryPresenter : BaseLoginPresenter<PasswordRecoveryView>() {

    companion object {
        val TAG: String = PasswordRecoveryPresenter::class.java.simpleName
    }

    init {
        App.getApp().appComponent.inject(this)
    }

    fun recoverPassword(email: String, recaptchaResponse: String) {
        signInJob = presenterScope.launch {
            loginRepository.passwordRecovery(email, recaptchaResponse).collect { result ->
                when (result) {
                    is NetworkResult.Success -> viewState.onPasswordRecoverySuccess(email)
                    is NetworkResult.Error -> fetchError(result.exception)
                    is NetworkResult.Loading -> Unit
                }
            }
        }
    }
}