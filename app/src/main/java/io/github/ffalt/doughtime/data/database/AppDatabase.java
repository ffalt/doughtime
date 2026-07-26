package io.github.ffalt.doughtime.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import io.github.ffalt.doughtime.data.dao.TimerDao;
import io.github.ffalt.doughtime.data.entity.Timer;
import io.github.ffalt.doughtime.data.entity.TimerStep;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Timer.class, TimerStep.class}, version = 3)
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
                            .addCallback(ROOM_DATABASE_CALLBACK)
                            .fallbackToDestructiveMigration(false)
                            .build();
                }
            }
        }
        return instance;
    }

    private static final RoomDatabase.Callback ROOM_DATABASE_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            DATABASE_EXECUTOR.execute(() -> {
                TimerDao dao = instance.timerDao();
                insertDefaultData(dao);
            });
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            DATABASE_EXECUTOR.execute(() -> {
                TimerDao dao = instance.timerDao();
                if (dao.getTimerCount() == 0) {
                    insertDefaultData(dao);
                }
            });
        }

        private void insertDefaultData(TimerDao dao) {
            Timer sourdough = new Timer("Sauerteg", "Standard sourdough process");
            long id = dao.insertTimer(sourdough);
            dao.insertTimerStep(new TimerStep(id, "Fütterung", "Fütter deinen Starter-Teig", 4 * 60 * 60, 0));
            dao.insertTimerStep(new TimerStep(id, "Teigruhe", "Teig gehen lassen", 3 * 60 * 60, 1));
            dao.insertTimerStep(new TimerStep(id, "Teigruhe 2", "Wenn nötig Teig weiter gehen lassen", 30 * 60 * 60, 2));
            dao.insertTimerStep(new TimerStep(id, "Vorheizen", "Ofen auf 250 Grad vorheizen", 30 * 60 * 60, 3));
            dao.insertTimerStep(new TimerStep(id, "Backen Phase 1", "Backen mit Deckel 250 Grad", 10 * 60, 4));
            dao.insertTimerStep(new TimerStep(id, "Backen Phase 2", "Backen 220 Grad", 20 * 60, 5));
            dao.insertTimerStep(new TimerStep(id, "Backen Phase 3", "Backen bei 200 Grad", 35 * 60, 6));
        }
    };
}
