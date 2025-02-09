package app.editors.manager.ui.fragments.main

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import app.documents.core.network.common.contracts.ApiContract
import app.documents.core.network.manager.models.explorer.Item
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.managers.tools.ActionMenuItem
import app.editors.manager.managers.tools.PreferenceTool
import app.editors.manager.mvp.models.states.OperationsState
import app.editors.manager.mvp.presenters.main.DocsBasePresenter
import app.editors.manager.mvp.presenters.main.DocsOnDevicePresenter
import app.editors.manager.mvp.presenters.main.OpenState
import app.editors.manager.mvp.views.main.DocsOnDeviceView
import app.editors.manager.ui.activities.login.PortalsActivity
import app.editors.manager.ui.activities.main.ActionButtonFragment
import app.editors.manager.ui.activities.main.IMainActivity
import app.editors.manager.ui.dialogs.ActionBottomDialog
import app.editors.manager.ui.dialogs.explorer.ExplorerContextItem
import app.editors.manager.ui.views.custom.PlaceholderViews
import lib.toolkit.base.managers.tools.LocalContentTools
import lib.toolkit.base.managers.utils.ActivitiesUtils
import lib.toolkit.base.managers.utils.EditType
import lib.toolkit.base.managers.utils.EditorsContract
import lib.toolkit.base.managers.utils.EditorsType
import lib.toolkit.base.managers.utils.FolderChooser
import lib.toolkit.base.managers.utils.RequestPermissions
import lib.toolkit.base.managers.utils.StringUtils.getHelpUrl
import lib.toolkit.base.managers.utils.UiUtils
import lib.toolkit.base.managers.utils.launchAfterResume
import lib.toolkit.base.ui.dialogs.common.CommonDialog.Dialogs
import moxy.presenter.InjectPresenter
import java.util.Locale

class DocsOnDeviceFragment : DocsBaseFragment(), DocsOnDeviceView, ActionButtonFragment {

