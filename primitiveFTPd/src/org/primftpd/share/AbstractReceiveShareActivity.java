package org.primftpd.share;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import org.primftpd.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractReceiveShareActivity extends FragmentActivity {
    private static final String COPY_STARTED = "share.copy.started";
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected ShareCopyViewModel copyModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        copyModel = new ViewModelProvider(this).get(ShareCopyViewModel.class);
        // A ViewModel survives rotation, but not process death. Do not duplicate partial copies.
        if (savedInstanceState != null && savedInstanceState.getBoolean(COPY_STARTED)
                && !copyModel.hasStarted()) {
            Toast.makeText(this, R.string.share_copy_interrupted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    protected void observeCopyProgress() {
        ProgressBar progressBar = findViewById(R.id.copy_progress);
        TextView progressText = findViewById(R.id.copy_progress_text);
        copyModel.getProgress().observe(this, progress -> {
            progressBar.setVisibility(View.VISIBLE);
            progressText.setVisibility(View.VISIBLE);
            progressBar.setMax(progress.getTotal());
            progressBar.setProgress(progress.getCompleted());
            progressText.setText(getString(R.string.share_copy_progress,
                    progress.getCompleted(), progress.getTotal()));
            if (progress.getFinished()) {
                if (progress.getFailures() > 0) {
                    Toast.makeText(this, getString(R.string.share_copy_failed,
                            progress.getFailures()), Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    onCopyFinished(copyModel.getTargetDir());
                }
            }
        });
    }

    protected void saveUris(TargetDir targetDir, List<Uri> uris,
                            List<String> contents, String type) {
        copyModel.start(targetDir, uris, contents, type);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(COPY_STARTED, copyModel.hasStarted());
        super.onSaveInstanceState(outState);
    }

    protected void onCopyFinished(TargetDir targetDir) {
        finish();
    }
}
