package com.example.subscriptions.ui.views;

import com.example.subscriptions.domain.catalogs.Client;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class ClientView implements EntityView<Client> {

    @Override
    public Class<Client> entity() {
        return Client.class;
    }

    @Override
    public void list(ListSpec<Client> list) {
        list.columns(Client::getCode, Client::getDescription, Client::getStatus,
                        Client::getEmail, Client::getPhone, Client::getRegistrationDate)
                .label(Client::getCode, "Код")
                .label(Client::getDescription, "Клиент")
                .sortBy(Client::getDescription);
        list.filter(Client::getStatus).label("Статус").multiOptions();
        list.filter(Client::getRegistrationDate).label("Дата регистрации").dateRange();
    }

    @Override
    public void fields(EntityConfigBuilder<Client> f) {
        f.icon("user-round");
        f.field(Client::getDescription).label("Наименование").order(0).width("full")
                .placeholder("ООО «Ромашка» или Иванов Иван").hint("Как клиент отображается в документах.")
         .field(Client::getCode).label("Код").order(1).width("half").hint("Присваивается автоматически.")
         .field(Client::getStatus).order(2).width("half")
         .field(Client::getEmail).order(3).width("half").placeholder("client@example.com")
         .field(Client::getPhone).order(4).width("half").placeholder("+7 900 000-00-00")
         .field(Client::getRegistrationDate).order(5).width("half").format("dd.MM.yyyy");

    }
}
