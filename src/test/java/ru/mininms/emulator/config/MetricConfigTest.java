package ru.mininms.emulator.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты кросс-полевой валидации MetricConfig (методы @AssertTrue
 * вызываются напрямую — Spring-биндинг проверяет те же правила на старте).
 */
class MetricConfigTest {

    @Test
    void counterRequiresNonNegativeRate() {
        assertThat(counter(-5.0).isCounterSpecConsistent()).isFalse();
        assertThat(counter(0.0).isCounterSpecConsistent()).isTrue();
    }

    @Test
    void numericAndStringSkipCounterValidation() {
        MetricConfig numeric = new MetricConfig();
        numeric.setMin(-60.0);
        numeric.setMax(-20.0);
        assertThat(numeric.isCounterSpecConsistent()).isTrue();

        MetricConfig string = new MetricConfig();
        string.setKind("string");
        string.setValue("MSK-DC1");
        assertThat(string.isCounterSpecConsistent()).isTrue();
    }

    @Test
    void stringSpecRequiresDictionaryOrValue() {
        MetricConfig scenarioDriven = new MetricConfig();
        scenarioDriven.setKind("string");
        scenarioDriven.setMode("scenario");
        scenarioDriven.setValues(List.of("OK", "DEGRADED", "CRITICAL", "RECOVERING", "UNREACHABLE", "DEGRADED"));
        assertThat(scenarioDriven.isStringSpecConsistent()).isTrue();

        scenarioDriven.setValues(List.of("OK", "DEGRADED")); // не 6 значений
        assertThat(scenarioDriven.isStringSpecConsistent()).isFalse();

        MetricConfig stat = new MetricConfig();
        stat.setKind("string");
        stat.setValue("MSK-DC1");
        assertThat(stat.isStringSpecConsistent()).isTrue();
    }

    @Test
    void numericBoundsAreRequiredOnlyForNonString() {
        MetricConfig counterWithoutBounds = new MetricConfig();
        counterWithoutBounds.setKind("counter");
        assertThat(counterWithoutBounds.isNumericBoundsPresent()).isFalse();

        MetricConfig stringWithoutBounds = new MetricConfig();
        stringWithoutBounds.setKind("string");
        assertThat(stringWithoutBounds.isNumericBoundsPresent()).isTrue();
    }

    private MetricConfig counter(double min) {
        MetricConfig counter = new MetricConfig();
        counter.setType("IF_IN_OCTETS");
        counter.setKind("counter");
        counter.setMin(min);
        counter.setMax(12500000.0);
        counter.setNormalLow(1000000.0);
        counter.setNormalHigh(5000000.0);
        return counter;
    }
}