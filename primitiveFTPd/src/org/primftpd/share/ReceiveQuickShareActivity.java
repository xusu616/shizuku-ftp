package org.primftpd.share;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.content.IntentCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.primftpd.R;
import org.primftpd.util.Defaults;
import org.primftpd.util.ServicesStartStopUtil;
import org.primftpd.util.TmpDirType;

import java.util.Collections;
import java.util.List;

public class ReceiveQuickShareActivity extends AbstractReceiveShareActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;

        // Preserve the existing workaround for platform inset issues on API 28/29.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EdgeToEdge.enable(this);
        }
        setContentView(R.layout.receive_share);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        ListView listView = findViewById(R.id.list);
        ViewCompat.setOnApplyWindowInsetsListener((View) listView.getParent(), (v, insetsCompat) -> {
            Insets insets = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return insetsCompat;
        });

        Intent intent = getIntent();
        List<Uri> uris = null;
        List<String> contents = null;
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
            if (uri != null) uris = Collections.singletonList(uri);
            String content = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (content != null) contents = Collections.singletonList(content);
        }
        if (uris == null || uris.isEmpty() || uris.contains(null)) {
            logger.warn("Received quick share without valid stream URIs");
            finish();
            return;
        }
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, uris));
        observeCopyProgress();
        if (!copyModel.hasStarted()) {
            // URI grants may expire after this Activity finishes, so copy to app storage first.
            TargetDir targetDir = new TargetDir(Defaults.buildTmpDir(this, TmpDirType.QUICK_SHARE));
            saveUris(targetDir, uris, contents, intent.getType());
        }
    }

    @Override
    protected void onCopyFinished(TargetDir targetDir) {
        QuickShareBean quickShareBean = new QuickShareBean(targetDir.getTargetPath());
        ServicesStartStopUtil.stopServers(this);
        ServicesStartStopUtil.startServers(this, quickShareBean);
        Toast.makeText(this, R.string.quickShareServerStarted, Toast.LENGTH_SHORT).show();
        finish();
    }
}
