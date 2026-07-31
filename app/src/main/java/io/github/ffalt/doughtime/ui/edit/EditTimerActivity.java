package io.github.ffalt.doughtime.ui.edit;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
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

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                stepAdapter.moveStep(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });

        stepAdapter = new TimerStepAdapter(stepsList, itemTouchHelper::startDrag);
        recyclerSteps.setAdapter(stepAdapter);
        itemTouchHelper.attachToRecyclerView(recyclerSteps);

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
            recyclerSteps.smoothScrollToPosition(stepsList.size() - 1);
        });

        View contentContainer = findViewById(R.id.content_container);
        ViewCompat.setOnApplyWindowInsetsListener(recyclerSteps.getRootView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(insets.left, 0, insets.right, 0);
            contentContainer.setPadding(0, 0, 0, Math.max(insets.bottom, imeInsets.bottom));
            return windowInsets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveTimer();
                return true;
            }
            return false;
        });
    }

    private void saveTimer() {
        String title = editTitle.getText().toString();
        String description = editDescription.getText().toString();
        if (title.isEmpty()) {
            return;
        }

        for (int i = 0; i < stepsList.size(); i++) {
            stepsList.get(i).stepOrder = i;
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
