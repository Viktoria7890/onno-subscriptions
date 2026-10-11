package com.example.subscriptions.service;

import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import su.onno.posting.PostingService;

import java.util.UUID;

/**
 * Отмена подписки с указанием причины.
 *
 * <p>Если подписка уже проведена, после смены статуса она <b>перепроводится</b>: движок
 * сторнирует прежние движения, а {@code handlePosting} отменённой подписки новых не создаёт —
 * деньги возвращаются на лицевой счёт, выручка перестаёт признаваться. Сохранение и
 * перепроведение — две отдельные транзакции (так требует onno: проведение владеет своей
 * транзакцией), поэтому здесь нет {@code @Transactional}.</p>
 */
@Service
public class SubscriptionCancellationService {

    private final SubscriptionRepository subscriptions;
    private final PostingService posting;

    public SubscriptionCancellationService(SubscriptionRepository subscriptions, PostingService posting) {
        this.subscriptions = subscriptions;
        this.posting = posting;
    }

    public static boolean canCancel(SubscriptionStatus status) {
        return status != SubscriptionStatus.CANCELLED && status != SubscriptionStatus.EXPIRED;
    }

    public Subscription cancel(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new CancellationRejected("Укажите причину отмены");
        }
        Subscription subscription = subscriptions.findActiveById(id)
                .orElseThrow(() -> new CancellationRejected("Подписка не найдена"));
        if (!canCancel(subscription.getStatus())) {
            throw new CancellationRejected(subscription.getStatus() == SubscriptionStatus.CANCELLED
                    ? "Подписка уже отменена"
                    : "Истёкшую подписку отменить нельзя");
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelReason(reason.trim());
        Subscription saved = subscriptions.save(subscription);
        if (saved.isPosted()) {
            posting.repost(saved);
        }
        return subscriptions.findActiveById(id).orElse(saved);
    }

    /** Ожидаемый бизнес-отказ (UI превращает его в понятное сообщение, а не в 500). */
    public static class CancellationRejected extends RuntimeException {
        public CancellationRejected(String message) {
            super(message);
        }
    }
}
