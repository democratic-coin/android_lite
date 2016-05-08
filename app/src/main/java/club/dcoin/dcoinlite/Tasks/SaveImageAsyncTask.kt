package club.dcoin.dcoinlite.Tasks

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Created by faraday on 5/7/16.
 * ${PROJECT}
 */

class SaveImageTask(val activity: Activity): AsyncTask<String, Int, Unit>() {

    override fun doInBackground(vararg params: String?) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val url = params[0]
        val uri = Uri.parse("$url&ios=1&first=1")
        val keyUrl = URL(uri.toString()) //you can write here any link
        val file = File(dir, "dcoin-key.png")

        val ucon = keyUrl.openConnection()

        val cookie = CookieManager.getInstance().getCookie(url)
        ucon.addRequestProperty("Cookie", cookie)

        val `is` = ucon.inputStream

        val bitmap = BitmapFactory.decodeStream(`is`)
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)

        fos.flush()
        fos.close()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val mediaScanIntent = Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val contentUri = Uri.fromFile(file)
            mediaScanIntent.data = contentUri
            activity.sendBroadcast(mediaScanIntent)
        } else {
            val fileUri = "file://" + Environment.getExternalStorageDirectory().absolutePath
            activity.sendBroadcast(Intent(
                    Intent.ACTION_MEDIA_MOUNTED,
                    Uri.parse(fileUri)))
        }
    }

    override fun onPostExecute(result: Unit?) {
        super.onPostExecute(result)
        Toast.makeText(activity, "Your key saved into the Gallery", Toast.LENGTH_SHORT)
                .show()
    }
}