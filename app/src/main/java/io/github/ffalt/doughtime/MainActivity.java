package io.github.ffalt.doughtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import io.github.ffalt.doughtime.service.TimerService;
import io.github.ffalt.doughtime.ui.edit.EditTimerActivity;
import io.github.ffalt.doughtime.ui.main.ActiveTimerAdapter;
import io.github.ffalt.doughtime.ui.main.TimerAdapter;
import io.github.ffalt.doughtime.ui.main.TimerViewModel;
import io.github.ffalt.doughtime.ui.timer.TimerRunActivity;

public class MainActivity extends AppCompatActivity implements TimerAdapter.OnTimerClickListener, TimerService.TimerListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 101;
    private static final String PREFS_NAME = "doughtime_prefs";
    private static final String PREF_ASKED_FULL_SCREEN_INTENT = "asked_full_screen_intent";

    private TimerViewModel viewModel;
    private TimerAdapter adapter;
    private ActiveTimerAdapter activeAdapter;
    private TimerService timerService;
    private boolean isBound = false;
    private java.util.List<TimerWithSteps> allTimers;

    private RecyclerView recyclerActiveTimers;
    private TextView textActiveTimersLabel;
    private TextView textAllTimersLabel;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.LocalBinder binder = (TimerService.LocalBinder) service;
            timerService = binder.getService();
            isBound = true;
            timerService.addListener(MainActivity.this);
            updateTimersUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "TimerService disconnected unexpectedly: " + name);
            timerService = null;
            isBound = false;
            updateTimersUI();
            attemptServiceRebind();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimerAdapter(this);
        recyclerView.setAdapter(adapter);

        recyclerActiveTimers = findViewById(R.id.recycler_active_timers);
        recyclerActiveTimers.setLayoutManager(new LinearLayoutManager(this));
        activeAdapter = new ActiveTimerAdapter(timer -> {
            if (timerService != null && timer.isAlarmPlaying) {
                timerService.stopAlarmOnly(timer.timer.timer.id);
            }
            Intent intent = new Intent(MainActivity.this, TimerRunActivity.class);
            intent.putExtra("TIMER_ID", timer.timer.timer.id);
            startActivity(intent);
        });
        recyclerActiveTimers.setAdapter(activeAdapter);
        RecyclerView.ItemAnimator activeItemAnimator = recyclerActiveTimers.getItemAnimator();
        if (activeItemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) activeItemAnimator).setSupportsChangeAnimations(false);
        }

        textActiveTimersLabel = findViewById(R.id.text_active_timers_label);
        textAllTimersLabel = findViewById(R.id.text_all_timers_label);

        viewModel = new ViewModelProvider(this).get(TimerViewModel.class);
        viewModel.getAllTimers().observe(this, timers -> {
            this.allTimers = timers;
            updateTimersUI();
        });

        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fab_add_timer);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditTimerActivity.class);
            startActivity(intent);
        });

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView.getRootView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) fabAdd.getLayoutParams();
            int margin = getResources().getDimensionPixelSize(R.dimen.fab_margin);
            mlp.leftMargin = margin;
            mlp.rightMargin = margin;
            mlp.bottomMargin = insets.bottom + margin;
            fabAdd.setLayoutParams(mlp);
            
            return windowInsets;
        });

        if (!checkNotificationPermission()) {
            checkFullScreenIntentPermission();
        }
    }

    /**
     * @return true if the permission dialog was requested, false if nothing was asked
     */
    private boolean checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            checkFullScreenIntentPermission();
        }
    }

    private void checkFullScreenIntentPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (nm.canUseFullScreenIntent()) {
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ASKED_FULL_SCREEN_INTENT, false)) {
            return;
        }
        prefs.edit().putBoolean(PREF_ASKED_FULL_SCREEN_INTENT, true).apply();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_full_screen_intent_title)
                .setMessage(R.string.permission_full_screen_intent_message)
                .setPositiveButton(R.string.action_open_settings, (d, w) -> openFullScreenIntentSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void openFullScreenIntentSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Log.w(TAG, "No settings screen for the full screen intent permission", e);
        }
    }

    private void attemptServiceRebind() {
        if (!isFinishing() && !isDestroyed()) {
            boolean rebound = bindService(new Intent(this, TimerService.class), connection, Context.BIND_AUTO_CREATE);
            if (!rebound) {
                Log.e(TAG, "Failed to rebind TimerService after unexpected disconnect");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, TimerService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            timerService.removeListener(this);
            unbindService(connection);
            isBound = false;
        }
    }

    private void updateTimersUI() {
        if (timerService != null) {
            java.util.Collection<TimerService.ActiveTimer> activeTimers = timerService.getAllActiveTimers();
            if (activeTimers.isEmpty()) {
                recyclerActiveTimers.setVisibility(View.GONE);
                textActiveTimersLabel.setVisibility(View.GONE);
            } else {
                recyclerActiveTimers.setVisibility(View.VISIBLE);
                textActiveTimersLabel.setVisibility(View.VISIBLE);
                activeAdapter.submitList(activeTimers);
            }

            if (allTimers != null) {
                java.util.Set<Long> activeIds = new java.util.HashSet<>();
                for (TimerService.ActiveTimer at : activeTimers) {
                    activeIds.add(at.timer.timer.id);
                }

                java.util.List<TimerWithSteps> filtered = new java.util.ArrayList<>();
                for (TimerWithSteps t : allTimers) {
                    if (!activeIds.contains(t.timer.id)) {
                        filtered.add(t);
                    }
                }
                adapter.submitList(filtered);

                // Show "All Timers" title only if there are active timers AND non-active timers
                if (!activeTimers.isEmpty() && !filtered.isEmpty()) {
                    textAllTimersLabel.setVisibility(View.VISIBLE);
                } else {
                    textAllTimersLabel.setVisibility(View.GONE);
                }
            } else {
                textAllTimersLabel.setVisibility(View.GONE);
            }
        } else {
            recyclerActiveTimers.setVisibility(View.GONE);
            textActiveTimersLabel.setVisibility(View.GONE);
            textAllTimersLabel.setVisibility(View.GONE);
            if (allTimers != null) {
                adapter.submitList(allTimers);
            }
        }
    }

    @Override
    public void onTick(long timerId, long millisUntilFinished) {
        activeAdapter.notifyTimerUpdated(timerId);
    }

    @Override
    public void onFinish(long timerId) {
        updateTimersUI();
    }

    @Override
    public void onStatusChanged(long timerId) {
        updateTimersUI();
    }

    @Override
    public void onStartClick(TimerWithSteps timer) {
        Intent intent = new Intent(this, TimerRunActivity.class);
        intent.putExtra("TIMER_ID", timer.timer.id);
        startActivity(intent);
    }

    @Override
    public void onOpenClick(TimerWithSteps timer) {
        Intent intent = new Intent(this, TimerRunActivity.class);
        intent.putExtra("TIMER_ID", timer.timer.id);
        intent.putExtra(TimerRunActivity.EXTRA_AUTO_START, false);
        startActivity(intent);
    }

    @Override
    public void onEditClick(TimerWithSteps timer) {
        Intent intent = new Intent(this, EditTimerActivity.class);
        intent.putExtra("TIMER_ID", timer.timer.id);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(TimerWithSteps timer) {
        viewModel.delete(timer.timer);
    }

    @Override
    public void onDuplicateClick(TimerWithSteps timer) {
        viewModel.duplicate(timer.timer.id);
    }
}
