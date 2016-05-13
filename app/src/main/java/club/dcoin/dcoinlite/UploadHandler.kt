package club.dcoin.dcoinlite

/**
 * Created by faraday on 5/12/16.
 * ${PROJECT}
 */
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.util.Log
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.ValueCallback
import android.widget.Toast
import java.io.File

/**
 * Handle the file upload. This does not support selecting multiple files yet.
 */


class NewUploadHandler(private val mController: MainActivityFragment.Controller) {
    /*
     * The Object used to inform the WebView of the file to upload.
     */
    private var mUploadMessage: ValueCallback<Array<Uri>>? = null
    private var mHandled: Boolean = false
    private var mParams: FileChooserParams? = null
    private var mCapturedMedia: Uri? = null

    fun onResult(resultCode: Int, intent: Intent) {
        val uris: Array<Uri>?
        Log.d("JavaGoWV", "onResult $resultCode")
        // As the media capture is always supported, we can't use
        // FileChooserParams.parseResult().
        uris = parseResult(resultCode, intent)
        mUploadMessage!!.onReceiveValue(uris)
        mHandled = true
    }

    fun openFileChooser(callback: ValueCallback<Array<Uri>>, chooserParams: FileChooserParams, capture: String) {
        Log.d("JavaGoWV", "onResult $chooserParams $mUploadMessage")
        if (mUploadMessage != null) {
            // Already a file picker operation in progress.
            return
        }
        mUploadMessage = callback
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
            mController.activity.startActivityForResult(intent, MainActivityFragment.Controller.Result.FILE_SELECTED)
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

    companion object {
        private val IMAGE_MIME_TYPE = "image/*"
        private val FILE_PROVIDER_AUTHORITY = "club.dcoin.dcoinlite.file"
    }
}
