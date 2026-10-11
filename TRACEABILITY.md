# Трассировка требований → реализация → тесты

Каждый тест помечен аннотацией `@Req("Rn")` (и `[Rn]` в `@DisplayName`).
`TraceabilityTest` строит матрицу по этим аннотациям и **падает**, если хотя бы одно требование не покрыто
или тест ссылается на неизвестное требование. Матрица печатается в вывод теста.

| ID | Требование (из ТЗ) | Где реализовано | Тесты |
|----|--------------------|-----------------|-------|
| R1 | Платёж пополняет лицевой счёт | `Payment.handlePosting` → приход в `AccountBalance` | `Movements.paymentMovement`, `Movements.paymentRules`, `SubscriptionFlowIT.paymentsTopUpAccount` |
| R2 | Стоимость подписки списывается с лицевого счёта в момент проведения | `Subscription.handlePosting` → расход из `AccountBalance` | `Movements.subscriptionMovements`, `SubscriptionFlowIT.postingSubscriptionChargesAndRecognisesRevenue` |
| R3 | Оформить подписку при нехватке денег нельзя | `Subscription.beforePost` (понятное сообщение) + регистр `AccountBalance` типа BALANCE без `allowNegative` (атомарная гарантия) | `Funds.*`, `SubscriptionFlowIT.cannotPostWithoutEnoughMoney`, `SubscriptionFlowIT.balanceRegisterNeverGoesNegative` |
| R4 | Срок = дата начала + **максимальный** срок среди строк | `Subscription.recalculate` (`days = periodDays × periods`, `endDate = startDate + max(days)`) | `Recalculation.endDateUsesMaximumLineTerm`, `Recalculation.lineTermIsPeriodDaysTimesPeriods`, `SubscriptionFlowIT.endDateIsStartPlusMaxTerm` |
| R5 | Отменённая подписка не создаёт движений | ранний `return` в `Subscription.handlePosting`; отмена проведённой → перепроведение | `Movements.cancelledSubscriptionHasNoMovements`, `Funds.cancelledNeedsNoMoney`, `Recalculation.cancelledFlagFollowsStatus`, `SubscriptionFlowIT.cancelledSubscriptionPostsNothing`, `SubscriptionFlowIT.cancellingPostedSubscriptionReversesMovements` |
| R6 | Выручка в разрезе тарифов и клиентов | `TariffRevenue` (TURNOVER; измерения: тариф, клиент; ресурсы: сумма, периодов) | `Movements.subscriptionMovements`, `SubscriptionFlowIT.postingSubscriptionChargesAndRecognisesRevenue` |
| R7 | Клиент обязателен | `SubscriptionRules.CLIENT_REQUIRED` (+ `required = true`) | `Rules.clientRequired`, `SubscriptionFlowIT.invalidSubscriptionsAreRejectedOnSave` |
| R8 | Хотя бы одна строка (и тариф в каждой строке) | `SubscriptionRules.LINES_REQUIRED`, `TARIFF_REQUIRED` | `Rules.atLeastOneLine`, `Rules.tariffRequiredInEachLine`, `SubscriptionFlowIT.invalidSubscriptionsAreRejectedOnSave` |
| R9 | Число периодов больше нуля | `SubscriptionRules.PERIODS_POSITIVE` | `Rules.periodsMustBePositive`, `SubscriptionFlowIT.invalidSubscriptionsAreRejectedOnSave` |
| R10 | Тариф доступен для подключения | `SubscriptionRules.TARIFF_AVAILABLE` + `refFilter("available = true")` в пикере | `Rules.tariffMustBeAvailable`, `Rules.unknownTariffIsUnavailable`, `Rules.availabilityIsCheckedOnlyBeforePosting`, `SubscriptionFlowIT.unavailableTariffIsRejected` |
| R11 | Автоподстановка даты документа и даты начала при создании | инициализаторы полей + `Subscription.onFilling` | `Recalculation.onFillingSetsDatesWithoutOverwriting`, `Recalculation.blankFormDefaults`, `SubscriptionFlowIT.autofillOnCreate` |
| R12 | Цена строки из тарифа; пересчёт сумм и даты окончания при каждом сохранении | `Subscription.beforeWrite` → `recalculate` | `Recalculation.priceFromTariffAndTotals`, `Recalculation.recalculatesOnEverySave`, `Recalculation.postedPriceIsFrozen`, `SubscriptionFlowIT.autofillOnCreate` |
| R13 | Регламентное задание: «Активна» / «Истекла» по датам | `SubscriptionStatusJob` (`@ScheduledJob` + `BackgroundTask`) → `SubscriptionStatusService` | `SubscriptionStatusResolveTest.resolve` (8 случаев), `SubscriptionFlowIT.statusTransitionsByDates`, `SubscriptionFlowIT.scheduledJobUpdatesStaleStatuses`, `SubscriptionFlowIT.statusIsSetRightAfterPosting` |
| R14 | Отмена подписки с причиной (ActionSpec + модальное окно) | `SubscriptionView.actions` → `SubscriptionCancellationService` | `SubscriptionFlowIT.cancellingPostedSubscriptionReversesMovements`, `SubscriptionFlowIT.cancellationGuards` |
| R15 | Отчётность: остатки лицевых счетов и выручка | отчёты регистров в разделе «Отчёты» + дашборд | `SubscriptionFlowIT.paymentsTopUpAccount`, `SubscriptionFlowIT.postingSubscriptionChargesAndRecognisesRevenue` |
