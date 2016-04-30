package club.dcoin.dcoinlite

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.app.Fragment
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.regex.Pattern

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment() {
    var webView: WebView? = null
    val httpClient = OkHttpClient()
    var mUploadHandler: UploadHandler? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_main, container, false)

        webView = view.findViewById(R.id.webView) as WebView
        webView?.setWebViewClient(CustomWebClient())
        webView?.setWebChromeClient(CustomChromeClient())
        initializeWebView()
        WebAsyncRequest().execute("http://getpool.dcoin.club")

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {

        if (requestCode == Controller.Result.FILE_SELECTED) {
            // Chose a file from the file picker.
            if (mUploadHandler != null) {
                mUploadHandler!!.onResult(resultCode, intent)
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

    inner class WebAsyncRequest: AsyncTask<String, Int, String>() {

        override fun doInBackground(vararg params: String?): String? {
            val request = Request.Builder().url(params[0]).build()
            val response = httpClient.newCall(request).execute()

            val gson = Gson()
            val json = gson.fromJson(response.body().string(), JsonObject::class.java)
            val pool = json.get("pool").asString

            return pool
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            Log.d("MainActivityFragment", "onCreateView $result")
            webView?.loadUrl(result)
        }
    }

    inner class CustomWebClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val p = Pattern.compile("dcoinKey&password=(.*)$")
            val m = p.matcher(url)
            if (m.find()) {
                try {
                    val thread = Thread(Runnable {
                        try {
                            //File root = android.os.Environment.getExternalStorageDirectory();
                            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                            val keyUrl = URL("http://pool.dcoin.club/ajax?controllerName=dcoinKey&first=1")
                            val file = File(dir, "dcoin-key.png")

                            /* Open a connection to that URL. */
                            val ucon = keyUrl.openConnection()

                            val inputStream = ucon.inputStream

                            val bis = BufferedInputStream(inputStream)

                            val baf = ByteArrayOutputStream(5000)
                            var current: Int = 0
                            while ({ current = bis.read(); current}() != -1) {
                                baf.write(current)
                            }

                            /* Convert the Bytes read to a String. */
                            val fos = FileOutputStream(file)
                            fos.write(baf.toByteArray())
                            fos.flush()
                            fos.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    })
                    thread.start()


                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

            Log.e("JavaGoWV", "shouldOverrideUrlLoading " + url)

            if (url.endsWith(".mp4")) {
                val intetnt = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intetnt)
                return true
            } else {
                return false
            }
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

            return url
        }


        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
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

            AlertDialog.Builder(this@MainActivityFragment.activity).setTitle(newTitle).setMessage(message).setPositiveButton(android.R.string.ok) { dialog, which -> result.confirm() }.setNegativeButton(android.R.string.cancel) { dialog, which -> result.cancel() }.setCancelable(false).create().show()
            return true

            // return super.onJsConfirm(view, url, message, result);
        }

        // Android 3.0
        @JvmOverloads fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String = "") {
            openFileChooser(uploadMsg, "", "filesystem")
        }

        // Android 4.1
        fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) {
            mUploadHandler = UploadHandler(Controller())
            mUploadHandler!!.openFileChooser(uploadMsg, acceptType, capture)
        }
    }// Android 2.x

    inner class Controller {

        val activity: Activity
            get() = this@MainActivityFragment.activity

        object Result {
            val FILE_SELECTED = 4
        }
    }

    class UploadHandler(private val mController: Controller) {
        /*
         * The Object used to inform the WebView of the file to upload.
         */
        private var mUploadMessage: ValueCallback<Uri>? = null
        private var mCameraFilePath: String? = null
        private var mHandled: Boolean = false
        private var mCaughtActivityNotFoundException: Boolean = false

        fun onResult(resultCode: Int, intent: Intent?) {
            if (resultCode == Activity.RESULT_CANCELED && mCaughtActivityNotFoundException) {
                mCaughtActivityNotFoundException = false
                return
            }
            var result: Uri? = if (intent == null || resultCode != Activity.RESULT_OK)
                null
            else
                intent.data
            if (result == null && intent == null && resultCode == Activity.RESULT_OK) {
                val cameraFile = File(mCameraFilePath)
                if (cameraFile.exists()) {
                    result = Uri.fromFile(cameraFile)
                    // Broadcast to the media scanner that we have a new photo
                    // so it will be added into the gallery for the user.
                    mController.activity.sendBroadcast(
                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result))
                }
            }
            mUploadMessage!!.onReceiveValue(result)
            mHandled = true
            mCaughtActivityNotFoundException = false
        }

        fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) {

            Log.d("JavaGoWV", "openFileChooser ValueCallback")

            val imageMimeType = "image/*"
            val videoMimeType = "video/*"
            val audioMimeType = "audio/*"
            val mediaSourceKey = "capture"
            val mediaSourceValueCamera = "camera"
            val mediaSourceValueFileSystem = "filesystem"
            val mediaSourceValueCamcorder = "camcorder"
            val mediaSourceValueMicrophone = "microphone"
            // According to the spec, media source can be 'filesystem' or 'camera' or 'camcorder'
            // or 'microphone' and the default value should be 'filesystem'.
            var mediaSource = mediaSourceValueFileSystem
            if (mUploadMessage != null) {
                // Already a file picker operation in progress.
                return
            }
            mUploadMessage = uploadMsg
            // Parse the accept type.
            val params = acceptType.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val mimeType = params[0]
            if (capture.length > 0) {
                mediaSource = capture
            }

            Log.d("JavaGoWV", "openFileChooser 1 ")
            if (capture == mediaSourceValueFileSystem) {
                for (p in params) {
                    val keyValue = p.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (keyValue.size == 2) {
                        // Process key=value parameters.
                        if (mediaSourceKey == keyValue[0]) {
                            mediaSource = keyValue[1]
                        }
                    }
                }
            }
            Log.d("JavaGoWV", "openFileChooser 2 ")
            //Ensure it is not still set from a previous upload.
            mCameraFilePath = null
            if (mimeType == imageMimeType) {
                if (mediaSource == mediaSourceValueCamera) {
                    startActivity(createCameraIntent())
                    return
                } else {
                    val chooser = createChooserIntent(createCameraIntent())
                    chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(imageMimeType))
                    startActivity(chooser)
                    return
                }
            } else if (mimeType == videoMimeType) {
                if (mediaSource == mediaSourceValueCamcorder) {
                    startActivity(createCamcorderIntent())
                    return
                } else {
                    val chooser = createChooserIntent(createCamcorderIntent())
                    chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(videoMimeType))
                    startActivity(chooser)
                    return
                }
            } else if (mimeType == audioMimeType) {
                if (mediaSource == mediaSourceValueMicrophone) {
                    startActivity(createSoundRecorderIntent())
                    return
                } else {
                    val chooser = createChooserIntent(createSoundRecorderIntent())
                    chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(audioMimeType))
                    startActivity(chooser)
                    return
                }
            }
            Log.d("JavaGoWV", "createDefaultOpenableIntent")
            startActivity(createDefaultOpenableIntent())
        }

        private fun startActivity(intent: Intent) {
            try {
                mController.activity.startActivityForResult(intent, Controller.Result.FILE_SELECTED)
            } catch (e: ActivityNotFoundException) {
                // No installed app was able to handle the intent that
                // we sent, so fallback to the default file upload control.
                try {
                    mCaughtActivityNotFoundException = true
                    mController.activity.startActivityForResult(createDefaultOpenableIntent(),
                            Controller.Result.FILE_SELECTED)
                } catch (e2: ActivityNotFoundException) {
                    // Nothing can return us a file, so file upload is effectively disabled.
                    Toast.makeText(mController.activity, "Upload disabled",
                            Toast.LENGTH_LONG).show()
                }

            }

        }

        private fun createDefaultOpenableIntent(): Intent {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            val uri = Uri.parse(Environment.getExternalStorageDirectory().path + "/download/")
            i.setDataAndType(uri, "*/*")
            Log.d("JavaGoWV", "chooser 0")
            val chooser = createChooserIntent(createCameraIntent(), createCamcorderIntent(),
                    createSoundRecorderIntent())
            chooser.putExtra(Intent.EXTRA_INTENT, i)
            return chooser
        }

        private fun createChooserIntent(vararg intents: Intent): Intent {
            val chooser = Intent(Intent.ACTION_CHOOSER)
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents)
            chooser.putExtra(Intent.EXTRA_TITLE, "Choose file to upload")
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
            mCameraFilePath = cameraDataDir.absolutePath + File.separator +
                    System.currentTimeMillis() + ".jpg"
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(File(mCameraFilePath)))
            return cameraIntent
        }

        private fun createCamcorderIntent(): Intent {
            return Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        }

        private fun createSoundRecorderIntent(): Intent {
            return Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        }
    }

}


