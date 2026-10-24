package org.primftpd.share;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.IntentCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.primftpd.R;
import org.primftpd.ui.DownloadOrSaveDialogFragment;
import org.primftpd.util.FilenameUnique;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReceiveSaveAsActivity extends AbstractReceiveShareActivity {

    public static final String DIALOG_TAG = "dialogs";

    public static final String PREFIX_HTTP = "http://";
    public static final String PREFIX_HTTPS = "https://";

    private List<Uri> uris;
    private List<String> contents;
    private String type;

    private final ActivityResultLauncher<Uri> targetDirectoryLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::onTargetDirectorySelected);

    @Override
    protected boolean keepWakelock() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.debug("onCreate()");

        // EdgeToEdge on Android pre-15
        // There are some serious insets listener issues on API 28/29,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EdgeToEdge.enable(this);
        }
        // set layout
        setContentView(R.layout.receive_share);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        ListView listView = findViewById(R.id.list);
        ViewCompat.setOnApplyWindowInsetsListener(listView, (v, insetsCompat) -> {
            final Insets insets = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars()
                                                         | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return insetsCompat;
        });

        // read intent
        Intent intent = getIntent();
        String action = intent.getAction();
        type = intent.getType();

        logger.debug("got action: '{}' and type: '{}'", action, type);

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
            if (uri != null) {
                uris = Collections.singletonList(uri);
            }
            String content = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (content != null) {
                contents = Collections.singletonList(content);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri.class);
            contents = intent.getStringArrayListExtra(Intent.EXTRA_TEXT);
        }

        logger.debug("got uris: '{}', contents: '{}'", uris, contents);

        // check for save to file or use download intent
        List<String> strings = new ArrayList<>();
        if (contents != null) {
            strings.addAll(contents);
        }
        if (uris != null) {
            for (Uri uri : uris) {
                strings.add(uri.toString());
            }
        }

        // display uris
        if (uris != null) {
            listView.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    uris
            ));
        } else if (contents != null) {
            listView.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    contents
            ));
        }

        boolean showDownloadDialog = false;
        String singleString = null;
        if (strings.size() == 1) {
            singleString = strings.get(0);
            if (singleString.startsWith(PREFIX_HTTP) || singleString.startsWith(PREFIX_HTTPS)) {
                showDownloadDialog = true;
            }
        }

        if (savedInstanceState != null) {
            return; // FragmentManager and ActivityResultRegistry restore the pending UI/result.
        }
        if (showDownloadDialog) {
            DownloadOrSaveDialogFragment dialog = new DownloadOrSaveDialogFragment();
            dialog.show(getSupportFragmentManager(), DIALOG_TAG);
            Bundle diagArgs = new Bundle();
            diagArgs.putString(DownloadOrSaveDialogFragment.KEY_URL, singleString);
            dialog.setArguments(diagArgs);
        } else {
            prepareSaveToIntent();
        }
    }

    public void prepareSaveToIntent() {
        if (uris != null || contents != null) {
            logger.debug("trying to create SAF intent");
            targetDirectoryLauncher.launch(null);
        }
    }

    private void onTargetDirectorySelected(Uri uri) {
        if (uri == null) {
            return; // The user cancelled the directory picker.
        }
        logger.debug("got SAF uri: '{}'", uri);
        TargetDir targetDir = new TargetDir(this, uri.toString());
        if (uris != null) {
            ProgressDialog progressDialog = createProgressDialog(uris.size());
            saveUris(progressDialog, targetDir, uris, contents, type);
        } else {
            saveContents(targetDir);
        }
    }


    protected void saveContents(TargetDir targetDir) {
        if (contents == null) {
            finish();
            return;
        }
        for (String content : contents) {
            if (content == null) {
                continue;
            }
            OutputStream os = null;
            try {
                String filename = FilenameUnique.filename(null, null, "txt", targetDir, this);
                logger.debug("saving with filename: {}", filename);
                os = targetDir.createOutStream(filename);
                PrintStream ps = new PrintStream(os);
                ps.println(content);
            } catch (Exception e) {
                logger.warn("could not copy shared data", e);
            } finally {
                try {
                    if (os != null) os.close();
                } catch (IOException e) {
                    logger.warn("could not copy shared data", e);
                }
            }
        }
        finish();
    }
}
