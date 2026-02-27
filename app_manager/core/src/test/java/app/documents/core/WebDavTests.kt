package app.documents.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import app.documents.core.model.cloud.PortalProvider
import app.documents.core.model.cloud.WebdavProvider
import app.documents.core.network.common.contracts.ApiContract
import app.documents.core.network.manager.models.explorer.CloudFile
import app.documents.core.network.manager.models.explorer.CloudFolder
import app.documents.core.network.manager.models.explorer.Item
import app.documents.core.network.manager.models.explorer.Operation
import app.documents.core.network.manager.models.request.RequestCreate
import app.documents.core.network.webdav.WebDavService
import app.documents.core.network.webdav.models.WebDavModel
import app.documents.core.providers.WebDavFileProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import lib.toolkit.base.managers.utils.StringUtils
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

class WebdavProviderTest {

    @Test
    fun `valueOf NextCloud string returns NextCloud`() {
        assertEquals(WebdavProvider.NextCloud, WebdavProvider.valueOf("NextCloud"))
    }

    @Test
    fun `valueOf OwnCloud string returns OwnCloud`() {
        assertEquals(WebdavProvider.OwnCloud, WebdavProvider.valueOf("OwnCloud"))
    }

    @Test
    fun `valueOf Yandex string returns Yandex`() {
        assertEquals(WebdavProvider.Yandex, WebdavProvider.valueOf("Yandex"))
    }

    @Test
    fun `valueOf KDrive string returns KDrive`() {
        assertEquals(WebdavProvider.KDrive, WebdavProvider.valueOf("KDrive"))
    }

