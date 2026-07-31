package io.github.ffalt.doughtime.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.dao.TimerDao;
import io.github.ffalt.doughtime.data.database.AppDatabase;
import io.github.ffalt.doughtime.data.entity.Timer;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimerRepository {
    private final Application application;
    private final TimerDao timerDao;
    private final LiveData<List<TimerWithSteps>> allTimers;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public TimerRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getDatabase(application);
        timerDao = db.timerDao();
        allTimers = Transformations.map(timerDao.getAllTimersWithSteps(), timers -> {
            for (TimerWithSteps timer : timers) {
                timer.sortSteps();
            }
            return timers;
        });
    }

    public LiveData<List<TimerWithSteps>> getAllTimers() {
        return allTimers;
    }

    public void insert(Timer timer, List<TimerStep> steps) {
        executorService.execute(() -> {
            long timerId = timerDao.insertTimer(timer);
            for (TimerStep step : steps) {
                step.timerId = timerId;
            }
            timerDao.insertTimerSteps(steps);
        });
    }

    public void update(Timer timer, List<TimerStep> steps) {
        executorService.execute(() -> {
            timerDao.updateTimer(timer);
            timerDao.deleteStepsForTimer(timer.id);
            for (TimerStep step : steps) {
                step.timerId = timer.id;
            }
            timerDao.insertTimerSteps(steps);
        });
    }

    public void delete(Timer timer) {
        executorService.execute(() -> timerDao.deleteTimer(timer));
    }

    public void duplicate(long timerId) {
        executorService.execute(() -> {
            TimerWithSteps original = timerDao.getTimerWithStepsByIdSync(timerId);
            if (original != null) {
                original.sortSteps();
                Timer newTimer = new Timer(
                        original.timer.title + application.getString(R.string.timer_copy_suffix),
                        original.timer.description
                );
                long newId = timerDao.insertTimer(newTimer);
                for (TimerStep step : original.steps) {
                    timerDao.insertTimerStep(new TimerStep(newId, step.title, step.description, step.durationSeconds, step.stepOrder));
                }
            }
        });
    }
}
