package io.github.ffalt.doughtime.data.entity;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;

public class TimerWithSteps {
    @Embedded
    public Timer timer;

    @Relation(
        parentColumn = "id",
        entityColumn = "timerId"
    )
    public List<TimerStep> steps;
}
