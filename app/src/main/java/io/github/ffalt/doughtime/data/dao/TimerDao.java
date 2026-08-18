package io.github.ffalt.doughtime.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import io.github.ffalt.doughtime.data.entity.Timer;
import io.github.ffalt.doughtime.data.entity.TimerStep;
import io.github.ffalt.doughtime.data.entity.TimerWithSteps;
import java.util.List;

@Dao
public interface TimerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertTimer(Timer timer);

    @Update
    void updateTimer(Timer timer);

    @Delete
    void deleteTimer(Timer timer);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertTimerStep(TimerStep step);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertTimerSteps(List<TimerStep> steps);

    @Query("DELETE FROM timer_steps WHERE timerId = :timerId")
    void deleteStepsForTimer(long timerId);

    @Transaction
    @Query("SELECT * FROM timers")
    LiveData<List<TimerWithSteps>> getAllTimersWithSteps();

    @Transaction
    @Query("SELECT * FROM timers WHERE id = :timerId")
    TimerWithSteps getTimerWithStepsByIdSync(long timerId);

    @Query("SELECT COUNT(*) FROM timers")
    int getTimerCount();

    @Transaction
    default void insertTimerWithSteps(Timer timer, List<TimerStep> steps) {
        long timerId = insertTimer(timer);
        for (TimerStep step : steps) {
            step.timerId = timerId;
        }
        insertTimerSteps(steps);
    }

    @Transaction
    default void replaceTimerWithSteps(Timer timer, List<TimerStep> steps) {
        updateTimer(timer);
        deleteStepsForTimer(timer.id);
        for (TimerStep step : steps) {
            step.timerId = timer.id;
        }
        insertTimerSteps(steps);
    }

    @Transaction
    default void duplicateTimer(long timerId, String titleSuffix) {
        TimerWithSteps original = getTimerWithStepsByIdSync(timerId);
        if (original == null) {
            return;
        }
        original.sortSteps();
        long copyId = insertTimer(new Timer(original.timer.title + titleSuffix, original.timer.description));
        for (TimerStep step : original.steps) {
            insertTimerStep(new TimerStep(
                    copyId,
                    step.title,
                    step.description,
                    step.durationSeconds,
                    step.stepOrder
            ));
        }
    }
}
