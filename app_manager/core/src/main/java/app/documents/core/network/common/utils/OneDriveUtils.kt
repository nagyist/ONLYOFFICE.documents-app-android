package app.documents.core.network.common.utils

import app.documents.core.network.common.contracts.ApiContract
import app.documents.core.network.common.models.Storage

object OneDriveUtils {

    private const val KEY_SORT = "orderby"
    private const val VAL_SORT_NAME = "name"
    private const val VAL_SORT_SIZE = "size"
    private const val VAL_SORT_UPDATED = "lastModifiedDateTime"
    private const val VAL_SORT_ASC = "asc"
    private const val VAL_SORT_DESC = "desc"

    const val ONEDRIVE_PORTAL = "onedrive.live.com"
    const val ONEDRIVE_STORAGE = "OneDrive"
    const val ONEDRIVE_BASE_URL = "https://graph.microsoft.com/"
    const val ONEDRIVE_AUTH_URL = "https://login.microsoftonline.com/"

    const val KEY_CONFLICT_BEHAVIOR = "@microsoft.graph.conflictBehavior"
    const val VAL_CONFLICT_BEHAVIOR_RENAME = "rename"
    const val VAL_CONFLICT_BEHAVIOR_REPLACE = "replace"
    const val VAL_CONFLICT_BEHAVIOR_FAIL = "fail"

    const val VAL_SHARE_TYPE_READ = "view"
    const val VAL_SHARE_TYPE_READ_WRITE = "edit"
    const val VAL_SHARE_TYPE_EMBED = "embed"

    const val VAL_SHARE_SCOPE_ANON = "anonymous"
    const val VAL_SHARE_SCOPE_ORG = "organization"

    const val REQUEST_MEDIA_TYPE = "${ApiContract.VALUE_CONTENT_TYPE};" +
            "odata.metadata=minimal;" +
            "odata.streaming=true;" +
            "IEEE754Compatible=false;" +
            "charset=utf-8"

    val storage = Storage(
        ONEDRIVE_STORAGE,
        app.documents.core.network.BuildConfig.ONE_DRIVE_COM_CLIENT_ID,
        app.documents.core.network.BuildConfig.ONE_DRIVE_COM_REDIRECT_URL
    )
}