package app.editors.manager.mvp.presenters.share

import app.documents.core.account.CloudAccount
import app.documents.core.network.ApiContract
import app.documents.core.network.models.share.request.RequestShare
import app.documents.core.network.models.share.request.RequestShareItem
import app.documents.core.share.ShareService
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.app.appComponent
import app.editors.manager.app.getShareApi
import app.editors.manager.managers.utils.GlideUtils
import app.editors.manager.mvp.models.explorer.CloudFile
import app.editors.manager.mvp.models.explorer.CloudFolder
import app.editors.manager.mvp.models.explorer.Item
import app.editors.manager.mvp.models.models.ModelShareStack
import app.editors.manager.mvp.models.ui.GroupUi
import app.editors.manager.mvp.models.ui.ShareHeaderUi
import app.editors.manager.mvp.models.ui.UserUi
import app.editors.manager.mvp.presenters.base.BasePresenter
import app.editors.manager.mvp.views.share.AddView
import app.editors.manager.ui.fragments.share.AddFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import lib.toolkit.base.ui.adapters.holder.ViewType
import moxy.InjectViewState

@InjectViewState
class AddPresenter : BasePresenter<AddView>() {

    companion object {
        val TAG: String = AddPresenter::class.java.simpleName
    }

    private lateinit var item: Item
    private lateinit var type: AddFragment.Type
    private val shareStack: ModelShareStack = ModelShareStack.getInstance()
    private var isCommon: Boolean = false
    private var searchValue: String? = null

    private var disposable: Disposable? = null

    init {
        App.getApp().appComponent.inject(this)
    }

    private val account: CloudAccount =
        context.appComponent.accountOnline ?: throw RuntimeException("Account can't be null")
    private val shareApi: ShareService = context.getShareApi()

    override fun onDestroy() {
        super.onDestroy()
        disposable = null
    }


    private fun getUsers() {
        isCommon = false
        disposable = shareApi.getUsers()
            .subscribeOn(Schedulers.io())
            .map { response ->
                response.response.filter { it.id != account.id }.map {
                    UserUi(it.id, it.department, it.displayName, GlideUtils.loadAvatar(it.avatarSmall))
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                shareStack.addUsers(response)
                viewState.onGetUsers(userListItems)
            }, { error ->
                fetchError(error)
            })
    }