    @Test
    fun `valueOf WebDav string returns WebDav`() {
        assertEquals(WebdavProvider.WebDav, WebdavProvider.valueOf("WebDav"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf unknown string throws IllegalArgumentException`() {
        WebdavProvider.valueOf("Unknown")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf empty string throws IllegalArgumentException`() {
        WebdavProvider.valueOf("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf case-sensitive string throws IllegalArgumentException`() {
        WebdavProvider.valueOf("nextcloud")
    }

    @Test
    fun `valueOf Webdav PortalProvider with NextCloud returns NextCloud`() {
        val portalProvider = PortalProvider.Webdav(WebdavProvider.NextCloud)
        assertEquals(WebdavProvider.NextCloud, WebdavProvider.valueOf(portalProvider))
    }

    @Test
    fun `valueOf Webdav PortalProvider with OwnCloud returns OwnCloud`() {
        val portalProvider = PortalProvider.Webdav(WebdavProvider.OwnCloud)
        assertEquals(WebdavProvider.OwnCloud, WebdavProvider.valueOf(portalProvider))
    }

    @Test
    fun `valueOf Webdav PortalProvider with KDrive returns KDrive`() {
        val portalProvider = PortalProvider.Webdav(WebdavProvider.KDrive)
        assertEquals(WebdavProvider.KDrive, WebdavProvider.valueOf(portalProvider))
    }

    @Test
    fun `valueOf Webdav PortalProvider with Yandex returns Yandex`() {
        val portalProvider = PortalProvider.Webdav(WebdavProvider.Yandex)
        assertEquals(WebdavProvider.Yandex, WebdavProvider.valueOf(portalProvider))
    }

    @Test
    fun `valueOf Webdav PortalProvider with WebDav returns WebDav`() {
        val portalProvider = PortalProvider.Webdav(WebdavProvider.WebDav)
        assertEquals(WebdavProvider.WebDav, WebdavProvider.valueOf(portalProvider))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf Cloud Workspace PortalProvider throws IllegalArgumentException`() {
        WebdavProvider.valueOf(PortalProvider.Cloud.Workspace)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf Dropbox PortalProvider throws IllegalArgumentException`() {
        WebdavProvider.valueOf(PortalProvider.Dropbox)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf GoogleDrive PortalProvider throws IllegalArgumentException`() {
        WebdavProvider.valueOf(PortalProvider.GoogleDrive)
    }

    @Test
    fun `DEFAULT_NEXT_CLOUD_PATH equals remote php dav files`() {
        assertEquals("/remote.php/dav/files/", WebdavProvider.DEFAULT_NEXT_CLOUD_PATH)
    }

    @Test
    fun `DEFAULT_OWNCLOUD_PATH equals remote php dav files`() {
        assertEquals("/remote.php/dav/files/", WebdavProvider.DEFAULT_OWNCLOUD_PATH)
    }

}

class WebDavFileProviderTest {

    @MockK
    private lateinit var webDavService: WebDavService

    @MockK
    private lateinit var context: Context

    private lateinit var webDavFileProvider: WebDavFileProvider

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        mockkStatic(Uri::class)
        every { Environment.getExternalStorageDirectory() } returns File("/")
        every { Uri.encode(any(), any()) } answers { firstArg() }
        every { Uri.encode(any()) } answers { firstArg() }

        MockKAnnotations.init(this)
        webDavFileProvider = WebDavFileProvider(webDavService, context)
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setNewThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun tearDown() {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
        unmockkStatic(Environment::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun `rename should return updated CloudFile on successful rename`() {

        val oldId = "http://example.com/dav/files/user/test.txt"
        val oldTitle = "test.txt"
        val fileExtension = ".txt"
        val newNameWithoutExt = "renamed"
        val version = 1

        val cloudFile = CloudFile().apply {
            id = oldId
            title = oldTitle
            fileExst = fileExtension
        }

        val encodedNewName = StringUtils.getEncodedString(newNameWithoutExt) + fileExtension
        val newId = "http://example.com/dav/files/user/" + encodedNewName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.move(newId, oldId, "F") } returns mockCall
        every { mockCall.execute() } returns mockResponse
        
        val testObserver = webDavFileProvider.rename(cloudFile, newNameWithoutExt, version).test()
        
        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val resultItem = testObserver.values()[0] as CloudFile
        assertEquals(newId, resultItem.id)
        assertEquals(newNameWithoutExt + fileExtension, resultItem.title)

        verify(exactly = 1) { webDavService.move(newId, oldId, "F") }
    }

    @Test
    fun `rename should return updated CloudFolder on successful rename`() {
        val oldId = "http://example.com/dav/files/user/oldFolder/"
        val oldTitle = "oldFolder"
        val newName = "newFolder"

        val cloudFolder = CloudFolder().apply {
            id = oldId
            title = oldTitle
        }

        val newId = "http://example.com/dav/files/user/newFolder/"
        val encodedCorrectPath = StringUtils.getEncodedString(newId)!!

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.move(encodedCorrectPath, oldId, "F") } returns mockCall
        every { mockCall.execute() } returns mockResponse
        
        val testObserver = webDavFileProvider.rename(cloudFolder, newName, null).test()
        
        testObserver.assertNoErrors()
        testObserver.assertComplete()

        val result = testObserver.values()[0] as CloudFolder
        assertEquals(newId, result.id)
        assertEquals(newName, result.title)

        verify(exactly = 1) { webDavService.move(encodedCorrectPath, oldId, "F") }
    }

    @Test
    fun `rename should return error on failure`() {

        val oldId = "http://example.com/dav/files/user/test.txt"
        val oldTitle = "test.txt"
        val fileExtension = ".txt"
        val newNameWithoutExt = "renamed"
        val version = 1

        val cloudFile = CloudFile().apply {
            id = oldId
            title = oldTitle
            fileExst = fileExtension
        }

        val encodedNewName = StringUtils.getEncodedString(newNameWithoutExt) + fileExtension
        val newId = "http://example.com/dav/files/user/" + encodedNewName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.error(404, mockk(relaxed = true))

        every { webDavService.move(newId, oldId, "F") } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider.rename(cloudFile, newNameWithoutExt, version).test()
        
        testObserver.assertError(HttpException::class.java)

        verify(exactly = 1) { webDavService.move(newId, oldId, "F") }
    }

    @Test
    fun `createFolder should return new CloudFolder on successful creation`() {
        val folderId = "http://example.com/dav/files/user/"
        val newFolderName = "newFolder"
        val requestCreate = RequestCreate().apply { title = newFolderName }
        val newFolderPath = folderId + newFolderName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.createFolder(newFolderPath) } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider.createFolder(folderId, requestCreate).test()
        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val result = testObserver.values()[0]
        assertEquals(newFolderName, result.title)
        assertEquals(newFolderPath, result.id)

        verify(exactly = 1) { webDavService.createFolder(newFolderPath) }
    }

    @Test
    fun `createFolder should return error on failure`() {
        val folderId = "http://example.com/dav/files/user/"
        val newFolderName = "newFolder"
        val requestCreate = RequestCreate().apply { title = newFolderName }
        val newFolderPath = folderId + newFolderName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.error(500, mockk(relaxed = true))

        every { webDavService.createFolder(newFolderPath) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        val testObserver = webDavFileProvider.createFolder(folderId, requestCreate).test()
        testObserver.assertError(HttpException::class.java)

        verify(exactly = 1) { webDavService.createFolder(newFolderPath) }
    }

    @Test
    fun `delete should return operations on successful deletion`() {

        val fileId = "http://example.com/dav/files/user/test.txt"
        val cloudFile = CloudFile().apply { id = fileId }
        val items = listOf<Item>(cloudFile)

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(204, mockk<ResponseBody>(relaxed = true))

        every { webDavService.delete(fileId) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        val testObserver = webDavFileProvider.delete(items, null).test()
        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val operations: List<Operation> = testObserver.values()[0]
        assertEquals(1, operations.size)
        assertEquals(100, operations[0].progress)

        verify(exactly = 1) { webDavService.delete(fileId) }
    }

    @Test
    fun `delete should return error on failure`() {
        val fileId = "http://example.com/dav/files/user/test.txt"
        val cloudFile = CloudFile().apply { id = fileId }
        val items = listOf<Item>(cloudFile)

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.error(500, mockk(relaxed = true))

        every { webDavService.delete(fileId) } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider.delete(items, null).test()

        testObserver.assertError(HttpException::class.java)

        verify(exactly = 1) { webDavService.delete(fileId) }
    }

    @Test
    fun `delete with 202 response should succeed`() {
        val fileId = "http://example.com/dav/files/user/test.txt"
        val cloudFile = CloudFile().apply { id = fileId }
        val items = listOf<Item>(cloudFile)

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(202, mockk<ResponseBody>(relaxed = true))

        every { webDavService.delete(fileId) } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider.delete(items, null).test()
        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        verify(exactly = 1) { webDavService.delete(fileId) }
    }

    @Test
    fun `rename CloudFile with null version returns empty Item without calling service`() {

        val cloudFile = CloudFile().apply {
            id = "http://example.com/dav/files/user/test.txt"
            title = "test.txt"
            fileExst = ".txt"
        }


        val testObserver = webDavFileProvider.rename(cloudFile, "renamed", null).test()
        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        verify(exactly = 0) { webDavService.move(any(), any(), any()) }
    }

    @Test
    fun `transfer with isMove true should move item to destination folder`() {
        val fileId = "http://example.com/dav/files/user/test.txt"
        val destId = "http://example.com/dav/files/dest/"
        val fileName = "test.txt"

        val cloudFile = CloudFile().apply {
            id = fileId
            title = fileName
        }
        val destFolder = CloudFolder().apply { id = destId }
        val expectedDestination = destId + fileName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.move(expectedDestination, fileId, "F") } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider
            .transfer(listOf(cloudFile), destFolder, 0, isMove = true, isOverwrite = false)
            .test()
        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val operations: List<Operation> = testObserver.values()[0]
        assertEquals(1, operations.size)
        assertEquals(100, operations[0].progress)

        verify(exactly = 1) { webDavService.move(expectedDestination, fileId, "F") }
    }

    @Test
    fun `transfer with isMove true and overwrite should pass T header`() {

        val fileId = "http://example.com/dav/files/user/test.txt"
        val destId = "http://example.com/dav/files/dest/"
        val fileName = "test.txt"

        val cloudFile = CloudFile().apply {
            id = fileId
            title = fileName
        }
        val destFolder = CloudFolder().apply { id = destId }
        val expectedDestination = destId + fileName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.move(expectedDestination, fileId, "T") } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider
            .transfer(listOf(cloudFile), destFolder, 0, isMove = true, isOverwrite = true)
            .test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()

        verify(exactly = 1) { webDavService.move(expectedDestination, fileId, "T") }
    }

    @Test
    fun `transfer with isMove false should copy item to destination folder`() {

        val fileId = "http://example.com/dav/files/user/test.txt"
        val destId = "http://example.com/dav/files/dest/"
        val fileName = "test.txt"

        val cloudFile = CloudFile().apply {
            id = fileId
            title = fileName
        }
        val destFolder = CloudFolder().apply { id = destId }
        val expectedDestination = destId + fileName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.copy(expectedDestination, fileId, "F") } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider
            .transfer(listOf(cloudFile), destFolder, 0, isMove = false, isOverwrite = false)
            .test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val operations: List<Operation> = testObserver.values()[0]
        assertEquals(1, operations.size)
        assertEquals(100, operations[0].progress)

        verify(exactly = 1) { webDavService.copy(expectedDestination, fileId, "F") }
    }

    @Test
    fun `transfer with isMove false and overwrite should pass T header`() {

        val fileId = "http://example.com/dav/files/user/test.txt"
        val destId = "http://example.com/dav/files/dest/"
        val fileName = "test.txt"

        val cloudFile = CloudFile().apply {
            id = fileId
            title = fileName
        }
        val destFolder = CloudFolder().apply { id = destId }
        val expectedDestination = destId + fileName

        val mockCall: Call<ResponseBody> = mockk()
        val mockResponse: Response<ResponseBody> = Response.success(mockk())

        every { webDavService.copy(expectedDestination, fileId, "T") } returns mockCall
        every { mockCall.execute() } returns mockResponse


        val testObserver = webDavFileProvider
            .transfer(listOf(cloudFile), destFolder, 0, isMove = false, isOverwrite = true)
            .test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()

        verify(exactly = 1) { webDavService.copy(expectedDestination, fileId, "T") }
    }

    // region getFiles

    private fun makeBean(href: String, contentLength: String? = null, contentType: String? = null): WebDavModel.ResponseBean {
        return WebDavModel.ResponseBean().apply {
            setHref(href)
            setContentLength(contentLength)
            setContentType(contentType)
        }
    }

    private fun makeWebDavModel(vararg beans: WebDavModel.ResponseBean): WebDavModel {
        return WebDavModel().apply { list = beans.toList() }
    }

    @Test
    fun `getFiles should return Explorer with files and folders on success`() {

        val id = "http://example.com/dav/files/user/"
        val parentBean = makeBean(id, contentType = "httpd/unix-directory")
        val fileBean = makeBean("${id}test.txt", contentLength = "1024", contentType = "text/plain")
        val folderBean = makeBean("${id}subFolder/", contentType = "httpd/unix-directory")
        val model = makeWebDavModel(parentBean, fileBean, folderBean)

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.success(model)


        val testObserver = webDavFileProvider.getFiles(id, null).test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val explorer = testObserver.values()[0]
        assertEquals(id, explorer.current.id)
        assertEquals("user", explorer.current.title)
        assertEquals(1, explorer.files.size)
        assertEquals(1, explorer.folders.size)
        assertEquals("${id}test.txt", explorer.files[0].id)
        assertEquals("test.txt", explorer.files[0].title)
        assertEquals(id, explorer.files[0].folderId)
        assertEquals(1024L, explorer.files[0].pureContentLength)
        assertEquals("${id}subFolder/", explorer.folders[0].id)
        assertEquals("subFolder", explorer.folders[0].title)
        assertEquals(id, explorer.folders[0].parentId)

        verify(exactly = 1) { webDavService.propfind(id) }
    }

    @Test
    fun `getFiles should return error on HTTP failure`() {

        val id = "http://example.com/dav/files/user/"

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.error(500, mockk(relaxed = true))


        val testObserver = webDavFileProvider.getFiles(id, null).test()


        testObserver.assertError(HttpException::class.java)

        verify(exactly = 1) { webDavService.propfind(id) }
    }

    @Test
    fun `getFiles should only return items matching filterValue`() {

        val id = "http://example.com/dav/files/user/"
        val filter = mapOf(ApiContract.Parameters.ARG_FILTER_VALUE to "test")
        val parentBean = makeBean(id, contentType = "httpd/unix-directory")
        val matchingFile = makeBean("${id}test.txt", contentLength = "512", contentType = "text/plain")
        val otherFile = makeBean("${id}other.txt", contentLength = "256", contentType = "text/plain")
        val matchingFolder = makeBean("${id}testFolder/", contentType = "httpd/unix-directory")
        val otherFolder = makeBean("${id}docs/", contentType = "httpd/unix-directory")
        val model = makeWebDavModel(parentBean, matchingFile, otherFile, matchingFolder, otherFolder)

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.success(model)


        val testObserver = webDavFileProvider.getFiles(id, filter).test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val explorer = testObserver.values()[0]
        assertEquals(1, explorer.files.size)
        assertEquals("test.txt", explorer.files[0].title)
        assertEquals(1, explorer.folders.size)
        assertEquals("testFolder", explorer.folders[0].title)
    }

    @Test
    fun `getFiles should sort files and folders by title when no sort order specified`() {
        val id = "http://example.com/dav/files/user/"
        val filter = mapOf(ApiContract.Parameters.ARG_SORT_BY to ApiContract.Parameters.VAL_SORT_BY_TITLE)
        val parentBean = makeBean(id, contentType = "httpd/unix-directory")
        val fileBeanB = makeBean("${id}b.txt", contentLength = "200", contentType = "text/plain")
        val fileBeanA = makeBean("${id}a.txt", contentLength = "100", contentType = "text/plain")
        val folderBeanB = makeBean("${id}bravo/", contentType = "httpd/unix-directory")
        val folderBeanA = makeBean("${id}alpha/", contentType = "httpd/unix-directory")
        val model = makeWebDavModel(parentBean, fileBeanB, fileBeanA, folderBeanB, folderBeanA)

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.success(model)


        val testObserver = webDavFileProvider.getFiles(id, filter).test()


        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)

        val explorer = testObserver.values()[0]
        // sortBy gives ascending; without reverse → [a.txt, b.txt]
        assertEquals("a.txt", explorer.files[0].title)
        assertEquals("b.txt", explorer.files[1].title)
        assertEquals("alpha", explorer.folders[0].title)
        assertEquals("bravo", explorer.folders[1].title)
    }

    @Test
    fun `getFiles should reverse sort when sort order is ascending`() {

        val id = "http://example.com/dav/files/user/"
        val filter = mapOf(
            ApiContract.Parameters.ARG_SORT_BY to ApiContract.Parameters.VAL_SORT_BY_TITLE,
            ApiContract.Parameters.ARG_SORT_ORDER to ApiContract.Parameters.VAL_SORT_ORDER_ASC
        )
        val parentBean = makeBean(id, contentType = "httpd/unix-directory")
        val fileBeanA = makeBean("${id}a.txt", contentLength = "100", contentType = "text/plain")
        val fileBeanB = makeBean("${id}b.txt", contentLength = "200", contentType = "text/plain")
        val model = makeWebDavModel(parentBean, fileBeanA, fileBeanB)

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.success(model)


        val testObserver = webDavFileProvider.getFiles(id, filter).test()


        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)

        val explorer = testObserver.values()[0]
        // sortBy → [a.txt, b.txt], then reversed → [b.txt, a.txt]
        assertEquals("b.txt", explorer.files[0].title)
        assertEquals("a.txt", explorer.files[1].title)
    }

    @Test
    fun `getFiles should sort files by size`() {

        val id = "http://example.com/dav/files/user/"
        val filter = mapOf(ApiContract.Parameters.ARG_SORT_BY to ApiContract.Parameters.VAL_SORT_BY_SIZE)
        val parentBean = makeBean(id, contentType = "httpd/unix-directory")
        val largefile = makeBean("${id}large.txt", contentLength = "9999", contentType = "text/plain")
        val smallFile = makeBean("${id}small.txt", contentLength = "1", contentType = "text/plain")
        val model = makeWebDavModel(parentBean, largefile, smallFile)

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.success(model)


        val testObserver = webDavFileProvider.getFiles(id, filter).test()


        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)

        val explorer = testObserver.values()[0]
        // sortBy size ascending → [1, 9999]
        assertEquals(1L, explorer.files[0].pureContentLength)
        assertEquals(9999L, explorer.files[1].pureContentLength)
    }

    @Test
    fun `getFiles with only parent folder bean returns empty Explorer`() {

        val id = "http://example.com/dav/files/user/"
        val parentBean = makeBean(id, contentType = "httpd/unix-directory")
        val model = makeWebDavModel(parentBean)

        val mockCall: Call<WebDavModel> = mockk()
        every { webDavService.propfind(id) } returns mockCall
        every { mockCall.execute() } returns Response.success(model)


        val testObserver = webDavFileProvider.getFiles(id, null).test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val explorer = testObserver.values()[0]
        assertEquals(0, explorer.files.size)
        assertEquals(0, explorer.folders.size)
    }

    @Test
    fun `getStatusOperation returns ResponseOperation with empty response list`() {
        val result = webDavFileProvider.getStatusOperation()

        assertTrue(result.response.isEmpty())
    }

    @Test
    fun `terminate returns null`() {
        assertNull(webDavFileProvider.terminate())
    }

    @Test
    fun `delete multiple items should emit single operations list after all succeed`() {

        val fileId1 = "http://example.com/dav/files/user/file1.txt"
        val fileId2 = "http://example.com/dav/files/user/file2.txt"
        val items = listOf<Item>(
            CloudFile().apply { id = fileId1 },
            CloudFile().apply { id = fileId2 }
        )

        val mockCall1: Call<ResponseBody> = mockk()
        val mockCall2: Call<ResponseBody> = mockk()
        every { webDavService.delete(fileId1) } returns mockCall1
        every { webDavService.delete(fileId2) } returns mockCall2
        every { mockCall1.execute() } returns Response.success(204, mockk<ResponseBody>(relaxed = true))
        every { mockCall2.execute() } returns Response.success(204, mockk<ResponseBody>(relaxed = true))


        val testObserver = webDavFileProvider.delete(items, null).test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)

        val operations: List<Operation> = testObserver.values()[0]
        assertEquals(1, operations.size)
        assertEquals(100, operations[0].progress)

        verify(exactly = 1) { webDavService.delete(fileId1) }
        verify(exactly = 1) { webDavService.delete(fileId2) }
    }

    @Test
    fun `delete fails immediately when first item returns error`() {

        val fileId1 = "http://example.com/dav/files/user/file1.txt"
        val fileId2 = "http://example.com/dav/files/user/file2.txt"
        val items = listOf<Item>(
            CloudFile().apply { id = fileId1 },
            CloudFile().apply { id = fileId2 }
        )

        val mockCall1: Call<ResponseBody> = mockk()
        val mockCall2: Call<ResponseBody> = mockk()
        every { webDavService.delete(fileId1) } returns mockCall1
        every { webDavService.delete(fileId2) } returns mockCall2
        every { mockCall1.execute() } returns Response.error(403, mockk(relaxed = true))
        every { mockCall2.execute() } returns Response.success(204, mockk<ResponseBody>(relaxed = true))


        val testObserver = webDavFileProvider.delete(items, null).test()


        testObserver.assertError(HttpException::class.java)
    }

    @Test
    fun `transfer move multiple items emits single operations list`() {

        val destId = "http://example.com/dav/files/dest/"
        val fileId1 = "http://example.com/dav/files/user/file1.txt"
        val fileId2 = "http://example.com/dav/files/user/file2.txt"
        val items = listOf<Item>(
            CloudFile().apply { id = fileId1; title = "file1.txt" },
            CloudFile().apply { id = fileId2; title = "file2.txt" }
        )
        val destFolder = CloudFolder().apply { id = destId }

        val mockCall1: Call<ResponseBody> = mockk()
        val mockCall2: Call<ResponseBody> = mockk()
        every { webDavService.move(destId + "file1.txt", fileId1, "F") } returns mockCall1
        every { webDavService.move(destId + "file2.txt", fileId2, "F") } returns mockCall2
        every { mockCall1.execute() } returns Response.success(mockk())
        every { mockCall2.execute() } returns Response.success(mockk())


        val testObserver = webDavFileProvider
            .transfer(items, destFolder, 0, isMove = true, isOverwrite = false)
            .test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        // buffer(2) collects both responses then emits a single List<Operation>
        testObserver.assertValueCount(1)

        val operations: List<Operation> = testObserver.values()[0]
        assertEquals(1, operations.size)
        assertEquals(100, operations[0].progress)

        verify(exactly = 1) { webDavService.move(destId + "file1.txt", fileId1, "F") }
        verify(exactly = 1) { webDavService.move(destId + "file2.txt", fileId2, "F") }
    }

    @Test
    fun `transfer copy multiple items emits one List per item`() {

        val destId = "http://example.com/dav/files/dest/"
        val fileId1 = "http://example.com/dav/files/user/file1.txt"
        val fileId2 = "http://example.com/dav/files/user/file2.txt"
        val items = listOf<Item>(
            CloudFile().apply { id = fileId1; title = "file1.txt" },
            CloudFile().apply { id = fileId2; title = "file2.txt" }
        )
        val destFolder = CloudFolder().apply { id = destId }

        val mockCall1: Call<ResponseBody> = mockk()
        val mockCall2: Call<ResponseBody> = mockk()
        every { webDavService.copy(destId + "file1.txt", fileId1, "F") } returns mockCall1
        every { webDavService.copy(destId + "file2.txt", fileId2, "F") } returns mockCall2
        every { mockCall1.execute() } returns Response.success(mockk())
        every { mockCall2.execute() } returns Response.success(mockk())


        val testObserver = webDavFileProvider
            .transfer(items, destFolder, 0, isMove = false, isOverwrite = false)
            .test()


        testObserver.assertNoErrors()
        testObserver.assertComplete()
        testObserver.assertValueCount(2)

        testObserver.values().forEach { operations ->
            assertEquals(1, operations.size)
            assertEquals(100, operations[0].progress)
        }

        verify(exactly = 1) { webDavService.copy(destId + "file1.txt", fileId1, "F") }
        verify(exactly = 1) { webDavService.copy(destId + "file2.txt", fileId2, "F") }
    }

}
