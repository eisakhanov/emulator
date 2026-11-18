package ru.mininms.emulator.model;

import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.MetricConfig;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Состояние одного эмулируемого устройства: текущие значения метрик и активный сценарий.
 *
 * <p>Потокобезопасность: с состоянием параллельно работают три стороны —
 * движок симуляции (тик), REST-потоки (чтение/смена сценария) и SNMP-агенты
 * (чтение значений), поэтому значения лежат в {@link ConcurrentHashMap},
 * а сценарий — в {@link AtomicReference}.
 */
public final class DeviceState {

    private final DeviceConfig device;
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Map<String, Instant> timestamps = new ConcurrentHashMap<>();
    /** Скорости прироста счётчиков (kind=counter) — по ним ходит симуляция. */
    private final Map<String, Double> counterRates = new ConcurrentHashMap<>();
    /** Момент предыдущего шага симуляции, epoch ms — для расчёта dt приращения счётчиков. */
    private final AtomicLong lastStepEpochMs = new AtomicLong();
    private final AtomicReference<ActiveScenario> scenario = new AtomicReference<>(ActiveScenario.normal());

    public DeviceState(DeviceConfig device) {
        this.device = device;
        Instant now = Instant.now();
        for (MetricConfig metric : device.getMetrics()) {
            // числовые — случайная точка внутри нормы; строковые — стартовое значение из
            // конфигурации; счётчики — нуль с момента старта эмулятора (согласовано с sysUpTime от нуля)
            Object initial;
            if (metric.isString()) {
                initial = metric.initialStringValue();
            } else if (metric.isCounter()) {
                counterRates.put(metric.getType(), ThreadLocalRandom.current().nextDouble(
                        metric.effectiveNormalLow(), metric.effectiveNormalHigh()));
                initial = 0L;
            } else {
                initial = ThreadLocalRandom.current().nextDouble(
                        metric.effectiveNormalLow(), metric.effectiveNormalHigh());
            }
            values.put(metric.getType(), initial);
            timestamps.put(metric.getType(), now);
        }
    }

    public DeviceConfig device() {
        return device;
    }

    public long id() {
        return device.getId();
    }

    public ActiveScenario scenario() {
        return scenario.get();
    }

    public void setScenario(ActiveScenario newScenario) {
        scenario.set(newScenario);
    }

    /** «Устройство молчит» — активен сценарий SILENT. */
    public boolean isSilent() {
        return scenario.get().type() == ScenarioType.SILENT;
    }

    /** Текущее значение метрики: Double для числовых, String для строковых. */
    public Object value(String metricType) {
        return values.get(metricType);
    }

    /** Числовое значение метрики (только для kind=numeric). */
    public double doubleValue(String metricType) {
        return (Double) values.get(metricType);
    }

    /** Строковое значение метрики (только для kind=string). */
    public String stringValue(String metricType) {
        return (String) values.get(metricType);
    }

    /** Накопленное значение счётчика (только для kind=counter). */
    public long counterValue(String metricType) {
        return (Long) values.get(metricType);
    }

    /** Скорость прироста счётчика (только для kind=counter). */
    public double counterRate(String metricType) {
        return counterRates.get(metricType);
    }

    /** Секунды с прошлого шага симуляции (0 на первом шаге) — для приращения счётчиков. */
    public double secondsSinceLastStep(long nowEpochMs) {
        long previous = lastStepEpochMs.getAndSet(nowEpochMs);
        return previous == 0 ? 0.0 : Math.max(0.0, (nowEpochMs - previous) / 1000.0);
    }

    public Instant timestamp(String metricType) {
        return timestamps.get(metricType);
    }

    public void updateValue(String metricType, double value) {
        values.put(metricType, value);
        timestamps.put(metricType, Instant.now());
    }

    /** Обновить строковое значение; timestamp меняется только при смене значения. */
    public void updateStringValue(String metricType, String value) {
        Object previous = values.put(metricType, value);
        if (!value.equals(previous)) {
            timestamps.put(metricType, Instant.now());
        }
    }

    /** Обновить скорость прироста счётчика. */
    public void updateCounterRate(String metricType, double rate) {
        counterRates.put(metricType, rate);
    }

    /** Прирастить счётчик на дельту (атомарно; дельта не бывает отрицательной). */
    public void incrementCounter(String metricType, double delta) {
        values.compute(metricType, (type, current) -> (Long) current + (long) Math.ceil(Math.max(0.0, delta)));
        timestamps.put(metricType, Instant.now());
    }
}