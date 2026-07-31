package io.github.ffalt.doughtime.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.database.AppDatabase;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimerService extends Service {
    private static final String CHANNEL_ID = "TimerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STOP = "io.github.ffalt.doughtime.ACTION_STOP";
    public static final String ACTION_NOTIFICATION_TOGGLE_PAUSE_RESUME =
            "io.github.ffalt.doughtime.ACTION_NOTIFICATION_TOGGLE_PAUSE_RESUME";
    public static final String ACTION_NOTIFICATION_START_NEXT =
            "io.github.ffalt.doughtime.ACTION_NOTIFICATION_START_NEXT";
    public static final String ACTION_NOTIFICATION_CLICK =
            "io.github.ffalt.doughtime.ACTION_NOTIFICATION_CLICK";
    public static final String ACTION_EXACT_ALARM = "io.github.ffalt.doughtime.ACTION_EXACT_ALARM";
    public static final String ACTION_EXACT_ALARM_FIRED = "io.github.ffalt.doughtime.ACTION_EXACT_ALARM_FIRED";
    public static final String EXTRA_TIMER_ID = "io.github.ffalt.doughtime.EXTRA_TIMER_ID";
    public static final String EXTRA_STEP_INDEX = "io.github.ffalt.doughtime.EXTRA_STEP_INDEX";
    private static final int REQUEST_CODE_OFFSET_NOTIFICATION_PAUSE_RESUME = 10_000;
    private static final int REQUEST_CODE_OFFSET_NOTIFICATION_START_NEXT = 20_000;

    private final IBinder binder = new LocalBinder();
    private final java.util.Map<Long, ActiveTimer> activeTimers = new java.util.HashMap<>();
    private final java.util.List<TimerListener> listeners = new java.util.ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor();
    private Ringtone ringtone;
    private Vibrator vibrator;
    private AlarmManager alarmManager;

    public interface TimerListener {
        void onTick(long timerId, long millisUntilFinished);

        void onFinish(long timerId);

        void onStatusChanged(long timerId);
    }

    public static class ActiveTimer {
        public TimerWithSteps timer;
        public int currentStepIndex;
        public long timeLeftInMillis;
        public boolean timerRunning;
        public CountDownTimer countDownTimer;
        public boolean isAlarmPlaying;

        public ActiveTimer(TimerWithSteps timer, int currentStepIndex) {
            this.timer = timer;
            this.currentStepIndex = currentStepIndex;
            this.timeLeftInMillis = timer.steps.get(currentStepIndex).durationSeconds * 1000;
        }
    }

    public class LocalBinder extends Binder {
        public TimerService getService() {
            return TimerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        alarmManager = getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = getSystemService(VibratorManager.class);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = getSystemService(Vibrator.class);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopAllTimers();
                return START_NOT_STICKY;
            }
            if (ACTION_NOTIFICATION_TOGGLE_PAUSE_RESUME.equals(action)) {
                long timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L);
                togglePauseResumeTimer(timerId);
                return START_NOT_STICKY;
            }
            if (ACTION_NOTIFICATION_START_NEXT.equals(action)) {
                long timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L);
                startNextTimerStep(timerId);
                return START_NOT_STICKY;
            }
            if (ACTION_NOTIFICATION_CLICK.equals(action)) {
                handleNotificationClick();
                return START_NOT_STICKY;
            }
            if (ACTION_EXACT_ALARM_FIRED.equals(action)) {
                long timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L);
                int stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1);
                handleExactAlarmFired(timerId, stepIndex);
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    public void stopAllTimers() {
        for (ActiveTimer at : new java.util.ArrayList<>(activeTimers.values())) {
            stopTimer(at.timer.timer.id);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void addListener(TimerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TimerListener listener) {
        listeners.remove(listener);
    }

    public void startTimer(TimerWithSteps timer, int stepIndex) {
        long timerId = timer.timer.id;
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer != null) {
            if (activeTimer.countDownTimer != null) {
                activeTimer.countDownTimer.cancel();
                activeTimer.countDownTimer = null;
            }
            activeTimer.isAlarmPlaying = false;
            cancelExactAlarm(timerId);
        }

        activeTimer = new ActiveTimer(timer, stepIndex);
        activeTimers.put(timerId, activeTimer);

        if (activeTimer.timeLeftInMillis > 0) {
            runTimer(activeTimer);
        } else {
            activeTimer.timerRunning = false;
            for (TimerListener listener : listeners) {
                listener.onFinish(timerId);
            }
            updateNotification();
        }
    }

    private void runTimer(ActiveTimer activeTimer) {
        if (activeTimer.countDownTimer != null) {
            activeTimer.countDownTimer.cancel();
            activeTimer.countDownTimer = null;
        }

        scheduleExactAlarm(activeTimer);

        final long timerId = activeTimer.timer.timer.id;
        activeTimer.countDownTimer = new CountDownTimer(activeTimer.timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                activeTimer.timeLeftInMillis = millisUntilFinished;
                for (TimerListener listener : listeners) {
                    listener.onTick(timerId, millisUntilFinished);
                }
                updateNotification();
            }

            @Override
            public void onFinish() {
                finishTimerStep(activeTimer, activeTimer.currentStepIndex);
            }
        }.start();

        activeTimer.timerRunning = true;
        startForeground(NOTIFICATION_ID, getNotification());
    }

    public void pauseTimer(long timerId) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer != null) {
            if (activeTimer.countDownTimer != null) {
                activeTimer.countDownTimer.cancel();
                activeTimer.countDownTimer = null;
            }
            activeTimer.timerRunning = false;
            cancelExactAlarm(timerId);
            for (TimerListener listener : listeners) {
                listener.onStatusChanged(timerId);
            }
            updateNotification();
        }
    }

    public void resumeTimer(long timerId) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer != null) {
            runTimer(activeTimer);
            for (TimerListener listener : listeners) {
                listener.onStatusChanged(timerId);
            }
        }
    }

    private void togglePauseResumeTimer(long timerId) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer == null) {
            return;
        }

        if (!canTogglePauseResumeFromNotification(
                activeTimer.timerRunning,
                activeTimer.isAlarmPlaying,
                activeTimer.timeLeftInMillis
        )) {
            return;
        }

        if (activeTimer.timerRunning) {
            pauseTimer(timerId);
        } else {
            resumeTimer(timerId);
        }
    }

    private void startNextTimerStep(long timerId) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer == null) {
            return;
        }

        int nextStep = activeTimer.currentStepIndex + 1;
        if (nextStep < activeTimer.timer.steps.size()) {
            stopAlarmOnly(timerId);
            startTimer(activeTimer.timer, nextStep);
            return;
        }

        stopTimer(timerId);
    }

    public void stopTimer(long timerId) {
        ActiveTimer activeTimer = activeTimers.remove(timerId);
        if (activeTimer != null) {
            if (activeTimer.countDownTimer != null) {
                activeTimer.countDownTimer.cancel();
                activeTimer.countDownTimer = null;
            }
            cancelExactAlarm(timerId);
            activeTimer.timerRunning = false;
            if (activeTimer.isAlarmPlaying) {
                activeTimer.isAlarmPlaying = false;
                checkAlarms();
            }
            for (TimerListener listener : listeners) {
                listener.onFinish(timerId);
            }
        }

        if (activeTimers.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else {
            updateNotification();
        }
    }

    public void adjustTimer(long timerId, long deltaMillis) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer == null) {
            return;
        }

        if (activeTimer.isAlarmPlaying) {
            if (deltaMillis > 0) {
                // Stop alarm and start next or same step with delta as initial time
                stopAlarmOnly(timerId);
                activeTimer.timeLeftInMillis = deltaMillis;
                activeTimer.timerRunning = true;
                runTimer(activeTimer);
                for (TimerListener listener : listeners) {
                    listener.onStatusChanged(timerId);
                }
            }
            // Ignore minus when alarm is playing (should be hidden in UI anyway)
            return;
        }

        long newTimeLeft = Math.max(0, activeTimer.timeLeftInMillis + deltaMillis);
        if (newTimeLeft == activeTimer.timeLeftInMillis) {
            return;
        }

        activeTimer.timeLeftInMillis = newTimeLeft;

        if (activeTimer.timerRunning) {
            if (activeTimer.countDownTimer != null) {
                activeTimer.countDownTimer.cancel();
            }
            if (newTimeLeft > 0) {
                runTimer(activeTimer);
            } else {
                finishTimerStep(activeTimer, activeTimer.currentStepIndex);
            }
        } else {
            for (TimerListener listener : listeners) {
                listener.onTick(timerId, newTimeLeft);
            }
        }
        updateNotification();
    }

    private void checkAlarms() {
        boolean anyAlarm = false;
        for (ActiveTimer at : activeTimers.values()) {
            if (at.isAlarmPlaying) {
                anyAlarm = true;
                break;
            }
        }
        if (!anyAlarm) {
            stopAlarm();
        }
    }

    public void stopAlarmOnly(long timerId) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer != null) {
            activeTimer.isAlarmPlaying = false;
        }
        checkAlarms();
    }

    private void handleExactAlarmFired(long timerId, int stepIndex) {
        if (timerId <= 0 || stepIndex < 0) {
            return;
        }

        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer != null) {
            if (!activeTimer.timerRunning && activeTimer.timeLeftInMillis > 0) {
                return;
            }
            finishTimerStep(activeTimer, stepIndex);
            return;
        }

        recoveryExecutor.execute(() -> {
            TimerWithSteps timer = AppDatabase.getDatabase(getApplicationContext())
                    .timerDao()
                    .getTimerWithStepsByIdSync(timerId);
            if (timer == null || timer.steps == null || stepIndex >= timer.steps.size()) {
                return;
            }
            timer.sortSteps();

            mainHandler.post(() -> {
                ActiveTimer existingTimer = activeTimers.get(timerId);
                if (existingTimer != null) {
                    finishTimerStep(existingTimer, stepIndex);
                    return;
                }

                ActiveTimer restoredTimer = new ActiveTimer(timer, stepIndex);
                activeTimers.put(timerId, restoredTimer);
                finishTimerStep(restoredTimer, stepIndex);
            });
        });
    }

    private void finishTimerStep(ActiveTimer activeTimer, int expectedStepIndex) {
        if (activeTimer == null || isStaleAlarmForStep(activeTimer.currentStepIndex, expectedStepIndex)) {
            return;
        }

        if (activeTimer.isAlarmPlaying && !activeTimer.timerRunning && activeTimer.timeLeftInMillis == 0) {
            return;
        }

        long timerId = activeTimer.timer.timer.id;
        if (activeTimer.countDownTimer != null) {
            activeTimer.countDownTimer.cancel();
            activeTimer.countDownTimer = null;
        }

        cancelExactAlarm(timerId);
        activeTimer.timeLeftInMillis = 0;
        activeTimer.timerRunning = false;
        if (!activeTimer.isAlarmPlaying) {
            activeTimer.isAlarmPlaying = true;
            playAlarm();
        }

        for (TimerListener listener : listeners) {
            listener.onFinish(timerId);
        }

        startForeground(NOTIFICATION_ID, getNotification());
        updateNotification();
    }

    private void scheduleExactAlarm(ActiveTimer activeTimer) {
        if (activeTimer == null || activeTimer.timeLeftInMillis <= 0 || alarmManager == null) {
            return;
        }

        long timerId = activeTimer.timer.timer.id;
        cancelExactAlarm(timerId);

        long triggerAtElapsed = computeAlarmTriggerElapsedRealtime(
                SystemClock.elapsedRealtime(),
                activeTimer.timeLeftInMillis
        );

        PendingIntent alarmPendingIntent = getAlarmPendingIntent(
                timerId,
                activeTimer.currentStepIndex,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    alarmPendingIntent
            );
            return;
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    alarmPendingIntent
            );
        } catch (SecurityException ignored) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    alarmPendingIntent
            );
        }
    }

    private void cancelExactAlarm(long timerId) {
        if (alarmManager == null) {
            return;
        }

        PendingIntent existingPendingIntent = getAlarmPendingIntent(
                timerId,
                0,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (existingPendingIntent != null) {
            alarmManager.cancel(existingPendingIntent);
            existingPendingIntent.cancel();
        }
    }

    private PendingIntent getAlarmPendingIntent(long timerId, int stepIndex, int flags) {
        Intent alarmIntent = new Intent(this, TimerAlarmReceiver.class);
        alarmIntent.setAction(ACTION_EXACT_ALARM);
        alarmIntent.putExtra(EXTRA_TIMER_ID, timerId);
        alarmIntent.putExtra(EXTRA_STEP_INDEX, stepIndex);

        return PendingIntent.getBroadcast(this, buildAlarmRequestCode(timerId), alarmIntent, flags);
    }

    static int buildAlarmRequestCode(long timerId) {
        return Long.hashCode(timerId);
    }

    static int buildNotificationActionRequestCode(long timerId, int actionOffset) {
        return (31 * Long.hashCode(timerId)) + actionOffset;
    }

    static long computeAlarmTriggerElapsedRealtime(long nowElapsedRealtime, long timeLeftInMillis) {
        return nowElapsedRealtime + Math.max(timeLeftInMillis, 0);
    }

    static boolean isStaleAlarmForStep(int currentStepIndex, int expectedStepIndex) {
        return currentStepIndex != expectedStepIndex;
    }

    static boolean canTogglePauseResumeFromNotification(
            boolean timerRunning,
            boolean isAlarmPlaying,
            long timeLeftInMillis
    ) {
        return timerRunning || !isAlarmPlaying && timeLeftInMillis > 0;
    }

    private void playAlarm() {
        this.playSound();
        this.vibrate();
    }

    private void playSound() {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (notification == null) {
            notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (notification == null) {
            notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
        if (ringtone != null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ringtone.setAudioAttributes(audioAttributes);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.setLooping(true);
            }
            ringtone.play();
        }
    }

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                long[] pattern = {0, 500, 500};
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                VibrationAttributes vibrationAttributes = new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build();
                vibrator.vibrate(effect, vibrationAttributes);
            } else {
                this.vibrateFallback();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrateFallback() {
        long[] pattern = {0, 500, 500};
        VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        vibrator.vibrate(effect, audioAttributes);
    }

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void handleNotificationClick() {
        boolean alarmWasPlaying = false;
        for (ActiveTimer at : activeTimers.values()) {
            if (at.isAlarmPlaying) {
                stopAlarmOnly(at.timer.timer.id);
                alarmWasPlaying = true;
            }
        }

        Intent intent = new Intent(this, io.github.ffalt.doughtime.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        if (alarmWasPlaying) {
            updateNotification();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_timer_service),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, TimerService.class);
        notificationIntent.setAction(ACTION_NOTIFICATION_CLICK);
        PendingIntent pendingIntent = PendingIntent.getService(this,
                0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = getString(R.string.app_name);
        String contentText;

        if (activeTimers.size() == 1) {
            ActiveTimer at = activeTimers.values().iterator().next();
            title = at.timer.timer.title;
            int hours = (int) (at.timeLeftInMillis / 3600000);
            int minutes = (int) (at.timeLeftInMillis % 3600000 / 60000);
            int seconds = (int) (at.timeLeftInMillis % 60000 / 1000);
            String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
            contentText = at.timerRunning
                    ? at.timer.steps.get(at.currentStepIndex).title
                    : getString(R.string.notification_status_paused_with_time, timeStr);
            if (!at.timerRunning && at.timeLeftInMillis == 0) {
                contentText = getString(R.string.label_time_is_up);
            }
        } else {
            int activeCount = activeTimers.size();
            contentText = getResources().getQuantityString(
                    R.plurals.notification_timers_active,
                    activeCount,
                    activeCount
            );
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true);

        ActiveTimer actionTimer = getNotificationActionTimer();
        if (actionTimer != null) {
            long timerId = actionTimer.timer.timer.id;

            Intent pauseResumeIntent = new Intent(this, TimerService.class);
            pauseResumeIntent.setAction(ACTION_NOTIFICATION_TOGGLE_PAUSE_RESUME);
            pauseResumeIntent.putExtra(EXTRA_TIMER_ID, timerId);
            PendingIntent pauseResumePendingIntent = PendingIntent.getService(
                    this,
                    buildNotificationActionRequestCode(
                            timerId,
                            REQUEST_CODE_OFFSET_NOTIFICATION_PAUSE_RESUME
                    ),
                    pauseResumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Intent startNextIntent = new Intent(this, TimerService.class);
            startNextIntent.setAction(ACTION_NOTIFICATION_START_NEXT);
            startNextIntent.putExtra(EXTRA_TIMER_ID, timerId);
            PendingIntent startNextPendingIntent = PendingIntent.getService(
                    this,
                    buildNotificationActionRequestCode(
                            timerId,
                            REQUEST_CODE_OFFSET_NOTIFICATION_START_NEXT
                    ),
                    startNextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            int pauseResumeIcon = actionTimer.timerRunning ? R.drawable.ic_pause : R.drawable.ic_play;
            int pauseResumeLabel = actionTimer.timerRunning ? R.string.action_pause : R.string.action_resume;
            builder.addAction(pauseResumeIcon, getString(pauseResumeLabel), pauseResumePendingIntent)
                    .addAction(
                            R.drawable.ic_skip_next,
                            getString(R.string.action_start_next),
                            startNextPendingIntent
                    );
        }

        return builder.build();
    }

    @Nullable
    private ActiveTimer getNotificationActionTimer() {
        if (activeTimers.isEmpty()) {
            return null;
        }

        if (activeTimers.size() == 1) {
            return activeTimers.values().iterator().next();
        }

        ActiveTimer alarmTimer = null;
        ActiveTimer runningTimer = null;
        ActiveTimer pausedTimer = null;
        long alarmTimerId = Long.MAX_VALUE;
        long runningTimerId = Long.MAX_VALUE;
        long pausedTimerId = Long.MAX_VALUE;

        for (java.util.Map.Entry<Long, ActiveTimer> entry : activeTimers.entrySet()) {
            long timerId = entry.getKey();
            ActiveTimer activeTimer = entry.getValue();
            if (activeTimer.isAlarmPlaying) {
                if (timerId < alarmTimerId) {
                    alarmTimer = activeTimer;
                    alarmTimerId = timerId;
                }
                continue;
            }
            if (activeTimer.timerRunning) {
                if (timerId < runningTimerId) {
                    runningTimer = activeTimer;
                    runningTimerId = timerId;
                }
                continue;
            }
            if (timerId < pausedTimerId) {
                pausedTimer = activeTimer;
                pausedTimerId = timerId;
            }
        }

        if (alarmTimer != null) {
            return alarmTimer;
        }
        if (runningTimer != null) {
            return runningTimer;
        }
        return pausedTimer;
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, getNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Long timerId : new java.util.ArrayList<>(activeTimers.keySet())) {
            cancelExactAlarm(timerId);
        }
        stopAlarm();
        recoveryExecutor.shutdownNow();
    }

    public ActiveTimer getActiveTimer(long timerId) {
        return activeTimers.get(timerId);
    }

    public java.util.Collection<ActiveTimer> getAllActiveTimers() {
        return activeTimers.values();
    }

    @Deprecated
    public TimerWithSteps getCurrentTimer() {
        return activeTimers.isEmpty() ? null : activeTimers.values().iterator().next().timer;
    }

    @Deprecated
    public int getCurrentStepIndex() {
        return activeTimers.isEmpty() ? 0 : activeTimers.values().iterator().next().currentStepIndex;
    }

    @Deprecated
    public long getTimeLeftInMillis() {
        return activeTimers.isEmpty() ? 0 : activeTimers.values().iterator().next().timeLeftInMillis;
    }

    @Deprecated
    public boolean isTimerRunning() {
        return !activeTimers.isEmpty() && activeTimers.values().iterator().next().timerRunning;
    }
}