    @InjectPresenter
    override lateinit var presenter: DocsOnDevicePresenter
    private var activity: IMainActivity? = null
    private var preferenceTool: PreferenceTool? = null

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { data: Uri? ->
        data?.let { presenter.import(it) }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { data: Uri? ->
        data?.let { presenter.openFromChooser(it) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private val readStorage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED && Environment.isExternalStorageManager()) {
            launchAfterResume {
                swipeRefreshLayout?.isEnabled = true
                preferenceTool?.isShowStorageAccess = false
                presenter.recreateStack()
                presenter.getItemsById(Environment.getExternalStorageDirectory().absolutePath)
            }
        } else {
            launchAfterResume {
                setVisibilityActionButton(false)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            activity = context as IMainActivity
            preferenceTool = App.getApp().appComponent.preference
        } catch (e: ClassCastException) {
            throw RuntimeException(
                DocsOnDeviceFragment::class.java.simpleName + " - must implement - " +
                        IMainActivity::class.java.simpleName
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.setSectionType(ApiContract.SectionType.DEVICE_DOCUMENTS)
        checkStorage()
        init(savedInstanceState)
    }

    override fun onStateMenuDefault(sortBy: String, isAsc: Boolean) {
        super.onStateMenuDefault(sortBy, isAsc)
        openItem?.isVisible = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.toolbar_item_main -> showActionBarMenu()
            R.id.toolbar_selection_delete -> presenter.delete()
            R.id.toolbar_item_open -> showSingleFragmentFilePicker()
        }
        return true
    }

    override fun onListEnd() {}

    override fun onSwipeRefresh(): Boolean {
        if (!super.onSwipeRefresh()) {
            presenter.getItemsById(LocalContentTools.getDir(requireContext()))
            return true
        }
        return false
    }

    override fun onStateUpdateRoot(isRoot: Boolean) {
        activity?.apply {
            setAppBarStates(false)
            showNavigationButton(!isRoot)
            showAccount(false)
        }
    }

    override fun onStateMenuSelection() {
        if (menu != null && menuInflater != null && context != null) {
            menuInflater?.inflate(R.menu.docs_select, menu)
            deleteItem = menu?.findItem(R.id.toolbar_selection_delete)?.apply {
                UiUtils.setMenuItemTint(requireContext(), this, lib.toolkit.base.R.color.colorPrimary)
                isVisible = true
            }
            activity?.showNavigationButton(true)
        }
    }

    override fun onStateEmptyBackStack() {
        swipeRefreshLayout?.isRefreshing = true
        presenter.getItemsById(Environment.getExternalStorageDirectory().absolutePath)
    }

    override fun onStateUpdateFilter(isFilter: Boolean, value: String?) {
        super.onStateUpdateFilter(isFilter, value)
        activity?.showNavigationButton(isFilter)
    }

    override fun onActionBarTitle(title: String) {
        setActionBarTitle(title)
    }

    override fun onActionButtonClick(buttons: ActionBottomDialog.Buttons?) {
        when (buttons) {
            ActionBottomDialog.Buttons.IMPORT -> {
                importFile.launch(arrayOf(ActivitiesUtils.PICKER_NO_FILTER))
            }

            else -> {
                super.onActionButtonClick(buttons)
            }
        }
    }

    override fun onAcceptClick(dialogs: Dialogs?, value: String?, tag: String?) {
        var string = value
        tag?.let {
            string = string?.trim { it <= ' ' }
            when (tag) {
                DocsBasePresenter.TAG_DIALOG_BATCH_DELETE_SELECTED -> presenter.deleteItems()
                DocsBasePresenter.TAG_DIALOG_CONTEXT_RENAME -> string?.let {
                    presenter.rename(it)
                }

                DocsBasePresenter.TAG_DIALOG_ACTION_SHEET -> presenter.createDocs(
                    "$string." + ApiContract.Extension.XLSX.lowercase(Locale.ROOT)
                )

                DocsBasePresenter.TAG_DIALOG_ACTION_PRESENTATION -> presenter.createDocs(
                    "$string." + ApiContract.Extension.PPTX.lowercase(Locale.ROOT)
                )

                DocsBasePresenter.TAG_DIALOG_ACTION_DOC -> presenter.createDocs(
                    "$string." + ApiContract.Extension.DOCX.lowercase(Locale.ROOT)
                )

                DocsBasePresenter.TAG_DIALOG_ACTION_FOLDER -> string?.let {
                    presenter.createFolder(it)
                }

                DocsBasePresenter.TAG_DIALOG_DELETE_CONTEXT -> presenter.deleteFile()
                else -> {
                }
            }
        }
        hideDialog()
    }

    override fun onCancelClick(dialogs: Dialogs?, tag: String?) {
        super.onCancelClick(dialogs, tag)
        if (tag == TAG_STORAGE_ACCESS) {
            preferenceTool?.isShowStorageAccess = false
            presenter.recreateStack()
            presenter.getItemsById(LocalContentTools.getDir(requireContext()))
        }
    }

    override fun onContextButtonClick(contextItem: ExplorerContextItem) {
        when (contextItem) {
            ExplorerContextItem.Upload -> presenter.upload()
            ExplorerContextItem.Copy -> showFolderChooser(OperationsState.OperationType.COPY)
            ExplorerContextItem.Move -> showFolderChooser(OperationsState.OperationType.MOVE)
            is ExplorerContextItem.Edit -> presenter.getFileInfo(EditType.EDIT)
            is ExplorerContextItem.Fill -> presenter.getFileInfo(EditType.FILL)
            is ExplorerContextItem.View -> presenter.getFileInfo(EditType.VIEW)
            is ExplorerContextItem.Delete -> showDeleteDialog(tag = DocsBasePresenter.TAG_DIALOG_DELETE_CONTEXT)
            else -> super.onContextButtonClick(contextItem)
        }
    }

    override fun onActionDialog() {
        actionBottomDialog?.let {
            it.onClickListener = this
            it.isLocal = true
            it.show(parentFragmentManager, ActionBottomDialog.TAG)
        }
    }

    override fun onRemoveItems(vararg items: Item) {
        onSnackBar(resources.getQuantityString(R.plurals.operation_delete_irretrievably, items.size))
        explorerAdapter?.let { adapter ->
            adapter.removeItems(items.toList())
//            adapter.checkHeaders()
            setPlaceholder(adapter.itemList.isNullOrEmpty())
            onClearMenu()
        }
    }

    override fun showDeleteDialog(count: Int, toTrash: Boolean, tag: String) {
        super.showDeleteDialog(count, false, tag)
    }

    override fun onShowPdf(uri: Uri) {
        showEditors(uri, EditorsType.PDF)
    }

    override fun onOpenMedia(state: OpenState.Media) {
        showMediaActivity(state.explorer, state.isWebDav)
    }

    override fun onShowEditors(uri: Uri, type: EditorsType, editType: EditType?) {
        showEditors(uri, type, null, editType)
    }

    override fun showEditors(uri: Uri?, type: EditorsType, info: String?, editType: EditType?) {
        try {
            val intent = Intent().apply {
                data = uri
                info?.let { putExtra(EditorsContract.KEY_DOC_SERVER, info) }
                putExtra(EditorsContract.KEY_HELP_URL, getHelpUrl(requireContext()))
                putExtra(EditorsContract.KEY_EDIT_TYPE, editType)
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            when (type) {
                EditorsType.DOCS -> {
                    intent.setClassName(requireContext(), EditorsContract.EDITOR_DOCUMENTS)
                    startActivityForResult(intent, REQUEST_DOCS)
                }

                EditorsType.PDF -> {
                    intent.setClassName(requireContext(), EditorsContract.PDF)
                    startActivity(intent)
                }

                else -> {
                    super.showEditors(uri, type, info, editType)
                }
            }
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            showToast("Not found")
        }
    }

    override fun onShowPortals() {
        PortalsActivity.showPortals(this)
    }

    override fun setVisibilityActionButton(isShow: Boolean) {
        if (placeholderViews?.type == PlaceholderViews.Type.ACCESS) {
            activity?.showActionButton(false)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    activity?.showActionButton(false)
                    return
                }
            }
            activity?.showActionButton(isShow)
        }
    }

    private fun init(savedInstanceState: Bundle?) {

        // Check shortcut
        val bundle = requireActivity().intent?.extras
        if (savedInstanceState == null && bundle != null && bundle.containsKey(KEY_SHORTCUT)) {
            when (bundle.getString(KEY_SHORTCUT)) {
                LocalContentTools.DOCX_EXTENSION -> {
                    onActionButtonClick(ActionBottomDialog.Buttons.DOC)
                }

                LocalContentTools.XLSX_EXTENSION -> {
                    onActionButtonClick(ActionBottomDialog.Buttons.SHEET)
                }

                LocalContentTools.PPTX_EXTENSION -> {
                    onActionButtonClick(ActionBottomDialog.Buttons.PRESENTATION)
                }
            }
            requireActivity().intent.removeExtra(KEY_SHORTCUT)
        }
    }

    private fun showSingleFragmentFilePicker() {
        try {
            openFile.launch(arrayOf(ActivitiesUtils.PICKER_NO_FILTER))
        } catch (e: ActivityNotFoundException) {
            onError(e.message)
        }
    }

    override fun onError(message: String?) {
        if (message?.contains(getString(R.string.errors_import_local_file_desc)) == true) {
            showSnackBar(R.string.errors_import_local_file)
        } else {
            super.onError(message)
        }
    }

    private fun checkStorage() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> {
                requestReadWritePermission()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    setActionBarTitle(getString(R.string.fragment_on_device_title))
                    onStateUpdateRoot(true)
                    swipeRefreshLayout?.isEnabled = false
                    mainItem?.isVisible = false
                    activity?.showActionButton(false)
                    placeholderViews?.setTemplatePlaceholder(PlaceholderViews.Type.EXTERNAL_STORAGE) {
                        requestManage()
                    }
                } else {
                    swipeRefreshLayout?.isEnabled = true
                    presenter.checkBackStack()
                }
            }
        }
    }

    private fun requestManage() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                readStorage.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + requireContext().packageName)
                    )
                )
            }
        } catch (e: ActivityNotFoundException) {
            openItem?.isVisible = false
            swipeRefreshLayout?.isEnabled = false
            activity?.showActionButton(false)
            placeholderViews?.setTemplatePlaceholder(PlaceholderViews.Type.ACCESS)
        }
    }

    private fun requestReadWritePermission() {
        RequestPermissions(requireActivity().activityResultRegistry, { permissions ->
            if (permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true && permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
                presenter.checkBackStack()
            } else {
                swipeRefreshLayout?.isEnabled = false
                openItem?.isVisible = false
                activity?.showActionButton(false)
                placeholderViews?.setTemplatePlaceholder(PlaceholderViews.Type.ACCESS)
            }
        }, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)).request()
    }


    private fun setPlaceholder(isEmpty: Boolean) {
        onPlaceholder(if (isEmpty) PlaceholderViews.Type.EMPTY else PlaceholderViews.Type.NONE)
    }

    private fun showFolderChooser(operation: OperationsState.OperationType) {
        FolderChooser(requireActivity().activityResultRegistry, { data ->
            data?.let { uri ->
                presenter.moveFile(uri, operation == OperationsState.OperationType.COPY)
            }
        }).show()
    }

    override val actionMenuClickListener: (ActionMenuItem) -> Unit = { item ->
        when (item) {
            is ActionMenuItem.Operation -> showFolderChooser(item.value)
            else -> super.actionMenuClickListener(item)
        }
    }

    override val isActivePage: Boolean
        get() = isAdded

    companion object {
        val TAG: String = DocsOnDeviceFragment::class.java.simpleName

        private const val TAG_STORAGE_ACCESS = "TAG_STORAGE_ACCESS"

        private const val KEY_SHORTCUT = "create_type"

        fun newInstance(): DocsOnDeviceFragment {
            return DocsOnDeviceFragment()
        }
    }
}