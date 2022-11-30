package app.editors.manager.ui.fragments.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.documents.core.network.common.contracts.ApiContract
import app.documents.core.network.webdav.WebDavService
import app.editors.manager.BuildConfig
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.databinding.FragmentChooseCloudsBinding
import app.documents.core.network.common.models.Storage
import app.documents.core.network.common.utils.GoogleDriveUtils
import app.documents.core.network.storages.dropbox.login.DropboxLoginHelper
import app.editors.manager.ui.fragments.storages.GoogleDriveSignInFragment
import app.documents.core.network.common.utils.OneDriveUtils
import app.editors.manager.ui.fragments.storages.OneDriveSignInFragment
import app.editors.manager.ui.activities.login.PortalsActivity
import app.editors.manager.ui.activities.login.WebDavLoginActivity
import app.editors.manager.ui.activities.main.MainActivity
import app.editors.manager.ui.fragments.base.BaseAppFragment
import javax.inject.Inject

class CloudsFragment : BaseAppFragment() {

    companion object {
        val TAG: String = CloudsFragment::class.java.simpleName

        private const val KEY_IS_BACK = "KEY_IS_BACK"

        fun newInstance(isBack: Boolean): CloudsFragment {
            return CloudsFragment().apply {
                arguments = Bundle(1).apply {
                    putBoolean(KEY_IS_BACK, isBack)
                }
            }
        }
    }

    @Inject
    lateinit var dropboxLoginHelper: DropboxLoginHelper

    private var isBack = false
    private var viewBinding: FragmentChooseCloudsBinding? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.getApp().appComponent.inject(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewBinding = FragmentChooseCloudsBinding.inflate(inflater, container, false)
        return viewBinding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            isBack = it.getBoolean(KEY_IS_BACK)
        }
        init()
    }

    fun init() {
        setActionBarTitle(getString(R.string.fragment_clouds_title))

        viewBinding?.cloudsItemOnlyOffice?.setOnClickListener {
            PortalsActivity.show(requireActivity())
        }

        viewBinding?.cloudsItemNextCloud?.bind(
            R.drawable.ic_storage_nextcloud,
            R.string.storage_select_next_cloud
        ) {
            WebDavLoginActivity.show(requireActivity(), WebDavService.Providers.NextCloud, null)
        }

        viewBinding?.cloudsItemOwnCloud?.bind(
            R.drawable.ic_storage_owncloud,
            R.string.storage_select_own_cloud
        ) {
            WebDavLoginActivity.show(requireActivity(), WebDavService.Providers.OwnCloud, null)
        }

        viewBinding?.cloudsItemKDrive?.bind(
            R.drawable.ic_storage_kdrive,
            R.string.storage_select_kdrive
        ) {
            WebDavLoginActivity.show(requireActivity(), WebDavService.Providers.KDrive, null)
        }

        viewBinding?.cloudsItemOneDrive?.bind(
            R.drawable.ic_storage_onedrive,
            R.string.storage_select_one_drive
        ) {
            showFragment(OneDriveSignInFragment.newInstance(OneDriveUtils.storage), OneDriveSignInFragment.TAG, false)
        }

        viewBinding?.cloudsItemDropbox?.bind(
            R.drawable.ic_storage_dropbox,
            R.string.storage_select_drop_box
        ) {
            dropboxLoginHelper.startSignInActivity(this) {
                App.getApp().refreshDropboxInstance()
                MainActivity.show(requireContext())
                requireActivity().finish()
            }
        }

        viewBinding?.cloudsItemGoogleDrive?.bind(
            R.drawable.ic_storage_google,
            R.string.storage_select_google_drive
        ) {
            showFragment(GoogleDriveSignInFragment.newInstance(GoogleDriveUtils.storage), GoogleDriveSignInFragment.TAG, false)
        }


        viewBinding?.cloudsItemYandex?.isVisible = false
//        viewBinding?.cloudsItemYandex?.bind(
//            R.drawable.ic_storage_yandex,
//            R.string.storage_select_yandex
//        ) {
//            WebDavLoginActivity.show(requireActivity(), WebDavApi.Providers.Yandex, null)
//        }

        viewBinding?.cloudsItemWebDav?.bind(
            R.drawable.ic_storage_webdav,
            R.string.storage_select_web_dav
        ) {
            WebDavLoginActivity.show(requireActivity(), WebDavService.Providers.WebDav, null)
        }
    }


}