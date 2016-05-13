package club.dcoin.dcoinlite

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import club.dcoin.dcoinlite.Tasks.SaveImageTask
import club.dcoin.dcoinlite.Tasks.WebAsyncRequest
import kotlinx.android.synthetic.main.content_main.*
import okhttp3.OkHttpClient
import java.io.File
import java.net.URL

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment() {
    var webView: WebView? = null
    val httpClient = OkHttpClient()
    var mUploadHandler: UploadHandler? = null
    var mNewUploaderHandler: NewUploadHandler? = null
    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )


    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    fun verifyStoragePermissions(activity: Activity) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_main, container, false)

        webView = view.findViewById(R.id.webView) as WebView
        webView?.setWebViewClient(CustomWebClient())
        webView?.setWebChromeClient(CustomChromeClient())
        initializeWebView()
        WebAsyncRequest(httpClient, webView!!).execute("http://getpool.dcoin.club")
        verifyStoragePermissions(activity)
//        webView?.loadUrl("http://imgland.net")
        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        Log.d("JavaGoWV", "Actual fragment onActivityResult $intent")
        if (requestCode == Controller.Result.FILE_SELECTED && intent != null) {
            Log.d("JavaGoWV", "onActivityResult file selected")
            if (mUploadHandler != null) {
                Log.d("JavaGoWV", "mUploadHandler is not null")
                mUploadHandler!!.onResult(resultCode, intent)
            }
            if (mNewUploaderHandler != null) {
                Log.d("JavaGoWV", "mNewUploadHandler is not null")
                mNewUploaderHandler!!.onResult(resultCode, intent)
            }
        }

        super.onActivityResult(requestCode, resultCode, intent)
    }


    fun initializeWebView() {
        val settings = webView?.settings
        settings?.javaScriptEnabled = true
        settings?.allowFileAccessFromFileURLs = true
        settings?.domStorageEnabled = true
        settings?.cacheMode = WebSettings.LOAD_NO_CACHE
        settings?.loadWithOverviewMode = true
        settings?.useWideViewPort = true
        settings?.setSupportZoom(true)
        webView?.clearHistory()
        webView?.clearFormData()
    }


    inner class CustomWebClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Log.d("JavaGoWV: URL =", url)

            if (url.contains("dcoinKey", true)) {
                SaveImageTask(activity).execute(url)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

            Log.e("JavaGoWV", "shouldOverrideUrlLoading " + url)

            if (url.endsWith(".mp4")) {
                val `in` = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(`in`)
                return true
            } else {
                return false
            }
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            Log.d("JavaGoWV", "failed: $failingUrl, error code: $errorCode [$description]")
        }

    }



    inner class CustomChromeClient : WebChromeClient() {

        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
            Log.d("JavaGoWV", String.format("%s @ %d: %s",
                    cm.message(), cm.lineNumber(), cm.sourceId()))
            return true
        }


        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, true, false)
        }


        private fun getTitleFromUrl(url: String): String {
            val title = url
            try {
                val urlObj = URL(url)
                val host = urlObj.host
                if (host != null && !host.isEmpty()) {
                    return urlObj.protocol + "://" + host
                }
                if (url.startsWith("file:")) {
                    val fileName = urlObj.file
                    if (fileName != null && !fileName.isEmpty()) {
                        return fileName
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            return title
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {

            if (message.contains("location")) return false

            val newTitle = getTitleFromUrl(url)

            AlertDialog.Builder(this@MainActivityFragment.activity).setTitle(newTitle).setMessage(message).setPositiveButton(android.R.string.ok) { dialog, which -> result.confirm() }.setCancelable(false).create().show()
            return true
            // return super.onJsAlert(view, url, message, result);
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            if (message.contains("location")) {
                return false
            }

            val newTitle = getTitleFromUrl(url)

            AlertDialog.Builder(this@MainActivityFragment.activity)
                    .setTitle(newTitle).setMessage(message)
                    .setPositiveButton(android.R.string.ok) {
                        dialog, which -> result.confirm()
                    }
                    .setNegativeButton(android.R.string.cancel) {
                        dialog, which -> result.cancel()
                    }
                    .setCancelable(false)
                    .create()
                    .show()
            return true

            // return super.onJsConfirm(view, url, message, result);
        }

        // Android 4.1
        @JvmOverloads
        fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String = "", capture: String) {
            Log.d("JavaGoWV", "openFileChooser")
            mUploadHandler = UploadHandler(Controller(activity))
            mUploadHandler!!.openFileChooser(uploadMsg, acceptType, capture)
        }

        @TargetApi(21)
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
            Log.d("JavaGoWV", "onShowFileChooser")
            mNewUploaderHandler = NewUploadHandler(Controller(activity))
            mNewUploaderHandler!!.openFileChooser(filePathCallback!!, fileChooserParams!!)

            return true
        }
    }

    inner class Controller(val activity: Activity) {
        object Result {
            val FILE_SELECTED = 4
        }
    }

    inner class UploadHandler(private val mController: Controller) {
        /*
         * The Object used to inform the WebView of the file to upload.
         */
        private var mUploadMessage: ValueCallback<Uri>? = null
        var filePath: String? = null
            private set
        private var mHandled: Boolean = false
        private var mCaughtActivityNotFoundException = false

        fun onResult(resultCode: Int, intent: Intent?) {
            Log.d("JavaGoWV", "onResult resultCode = $resultCode")
            if (resultCode == Activity.RESULT_CANCELED || mCaughtActivityNotFoundException) {
                // Couldn't resolve an activity, we are going to try again so skip
                // this result.
                mCaughtActivityNotFoundException = false
                return
            }

            var result: Uri? = if (intent == null || resultCode != Activity.RESULT_OK)
                null
            else
                intent.data
            // As we ask the camera to save the result of the user taking
            // a picture, the camera application does not return anything other
            // than RESULT_OK. So we need to check whether the file we expected
            // was written to disk in the in the case that we
            // did not get an intent returned but did get a RESULT_OK. If it was,
            // we assume that this result has came back from the camera.
            if (result == null && intent == null && resultCode == Activity.RESULT_OK) {
                val cameraFile = File(filePath)
                if (cameraFile.exists()) {
                    result = Uri.fromFile(cameraFile)
                    // Broadcast to the media scanner that we have a new photo
                    // so it will be added into the gallery for the user.
                    mController.activity.sendBroadcast(
                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result))
                }
            }
            mUploadMessage?.onReceiveValue(result)
            mUploadMessage = null
            mHandled = true
            mCaughtActivityNotFoundException = false
        }

        fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) {

            Log.d("JavaGoWV", "openFileChooser ValueCallback")

            val imageMimeType = "image/*"

            mUploadMessage = uploadMsg
            //Ensure it is not still set from a previous upload.
            filePath = null
            val chooser = createChooserIntent(createCameraIntent())
            chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(imageMimeType))
            startActivity(chooser)
        }

        private fun startActivity(intent: Intent) {
            try {
                Log.d("JavaGoWV", "startActivity 0")
                this@MainActivityFragment.startActivityForResult(intent, MainActivityFragment.Controller.Result.FILE_SELECTED)
            } catch (e: ActivityNotFoundException) {
                Log.d("JavaGoWV", "startActivity exception 0")
                try {
                    Log.d("JavaGoWV", "startActivity 1")
                    mCaughtActivityNotFoundException = true
                    mController.activity.startActivityForResult(createDefaultOpenableIntent(),
                            MainActivityFragment.Controller.Result.FILE_SELECTED)
                } catch (e2: ActivityNotFoundException) {
                    // Nothing can return us a file, so file upload is effectively disabled.
                    Log.d("JavaGoWV", "startActivity exception 1")
                    Toast.makeText(mController.activity, "Upload disabled",
                            Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun createDefaultOpenableIntent(): Intent {
            // Create and return a chooser with the default OPENABLE
            // actions including the camera, camcorder and sound
            // recorder where available.
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            val uri = Uri.parse(Environment.getExternalStorageDirectory().path + "/download/")
            i.setDataAndType(uri, "*/*")
            Log.d("JavaGoWV", "chooser 0")
            val chooser = createChooserIntent(createCameraIntent())
            chooser.putExtra(Intent.EXTRA_INTENT, i)
            return chooser
        }


        private fun createChooserIntent(vararg intents: Intent): Intent {
            Log.d("JavaGoWV", "createChooserIntent")
            val chooser = Intent(Intent.ACTION_CHOOSER)
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents)
            chooser.putExtra(Intent.EXTRA_TITLE, "Choose upload")
            Log.d("JavaGoWV", "return chooser")
            return chooser
        }


        private fun createOpenableIntent(type: String): Intent {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = type
            return i
        }


        private fun createCameraIntent(): Intent {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val externalDataDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM)
            val cameraDataDir = File(externalDataDir.absolutePath +
                    File.separator + "browser-photos")
            cameraDataDir.mkdirs()
            filePath = cameraDataDir.absolutePath + File.separator +
                    System.currentTimeMillis() + ".jpg"
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(File(filePath)))
            return cameraIntent
        }
    }

    inner class NewUploadHandler(private val mController: Controller) {
        /*
         * The Object used to inform the WebView of the file to upload.
         */
        var uploadMessage: ValueCallback<Array<Uri>>? = null
        private var mHandled: Boolean = false
        private var mParams: WebChromeClient.FileChooserParams? = null
        private var mCapturedMedia: Uri? = null

        fun onResult(resultCode: Int, intent: Intent) {
            val uris: Array<Uri>?
            Log.d("JavaGoWV", "onResult $resultCode")
            // As the media capture is always supported, we can't use
            // FileChooserParams.parseResult().
            uris = parseResult(resultCode, intent)
            if (uploadMessage == null || uris == null) {
                return
            }
            uploadMessage!!.onReceiveValue(uris)
            mHandled = true
        }

        fun openFileChooser(callback: ValueCallback<Array<Uri>>, chooserParams: WebChromeClient.FileChooserParams) {
            Log.d("JavaGoWV", "onResult $chooserParams $uploadMessage")
            if (uploadMessage != null) {
                // Already a file picker operation in progress.
                return
            }
            uploadMessage = callback
            mParams = chooserParams
            val captureIntents = createCaptureIntent()
            assert(captureIntents != null && captureIntents.size > 0)
            var intent: Intent?
            // Go to the media capture directly if capture is specified, this is the
            // preferred way.
            if (chooserParams.isCaptureEnabled && captureIntents!!.size == 1) {
                intent = captureIntents[0]
            } else {
                intent = Intent(Intent.ACTION_CHOOSER)
                intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, captureIntents)
                intent.putExtra(Intent.EXTRA_INTENT, chooserParams.createIntent())
            }
            startActivity(intent)
        }

        private fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
            if (resultCode == Activity.RESULT_CANCELED) {
                return null
            }
            var result: Uri? = if (intent == null || resultCode != Activity.RESULT_OK)
                null
            else
                intent.data
            // As we ask the camera to save the result of the user taking
            // a picture, the camera application does not return anything other
            // than RESULT_OK. So we need to check whether the file we expected
            // was written to disk in the in the case that we
            // did not get an intent returned but did get a RESULT_OK. If it was,
            // we assume that this result has came back from the camera.
            if (result == null && intent == null && resultCode == Activity.RESULT_OK
                    && mCapturedMedia != null) {
                result = mCapturedMedia
            }

            var uris: Array<Uri>? = null
            if (result != null) {
                uris = arrayOf(result)
            }
            return uris
        }

        private fun startActivity(intent: Intent) {
            try {
                this@MainActivityFragment.startActivityForResult(intent, MainActivityFragment.Controller.Result.FILE_SELECTED)
            } catch (e: ActivityNotFoundException) {
                // No installed app was able to handle the intent that
                // we sent, so file upload is effectively disabled.
                Toast.makeText(mController.activity, R.string.uploads_disabled,
                        Toast.LENGTH_LONG).show()
            }

        }

        private fun createCaptureIntent(): Array<Intent>? {
            var mimeType = "*/*"
            val acceptTypes = mParams!!.acceptTypes
            if (acceptTypes != null && acceptTypes.size > 0) {
                mimeType = acceptTypes[0]
            }
            val intents: Array<Intent>
            if (mimeType == IMAGE_MIME_TYPE) {
                intents = arrayOf(createCameraIntent(createTempFileContentUri(".jpg")))
            } else {
                intents = arrayOf(
                        createCameraIntent(createTempFileContentUri(".jpg"))
                )
            }
            return intents
        }

        private fun createTempFileContentUri(suffix: String): Uri {
            try {
                val mediaPath = File(mController.activity.filesDir, "captured_media")
                if (!mediaPath.exists() && !mediaPath.mkdir()) {
                    throw RuntimeException("Folder cannot be created.")
                }
                val mediaFile = File.createTempFile(
                        System.currentTimeMillis().toString(), suffix, mediaPath)
                return FileProvider.getUriForFile(mController.activity,
                        FILE_PROVIDER_AUTHORITY, mediaFile)
            } catch (e: java.io.IOException) {
                throw RuntimeException(e)
            }

        }

        private fun createCameraIntent(contentUri: Uri?): Intent {
            if (contentUri == null) throw IllegalArgumentException()
            mCapturedMedia = contentUri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedMedia)
            intent.clipData = ClipData.newUri(mController.activity.contentResolver,
                    FILE_PROVIDER_AUTHORITY, mCapturedMedia)
            return intent
        }

        private val IMAGE_MIME_TYPE = "image/*"
        private val FILE_PROVIDER_AUTHORITY = "club.dcoin.dcoinlite.file"
    }
}


