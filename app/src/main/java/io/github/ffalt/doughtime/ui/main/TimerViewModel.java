package io.github.ffalt.doughtime.ui.main;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import io.github.ffalt.doughtime.data.TimerRepository;
import io.github.ffalt.doughtime.data.entity.Timer;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import java.util.List;

public class TimerViewModel extends AndroidViewModel {
    private final TimerRepository repository;
    private final LiveData<List<TimerWithSteps>> allTimers;

    public TimerViewModel(@NonNull Application application) {
        super(application);
        repository = new TimerRepository(application);
        allTimers = repository.getAllTimers();
    }

    public LiveData<List<TimerWithSteps>> getAllTimers() {
        return allTimers;
    }

    public void insert(Timer timer, List<TimerStep> steps) {
        repository.insert(timer, steps);
    }

    public void update(Timer timer, List<TimerStep> steps) {
        repository.update(timer, steps);
    }

    public void delete(Timer timer) {
        repository.delete(timer);
    }

    public void duplicate(long timerId) {
        repository.duplicate(timerId);
    }
}
