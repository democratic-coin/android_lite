package club.dcoin.dcoinlite

/**
 * Created by faraday on 5/12/16.
 * ${PROJECT}
 */
import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import java.io.File

/**
 * Handle the file upload. This does not support selecting multiple files yet.
 */


class Result {
    companion object {
        val FILE_SELECTED = 4
    }
}

class UploadHandler(private val fragment: Fragment) {
    /*
     * The Object used to inform the WebView of the file to upload.
     */
    private var mUploadMessage: ValueCallback<Uri>? = null
    var filePath: String? = null
        private set
    private var mHandled: Boolean = false
    private var mCaughtActivityNotFoundException = false

    fun onResult(resultCode: Int, intent: Intent?) {
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
                fragment.activity.sendBroadcast(
                        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result))
            }
        }
        mUploadMessage?.onReceiveValue(result)
        mUploadMessage = null
        mHandled = true
        mCaughtActivityNotFoundException = false
    }

    fun openFileChooser(uploadMsg: ValueCallback<Uri>) {
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
            fragment.startActivityForResult(intent, Result.FILE_SELECTED)
        } catch (e: ActivityNotFoundException) {
            try {
                mCaughtActivityNotFoundException = true
                fragment.activity.startActivityForResult(createDefaultOpenableIntent(),
                        Result.FILE_SELECTED)
            } catch (e2: ActivityNotFoundException) {
                // Nothing can return us a file, so file upload is effectively disabled.
                Toast.makeText(fragment.activity, "Upload disabled",
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
        val chooser = createChooserIntent(createCameraIntent())
        chooser.putExtra(Intent.EXTRA_INTENT, i)
        return chooser
    }


    private fun createChooserIntent(vararg intents: Intent): Intent {
        val chooser = Intent(Intent.ACTION_CHOOSER)
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents)
        chooser.putExtra(Intent.EXTRA_TITLE, "Choose upload")
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

class NewUploadHandler(private val fragment: Fragment) {
    /*
     * The Object used to inform the WebView of the file to upload.
     */
    var uploadMessage: ValueCallback<Array<Uri>>? = null
    private var mHandled: Boolean = false
    private var mParams: WebChromeClient.FileChooserParams? = null
    private var mCapturedMedia: Uri? = null

    fun onResult(resultCode: Int, intent: Intent) {
        val uris: Array<Uri>?
        // As the media capture is always supported, we can't use
        // FileChooserParams.parseResult().
        uris = parseResult(resultCode, intent)
        if (uploadMessage == null || uris == null) {
            return
        }
        uploadMessage!!.onReceiveValue(uris)
        mHandled = true
    }

    @TargetApi(21)
    fun openFileChooser(callback: ValueCallback<Array<Uri>>, chooserParams: WebChromeClient.FileChooserParams) {
        if (uploadMessage != null) {
            // Already a file picker operation in progress.
            return
        }
        uploadMessage = callback
        mParams = chooserParams
        val captureIntents = createCaptureIntent()
        assert(captureIntents != null && captureIntents.size > 0)
        val intent: Intent?
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
            fragment.startActivityForResult(intent, Result.FILE_SELECTED)
        } catch (e: ActivityNotFoundException) {
            // No installed app was able to handle the intent that
            // we sent, so file upload is effectively disabled.
            Toast.makeText(fragment.activity, R.string.uploads_disabled,
                    Toast.LENGTH_LONG).show()
        }

    }

    @TargetApi(21)
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
                    createCameraIntent(createTempFileContentUri(".txt"))
            )
        }
        return intents
    }

    private fun createTempFileContentUri(suffix: String): Uri {
        try {
            val mediaPath = File(fragment.activity.filesDir, "captured_media")
            if (!mediaPath.exists() && !mediaPath.mkdir()) {
                throw RuntimeException("Folder cannot be created.")
            }
            val mediaFile = File.createTempFile(
                    System.currentTimeMillis().toString(), suffix, mediaPath)
            return FileProvider.getUriForFile(fragment.activity,
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
        intent.clipData = ClipData.newUri(fragment.activity.contentResolver,
                FILE_PROVIDER_AUTHORITY, mCapturedMedia)
        return intent
    }


    private val IMAGE_MIME_TYPE = "image/*"
    private val FILE_PROVIDER_AUTHORITY = "club.dcoin.dcoinlite.file"
}
