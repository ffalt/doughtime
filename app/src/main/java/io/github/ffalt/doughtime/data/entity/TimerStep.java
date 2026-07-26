package io.github.ffalt.doughtime.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "timer_steps",
    foreignKeys = @ForeignKey(
        entity = Timer.class,
        parentColumns = "id",
        childColumns = "timerId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("timerId")}
)
public class TimerStep {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long timerId;
    public String title;
    public String description;
    public long durationSeconds;
    public int stepOrder;

    public TimerStep() {
    }

    @Ignore
    public TimerStep(long timerId, String title, String description, long durationSeconds, int stepOrder) {
        this.timerId = timerId;
        this.title = title;
        this.description = description;
        this.durationSeconds = durationSeconds;
        this.stepOrder = stepOrder;
    }
}
