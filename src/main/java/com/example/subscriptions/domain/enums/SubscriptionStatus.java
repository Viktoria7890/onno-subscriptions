package com.example.subscriptions.domain.enums;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

/**
 * Жизненный цикл подписки.
 *
 * <pre>
 *   NEW ──(job: дата начала наступила)──▶ ACTIVE ──(job: срок вышел)──▶ EXPIRED
 *    │                                       │
 *    └──────────── действие «Отменить» ──────┴──▶ CANCELLED (движений нет)
 * </pre>
 *
 * Перевод в ACTIVE/EXPIRED делает регламентное задание
 * {@link com.example.subscriptions.jobs.SubscriptionStatusJob} по датам действия.
 */
@Enumeration(name = "SubscriptionStatuses", title = "Статус подписки")
public enum SubscriptionStatus {

    @EnumLabel(value = "Оформлена", color = "#6B7280") NEW,

    @EnumLabel(value = "Активна", color = "#059669") ACTIVE,

    @EnumLabel(value = "Истекла", color = "#D97706") EXPIRED,

    @EnumLabel(value = "Отменена", color = "#DC2626") CANCELLED;

    /** Подписка завершена и больше не меняет статус автоматически. */
    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }
}
