package com.example.subscriptions.jobs;

import com.example.subscriptions.service.SubscriptionStatusService;
import org.springframework.stereotype.Component;
import su.onno.annotations.ScheduledJob;
import su.onno.jobs.BackgroundTask;

/**
 * Регламентное задание: раз в час переводит подписки в «Активна» / «Истекла» по датам.
 * onno регистрирует все бины {@link BackgroundTask} с {@link ScheduledJob} как повторяющиеся
 * задания JobRunr (нужен {@code org.jobrunr.background-job-server.enabled=true}).
 */
@Component
@ScheduledJob(name = "subscription-statuses", cron = "0 * * * *")
public class SubscriptionStatusJob implements BackgroundTask {

    private final SubscriptionStatusService statuses;

    public SubscriptionStatusJob(SubscriptionStatusService statuses) {
        this.statuses = statuses;
    }

    @Override
    public void execute() {
        statuses.refreshAll();
    }
}
