package io.github.ffalt.doughtime.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.database.AppDatabase;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimerService extends Service {
    private static final String TAG = TimerService.class.getSimpleName();
    private static final String CHANNEL_ID = "TimerServiceChannel";
    private static final String ALARM_CHANNEL_ID = "TimerAlarmChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int ALARM_NOTIFICATION_ID = 2;
    public static final String ACTION_NOTIFICATION_TOGGLE_PAUSE_RESUME =
            "io.github.ffalt.doughtime.ACTION_NOTIFICATION_TOGGLE_PAUSE_RESUME";
    public static final String ACTION_NOTIFICATION_START_NEXT =
            "io.github.ffalt.doughtime.ACTION_NOTIFICATION_START_NEXT";
    public static final String ACTION_EXACT_ALARM = "io.github.ffalt.doughtime.ACTION_EXACT_ALARM";
    public static final String ACTION_EXACT_ALARM_FIRED = "io.github.ffalt.doughtime.ACTION_EXACT_ALARM_FIRED";
    public static final String EXTRA_TIMER_ID = "io.github.ffalt.doughtime.EXTRA_TIMER_ID";
    public static final String EXTRA_STEP_INDEX = "io.github.ffalt.doughtime.EXTRA_STEP_INDEX";
    private static final int REQUEST_CODE_OFFSET_NOTIFICATION_PAUSE_RESUME = 10_000;
    private static final int REQUEST_CODE_OFFSET_NOTIFICATION_START_NEXT = 20_000;
    private static final int REQUEST_CODE_OFFSET_NOTIFICATION_CONTENT = 30_000;
    private static final int REQUEST_CODE_OFFSET_ALARM_CONTENT = 40_000;
    private static final long NO_ALARM_NOTIFICATION = -1L;
    private static final long ALARM_TIMEOUT_MILLIS = 600_000L;

    private final IBinder binder = new LocalBinder();
    private final java.util.Map<Long, ActiveTimer> activeTimers = new java.util.HashMap<>();
    private final java.util.List<TimerListener> listeners = new java.util.ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor();
    private MediaPlayer alarmPlayer;
    private Vibrator vibrator;
    private AlarmManager alarmManager;
    private PowerManager.WakeLock alarmWakeLock;
    private long alarmNotificationTimerId = NO_ALARM_NOTIFICATION;
    private final Runnable alarmTimeoutRunnable = this::timeOutAlarm;
    private LiveData<java.util.List<TimerWithSteps>> storedTimers;
    private final Observer<java.util.List<TimerWithSteps>> storedTimersObserver = this::syncWithStoredTimers;

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
        storedTimers = AppDatabase.getDatabase(this).timerDao().getAllTimersWithSteps();
        storedTimers.observeForever(storedTimersObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
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
            if (ACTION_EXACT_ALARM_FIRED.equals(action)) {
                startForeground(NOTIFICATION_ID, getNotification());
                long timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L);
                int stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1);
                handleExactAlarmFired(timerId, stepIndex);
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
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
        if (!hasStep(timer, stepIndex)) {
            return;
        }

        long timerId = timer.timer.id;
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer != null) {
            if (activeTimer.countDownTimer != null) {
                activeTimer.countDownTimer.cancel();
                activeTimer.countDownTimer = null;
            }
            activeTimer.isAlarmPlaying = false;
            checkAlarms();
            cancelExactAlarm(timerId);
        }

        activeTimer = new ActiveTimer(timer, stepIndex);
        activeTimers.put(timerId, activeTimer);

        if (activeTimer.timeLeftInMillis > 0) {
            runTimer(activeTimer);
            for (TimerListener listener : listeners) {
                listener.onStatusChanged(timerId);
            }
        } else {
            activeTimer.timerRunning = false;
            for (TimerListener listener : listeners) {
                listener.onFinish(timerId);
            }
            updateNotification();
        }
    }

    private void runTimer(ActiveTimer activeTimer) {
        if (activeTimer.timeLeftInMillis <= 0) {
            return;
        }

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
                if (activeTimer == getNotificationTimer()) {
                    updateNotification();
                }
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

    private void stopSelfIfNoTimers() {
        if (activeTimers.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void syncWithStoredTimers(java.util.List<TimerWithSteps> stored) {
        for (ActiveTimer activeTimer : new java.util.ArrayList<>(activeTimers.values())) {
            long timerId = activeTimer.timer.timer.id;
            TimerWithSteps storedTimer = findStoredTimer(stored, timerId);
            if (storedTimer == null) {
                stopTimer(timerId);
                continue;
            }
            if (!hasStep(storedTimer, activeTimer.currentStepIndex)) {
                continue;
            }

            storedTimer.sortSteps();
            activeTimer.timer = storedTimer;
            for (TimerListener listener : listeners) {
                listener.onStatusChanged(timerId);
            }
        }

        if (!activeTimers.isEmpty()) {
            updateNotification();
        }
    }

    @Nullable
    private static TimerWithSteps findStoredTimer(java.util.List<TimerWithSteps> stored, long timerId) {
        for (TimerWithSteps storedTimer : stored) {
            if (storedTimer.timer.id == timerId) {
                return storedTimer;
            }
        }
        return null;
    }

    public void adjustTimer(long timerId, long deltaMillis) {
        ActiveTimer activeTimer = activeTimers.get(timerId);
        if (activeTimer == null) {
            return;
        }

        if (activeTimer.isAlarmPlaying) {
            if (deltaMillis > 0) {
                stopAlarmOnly(timerId);
                activeTimer.timeLeftInMillis = deltaMillis;
                activeTimer.timerRunning = true;
                runTimer(activeTimer);
                for (TimerListener listener : listeners) {
                    listener.onStatusChanged(timerId);
                }
            }
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
        ActiveTimer notificationTimer = getNotificationTimer();
        if (notificationTimer == null || !notificationTimer.isAlarmPlaying) {
            stopAlarm();
            return;
        }
        if (notificationTimer.timer.timer.id != alarmNotificationTimerId) {
            postAlarmNotification(notificationTimer);
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
            stopSelfIfNoTimers();
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
            if (!hasStep(timer, stepIndex)) {
                mainHandler.post(this::stopSelfIfNoTimers);
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
        checkAlarms();

        for (TimerListener listener : listeners) {
            listener.onFinish(timerId);
        }

        startForeground(NOTIFICATION_ID, getNotification());
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

    static boolean hasStep(TimerWithSteps timer, int stepIndex) {
        return timer != null
                && timer.steps != null
                && stepIndex >= 0
                && stepIndex < timer.steps.size();
    }

    static String formatTimeLeft(long timeLeftInMillis) {
        long safeTimeLeft = Math.max(0, timeLeftInMillis);
        int hours = (int) (safeTimeLeft / 3600000);
        int minutes = (int) (safeTimeLeft % 3600000 / 60000);
        int seconds = (int) (safeTimeLeft % 60000 / 1000);
        return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
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
        this.acquireAlarmWakeLock();
        mainHandler.removeCallbacks(alarmTimeoutRunnable);
        mainHandler.postDelayed(alarmTimeoutRunnable, ALARM_TIMEOUT_MILLIS);
    }

    private void timeOutAlarm() {
        for (ActiveTimer activeTimer : new java.util.ArrayList<>(activeTimers.values())) {
            if (!activeTimer.isAlarmPlaying) {
                continue;
            }
            activeTimer.isAlarmPlaying = false;
            for (TimerListener listener : listeners) {
                listener.onStatusChanged(activeTimer.timer.timer.id);
            }
        }
        checkAlarms();
        updateNotification();
    }

    @SuppressWarnings("deprecation")
    private void acquireAlarmWakeLock() {
        if (alarmWakeLock != null && alarmWakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = getSystemService(PowerManager.class);
        alarmWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "doughtime:alarm"
        );
        alarmWakeLock.acquire(10_000L);
    }

    private void releaseAlarmWakeLock() {
        if (alarmWakeLock != null && alarmWakeLock.isHeld()) {
            alarmWakeLock.release();
        }
        alarmWakeLock = null;
    }

    private void playSound() {
        if (alarmPlayer != null && alarmPlayer.isPlaying()) {
            return;
        }

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        if (alarmSound == null) {
            return;
        }

        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(this, alarmSound);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setLooping(true);
            player.prepare();
            player.start();
            alarmPlayer = player;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "Could not play the alarm sound", e);
            player.release();
        }
    }

    private void stopSound() {
        if (alarmPlayer == null) {
            return;
        }
        alarmPlayer.stop();
        alarmPlayer.release();
        alarmPlayer = null;
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
        mainHandler.removeCallbacks(alarmTimeoutRunnable);
        stopSound();
        if (vibrator != null) {
            vibrator.cancel();
        }
        releaseAlarmWakeLock();
        cancelAlarmNotification();
    }

    private void postAlarmNotification(ActiveTimer activeTimer) {
        PendingIntent contentIntent = buildTimerContentIntent(
                activeTimer.timer.timer.id,
                REQUEST_CODE_OFFSET_ALARM_CONTENT
        );
        Notification notification = new NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle(activeTimer.timer.timer.title)
                .setContentText(getString(R.string.label_time_is_up))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(contentIntent, true)
                .build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(ALARM_NOTIFICATION_ID, notification);
        alarmNotificationTimerId = activeTimer.timer.timer.id;
    }

    private void cancelAlarmNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(ALARM_NOTIFICATION_ID);
        alarmNotificationTimerId = NO_ALARM_NOTIFICATION;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_timer_service),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationChannel alarmChannel = new NotificationChannel(
                ALARM_CHANNEL_ID,
                getString(R.string.notification_channel_timer_alarm),
                NotificationManager.IMPORTANCE_HIGH
        );
        alarmChannel.setSound(null, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(alarmChannel);
    }

    private PendingIntent buildTimerContentIntent(long timerId, int requestCodeOffset) {
        Intent timerIntent = new Intent(this, io.github.ffalt.doughtime.ui.timer.TimerRunActivity.class);
        timerIntent.putExtra("TIMER_ID", timerId);
        timerIntent.putExtra(io.github.ffalt.doughtime.ui.timer.TimerRunActivity.EXTRA_AUTO_START, false);
        return TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(timerIntent)
                .getPendingIntent(
                        buildNotificationActionRequestCode(timerId, requestCodeOffset),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
    }

    private Notification getNotification() {
        ActiveTimer notificationTimer = getNotificationTimer();

        PendingIntent pendingIntent;
        if (notificationTimer != null) {
            pendingIntent = buildTimerContentIntent(
                    notificationTimer.timer.timer.id,
                    REQUEST_CODE_OFFSET_NOTIFICATION_CONTENT
            );
        } else {
            Intent mainIntent = new Intent(this, io.github.ffalt.doughtime.MainActivity.class);
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        String title = getString(R.string.app_name);
        String contentText = null;

        if (notificationTimer != null) {
            title = notificationTimer.timer.timer.title;
            String timeStr = formatTimeLeft(notificationTimer.timeLeftInMillis);
            contentText = notificationTimer.timerRunning
                    ? getString(
                            R.string.notification_running_with_time,
                            notificationTimer.timer.steps.get(notificationTimer.currentStepIndex).displayTitle(),
                            timeStr
                    )
                    : getString(R.string.notification_status_paused_with_time, timeStr);
            if (!notificationTimer.timerRunning && notificationTimer.timeLeftInMillis == 0) {
                contentText = getString(R.string.label_time_is_up);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true);

        int activeCount = activeTimers.size();
        if (activeCount > 1) {
            builder.setSubText(getResources().getQuantityString(
                    R.plurals.notification_timers_active,
                    activeCount,
                    activeCount
            ));
        }

        if (notificationTimer != null) {
            long timerId = notificationTimer.timer.timer.id;

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

            int pauseResumeIcon = notificationTimer.timerRunning ? R.drawable.ic_pause : R.drawable.ic_play;
            int pauseResumeLabel = notificationTimer.timerRunning ? R.string.action_pause : R.string.action_resume;
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
    private ActiveTimer getNotificationTimer() {
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
        storedTimers.removeObserver(storedTimersObserver);
        for (ActiveTimer activeTimer : new java.util.ArrayList<>(activeTimers.values())) {
            if (activeTimer.countDownTimer != null) {
                activeTimer.countDownTimer.cancel();
                activeTimer.countDownTimer = null;
            }
            cancelExactAlarm(activeTimer.timer.timer.id);
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
}
