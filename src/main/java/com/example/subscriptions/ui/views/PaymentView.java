package com.example.subscriptions.ui.views;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.documents.Payment;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class PaymentView implements EntityView<Payment> {

    @Override
    public Class<Payment> entity() {
        return Payment.class;
    }

    @Override
    public void list(ListSpec<Payment> list) {
        list.columns(Payment::getNumber, Payment::getDate, Payment::getClient, Payment::getAmount,
                        Payment::getMethod)
                .column("posted", "Проведён")
                .label(Payment::getNumber, "Номер")
                .label(Payment::getDate, "Дата")
                .sortBy(Payment::getDate, true)
                .groupable(Payment::getClient, Payment::getMethod, Payment::getDate)
                .aggregate(Payment::getAmount, ListSpec.Agg.SUM, "Сумма");
        list.filter(Payment::getMethod).label("Способ оплаты").multiOptions();
        list.filter(Payment::getDate).label("Дата").dateRange();
    }

    @Override
    public void fields(EntityConfigBuilder<Payment> f) {
        f.icon("wallet");
        f.field(Payment::getNumber).label("Номер").order(0).width("half")
         .field(Payment::getDate).label("Дата").order(1).width("half").format("dd.MM.yyyy HH:mm")
         .field("posted").label("Проведён")
         .field(Payment::getClient).order(2).width("full").hint("Чей лицевой счёт пополняется.")
         .field(Payment::getAmount).order(3).width("half").format("currency:RUB")
         .field(Payment::getMethod).order(4).width("half")
         .field(Payment::getComment).order(5).widget("textarea");
        f.refField(Payment::getClient).refSecondary(Client::getEmail);
    }
}
