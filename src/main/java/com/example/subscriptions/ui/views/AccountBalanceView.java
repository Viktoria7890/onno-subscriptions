package com.example.subscriptions.ui.views;

import com.example.subscriptions.domain.registers.AccountBalance;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;

/** Подсказки отображения для отчёта по регистру остатков «Лицевые счета». */
@Component
public class AccountBalanceView implements EntityView<AccountBalance> {

    @Override
    public Class<AccountBalance> entity() {
        return AccountBalance.class;
    }

    @Override
    public void fields(EntityConfigBuilder<AccountBalance> f) {
        f.icon("landmark");
        f.field(AccountBalance::getAmount).format("currency:RUB")
         .field(AccountBalance::getPeriod).label("Период").format("dd.MM.yyyy HH:mm");
    }
}
