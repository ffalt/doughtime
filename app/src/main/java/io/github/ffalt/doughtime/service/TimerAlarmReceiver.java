package io.github.ffalt.doughtime.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimerAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TimerService.ACTION_EXACT_ALARM.equals(intent.getAction())) {
            return;
        }

        long timerId = intent.getLongExtra(TimerService.EXTRA_TIMER_ID, -1L);
        int stepIndex = intent.getIntExtra(TimerService.EXTRA_STEP_INDEX, -1);
        if (timerId <= 0 || stepIndex < 0) {
            return;
        }

        Intent serviceIntent = new Intent(context, TimerService.class);
        serviceIntent.setAction(TimerService.ACTION_EXACT_ALARM_FIRED);
        serviceIntent.putExtra(TimerService.EXTRA_TIMER_ID, timerId);
        serviceIntent.putExtra(TimerService.EXTRA_STEP_INDEX, stepIndex);

        context.startForegroundService(serviceIntent);
    }
}

