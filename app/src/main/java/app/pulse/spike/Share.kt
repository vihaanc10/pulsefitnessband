package app.pulse.spike

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Opens Android's share menu so the CSV can go to Drive, email, WhatsApp, a USB cable, etc. */
fun shareLogFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export ${file.name}"))
}
