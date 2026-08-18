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
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

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
        executorService.execute(() -> timerDao.insertTimerWithSteps(timer, steps));
    }

    public void update(Timer timer, List<TimerStep> steps) {
        executorService.execute(() -> timerDao.replaceTimerWithSteps(timer, steps));
    }

    public void delete(Timer timer) {
        executorService.execute(() -> timerDao.deleteTimer(timer));
    }

    public void duplicate(long timerId) {
        String titleSuffix = application.getString(R.string.timer_copy_suffix);
        executorService.execute(() -> timerDao.duplicateTimer(timerId, titleSuffix));
    }
}
