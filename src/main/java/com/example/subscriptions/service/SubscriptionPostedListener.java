package com.example.subscriptions.service;

import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.repository.SubscriptionRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import su.onno.posting.DocumentPostedEvent;

/**
 * Сразу после проведения выставляем подписке актуальный статус по датам (не дожидаясь ближайшего
 * запуска регламентного задания). Реакция на проведение — через Spring-событие, а не из
 * {@code handlePosting}: там нет DI, и он может переигрываться (preview / восстановление).
 */
@Component
public class SubscriptionPostedListener {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionStatusService statuses;

    public SubscriptionPostedListener(SubscriptionRepository subscriptions, SubscriptionStatusService statuses) {
        this.subscriptions = subscriptions;
        this.statuses = statuses;
    }

    @EventListener
    public void onPosted(DocumentPostedEvent event) {
        if (event.document() instanceof Subscription posted && posted.getId() != null) {
            // Перечитываем: в событии объект в состоянии на момент проведения.
            subscriptions.findActiveById(posted.getId()).ifPresent(statuses::refresh);
        }
    }
}