    private fun getGroups() {
        isCommon = false
        disposable = shareApi.getGroups()
            .subscribeOn(Schedulers.io())
            .map { response -> response.response.map { GroupUi(it.id, it.name, it.manager ?: "null") } }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                shareStack.addGroups(response)
                viewState.onGetGroups(groupListItems)
            }, { error ->
                fetchError(error)
            })
    }

    fun getCommons() {
        isCommon = true
        disposable = Observable.zip(shareApi.getUsers(), shareApi.getGroups()) { users, groups ->
            shareStack.addGroups(groups.response.map { GroupUi(it.id, it.name, it.manager ?: "null") })
            shareStack.addUsers(users.response.filter { it.id != account.id }.map {
                UserUi(it.id, it.department, it.displayName, GlideUtils.loadAvatar(it.avatarMedium))
            })
            return@zip true
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                viewState.onGetCommon(commonList)
            }, { error ->
                fetchError(error)
            })
    }

    private fun getFilter(searchValue: String) {
        isCommon = true

        disposable = Observable.zip(shareApi.getUsers(getOptions(searchValue)),
            shareApi.getGroups(getOptions(searchValue, true))) { users, groups ->
            shareStack.clearModel()
            shareStack.addGroups(groups.response.map { GroupUi(it.id, it.name, it.manager ?: "null") })
            shareStack.addUsers(users.response.filter { it.id != account.id }.map {
                UserUi(it.id, it.department, it.displayName, GlideUtils.loadAvatar(it.avatarMedium))
            })
            return@zip true
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                viewState.onGetCommon(commonList)
            }, { error ->
                fetchError(error)
            })
    }

    private fun shareFileTo(id: String) {
        requestShare?.let { request ->
            disposable = shareApi.setFileAccess(id, request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    shareStack.resetChecked()
                    viewState.onSuccessAdd()
                }
        }

    }

    private fun shareFolderTo(id: String) {
        requestShare?.let { request ->
            disposable = shareApi.setFolderAccess(id, request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    shareStack.resetChecked()
                    viewState.onSuccessAdd()
                }
        }
    }

    private val requestShare: RequestShare?
        get() {
            val shareItems: MutableList<RequestShareItem> = ArrayList()

            // Get users access list
            for (item in shareStack.userSet) {
                if (item.isSelected) {
                    val idItem = item.id
                    val accessCode = shareStack.accessCode
                    shareItems.add(RequestShareItem(shareTo = idItem, access = accessCode.toString()))
                }
            }

            // Get groups access list
            for (item in shareStack.groupSet) {
                if (item.isSelected) {
                    val idItem = item.id
                    val accessCode = shareStack.accessCode
                    shareItems.add(RequestShareItem(shareTo = idItem, access = accessCode.toString()))
                }
            }
            if (shareItems.isNotEmpty()) {
                val message = shareStack.message
                return RequestShare(
                    share = shareItems,
                    isNotify = !message.isNullOrEmpty(),
                    sharingMessage = message ?: ""
                )
            }
            return null
        }

    private val commonList: List<ViewType>
        get() {
            val commonList: MutableList<ViewType> = ArrayList()

            // Users
            if (!shareStack.isUserEmpty) {
                commonList.add(ShareHeaderUi(context.getString(R.string.share_add_common_header_users)))
                commonList.addAll(userListItems)
            }

            // Groups
            if (!shareStack.isGroupEmpty) {
                commonList.add(ShareHeaderUi(context.getString(R.string.share_add_common_header_groups)))
                commonList.addAll(groupListItems)
            }
            return commonList
        }

    private fun getOptions(value: String, isGroup: Boolean = false): Map<String, String> =
        mapOf(
            ApiContract.Parameters.ARG_FILTER_VALUE to value,
            ApiContract.Parameters.ARG_FILTER_BY to if (isGroup) ApiContract.Parameters.VAL_SORT_BY_NAME else ApiContract.Parameters.VAL_SORT_BY_DISPLAY_NAME,
            ApiContract.Parameters.ARG_FILTER_OP to ApiContract.Parameters.VAL_FILTER_OP_CONTAINS
        )

    val shared: Unit
        get() {
            when (type) {
                AddFragment.Type.USERS -> getUsers()
                AddFragment.Type.GROUPS -> getGroups()
            }
        }

    fun shareItem() {
        if (item is CloudFolder) {
            shareFolderTo(item.id)
        } else if (item is CloudFile) {
            shareFileTo(item.id)
        }
    }

    /*
    * Update states
    * */
    fun updateTypeSharedListState() {
        when (type) {
            AddFragment.Type.USERS -> viewState.onGetUsers(userListItems)
            AddFragment.Type.GROUPS -> viewState.onGetGroups(groupListItems)
        }
    }

    fun updateCommonSharedListState() {
        viewState.onGetCommon(commonList)
    }

    fun updateSearchState() {
        viewState.onSearchValue(searchValue)
    }

    /*
    * Getters/Setters
    * */
    fun setItem(item: Item) {
        this.item = item
    }

    fun setType(type: AddFragment.Type) {
        this.type = type
    }


    private val userListItems: List<ViewType>
        get() = shareStack.userSet.toMutableList().sortedBy { it.displayName }
    private val groupListItems: List<ViewType>
        get() = shareStack.groupSet.toMutableList().sortedBy { it.name }

    val countChecked: Int
        get() = shareStack.countChecked

    fun resetChecked() {
        shareStack.resetChecked()
    }

    var accessCode: Int
        get() = shareStack.accessCode
        set(accessCode) {
            shareStack.accessCode = accessCode
        }

    fun setMessage(message: String?) {
        shareStack.message = message
    }

    fun setSearchValue(searchValue: String?) {
        this.searchValue = searchValue

        searchValue?.let { value ->
            getFilter(value)
        }
    }
}