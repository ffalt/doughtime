package io.github.ffalt.doughtime.ui.timer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import io.github.ffalt.doughtime.service.TimerService;
import io.github.ffalt.doughtime.ui.main.TimerViewModel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class TimerRunActivity extends AppCompatActivity implements TimerService.TimerListener {
    private static final int PREVIEW_DETAILS_LEFT_COLUMN_WIDTH = 24;
    private static final int PREVIEW_DETAILS_RIGHT_COLUMN_WIDTH = 14;

    private TimerService timerService;
    private boolean isBound = false;
    private long timerId;
    private TimerWithSteps timerWithSteps;

    private TextView textStepTitle;
    private TextView textCountdown;
    private TextView textStepDescription;
    private MaterialButton buttonMinus5;
    private MaterialButton buttonPlus5;
    private MaterialButton buttonStop;
    private LinearLayout layoutNextStepsItems;
    private LinearLayout layoutAlarmControls;
    private LinearLayout layoutActiveControls;
    private MaterialButton buttonPauseResumeIcon;
    private MaterialButton buttonResetIcon;
    private MaterialButton buttonAlarmOk;
    private MaterialButton buttonStartNext;
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private Runnable repeatRunnable;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.LocalBinder binder = (TimerService.LocalBinder) service;
            timerService = binder.getService();
            isBound = true;
            timerService.addListener(TimerRunActivity.this);
            
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer == null) {
                loadTimerAndStart();
            } else {
                timerWithSteps = activeTimer.timer;
                updateUI();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private void setupAdjustmentButtons() {
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            private static final long INITIAL_DELAY = 500;
            private static final long REPEAT_INTERVAL = 100;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int id = v.getId();
                long delta = (id == R.id.button_plus_5) ? 300_000L : -300_000L;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (repeatRunnable != null) {
                            repeatHandler.removeCallbacks(repeatRunnable);
                        }
                        
                        // Perform first adjustment
                        adjustTimer(delta);
                        
                        repeatRunnable = new Runnable() {
                            @Override
                            public void run() {
                                adjustTimer(delta);
                                repeatHandler.postDelayed(this, REPEAT_INTERVAL);
                            }
                        };
                        repeatHandler.postDelayed(repeatRunnable, INITIAL_DELAY);
                        v.setPressed(true);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (repeatRunnable != null) {
                            repeatHandler.removeCallbacks(repeatRunnable);
                            repeatRunnable = null;
                        }
                        v.setPressed(false);
                        return true;
                }
                return false;
            }
        };

        buttonMinus5.setOnTouchListener(touchListener);
        buttonPlus5.setOnTouchListener(touchListener);
    }

    private void adjustTimer(long deltaMillis) {
        if (timerService != null) {
            timerService.adjustTimer(timerId, deltaMillis);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer_run);

        timerId = getIntent().getLongExtra("TIMER_ID", -1);

        textStepTitle = findViewById(R.id.text_current_step_title);
        textCountdown = findViewById(R.id.text_countdown);
        buttonMinus5 = findViewById(R.id.button_minus_5);
        buttonPlus5 = findViewById(R.id.button_plus_5);
        textStepDescription = findViewById(R.id.text_current_step_description);
        layoutNextStepsItems = findViewById(R.id.layout_next_steps_items);
        layoutAlarmControls = findViewById(R.id.layout_alarm_controls);
        layoutActiveControls = findViewById(R.id.layout_active_controls);
        buttonPauseResumeIcon = findViewById(R.id.button_pause_resume_icon);
        buttonResetIcon = findViewById(R.id.button_reset_icon);
        buttonStop = findViewById(R.id.button_stop_icon);
        buttonAlarmOk = findViewById(R.id.button_alarm_ok);
        buttonStartNext = findViewById(R.id.button_start_next);

        buttonPauseResumeIcon.setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                if (activeTimer.timerRunning) {
                    timerService.pauseTimer(timerId);
                } else {
                    timerService.resumeTimer(timerId);
                }
            }
        });


        buttonStop.setOnClickListener(v -> showStopConfirmationDialog());

        buttonStartNext.setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                int nextStep = activeTimer.currentStepIndex + 1;
                if (nextStep < timerWithSteps.steps.size()) {
                    timerService.stopAlarmOnly(timerId);
                    timerService.startTimer(timerWithSteps, nextStep);
                    layoutAlarmControls.setVisibility(View.GONE);
                    layoutActiveControls.setVisibility(View.VISIBLE);
                    updateUI();
                } else {
                    timerService.stopTimer(timerId);
                    finish();
                }
            }
        });

        buttonAlarmOk.setOnClickListener(v -> {
            if (timerService != null) {
                timerService.stopAlarmOnly(timerId);
                updateUI();
            }
        });

        buttonResetIcon.setOnClickListener(v -> {
            TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
            if (activeTimer != null) {
                timerService.startTimer(timerWithSteps, activeTimer.currentStepIndex);
                updateUI();
            }
        });

        setupAdjustmentButtons();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar).getRootView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return windowInsets;
        });

        startTimerService();
    }

    private void startTimerService() {
        Intent intent = new Intent(this, TimerService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void loadTimerAndStart() {
        TimerViewModel viewModel = new ViewModelProvider(this).get(TimerViewModel.class);
        viewModel.getAllTimers().observe(this, timers -> {
            for (TimerWithSteps t : timers) {
                if (t.timer.id == timerId) {
                    timerWithSteps = t;
                    timerService.startTimer(t, 0);
                    updateUI();
                    break;
                }
            }
        });
    }

    private void showStopConfirmationDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_stop_timer_title)
                .setMessage(R.string.dialog_stop_timer_message)
                .setPositiveButton(R.string.dialog_stop_timer_positive, (dialog, which) -> {
                    if (timerService != null) {
                        timerService.stopTimer(timerId);
                    }
                    finish();
                })
                .setNegativeButton(R.string.dialog_stop_timer_negative, null)
                .show();
    }

    private void updateUI() {
        if (timerWithSteps == null || timerService == null) {
            return;
        }

        TimerService.ActiveTimer activeTimer = timerService.getActiveTimer(timerId);
        if (activeTimer == null) {
            return;
        }

        int currentIndex = activeTimer.currentStepIndex;
        TimerStep currentStep = timerWithSteps.steps.get(currentIndex);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(timerWithSteps.timer.title);

        textStepTitle.setText(getString(
                R.string.active_timer_step,
                currentIndex + 1,
                currentStep.title
        ));
        textStepDescription.setText(currentStep.description);
        if (currentStep.description == null || currentStep.description.trim().isEmpty()) {
            textStepDescription.setVisibility(View.GONE);
        } else {
            textStepDescription.setVisibility(View.VISIBLE);
        }

        renderNextStepItems(buildStepItemPreviews(
                timerWithSteps.steps,
                currentIndex,
                activeTimer.timeLeftInMillis,
                System.currentTimeMillis(),
                getString(R.string.label_current_step),
                getString(R.string.label_duration),
                getString(R.string.label_ends_at)
        ));

        long timeLeft = activeTimer.timeLeftInMillis;
        updateCountdown(timeLeft);
        
        if (timeLeft == 0 && !activeTimer.timerRunning) {
            layoutActiveControls.setVisibility(View.GONE);
            layoutAlarmControls.setVisibility(View.VISIBLE);
            buttonMinus5.setVisibility(View.GONE);
            
            // Show OK button only if alarm is actually playing
            if (activeTimer.isAlarmPlaying) {
                buttonAlarmOk.setVisibility(View.VISIBLE);
                buttonStartNext.setVisibility(View.GONE);
            } else {
                buttonAlarmOk.setVisibility(View.GONE);
                buttonStartNext.setVisibility(View.VISIBLE);
            }
        } else {
            layoutActiveControls.setVisibility(View.VISIBLE);
            layoutAlarmControls.setVisibility(View.GONE);
            buttonMinus5.setVisibility(View.VISIBLE);
            buttonPauseResumeIcon.setIconResource(activeTimer.timerRunning ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void updateCountdown(long millisUntilFinished) {
        int hours = (int) (millisUntilFinished / 3600000);
        int minutes = (int) (millisUntilFinished % 3600000 / 60000);
        int seconds = (int) (millisUntilFinished % 60000 / 1000);
        textCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    private static List<StepPreviewItem> buildStepItemPreviews(
            List<TimerStep> steps,
            int currentStepIndex,
            long currentStepTimeLeftInMillis,
            long nowInMillis,
            String currentLabel,
            String durationLabel,
            String endsAtLabel
    ) {
        List<StepPreviewItem> itemPreviews = new ArrayList<>();
        if (steps.isEmpty() || currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return itemPreviews;
        }

        // Calculate all end times relative to current moment
        long[] endTimes = new long[steps.size()];

        // For steps before current, we don't know exactly when they ended relative to now
        // if we just have currentStepTimeLeft. But we can estimate based on durations
        // for visualization purposes. Actually, it might be better to show them as "Past".
        
        long currentStepEndTime = nowInMillis + Math.max(currentStepTimeLeftInMillis, 0);
        endTimes[currentStepIndex] = currentStepEndTime;
        
        // Future steps
        long rollingTime = currentStepEndTime;
        for (int i = currentStepIndex + 1; i < steps.size(); i++) {
            rollingTime += Math.max(steps.get(i).durationSeconds, 0) * 1000L;
            endTimes[i] = rollingTime;
        }
        
        // Past steps (working backwards)
        rollingTime = nowInMillis - (steps.get(currentStepIndex).durationSeconds * 1000L - currentStepTimeLeftInMillis);
        for (int i = currentStepIndex - 1; i >= 0; i--) {
            endTimes[i] = rollingTime;
            rollingTime -= Math.max(steps.get(i).durationSeconds, 0) * 1000L;
        }

        for (int i = 0; i < steps.size(); i++) {
            TimerStep step = steps.get(i);
            boolean isActive = (i == currentStepIndex);
            boolean isPast = (i < currentStepIndex);
            String title = step.title;
            if (isActive) {
                title = currentLabel + ": " + title;
            }

            itemPreviews.add(buildPreviewItem(
                    title,
                    durationLabel,
                    formatDuration(step.durationSeconds),
                    endsAtLabel,
                    endTimes[i],
                    isActive,
                    isPast
            ));
        }

        return itemPreviews;
    }

    private void renderNextStepItems(List<StepPreviewItem> itemPreviews) {
        layoutNextStepsItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < itemPreviews.size(); i++) {
            StepPreviewItem itemPreview = itemPreviews.get(i);
            View itemView = inflater.inflate(
                    R.layout.item_next_step_preview,
                    layoutNextStepsItems,
                    false
            );

            TextView titleView = itemView.findViewById(R.id.text_next_step_title);
            LinearLayout detailsLayout = itemView.findViewById(R.id.layout_next_step_details);
            TextView durationView = itemView.findViewById(R.id.text_next_step_duration);
            TextView endsAtView = itemView.findViewById(R.id.text_next_step_ends_at);

            titleView.setText(itemPreview.titleText);
            if (itemPreview.isActive) {
                itemView.setBackgroundResource(R.drawable.bg_active_step_preview);
                // Use colorOnSecondaryContainer to ensure readability on secondaryContainer background
                TypedValue typedValue = new TypedValue();
                int colorOnSecondaryContainerRes = getResources().getIdentifier("colorOnSecondaryContainer", "attr", getPackageName());
                if (colorOnSecondaryContainerRes != 0) {
                    getTheme().resolveAttribute(colorOnSecondaryContainerRes, typedValue, true);
                    int onColor = typedValue.data;
                    titleView.setTextColor(onColor);
                    durationView.setTextColor(onColor);
                    endsAtView.setTextColor(onColor);
                }
            } else if (itemPreview.isPast) {
                titleView.setAlpha(0.6f);
                detailsLayout.setAlpha(0.6f);
            }

            if (itemPreview.hasDetails()) {
                detailsLayout.setVisibility(View.VISIBLE);
                durationView.setText(itemPreview.leftDetailsText);
                endsAtView.setText(itemPreview.rightDetailsText);
            } else {
                detailsLayout.setVisibility(View.GONE);
            }

            final int targetIndex = i;
            if (!itemPreview.isActive) {
                itemView.setOnLongClickListener(v -> {
                    if (timerService != null && timerWithSteps != null) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.dialog_switch_step_title)
                                .setMessage(R.string.dialog_switch_step_message)
                                .setPositiveButton(R.string.dialog_switch_step_confirm, (dialog, which) -> {
                                    timerService.stopAlarmOnly(timerId);
                                    timerService.startTimer(timerWithSteps, targetIndex);
                                    layoutAlarmControls.setVisibility(View.GONE);
                                    layoutActiveControls.setVisibility(View.VISIBLE);
                                    updateUI();
                                })
                                .setNegativeButton(R.string.dialog_cancel, null)
                                .show();
                        return true;
                    }
                    return false;
                });
            }

            layoutNextStepsItems.addView(itemView);
        }
    }

    private static StepPreviewItem buildPreviewItem(
            String titleText,
            String durationLabel,
            String durationText,
            String endsAtLabel,
            long endTimeInMillis,
            boolean isActive,
            boolean isPast
    ) {
        return StepPreviewItem.withDetails(
                titleText,
                durationLabel + " " + durationText,
                endsAtLabel + " " + formatClockTime(endTimeInMillis),
                isActive,
                isPast
        );
    }

    private static final class StepPreviewItem {
        private final String titleText;
        private final String leftDetailsText;
        private final String rightDetailsText;
        private final boolean isActive;
        private final boolean isPast;

        private StepPreviewItem(
                String titleText,
                String leftDetailsText,
                String rightDetailsText,
                boolean isActive,
                boolean isPast
        ) {
            this.titleText = titleText;
            this.leftDetailsText = leftDetailsText;
            this.rightDetailsText = rightDetailsText;
            this.isActive = isActive;
            this.isPast = isPast;
        }

        private static StepPreviewItem withDetails(
                String titleText,
                String leftDetailsText,
                String rightDetailsText,
                boolean isActive,
                boolean isPast
        ) {
            return new StepPreviewItem(titleText, leftDetailsText, rightDetailsText, isActive, isPast);
        }

        private static StepPreviewItem titleOnly(String titleText) {
            return new StepPreviewItem(titleText, "", "", false, false);
        }

        private boolean hasDetails() {
            return !leftDetailsText.isEmpty() || !rightDetailsText.isEmpty();
        }
    }

    static String formatDuration(long durationSeconds) {
        long safeDurationSeconds = Math.max(durationSeconds, 0);
        long totalMinutes = safeDurationSeconds / 60;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    static String formatClockTime(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
        );
    }

    @Override
    public void onTick(long tickTimerId, long millisUntilFinished) {
        if (this.timerId == tickTimerId) {
            updateCountdown(millisUntilFinished);
        }
    }

    @Override
    public void onFinish(long finishTimerId) {
        if (this.timerId == finishTimerId) {
            if (timerService != null && timerService.getActiveTimer(finishTimerId) == null) {
                finish();
            } else {
                updateUI();
            }
        }
    }

    @Override
    public void onStatusChanged(long changedTimerId) {
        if (this.timerId == changedTimerId) {
            updateUI();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            timerService.removeListener(this);
            unbindService(connection);
            isBound = false;
        }
    }
}
