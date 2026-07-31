package io.github.ffalt.doughtime.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import io.github.ffalt.doughtime.R;
import io.github.ffalt.doughtime.data.dao.TimerDao;
import io.github.ffalt.doughtime.data.entity.Timer;
import io.github.ffalt.doughtime.data.entity.TimerStep;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Timer.class, TimerStep.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TimerDao timerDao();

    private static volatile AppDatabase instance;
    private static final Executor DATABASE_EXECUTOR = Executors.newSingleThreadExecutor();

    public static AppDatabase getDatabase(final Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "dough_time_database")
                            .addCallback(new DatabaseCallback(context))
                            .fallbackToDestructiveMigration(false)
                            .build();
                }
            }
        }
        return instance;
    }

    private static class DatabaseCallback extends RoomDatabase.Callback {
        private final Context context;

        DatabaseCallback(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            DATABASE_EXECUTOR.execute(() -> {
                TimerDao dao = instance.timerDao();
                insertDefaultData(dao, context);
            });
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            DATABASE_EXECUTOR.execute(() -> {
                TimerDao dao = instance.timerDao();
                if (dao.getTimerCount() == 0) {
                    insertDefaultData(dao, context);
                }
            });
        }

        private void insertDefaultData(TimerDao dao, Context appContext) {
            Timer sourdough = new Timer(
                    appContext.getString(R.string.default_timer_sourdough_title),
                    appContext.getString(R.string.default_timer_sourdough_desc));
            long id = dao.insertTimer(sourdough);
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_feeding_title),
                    appContext.getString(R.string.default_step_feeding_desc),
                    4 * 60 * 60, 0));
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_resting1_title),
                    appContext.getString(R.string.default_step_resting1_desc),
                    3 * 60 * 60, 1));
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_resting2_title),
                    appContext.getString(R.string.default_step_resting2_desc),
                    30 * 60, 2));
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_preheat_title),
                    appContext.getString(R.string.default_step_preheat_desc),
                    30 * 60, 3));
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_baking1_title),
                    appContext.getString(R.string.default_step_baking1_desc),
                    10 * 60, 4));
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_baking2_title),
                    appContext.getString(R.string.default_step_baking2_desc),
                    20 * 60, 5));
            dao.insertTimerStep(new TimerStep(id,
                    appContext.getString(R.string.default_step_baking3_title),
                    appContext.getString(R.string.default_step_baking3_desc),
                    35 * 60, 6));
        }
    }
}
