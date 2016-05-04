package club.dcoin.dcoinlite

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.support.v4.app.Fragment
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
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
import java.io.*
import java.net.CookieHandler
import java.net.CookiePolicy
import java.net.URL
import java.util.regex.Pattern

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment() {
    var webView: WebView? = null
    val httpClient = OkHttpClient()
    var mUploadHandler: UploadHandler? = null
    var address: String? = null
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
        WebAsyncRequest().execute("http://getpool.dcoin.club")
        verifyStoragePermissions(activity)
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
            address = result
            webView?.loadUrl(result)
        }
    }

    inner class CustomWebClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Log.d("JavaGoWV: URL =", url)

            if (url.contains("dcoinKey", true)) {
                Log.d("JavaGoWV: FOUND =", url)
                try {
                    val thread = Thread(Runnable {
                        try {
                            //File root = android.os.Environment.getExternalStorageDirectory();
                            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                            Log.d("JavaGoWV", "dir " + dir)
                            val uri = Uri.parse("$url&ios=1&first=1")
                            val keyUrl = URL(uri.toString()) //you can write here any link
                            //URL keyUrl = new URL("http://yandex.ru/"); //you can write here any link
                            val file = File(dir, "dcoin-key.png")

                            Log.d("JavaGoWV", "download begining")
                            Log.d("JavaGoWV", "download keyUrl:" + keyUrl)

                            /* Open a connection to that URL. */
                            val ucon = keyUrl.openConnection()

                            val cookie = CookieManager.getInstance().getCookie(url)
                            Log.d("JavaGoWV", cookie)

                            ucon.addRequestProperty("Cookie", cookie)
                            Log.d("JavaGoWV", "0")
                            /*
                            * Define InputStreams to read from the URLConnection.
                            */
                            val `is` = ucon.inputStream

                            Log.d("JavaGoWV", "01")

                            val bis = BufferedInputStream(`is`)

                            Log.d("JavaGoWV", "1")
                            /*
                            * Read bytes to the Buffer until there is nothing more to read(-1).
                            */
//                            var baf = ByteArrayInputStream(5000)
//                            var current = 0
//                            while ({current = bis.read(); current}() != -1) {
//                                baf.write(current)
//                                current = bis.read()
//                            }
//
//                            /* Convert the Bytes read to a String. */
                            val bitmap = BitmapFactory.decodeStream(`is`)
                            val fos = FileOutputStream(file)
                            Log.d("JavaGoWV", bitmap.toString())
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)

//                            fos.write(baf.toByteArray())
                            fos.flush()
                            fos.close()

                            Log.d("JavaGoWV", "3")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                val mediaScanIntent = Intent(
                                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                val contentUri = Uri.fromFile(file)
                                mediaScanIntent.data = contentUri
                                this@MainActivityFragment.activity.sendBroadcast(mediaScanIntent)
                            } else {
                                val fileUri = "file://" + Environment.getExternalStorageDirectory().absolutePath
                                print("File: ${fileUri.toString()}")
                                this@MainActivityFragment.activity.sendBroadcast(Intent(
                                        Intent.ACTION_MEDIA_MOUNTED,
                                        Uri.parse(fileUri)))
                            }

                            Log.d("JavaGoWV", "4")
                        } catch (e: Exception) {
                            Log.e("JavaGoWV error 0", e.toString())
                            e.printStackTrace()
                        }
                    })
                    thread.start()


                } catch (e: Exception) {
                    Log.e("JavaGoWV error", e.toString())
                    e.printStackTrace()
                }

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


        fun onLoadStarted(view: WebView, url: String) {
            Log.d("Go", "WebView onLoadStarted: " + url)
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

            AlertDialog.Builder(this@MainActivityFragment.activity).setTitle(newTitle).setMessage(message).setPositiveButton(android.R.string.ok) { dialog, which -> result.confirm() }.setNegativeButton(android.R.string.cancel) { dialog, which -> result.cancel() }.setCancelable(false).create().show()
            return true

            // return super.onJsConfirm(view, url, message, result);
        }

        // Android 4.1
        fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String = "", capture: String) {
            mUploadHandler = UploadHandler(Controller())
            mUploadHandler!!.openFileChooser(uploadMsg, acceptType, capture)
        }
    }

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
        var filePath: String? = null
            private set
        private var mHandled: Boolean = false
        private var mCaughtActivityNotFoundException: Boolean = false
        fun handled(): Boolean {
            return mHandled
        }

        fun onResult(resultCode: Int, intent: Intent?) {
            if (resultCode == Activity.RESULT_CANCELED && mCaughtActivityNotFoundException) {
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
                // To maintain backwards compatibility with the previous implementation
                // of the media capture API, if the value of the 'capture' attribute is
                // "filesystem", we should examine the accept-type for a MIME type that
                // may specify a different capture value.
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
            filePath = null
            if (mimeType == imageMimeType) {
                if (mediaSource == mediaSourceValueCamera) {
                    Log.d("JavaGoWV", "001")
                    // Specified 'image/*' and requested the camera, so go ahead and launch the
                    // camera directly.
                    startActivity(createCameraIntent())
                    return
                } else {
                    // Specified just 'image/*', capture=filesystem, or an invalid capture parameter.
                    // In all these cases we show a traditional picker filetered on accept type
                    // so launch an intent for both the Camera and image/* OPENABLE.
                    Log.d("JavaGoWV", "chooser 4")
                    val chooser = createChooserIntent(createCameraIntent())
                    chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(imageMimeType))
                    startActivity(chooser)
                    return
                }
            } else if (mimeType == videoMimeType) {
                if (mediaSource == mediaSourceValueCamcorder) {
                    // Specified 'video/*' and requested the camcorder, so go ahead and launch the
                    // camcorder directly.
                    startActivity(createCamcorderIntent())
                    return
                } else {
                    // Specified just 'video/*', capture=filesystem or an invalid capture parameter.
                    // In all these cases we show an intent for the traditional file picker, filtered
                    // on accept type so launch an intent for both camcorder and video/* OPENABLE.
                    Log.d("JavaGoWV", "chooser 3")
                    val chooser = createChooserIntent(createCamcorderIntent())
                    chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(videoMimeType))
                    startActivity(chooser)
                    return
                }
            } else if (mimeType == audioMimeType) {
                if (mediaSource == mediaSourceValueMicrophone) {
                    // Specified 'audio/*' and requested microphone, so go ahead and launch the sound
                    // recorder.
                    startActivity(createSoundRecorderIntent())
                    return
                } else {
                    // Specified just 'audio/*',  capture=filesystem of an invalid capture parameter.
                    // In all these cases so go ahead and launch an intent for both the sound
                    // recorder and audio/* OPENABLE.
                    Log.d("JavaGoWV", "chooser 2")
                    val chooser = createChooserIntent(createSoundRecorderIntent())
                    chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(audioMimeType))
                    startActivity(chooser)
                    return
                }
            }
            // No special handling based on the accept type was necessary, so trigger the default
            // file upload chooser.
            Log.d("JavaGoWV", "createDefaultOpenableIntent")
            /*
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath()
					+ "/Android/data/org.golang.app/files/");
			Log.d("JavaGoWV", "path ="+Environment.getExternalStorageDirectory().getPath() + "/Android/data/org.golang.app/files/");
			intent.setDataAndType(uri, "**");
			//startActivity(Intent.createChooser(intent, "Open folder"));*/
            startActivity(createDefaultOpenableIntent())
        }

        private fun startActivity(intent: Intent) {
            try {
                mController.activity.startActivityForResult(intent, MainActivityFragment.Controller.Result.FILE_SELECTED)
            } catch (e: ActivityNotFoundException) {
                // No installed app was able to handle the intent that
                // we sent, so fallback to the default file upload control.
                try {
                    mCaughtActivityNotFoundException = true
                    mController.activity.startActivityForResult(createDefaultOpenableIntent(),
                            MainActivityFragment.Controller.Result.FILE_SELECTED)
                } catch (e2: ActivityNotFoundException) {
                    // Nothing can return us a file, so file upload is effectively disabled.
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
            val chooser = createChooserIntent(createCameraIntent(), createCamcorderIntent(),
                    createSoundRecorderIntent())
            chooser.putExtra(Intent.EXTRA_INTENT, i)
            return chooser
        }

        private fun createChooserIntent(vararg intents: Intent): Intent {

            Log.d("JavaGoWV", "createChooserIntent")
            val chooser = Intent(Intent.ACTION_CHOOSER)
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents)
            chooser.putExtra(Intent.EXTRA_TITLE,
                    "Choose upload")
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

        private fun createCamcorderIntent(): Intent {
            return Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        }

        private fun createSoundRecorderIntent(): Intent {
            return Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        }
    }

}


