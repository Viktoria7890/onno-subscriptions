package com.example.subscriptions;

import com.example.subscriptions.service.SubscriptionStatusResolveTest;
import com.example.subscriptions.support.Req;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Матрица трассировки «требование → тесты». Падает, если какое-то требование из
 * {@code TRACEABILITY.md} не покрыто ни одним тестом, или тест ссылается на несуществующее.
 */
@DisplayName("Трассировка требований")
class TraceabilityTest {

    /** R1..R15 — см. TRACEABILITY.md. */
    static final Set<String> REQUIREMENTS = IntStream.rangeClosed(1, 15)
            .mapToObj(i -> "R" + i)
            .collect(Collectors.toCollection(TreeSet::new));

    static final Class<?>[] TEST_CLASSES = {
            com.example.subscriptions.domain.documents.SubscriptionDomainTest.class,
            SubscriptionStatusResolveTest.class,
            SubscriptionFlowIT.class,
    };

    static Map<String, Set<String>> matrix() {
        Map<String, Set<String>> matrix = new TreeMap<>();
        Deque<Class<?>> queue = new ArrayDeque<>(java.util.List.of(TEST_CLASSES));
        while (!queue.isEmpty()) {
            Class<?> type = queue.pop();
            queue.addAll(java.util.List.of(type.getDeclaredClasses()));
            for (Method method : type.getDeclaredMethods()) {
                for (Req req : method.getAnnotationsByType(Req.class)) {
                    matrix.computeIfAbsent(req.value(), k -> new TreeSet<>())
                            .add(type.getSimpleName() + "." + method.getName());
                }
            }
        }
        return matrix;
    }

    @Test
    @DisplayName("Каждое требование покрыто хотя бы одним тестом")
    void everyRequirementIsCovered() {
        Map<String, Set<String>> matrix = matrix();
        matrix.forEach((req, tests) -> System.out.println(req + " ← " + tests));

        assertThat(matrix.keySet()).as("тесты ссылаются только на известные требования")
                .isSubsetOf(REQUIREMENTS);
        assertThat(matrix.keySet()).as("все требования покрыты").containsAll(REQUIREMENTS);
    }
}
