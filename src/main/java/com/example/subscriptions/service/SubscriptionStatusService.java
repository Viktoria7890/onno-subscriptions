package com.example.subscriptions.service;

import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Перевод подписок в статусы «Активна» / «Истекла» по датам действия.
 *
 * <p>Срок действия — полуинтервал {@code [startDate, endDate)}: подписка активна, начиная с даты
 * начала, и истекает в дату окончания (дата начала + максимальный срок строк). Например, старт
 * 1 января и тариф на 30 дней → активна с 1 по 30 января, 31 января — «Истекла».</p>
 *
 * <p>Обрабатываются только проведённые, не удалённые и не отменённые подписки: черновик ещё не
 * оформлен, а отменённая подписка — терминальное состояние.</p>
 */
@Service
public class SubscriptionStatusService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionStatusService.class);

    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public SubscriptionStatusService(SubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    /** Чистая функция: какой статус должен быть у подписки на дату {@code today}. */
    public static SubscriptionStatus resolve(SubscriptionStatus current, LocalDate start, LocalDate end,
                                             LocalDate today) {
        if (current == SubscriptionStatus.CANCELLED) {
            return SubscriptionStatus.CANCELLED;
        }
        if (start == null || today == null) {
            return current;
        }
        if (end != null && !today.isBefore(end)) {
            return SubscriptionStatus.EXPIRED;
        }
        if (!today.isBefore(start)) {
            return SubscriptionStatus.ACTIVE;
        }
        return SubscriptionStatus.NEW;
    }

    /** Обновить статусы всех проведённых подписок на сегодня. Возвращает число изменённых. */
    public int refreshAll() {
        return refreshAll(LocalDate.now(clock));
    }

    public int refreshAll(LocalDate today) {
        int changed = 0;
        for (Subscription subscription : subscriptions.findAllActive()) {
            if (refresh(subscription, today)) {
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Статусы подписок обновлены на {}: изменено {}", today, changed);
        }
        return changed;
    }

    /** Обновить статус одной подписки на сегодня. */
    public boolean refresh(Subscription subscription) {
        return refresh(subscription, LocalDate.now(clock));
    }

    boolean refresh(Subscription subscription, LocalDate today) {
        if (!subscription.isPosted() || subscription.isDeletionMark()) {
            return false;
        }
        SubscriptionStatus target = resolve(subscription.getStatus(), subscription.getStartDate(),
                subscription.getEndDate(), today);
        if (target == subscription.getStatus()) {
            return false;
        }
        subscription.setStatus(target);
        subscriptions.save(subscription);
        return true;
    }
}
