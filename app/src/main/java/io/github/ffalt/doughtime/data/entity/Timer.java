package io.github.ffalt.doughtime.data.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "timers")
public class Timer {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public String description;

    public Timer() {
    }

    @Ignore
    public Timer(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
