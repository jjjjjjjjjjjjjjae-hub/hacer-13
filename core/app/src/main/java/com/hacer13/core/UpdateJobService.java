package com.hacer13.core;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Periodic, user-enabled data-only GitHub update check.
 * Android decides the exact execution time; no constant background process.
 */
public class UpdateJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        UpdateEngine.check(getApplicationContext(), true, result -> {
            // UpdateEngine already checks the Ed25519 signature before accepting any data.
            jobFinished(params, false);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        return true; // retry if Android interrupts a scheduled check
    }
}
