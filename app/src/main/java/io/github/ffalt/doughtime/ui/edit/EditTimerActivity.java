package io.github.ffalt.doughtime.ui.edit;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.entity.Timer;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import io.github.ffalt.doughtime.ui.main.TimerViewModel;
import java.util.ArrayList;
import java.util.List;

import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import com.google.android.material.appbar.MaterialToolbar;

public class EditTimerActivity extends AppCompatActivity {
    private TimerViewModel viewModel;
    private long timerId = -1;
    private EditText editTitle;
    private EditText editDescription;
    private TimerStepAdapter stepAdapter;
    private final List<TimerStep> stepsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_timer);

        viewModel = new ViewModelProvider(this).get(TimerViewModel.class);
        timerId = getIntent().getLongExtra("TIMER_ID", -1);

        editTitle = findViewById(R.id.edit_title);
        editDescription = findViewById(R.id.edit_description);
        RecyclerView recyclerSteps = findViewById(R.id.recycler_steps);
        recyclerSteps.setLayoutManager(new LinearLayoutManager(this));

        stepAdapter = new TimerStepAdapter(stepsList);
        recyclerSteps.setAdapter(stepAdapter);

        if (timerId != -1) {
            viewModel.getAllTimers().observe(this, timers -> {
                for (TimerWithSteps t : timers) {
                    if (t.timer.id == timerId) {
                        editTitle.setText(t.timer.title);
                        editDescription.setText(t.timer.description);
                        int oldSize = stepsList.size();
                        stepsList.clear();
                        if (oldSize > 0) {
                            stepAdapter.notifyItemRangeRemoved(0, oldSize);
                        }
                        stepsList.addAll(t.steps);
                        if (!t.steps.isEmpty()) {
                            stepAdapter.notifyItemRangeInserted(0, t.steps.size());
                        }
                        break;
                    }
                }
            });
        }

        findViewById(R.id.button_add_step).setOnClickListener(v -> {
            stepsList.add(new TimerStep(timerId != -1 ? timerId : 0, "", "", 0, stepsList.size()));
            stepAdapter.notifyItemInserted(stepsList.size() - 1);
        });

        findViewById(R.id.fab_save).setOnClickListener(v -> saveTimer());

        ViewCompat.setOnApplyWindowInsetsListener(recyclerSteps.getRootView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);

            View fabSave = findViewById(R.id.fab_save);
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) fabSave.getLayoutParams();
            int margin = getResources().getDimensionPixelSize(R.dimen.fab_margin);
            mlp.leftMargin = margin;
            mlp.rightMargin = margin;
            mlp.bottomMargin = insets.bottom + margin;
            fabSave.setLayoutParams(mlp);

            return windowInsets;
        });
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void saveTimer() {
        String title = editTitle.getText().toString();
        String description = editDescription.getText().toString();
        if (title.isEmpty()) {
            return;
        }

        Timer timer = new Timer(title, description);
        if (timerId != -1) {
            timer.id = timerId;
            viewModel.update(timer, stepsList);
        } else {
            viewModel.insert(timer, stepsList);
        }
        finish();
    }
}
