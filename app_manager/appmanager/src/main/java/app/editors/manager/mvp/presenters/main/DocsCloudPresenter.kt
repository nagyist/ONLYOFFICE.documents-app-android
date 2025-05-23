package app.editors.manager.mvp.presenters.main

import android.annotation.SuppressLint
import android.net.Uri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.documents.core.model.cloud.Access
import app.documents.core.model.cloud.CloudAccount
import app.documents.core.model.cloud.Recent
import app.documents.core.model.cloud.isDocSpace
import app.documents.core.network.common.NetworkClient
import app.documents.core.network.common.Result
import app.documents.core.network.common.contracts.ApiContract
import app.documents.core.network.common.extensions.request
import app.documents.core.network.common.models.BaseResponse.Companion.KEY_RESPONSE
import app.documents.core.network.manager.ManagerService
import app.documents.core.network.manager.models.explorer.CloudFile
import app.documents.core.network.manager.models.explorer.CloudFolder
import app.documents.core.network.manager.models.explorer.Current
import app.documents.core.network.manager.models.explorer.Explorer
import app.documents.core.network.manager.models.explorer.Item
import app.documents.core.network.manager.models.explorer.isFavorite
import app.documents.core.network.manager.models.request.RequestBatchOperation
import app.documents.core.network.manager.models.request.RequestCreate
import app.documents.core.network.manager.models.request.RequestDeleteShare
import app.documents.core.network.manager.models.request.RequestFavorites
import app.documents.core.network.share.models.request.RequestRoomShare
import app.documents.core.network.share.models.request.UserIdInvitation
import app.documents.core.providers.CloudFileProvider
import app.documents.core.providers.CloudFileProvider.Companion.STATIC_DOC_URL
import app.documents.core.providers.CloudFileProvider.RoomCallback
import app.documents.core.providers.RoomProvider
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.app.accountOnline
import app.editors.manager.app.api
import app.editors.manager.app.cloudFileProvider
import app.editors.manager.app.roomApi
import app.editors.manager.app.roomProvider
import app.editors.manager.app.shareApi
import app.editors.manager.managers.receivers.DownloadReceiver
import app.editors.manager.managers.receivers.DownloadReceiver.OnDownloadListener
import app.editors.manager.managers.receivers.RoomDuplicateReceiver
import app.editors.manager.managers.receivers.UploadReceiver
import app.editors.manager.managers.receivers.UploadReceiver.OnUploadListener
import app.editors.manager.managers.utils.FirebaseUtils
import app.editors.manager.managers.works.RoomDuplicateWork
import app.editors.manager.mvp.models.filter.Filter
import app.editors.manager.mvp.models.list.RecentViaLink
import app.editors.manager.mvp.models.models.OpenDataModel
import app.editors.manager.mvp.models.states.OperationsState
import app.editors.manager.mvp.views.main.DocsCloudView
import app.editors.manager.ui.dialogs.MoveCopyDialog
import app.editors.manager.ui.fragments.main.DocsRoomFragment.Companion.TAG_PROTECTED_ROOM_DOWNLOAD
import app.editors.manager.ui.fragments.main.DocsRoomFragment.Companion.TAG_PROTECTED_ROOM_OPEN_FOLDER
import app.editors.manager.ui.fragments.main.DocsRoomFragment.Companion.TAG_PROTECTED_ROOM_SHOW_INFO
import app.editors.manager.ui.views.custom.PlaceholderViews
import app.editors.manager.viewModels.main.CopyItems
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lib.toolkit.base.managers.tools.LocalContentTools
import lib.toolkit.base.managers.utils.AccountUtils
import lib.toolkit.base.managers.utils.ContentResolverUtils
import lib.toolkit.base.managers.utils.EditType
import lib.toolkit.base.managers.utils.FileUtils
import lib.toolkit.base.managers.utils.KeyboardUtils
import lib.toolkit.base.managers.utils.StringUtils
import moxy.InjectViewState
import moxy.presenterScope
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@InjectViewState
class DocsCloudPresenter(private val account: CloudAccount) : DocsBasePresenter<DocsCloudView>(),
    OnDownloadListener, OnUploadListener, RoomDuplicateReceiver.Listener {

    private val downloadReceiver: DownloadReceiver = DownloadReceiver()
    private val uploadReceiver: UploadReceiver = UploadReceiver()
    private var duplicateRoomReceiver: RoomDuplicateReceiver = RoomDuplicateReceiver()

    private var api: ManagerService? = null
    private var roomProvider: RoomProvider? = null

    private var conversionJob: Job? = null

    init {
        App.getApp().appComponent.inject(this)
        api = context.api
        roomProvider = context.roomProvider
        fileProvider = context.cloudFileProvider.apply {
            roomCallback = object : RoomCallback {

                override fun isRoomRoot(id: String?): Boolean {
                    val parts = modelExplorerStack.last()?.pathParts.orEmpty()
                    return if (parts.isNotEmpty()) isRoom && parts[0].id == id else false
                }

                override fun isArchive(): Boolean = ApiContract.SectionType.isArchive(currentSectionType)

                override fun isRecent(): Boolean {
                    return modelExplorerStack.rootFolderType == ApiContract.SectionType.CLOUD_RECENT
                }
            }
        }

        if (folderId != null) {
            modelExplorerStack.addStack(Explorer(current = Current().apply { id = folderId!! }))
        }
    }

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        downloadReceiver.setOnDownloadListener(this)
        uploadReceiver.setOnUploadListener(this)
        duplicateRoomReceiver.setListener(this)
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(duplicateRoomReceiver, RoomDuplicateReceiver.getFilters())
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(uploadReceiver, uploadReceiver.filter)
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(downloadReceiver, downloadReceiver.filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        interruptConversion()
        downloadReceiver.setOnDownloadListener(null)
        uploadReceiver.setOnUploadListener(null)
        duplicateRoomReceiver.setListener(null)
        LocalBroadcastManager.getInstance(context).unregisterReceiver(uploadReceiver)
        LocalBroadcastManager.getInstance(context).unregisterReceiver(duplicateRoomReceiver)
        LocalBroadcastManager.getInstance(context).unregisterReceiver(downloadReceiver)
    }

    override fun onItemClick(item: Item, position: Int) {
        onClickEvent(item, position)
        itemClicked?.let { itemClicked ->
            if (isSelectionMode) {
                val pickerMode = this.pickerMode
                if (pickerMode is PickerMode.Files) {
                    if (itemClicked is CloudFolder) {
                        openFolder(itemClicked.id, position)
                    } else if (itemClicked is CloudFile) {
                        if (itemClicked.isPdfForm) pickerMode.selectId(itemClicked.id)
                        modelExplorerStack.setSelectById(item, !itemClicked.isSelected)
                        viewState.onStateUpdateSelection(true)
                        viewState.onItemSelected(
                            position,
                            pickerMode.selectedIds.size.toString()
                        )
                    }
                    return
                }
                modelExplorerStack.setSelectById(item, !itemClicked.isSelected)
                if (!isSelectedItemsEmpty) {
                    viewState.onStateUpdateSelection(true)
                    viewState.onItemSelected(
                        position,
                        modelExplorerStack.countSelectedItems.toString()
                    )
                }
            } else if (isTrashMode && currentSectionType != ApiContract.SectionType.CLOUD_ARCHIVE_ROOM) {
                viewState.onSnackBarWithAction(
                    context.getString(R.string.trash_snackbar_move_text),
                    context.getString(R.string.trash_snackbar_move_button)
                ) { moveCopySelected(OperationsState.OperationType.RESTORE) }
            } else {
                if (itemClicked is CloudFolder) {
                    if (itemClicked.isRoom && itemClicked.passwordProtected) {
                        viewState.onRoomViaLinkPasswordRequired(false, TAG_PROTECTED_ROOM_OPEN_FOLDER)
                    } else {
                        openFolder(itemClicked.id, position)
                    }
                } else if (itemClicked is CloudFile) {
                    if (LocalContentTools.isOpenFormat(itemClicked.clearExt)) {
                        viewState.onConversionQuestion()
                    } else {
                        getFileInfo()
                    }
                } else if (itemClicked is RecentViaLink) {
                    openRecentViaLink()
                }
            }
        }
    }

    override fun copy(): Boolean {
        if (!checkFillFormsRoom()) {
            return false
        }

        if (pickerMode is PickerMode.Files || super.copy()) {
            checkMoveCopyFiles(MoveCopyDialog.ACTION_COPY)
            return true
        }

        return false
    }

    override fun move(): Boolean {
        if (!checkFillFormsRoom()) {
            return false
        }

        return if (super.move()) {
            checkMoveCopyFiles(MoveCopyDialog.ACTION_MOVE)
            true
        } else {
            false
        }
    }

    fun copyFilesToCurrent() {
        (fileProvider as? CloudFileProvider)?.let { provider ->
            val pickerMode = this.pickerMode
            if (pickerMode is PickerMode.Files) {
                val request = RequestBatchOperation(destFolderId = pickerMode.destFolderId).apply {
                    fileIds = pickerMode.selectedIds
                }
                disposable.add(provider.copyFiles(request).subscribe({ onBatchOperations() }, ::fetchError))
            }
        }
    }

    private fun checkFillFormsRoom(): Boolean {
        val explorer = operationStack?.explorer ?: return false
        if (roomClicked?.roomType == ApiContract.RoomType.FILL_FORMS_ROOM) {
            if (explorer.folders.isNotEmpty() || explorer.files.any { !it.isPdfForm }) {
                viewState.onDialogWarning(
                    context.getString(R.string.dialogs_warning_title),
                    context.getString(R.string.dialogs_warning_only_pdf_form_message),
                    null
                )
                return false
            }
        }
        return true
    }

    override fun getNextList() {
        val id = modelExplorerStack.currentId
        val loadPosition = modelExplorerStack.loadPosition
        if (id != null && loadPosition > 0) {
            val args = getArgs(filteringValue).toMutableMap()
            args[ApiContract.Parameters.ARG_START_INDEX] = loadPosition.toString()
            fileProvider?.let { provider ->
                disposable.add(provider.getFiles(id, args.putFilters()).subscribe({ explorer: Explorer? ->
                    modelExplorerStack.addOnNext(explorer)
                    val last = modelExplorerStack.last()
                    if (last != null) {
                        viewState.onDocsNext(getListWithHeaders(last, true))
                    }
                }) { throwable: Throwable -> fetchError(throwable) })
            }
        }
    }

    override fun createDocs(title: String) {
        FirebaseUtils.addAnalyticsCreateEntity(
            account.portalUrl,
            true,
            StringUtils.getExtensionFromPath(title)
        )

        modelExplorerStack.currentId?.let { id ->
            val requestCreate = RequestCreate()
            requestCreate.title = title
            fileProvider?.let { provider ->
                disposable.add(
                    provider.createFile(id, requestCreate).subscribe({ cloudFile ->
                        addFile(cloudFile)
                        addRecent(cloudFile)
                        viewState.onDialogClose()
                        onFileClickAction(cloudFile, EditType.EDIT)
                    }, ::fetchError))
            }
            showDialogWaiting(TAG_DIALOG_CANCEL_SINGLE_OPERATIONS)
        }
    }

    override fun getFileInfo() {
        val item = itemClicked
        if (item != null && item is CloudFile) {
            fileProvider?.let { provider ->
                disposable.add(
                    provider.fileInfo(item)
                        .subscribe({ onFileClickAction(item, editType = null) }, ::fetchError)
                )
            }
        }
    }

    override fun addRecent(file: CloudFile) {
        presenterScope.launch {
            recentDataSource.add(
                Recent(
                    fileId = file.id,
                    path = "",
                    name = file.title,
                    size = file.pureContentLength,
                    ownerId = account.id,
                    source = account.portalUrl
                )
            )
        }
    }

    override fun updateViewsState() {
        if (isSelectionMode) {
            viewState.onStateUpdateSelection(true)
            if (pickerMode is PickerMode.Files) {
                viewState.onActionBarTitle((pickerMode as PickerMode.Files).selectedIds.size.toString())
            } else {
                viewState.onActionBarTitle(modelExplorerStack.countSelectedItems.toString())
            }
            viewState.onStateAdapterRoot(modelExplorerStack.isNavigationRoot)
            viewState.onStateActionButton(false)
        } else if (isFilteringMode) {
            viewState.onActionBarTitle(context.getString(R.string.toolbar_menu_search_result))
            viewState.onStateUpdateFilter(true, filteringValue)
            viewState.onStateAdapterRoot(modelExplorerStack.isNavigationRoot)
            viewState.onStateActionButton(false)
        } else if (!modelExplorerStack.isRoot) {
            viewState.onStateAdapterRoot(false)
            viewState.onStateUpdateRoot(false)
            // TODO check security...
            if (isRoom) {
                viewState.onStateActionButton(modelExplorerStack.last()?.current?.security?.create == true)
            } else {
                viewState.onStateActionButton(isContextEditable && !isRecentViaLinkSection())
            }
            viewState.onActionBarTitle(currentTitle)
        } else {
            when {
                isTrashMode -> {
                    viewState.onStateActionButton(false)
                    viewState.onActionBarTitle("")
                }

                pickerMode == PickerMode.Folders -> {
                    if (isRoom && isRoot) {
                        viewState.onActionBarTitle(context.getString(R.string.operation_select_room_title))
                    } else {
                        viewState.onActionBarTitle(context.getString(R.string.operation_title))
                    }
                    viewState.onStateActionButton(false)
                }

                else -> {
                    viewState.onActionBarTitle("")
                    if (isRoom && modelExplorerStack.last()?.current?.security?.create == true) {
                        viewState.onStateActionButton(true)
                    } else {
                        viewState.onStateActionButton(isContextEditable)
                    }
                }
            }
            viewState.onStateAdapterRoot(true)
            viewState.onStateUpdateRoot(true)
        }
        viewState.onRoomLifetime(modelExplorerStack.last()?.current?.lifetime)
        viewState.onRoomFileIndexing(isIndexing)
    }

    override fun onActionClick() {
        viewState.onActionDialog(
            isRoot && (isUserSection || isCommonSection && isAdmin),
            !isVisitor || modelExplorerStack.last()?.current?.security?.create == true,
            roomClicked?.roomType
        )
    }

    /*
     * Loading callbacks
     * */
    override fun onDownloadError(info: String?) {
        viewState.onDialogClose()
        viewState.onSnackBar(info ?: context.getString(R.string.download_manager_error))
    }

    override fun onDownloadProgress(id: String?, total: Int, progress: Int) {
        viewState.onDialogProgress(total, progress)
    }

    override fun onDownloadComplete(
        id: String?,
        url: String?,
        title: String?,
        info: String?,
        path: String?,
        mime: String?,
        uri: Uri?,
    ) {
        viewState.onFinishDownload(uri)
        viewState.onDialogClose()
        viewState.onSnackBarWithAction("$info\n$title", context.getString(R.string.download_manager_open)) {
            uri?.let(::showDownloadFolderActivity)
        }
    }

    override fun onDownloadCanceled(id: String?, info: String?) {
        viewState.onDialogClose()
        viewState.onSnackBar(info)
    }

    override fun onDownloadRepeat(id: String?, title: String?, info: String?) {
        viewState.onDialogClose()
        viewState.onSnackBar(info)
    }

    override fun onUploadError(path: String?, info: String?, file: String?) {
        viewState.onSnackBar(info)
    }

    override fun onUploadErrorDialog(title: String, message: String, file: String?) {
        viewState.onDialogWarning(title, message, null)
    }

    override fun onHideDuplicateNotification(workerId: String?) {
        WorkManager
            .getInstance(context)
            .cancelWorkById(UUID.fromString(workerId))
    }

    override fun onDuplicateComplete() {
        if (isRoom && isRoot) refresh()
    }

    override fun onUploadComplete(
        path: String?,
        info: String?,
        title: String?,
        file: CloudFile?,
        id: String?,
    ) {
        if (modelExplorerStack.currentId == file?.folderId) {
            addFile(file)
        }
        viewState.onSnackBar(info)
    }

    override fun onUploadAndOpen(path: String?, title: String?, file: CloudFile?, id: String?) {
        viewState.onFileWebView(checkNotNull(file))
    }

    override fun onUploadFileProgress(progress: Int, id: String?, folderId: String?) {
        // Nothing
    }

    override fun onUploadCanceled(path: String?, info: String?, id: String?) {
        viewState.onSnackBar(info)
    }

    override fun onUploadRepeat(path: String?, info: String?) {
        viewState.onDialogClose()
        viewState.onSnackBar(info)
    }

    override fun getBackStack(): Boolean {
        val backStackResult = super.getBackStack()
        if (modelExplorerStack.last()?.filterType != preferenceTool.filter.type.filterVal) {
            refresh()
        }
        return backStackResult
    }

    override fun openFolder(id: String?, position: Int, roomType: Int?) {
        setFiltering(false)
        resetFilters()
        super.openFolder(id, position, roomType)
    }

    override fun createDownloadFile() {
        if (isRoom && isRoot && roomClicked?.passwordProtected == true) {
            viewState.onRoomViaLinkPasswordRequired(false, TAG_PROTECTED_ROOM_DOWNLOAD)
            return
        }
        super.createDownloadFile()
    }

    fun onContextClick(editType: EditType?) {
        when (val item = itemClicked) {
            is CloudFile -> {
                if (LocalContentTools.isOpenFormat(item.clearExt)) {
                    viewState.onConversionQuestion()
                    return
                }
                addRecent(item)
                onFileClickAction(item, editType)
            }
            is CloudFolder -> editRoom()
        }
    }

    fun saveExternalLinkToClipboard() {
        itemClicked?.let { item ->
            presenterScope.launch {
                request(
                    func = { context.shareApi.getShareFile(item.id) },
                    map = { response ->
                        response.response.find { it.sharedTo.shareLink.isNotEmpty() }?.sharedTo?.shareLink ?: ""
                    },
                    onSuccess = { externalLink ->
                        if (externalLink.isNotEmpty()) {
                            setDataToClipboard(externalLink)
                        } else {
                            viewState.onSnackBar(context.getString(R.string.share_access_denied))
                        }
                    }, onError = ::fetchError
                )
            }
        }
    }

    fun addToFavorite() {
        val requestFavorites = RequestFavorites()
        requestFavorites.fileIds = listOf(itemClicked?.id!!)
        (fileProvider as CloudFileProvider).let { provider ->
            val item = itemClicked
            if (item != null && item is CloudFile) {
                val isAdd = !item.isFavorite
                disposable.add(provider.addToFavorites(requestFavorites, isAdd)
                    .subscribe({
                        if (isAdd) {
                            item.fileStatus += ApiContract.FileStatus.FAVORITE
                        } else {
                            item.fileStatus -= ApiContract.FileStatus.FAVORITE
                        }
                        viewState.onUpdateFavoriteItem()
                        viewState.onSnackBar(
                            if (isAdd) {
                                context.getString(R.string.operation_add_to_favorites)
                            } else {
                                context.getString(R.string.operation_remove_from_favorites)
                            }
                        )
                    }) { throwable: Throwable -> fetchError(throwable) })
            }
        }
    }

    fun removeShareContext() {
        if (itemClicked != null) {
            val deleteShare = RequestDeleteShare()
            if (itemClicked is CloudFolder) {
                deleteShare.folderIds = ArrayList(listOf((itemClicked as CloudFolder).id))
            } else {
                deleteShare.fileIds = ArrayList(listOf(itemClicked!!.id))
            }
            disposable.add(Observable.fromCallable {
                api?.deleteShare(deleteShare)?.execute()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (itemClicked != null) {
                        modelExplorerStack.removeItemById(itemClicked?.id)
                    }
                    setPlaceholderType(
                        if (modelExplorerStack.isListEmpty)
                            PlaceholderViews.Type.EMPTY else PlaceholderViews.Type.NONE
                    )
                    viewState.onDocsGet(getListWithHeaders(modelExplorerStack.last(), true))
                    onBatchOperations()
                }) { throwable: Throwable -> fetchError(throwable) })
        }
    }

    fun emptyTrash() {
        val explorer = modelExplorerStack.last()
        if (explorer != null) {
            val provider = fileProvider as CloudFileProvider
            showDialogProgress(true, TAG_DIALOG_CANCEL_BATCH_OPERATIONS)
            batchDisposable = provider.clearTrash()
                .switchMap { status }
                .subscribe(
                    { progress: Int? ->
                        viewState.onDialogProgress(
                            FileUtils.LOAD_MAX_PROGRESS,
                            progress!!
                        )
                    },
                    { throwable: Throwable -> fetchError(throwable) }
                ) {
                    onBatchOperations()
                    refresh()
                }
        }
    }

    private fun setDataToClipboard(value: String) {
        KeyboardUtils.setDataToClipboard(
            context,
            value,
            context.getString(R.string.share_clipboard_external_link_label)
        )
        viewState.onSnackBar(context.getString(R.string.share_clipboard_external_copied))
    }

    private fun checkMoveCopyFiles(action: String) {
        val filesIds = (pickerMode as? PickerMode.Files)?.selectedIds ?: operationStack?.selectedFilesIds
        val foldersIds = operationStack?.selectedFoldersIds

        api?.let { api ->
            disposable.add(api.checkFiles(
                (pickerMode as? PickerMode.Files)?.destFolderId ?: destFolderId ?: "",
                foldersIds,
                filesIds
            )
                .map { it.response }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    when {
                        it.isNotEmpty() -> {
                            showMoveCopyDialog(it, action, modelExplorerStack.currentTitle)
                        }

                        action == MoveCopyDialog.ACTION_COPY -> {
                            transfer(ApiContract.Operation.DUPLICATE, false)
                        }

                        action == MoveCopyDialog.ACTION_MOVE -> {
                            transfer(ApiContract.Operation.DUPLICATE, true)
                        }
                    }
                }, ::fetchError)
            )
        }
    }

    private fun showMoveCopyDialog(files: List<CloudFile>, action: String, titleFolder: String) {
        val names = ArrayList<String>()
        for (file in files) {
            names.add(file.title)
        }
        viewState.showMoveCopyDialog(names, action, titleFolder)
    }

    private fun onFileClickAction(cloudFile: CloudFile, editType: EditType?) {
        if (cloudFile.isPdfForm && isUserSection && editType == null) {
            viewState.showFillFormChooserFragment()
            return
        }

        showDialogWaiting(TAG_DIALOG_CLEAR_DISPOSABLE)
        val extension = cloudFile.fileExst
        when (StringUtils.getExtension(extension)) {
            StringUtils.Extension.DOC,
            StringUtils.Extension.SHEET,
            StringUtils.Extension.PRESENTATION,
            StringUtils.Extension.FORM,
            StringUtils.Extension.PDF -> {
                checkSdkVersion { result ->
                    if (result) {
                        if (cloudFile.isPdfForm && editType == null) {
                            fillPdfForm()
                        } else {
                            openDocumentServer(
                                cloudFile = cloudFile,
                                canShareable = isItemShareable,
                                editType = if (LocalContentTools.isOpenFormat(cloudFile.clearExt) ||
                                    cloudFile.access == Access.Read
                                ) {
                                    EditType.VIEW
                                } else {
                                    editType
                                }
                            )
                        }
                    } else {
                        downloadTempFile(
                            cloudFile = cloudFile,
                            editType = if (cloudFile.access == Access.Read) {
                                EditType.VIEW
                            } else {
                                editType
                            }
                        )
                    }
                }
            }

            StringUtils.Extension.IMAGE, StringUtils.Extension.IMAGE_GIF, StringUtils.Extension.VIDEO_SUPPORT -> {
                addRecent(itemClicked as CloudFile)
                viewState.onFileMedia(getListMedia(cloudFile.id), false)
            }

            else -> viewState.onFileDownloadPermission()
        }
        FirebaseUtils.addAnalyticsOpenEntity(account.portalUrl, extension)
    }

    private fun openDocumentServer(cloudFile: CloudFile, canShareable: Boolean, editType: EditType?) {
        with(fileProvider as CloudFileProvider) {
            val token = AccountUtils.getToken(context, account.accountName)
            disposable.add(
                openDocument(cloudFile, token, canShareable, editType).subscribe({ result ->
                    viewState.onDialogClose()
                    if (result.isPdf) {
                        downloadTempFile(cloudFile, null)
                    } else if (result.info != null) {
                        viewState.onOpenDocumentServer(cloudFile, result.info, editType)
                    }
                }) { error ->
//                    if (error is HttpException && error.code() == 415) {
//                        downloadTempFile(cloudFile, EditType.VIEW)
//                    } else {
                        fetchError(error)
//                    }
                }
            )
        }
        addRecent(cloudFile)
    }

    private fun resetFilters() {
        preferenceTool.filter = Filter()
        viewState.onStateUpdateFilterMenu()
    }

    fun fillPdfForm() {
        showDialogWaiting(TAG_DIALOG_CLEAR_DISPOSABLE)
        val item = itemClicked
        if (item is CloudFile && item.isPdfForm) {
            checkSdkVersion { result ->
                if (result) {
                    openDocumentServer(item, canShareable = false, editType = EditType.FILL)
                } else {
                    downloadTempFile(item, editType = EditType.FILL)
                }
            }
        }
    }

    fun openFileById(id: String) {
        fileProvider?.let { provider ->
            disposable.add(
                provider.fileInfo(Item().apply { this.id = id })
                    .subscribe(
                        { file -> onFileClickAction(file, editType = null) },
                        ::fetchError
                    )
            )
        }
    }

    fun openFile(data: String) {
        val model = Json.decodeFromString<OpenDataModel>(data)
        if (model.file?.id == null && model.folder?.id != null) {
            openFolder(model.folder.id, 0)
            return
        }
        if (model.share.isNotEmpty()) {
            openFromLink(model)
            return
        }
        fileProvider?.let { provider ->
            disposable.add(provider.fileInfo(CloudFile().apply {
                id = model.file?.id.toString()
            }).subscribe({ cloudFile ->
                itemClicked = cloudFile
                onFileClickAction(cloudFile, editType = null)
            }, { error ->
                fetchError(error)
            }))
        }

    }

    fun openLocation() {
        resetFilters()
        if (folderId != itemFolderId) {
            setFiltering(false)
            modelExplorerStack.previous()?.let(modelExplorerStack::refreshStack)
            getItemsById(itemFolderId)
        } else if (isRoot) {
            getBackStack()
        }
    }

    /*
     * Getter/Setters for states
     * */
    private val isAdmin: Boolean
        get() = account.isAdmin

    private val isVisitor: Boolean
        get() = account.isVisitor

    /*
     * A&(B&(Cv(D&!E)))v((FvGvH)&D&!E)
     * */
    private val isContextEditable: Boolean
        get() = isUserSection || isCommonSection || isRoom && (isAdmin || isContextReadWrite && !isRoot) ||
                (isShareSection || isProjectsSection || isBunchSection) && isContextReadWrite && !isRoot

    /*
     * I&(!K&!F&!BvJ)
     * */
    val isContextItemEditable: Boolean
        get() = isContextEditable && (!isVisitor && !isShareSection || isCommonSection || isItemOwner)

    val isContextOwner: Boolean
        get() = StringUtils.equals(modelExplorerStack.currentFolderOwnerId, account.id)

    private val isContextReadWrite: Boolean
        get() = isContextOwner || modelExplorerStack.currentFolderAccess == Access.Read.code ||
                modelExplorerStack.currentFolderAccess == Access.None.code

    val isUserSection: Boolean
        get() = currentSectionType == ApiContract.SectionType.CLOUD_USER

    private val isShareSection: Boolean
        get() = currentSectionType == ApiContract.SectionType.CLOUD_SHARE

    private val isCommonSection: Boolean
        get() = currentSectionType == ApiContract.SectionType.CLOUD_COMMON

    private val isProjectsSection: Boolean
        get() = currentSectionType == ApiContract.SectionType.CLOUD_PROJECTS

    private val isBunchSection: Boolean
        get() = currentSectionType == ApiContract.SectionType.CLOUD_BUNCH

    private val isTrashSection: Boolean
        get() = currentSectionType == ApiContract.SectionType.CLOUD_TRASH ||
                currentSectionType == ApiContract.SectionType.CLOUD_ARCHIVE_ROOM

    private val isRoom: Boolean
        get() = currentSectionType > ApiContract.SectionType.CLOUD_PRIVATE_ROOM

    private val isClickedItemShared: Boolean
        get() = itemClicked?.shared == true

    private val isClickedItemFavorite: Boolean
        get() = itemClicked.isFavorite

    private val isItemOwner: Boolean
        get() = StringUtils.equals(itemClicked?.createdBy?.id, account.id)

    private val isItemReadWrite: Boolean
        get() = itemClicked?.access == Access.ReadWrite || isUserSection

    private val isItemEditable: Boolean
        get() = if (account.isDocSpace && currentSectionType == ApiContract.SectionType.CLOUD_VIRTUAL_ROOM) {
            itemClicked?.isCanEdit == true
        } else {
            !isVisitor && !isProjectsSection && (isItemOwner || isItemReadWrite ||
                    itemClicked?.access in listOf(Access.Review, Access.FormFiller, Access.Comment))
        }

    private val isItemShareable: Boolean
        get() = if (account.isDocSpace && (currentSectionType == ApiContract.SectionType.CLOUD_VIRTUAL_ROOM || currentSectionType == ApiContract.SectionType.CLOUD_USER)) {
            itemClicked?.isCanShare == true
        } else {
            isItemEditable && (!isCommonSection || isAdmin) && !isProjectsSection
                    && !isBunchSection && isItemReadWrite
        }

    private val isClickedItemStorage: Boolean
        get() = itemClicked?.providerItem == true

    private val itemFolderId: String?
        get() = (itemClicked as? CloudFile)?.folderId ?: (itemClicked as? CloudFolder)?.parentId

    val isCurrentRoom: Boolean
        get() = currentSectionType > ApiContract.SectionType.CLOUD_PRIVATE_ROOM // && modelExplorerStack.last()?.current?.isCanEdit == true

    private fun showDownloadFolderActivity(uri: Uri) {
        viewState.onDownloadActivity(uri)
    }

    fun archiveRooms(isArchive: Boolean) {
        viewState.onDialogProgress(
            context.getString(R.string.dialogs_wait_title),
            true,
            TAG_DIALOG_CANCEL_SINGLE_OPERATIONS
        )
        viewState.onDialogProgress(100, 0)
        requestJob = presenterScope.launch(Dispatchers.IO) {
            try {
                val provider = requireNotNull(roomProvider)
                val message = if (isSelectionMode) {
                    val selected = modelExplorerStack.selectedFolders.map(CloudFolder::id)
                    selected.forEachIndexed { index, id ->
                        provider.archiveRoom(id, isArchive)
                        withContext(Dispatchers.Main) {
                            val progress = 100 / (selected.size / (index + 1).toFloat())
                            viewState.onDialogProgress(100, progress.toInt())
                        }
                    }
                    if (isArchive) {
                        context.getString(R.string.context_rooms_archive_message)
                    } else {
                        context.resources.getQuantityString(R.plurals.context_rooms_unarchive_message, selected.size)
                    }
                } else {
                    provider.archiveRoom(roomClicked?.id.orEmpty(), isArchive = isArchive)
                    if (isArchive) {
                        context.getString(R.string.context_room_archive_message)
                    } else {
                        context.resources.getQuantityString(R.plurals.context_rooms_unarchive_message, 1)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (isSelectionMode) {
                        deselectAll()
                        setSelection(false)
                    }
                    viewState.onDialogProgress(100, 100)
                    viewState.onSnackBar(message)
                    if (isTrashSection) { popToRoot() }
                    refresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e !is CancellationException) {
                        fetchError(e)
                    }
                }
            } finally {
                viewState.onDialogClose()
            }
        }
    }

    fun copyLinkFromActionMenu(isRoom: Boolean) {
        if (isRoom) {
            copyRoomLink()
        } else {
            (itemClicked as? CloudFolder)?.let { saveLink(getInternalLink(it)) }
        }
    }

    fun copyLinkFromContextMenu() {
        val item = itemClicked
        when {
            (item as? CloudFolder)?.isRoom == true -> copyRoomLink()
            item is CloudFolder -> saveLink(getInternalLink(item))
            else -> saveExternalLinkToClipboard()
        }
    }

    fun pinRoom() {
        roomProvider?.let {
            itemClicked?.let { folder ->
                if (folder is CloudFolder) {
                    disposable.add(
                        it.pinRoom(folder.id, !folder.pinned)
                            .doOnSubscribe { viewState.onSwipeEnable(true) }
                            .subscribe({ response ->
                                if (response.statusCode.toInt() == ApiContract.HttpCodes.SUCCESS) {
                                    folder.pinned = !folder.pinned
                                    viewState.onUpdateFavoriteItem()
                                }
                            }, ::fetchError)
                    )
                }
            }
        }
    }

    fun createRoom(roomType: Int) {
        val files = modelExplorerStack.selectedFiles.toMutableList()
        val folders = modelExplorerStack.selectedFolders.toMutableList()
        val clickedItem = itemClicked

        deselectAll()
        if (files.isEmpty() && folders.isEmpty() && clickedItem != null) {
            if (clickedItem is CloudFolder) {
                folders.add(clickedItem)
            } else if (clickedItem is CloudFile) {
                files.add(clickedItem)
            }
        }

        if (roomType == ApiContract.RoomType.FILL_FORMS_ROOM) {
            if (folders.isNotEmpty() || files.any { !it.isPdfForm }) {
                viewState.onDialogWarning(
                    context.getString(R.string.dialogs_warning_title),
                    context.getString(R.string.dialogs_warning_fill_forms_room_create),
                    null
                )
                return
            }
        }

        viewState.showAddRoomFragment(
            type = roomType,
            copyItems = CopyItems(
                folderIds = folders.map(CloudFolder::id),
                fileIds = files.map(CloudFile::id)
            )
        )
    }

    fun editRoom() {
        roomClicked?.let { room ->
            viewState.showEditRoomFragment(room)
        }
    }

    fun deleteRoom() {
        if (isSelectionMode && modelExplorerStack.countSelectedItems > 0) {
            roomProvider?.let { provider ->
                disposable.add(
                    provider.deleteRoom(items = modelExplorerStack.selectedFolders.map(CloudFolder::id))
                        .subscribe({
                            viewState.onDialogClose()
                            viewState.onSnackBar(context.getString(R.string.room_delete_success))
                            deselectAll()
                            refresh()
                        }) { fetchError(it) }
                )
            }
        } else if (itemClicked != null) {
            roomProvider?.let { provider ->
                disposable.add(
                    provider.deleteRoom(itemClicked?.id ?: "")
                        .doOnComplete { popToRoot() }
                        .delay(1000, TimeUnit.MILLISECONDS)
                        .subscribe({
                            viewState.onDialogClose()
                            viewState.onSnackBar(context.getString(R.string.room_delete_success))
                            refresh()
                        }, ::fetchError)
                )
            }
        }
    }

    fun interruptConversion(): Boolean {
        val cancelled = conversionJob?.isCancelled == true // conversionJob == null || isCancelled
        conversionJob?.cancel()
        return cancelled
    }

    fun convertToOOXML() {
        val extension = LocalContentTools.toOOXML((itemClicked as? CloudFile)?.clearExt.orEmpty())
        viewState.onConversionProgress(0, extension)
        (fileProvider as? CloudFileProvider)?.let { fileProvider ->
            conversionJob = presenterScope.launch {
                try {
                    fileProvider.convertToOOXML(itemClicked?.id.orEmpty()).collectLatest {
                        withContext(Dispatchers.Main) {
                            viewState.onConversionProgress(it, extension)
                            if (it == 100) {
                                delay(300L)
                                viewState.onDialogClose()
                                refresh()
                                viewState.onScrollToPosition(0)
                                conversionJob?.cancel()
                            }
                        }
                    }
                } catch (error: Throwable) {
                    if (conversionJob?.isCancelled == true) return@launch
                    viewState.onDialogClose()
                    fetchError(error)
                }
            }
        }
    }

    fun checkRoomOwner() {
        if (roomClicked != null) {
            viewState.onLeaveRoomDialog(
                R.string.leave_room_title,
                if (isItemOwner) R.string.leave_room_owner_desc else R.string.leave_room_desc,
                isItemOwner
            )
        }
    }

    fun leaveRoom() {
        if (!isItemOwner) {
            showDialogWaiting(null)
            presenterScope.launch(Dispatchers.IO) {
                try {
                    context.roomApi.shareRoom(
                        id = roomClicked?.id ?: "",
                        body = RequestRoomShare(
                            invitations = listOf(
                                UserIdInvitation(
                                    id = account.id,
                                    access = Access.None.code
                                )
                            )
                        )
                    )
                    withContext(Dispatchers.Main) {
                        viewState.onDialogClose()
                        viewState.onSnackBar(context.getString(R.string.leave_room_message))
                        refresh()
                    }
                } catch (e: Exception) {
                    viewState.onDialogClose()
                    fetchError(e)
                }
            }
        } else {
            viewState.showSetOwnerFragment(roomClicked ?: error("room can not be null"))
        }
    }

    fun tryMove() {
        val item = itemClicked
        if (item is CloudFolder && item.roomType == ApiContract.RoomType.PUBLIC_ROOM) {
            viewState.onDialogQuestion(
                context.getString(R.string.rooms_move_to_public_title),
                context.getString(R.string.rooms_move_to_public_title_desc),
                TAG_DIALOG_MOVE_TO_PUBLIC
            )
        } else {
            move()
        }
    }

    fun getSelectedItemsCount(): Int {
        return modelExplorerStack.countSelectedItems
    }

    @SuppressLint("MissingPermission")
    fun updateDocument(data: Uri) {
        if (data.path?.isEmpty() == true) return
        context.contentResolver.openInputStream(data).use {
            val file = File(data.path)
            val body = MultipartBody.Part.createFormData(
                file.name, file.name, RequestBody.create(
                    MediaType.parse(ContentResolverUtils.getMimeType(context, data)), file
                )
            )
            disposable.add(
                (fileProvider as CloudFileProvider).updateDocument(itemClicked?.id.orEmpty(), body)
                    .subscribe({
                        FileUtils.deletePath(file)
                        viewState.onDialogClose()
                    }, {
                        FileUtils.deletePath(file)
                        fetchError(it)
                    })
            )
        }

    }

    // use for operation in order to filter by room
    fun setFilterByRoom(roomType: Int) {
        filters = mapOf(ApiContract.Parameters.ARG_FILTER_BY_TYPE_ROOM to roomType.toString())
        (fileProvider as CloudFileProvider).roomCallback = object : RoomCallback {
            override fun isRoomRoot(id: String?): Boolean {
                val parts = modelExplorerStack.last()?.pathParts.orEmpty()
                return if (parts.isNotEmpty()) {
                    parts[0].id == id
                } else {
                    modelExplorerStack.isStackEmpty || modelExplorerStack.isRoot
                }
            }
            override fun isArchive(): Boolean = false
            override fun isRecent(): Boolean = false
        }
    }

    fun duplicateRoom() {
        val workData = Data.Builder()
            .putString(RoomDuplicateWork.KEY_ROOM_ID, roomClicked?.id)
            .putString(RoomDuplicateWork.KEY_ROOM_TITLE, roomClicked?.title)
            .build()

        val request = OneTimeWorkRequest.Builder(RoomDuplicateWork::class.java)
            .addTag(RoomDuplicateWork.getTag(roomClicked?.id.hashCode(), roomClicked?.title))
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun openRecentViaLink() {
        setPlaceholderType(PlaceholderViews.Type.LOAD)
        (fileProvider as? CloudFileProvider)?.let { provider ->
            disposable.add(
                provider.getRecentViaLink()
                    .subscribe(::loadSuccess, ::fetchError)
            )
        }
    }

    private fun copyRoomLink() {
        roomClicked?.let { room ->
            if (room.roomType == ApiContract.RoomType.COLLABORATION_ROOM || room.roomType == ApiContract.RoomType.VIRTUAL_ROOM) {
                setDataToClipboard(getInternalLink(room))
            } else {
                presenterScope.launch {
                    try {
                        val externalLink = roomProvider?.getExternalLink(roomClicked?.id.orEmpty())
                        withContext(Dispatchers.Main) {
                            if (externalLink.isNullOrEmpty()) {
                                viewState.onError(context.getString(R.string.errors_unknown_error))
                            } else {
                                saveLink(externalLink)
                            }
                        }
                    } catch (error: Throwable) {
                        fetchError(error)
                    }
                }
            }
        }
    }

    private fun saveLink(link: String) {
        setDataToClipboard(link)
        viewState.onSnackBar(context.getString(R.string.rooms_info_copy_link_to_clipboard))
    }

    private fun getInternalLink(folder: CloudFolder): String {
        return "${context.accountOnline?.portal?.urlWithScheme}" + if (folder.isRoom) {
            "/rooms/shared/filter?folder=${folder.id}"
        } else {
            "rooms/shared/${folder.id}/filter?folder=${folder.id}"
        }
    }

    fun muteRoomNotifications(muted: Boolean) {
        presenterScope.launch {
            val roomId = roomClicked?.id.orEmpty()
            roomProvider?.muteRoomNotifications(roomId, muted)?.collect { result ->
                when (result) {
                    is Result.Error -> withContext(Dispatchers.Main) {
                        viewState.onError(context.getString(R.string.errors_unknown_error))
                    }
                    is Result.Success -> withContext(Dispatchers.Main) {
                        roomClicked?.mute = roomId in result.result
                        viewState.onSnackBar(
                            context.getString(
                                if (muted) {
                                    R.string.rooms_notifications_disabled
                                } else {
                                    R.string.rooms_notifications_enabled
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    // TODO For hotfix
    private fun openFromLink(model: OpenDataModel) {
        if (model.portal.isNullOrEmpty()) return
        showDialogWaiting(null)
        presenterScope.launch {
            val api = NetworkClient.getRetrofit<ManagerService>(model.portal, model.share, context)

            try {
                val response = withContext(Dispatchers.IO) {
                    JSONObject(api.openFile(model.file?.id ?: "").blockingGet().body()?.string()).getJSONObject(
                        KEY_RESPONSE
                    )

                }

                val json = withContext(Dispatchers.IO) {
                    JSONObject(api.getDocService().blockingGet().body()?.string())
                }

                val docService = if (json.optJSONObject(KEY_RESPONSE) != null) {
                    json.getJSONObject(KEY_RESPONSE).getString("docServiceUrlApi")
                        .replace(STATIC_DOC_URL, "")
                } else {
                    json.getString(KEY_RESPONSE)
                        .replace(STATIC_DOC_URL, "")
                }

                val result = withContext(Dispatchers.IO) {
                    response
                        .put("url", docService)
                        .put("fileId", model.file?.id)
                        .put("canShareable", false)
                }

                withContext(Dispatchers.Main) {
                    viewState.onDialogClose()
                    delay(50)
                    viewState.onOpenDocumentServer(
                        /* file = */ CloudFile().apply {
                            id = model.file?.id.toString()
                            title = model.file?.title ?: ""
                            fileExst = model.file?.extension ?: ""
                        },
                        /* info = */ result.toString(),
                        /* type = */ null
                    )
                }
            } catch (e: Exception) {
                fetchError(e)
            }
        }
    }

    fun exportIndex() {
        viewState.onDialogProgress(
            context.getString(R.string.dialogs_wait_title),
            false,
            TAG_DIALOG_CANCEL_SINGLE_OPERATIONS
        )
        presenterScope.launch {
            roomProvider?.exportIndex(roomClicked?.id.orEmpty())?.collect { result ->
                when (result) {
                    is Result.Error -> fetchError(result.exception)
                    is Result.Success -> {
                        val operation = result.result
                        val progress = operation.percentage
                        viewState.onDialogProgress(100, progress)
                        if (progress == 100 || operation.isCompleted) {
                            viewState.onDialogClose()
                            viewState.onRoomExportIndex(operation)
                        }
                    }
                }
            }
        }
    }

    fun authRoomViaLink(password: String, tag: String) {
        showDialogWaiting(TAG_DIALOG_CANCEL_SINGLE_OPERATIONS)
        requestJob = presenterScope.launch {
            val requestToken = roomClicked?.requestToken.orEmpty()
            roomProvider?.authRoomViaLink(requestToken, password)?.collect { result ->
                when (result) {
                    is Result.Error -> fetchError(result.exception)
                    is Result.Success -> {
                        val roomId = result.result
                        if (roomId == null) {
                            viewState.onRoomViaLinkPasswordRequired(true, tag)
                        } else {
                            roomClicked?.passwordProtected = false
                            refresh {
                                viewState.onDialogClose()
                                when (tag) {
                                    TAG_PROTECTED_ROOM_OPEN_FOLDER -> openFolder(result.result, 0)
                                    TAG_PROTECTED_ROOM_DOWNLOAD -> createDownloadFile()
                                    TAG_PROTECTED_ROOM_SHOW_INFO -> viewState.showRoomInfoFragment()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}