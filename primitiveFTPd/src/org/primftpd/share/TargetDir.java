package org.primftpd.share;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import androidx.documentfile.provider.DocumentFile;

public class TargetDir {

    private File targetPath;
    private Uri targetSafUrl;
    private Context context;

    public TargetDir(File targetPath) {
        this.targetPath = targetPath;
    }

    public TargetDir(Context context, String targetSafUrl) {
        this.context = context.getApplicationContext();
        this.targetSafUrl = Uri.parse(targetSafUrl);
    }

    public File getTargetPath() {
        return targetPath;
    }

    public boolean doesFilenameExist(String filename) {
        if (targetPath != null) {
            return doesFilenameExistFs(filename);
        } else if (targetSafUrl != null) {
            return doesFilenameExistSaf(filename);
        }
        return false;
    }

    OutputStream createOutStream(String filename) throws IOException {
        if (targetPath != null) {
            return createOutStreamFs(filename);
        } else if (targetSafUrl != null) {
            return createOutStreamSaf(filename);
        }
        throw new IllegalStateException("Target directory is not configured");
    }

    private boolean doesFilenameExistFs(String filename) {
        return new File(targetPath, filename).exists();
    }

    private boolean doesFilenameExistSaf(String filename) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, targetSafUrl);
        DocumentFile[] children = documentFile.listFiles();
        for (DocumentFile docFile : children) {
            if (docFile.getName().equals(filename)) {
                return true;
            }
        }
        return false;
    }

    private OutputStream createOutStreamFs(String filename) throws IOException {
        return new FileOutputStream(new File(targetPath, filename));
    }

    private OutputStream createOutStreamSaf(String filename) throws IOException {
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, targetSafUrl);
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        DocumentFile file = documentFile.createFile(mimeType, filename);
        if (file == null) {
            throw new IOException("Could not create target file: " + filename);
        }
        ContentResolver contentResolver = context.getContentResolver();
        OutputStream stream = contentResolver.openOutputStream(file.getUri());
        if (stream == null) {
            throw new IOException("Provider could not open target file: " + filename);
        }
        return stream;
    }
}
