package com.example.subscriptions.domain.enums;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

/** Статус клиента. */
@Enumeration(name = "ClientStatuses", title = "Статус клиента")
public enum ClientStatus {

    @EnumLabel(value = "Активен", color = "#059669") ACTIVE,

    @EnumLabel(value = "Приостановлен", color = "#D97706") SUSPENDED,

    @EnumLabel(value = "Закрыт", color = "#6B7280") CLOSED
}
