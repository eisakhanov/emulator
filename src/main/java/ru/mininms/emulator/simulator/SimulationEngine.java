package ru.mininms.emulator.simulator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.model.ActiveScenario;
import ru.mininms.emulator.model.DeviceState;
import ru.mininms.emulator.model.ScenarioType;

import java.util.Random;

/**
 * Движок симуляции: раз в {@code emulator.tick-interval} обновляет значения
 * всех метрик всех устройств.
 *
 * <p>Модель значений — <b>случайное блуждание с тягой к норме</b>, а не чистый
 * рандом: график истории метрики выглядит как живой, а пороговые правила NMS
 * с окном {@code duration} корректно отличают устойчивый рост (аларм) от
 * кратковременного всплеска (без аларма) — это и есть база сценарных тестов.
 *
 * <p>Сценарии меняют модель поведения целевой метрики:
 * <ul>
 *   <li>{@link ScenarioType#HIGH_LOAD} — закрывает 20% расстояния до цели за тик;</li>
 *   <li>{@link ScenarioType#FAILURE} — мгновенный прыжок к цели и удержание;</li>
 *   <li>{@link ScenarioType#RECOVERY} — усиленная тяга к норме, по возврату всех
 *       метрик в норму сценарий сам становится NORMAL;</li>
 *   <li>{@link ScenarioType#FLAPPING} — прямоугольные колебания вокруг цели
 *       с периодом periodSec (полпериода выше, полпериода ниже).</li>
 * </ul>
 * Значения при SILENT продолжают считаться (устройство «живо», но сеть молчит).
 */
@Component
public class SimulationEngine {

    /** Доля расстояния до цели, закрываемая за один тик при HIGH_LOAD. */
    static final double LOAD_RATE = 0.2;
    /** Доля расстояния до середины нормы, закрываемая за тик при RECOVERY. */
    static final double RECOVERY_RATE = 0.3;
    /** Сила тяги к середине нормы при обычном блуждании. */
    static final double NORMAL_PULL = 0.05;

    private final DeviceRegistry registry;
    private final Random random = new Random();

    public SimulationEngine(DeviceRegistry registry) {
        this.registry = registry;
    }

    /** Периодический тик (расписание настраивается свойством emulator.tick-interval). */
    @Scheduled(fixedDelayString = "${emulator.tick-interval:1s}")
    public void tick() {
        long now = System.currentTimeMillis();
        for (DeviceState state : registry.all()) {
            stepDevice(state, now);
        }
    }

    /** Один шаг устройства — виден тестам без Spring-контекста. */
    void stepDevice(DeviceState state, long nowEpochMs) {
        expireScenarioIfDue(state, nowEpochMs);
        double dtSec = state.secondsSinceLastStep(nowEpochMs);

        ActiveScenario scenario = state.scenario();
        for (MetricConfig metric : state.device().getMetrics()) {
            if (metric.isString()) {
                stepStringMetric(state, metric, scenario);
            } else if (metric.isCounter()) {
                stepCounterMetric(state, metric, scenario, nowEpochMs, dtSec);
            } else {
                state.updateValue(metric.getType(),
                        nextValue(state.doubleValue(metric.getType()), metric, scenario, nowEpochMs));
            }
        }

        if (scenario.type() == ScenarioType.RECOVERY && allMetricsInNormalRange(state)) {
            state.setScenario(ActiveScenario.normal());
        }
        registry.notifyListeners(state);
    }

    private void expireScenarioIfDue(DeviceState state, long nowEpochMs) {
        if (state.scenario().expired(nowEpochMs)) {
            state.setScenario(ActiveScenario.normal());
        }
    }

    /**
     * Строковые метрики: сценарные (mode=scenario) следуют за активным сценарием
     * устройства (значение по коду сценария), статичные (mode=static) не меняются.
     */
    private void stepStringMetric(DeviceState state, MetricConfig metric, ActiveScenario scenario) {
        if (metric.isScenarioDriven()) {
            state.updateStringValue(metric.getType(), metric.scenarioValue(scenario.type().code()));
        }
    }

    /**
     * Счётчики: скорость прироста проходит те же модели, что и числовые метрики
     * (блуждание и сценарии), а наружу отдаётся накопленная сумма counter += rate × dt.
     */
    private void stepCounterMetric(DeviceState state, MetricConfig metric, ActiveScenario scenario,
                                   long nowEpochMs, double dtSec) {
        double rate = nextValue(state.counterRate(metric.getType()), metric, scenario, nowEpochMs);
        state.updateCounterRate(metric.getType(), rate);
        state.incrementCounter(metric.getType(), rate * dtSec);
    }

    private double nextValue(double current, MetricConfig metric, ActiveScenario scenario, long nowEpochMs) {
        return switch (scenario.type()) {
            case NORMAL, SILENT -> randomWalk(current, metric);
            case HIGH_LOAD -> isTargetMetric(metric, scenario)
                    ? driftTowards(current, targetOf(scenario, metric), LOAD_RATE, metric)
                    : randomWalk(current, metric);
            case FAILURE -> isTargetMetric(metric, scenario)
                    ? jumpTo(targetOf(scenario, metric), metric)
                    : randomWalk(current, metric);
            case RECOVERY -> pullToNormal(current, metric);
            case FLAPPING -> isTargetMetric(metric, scenario)
                    ? flap(scenario, metric, nowEpochMs)
                    : randomWalk(current, metric);
        };
    }

    private boolean isTargetMetric(MetricConfig metric, ActiveScenario scenario) {
        return metric.getType().equals(scenario.metricType());
    }

    private double targetOf(ActiveScenario scenario, MetricConfig metric) {
        return scenario.targetValue() == null ? metric.getMax() : scenario.targetValue();
    }

    /** Случайное блуждание: шум ±step + слабая тяга к середине нормы. */
    private double randomWalk(double value, MetricConfig metric) {
        double mid = (metric.effectiveNormalLow() + metric.effectiveNormalHigh()) / 2;
        double pull = NORMAL_PULL * (mid - value);
        double noise = (random.nextDouble() - 0.5) * 2 * metric.getStep();
        return clamp(value + pull + noise, metric);
    }

    /** Плавный дрейф к цели: rate-я часть расстояния за тик + шум. */
    private double driftTowards(double value, double target, double rate, MetricConfig metric) {
        double noise = (random.nextDouble() - 0.5) * metric.getStep() * 0.5;
        return clamp(value + (target - value) * rate + noise, metric);
    }

    /** Мгновенный прыжок к цели (с небольшим шумом) и удержание. */
    private double jumpTo(double target, MetricConfig metric) {
        return clamp(target + (random.nextDouble() - 0.5) * metric.getStep(), metric);
    }

    /** Возврат к норме: усиленная тяга к середине нормального диапазона. */
    private double pullToNormal(double value, MetricConfig metric) {
        double mid = (metric.effectiveNormalLow() + metric.effectiveNormalHigh()) / 2;
        return clamp(value + (mid - value) * RECOVERY_RATE, metric);
    }

    /** Прямоугольные колебания: полпериода на center+amplitude, полпериода на center-amplitude. */
    private double flap(ActiveScenario scenario, MetricConfig metric, long nowEpochMs) {
        long periodMs = Math.max(1, scenario.periodSec()) * 1000L;
        long halfPeriod = Math.max(1, periodMs / 2);
        boolean highPhase = ((nowEpochMs - scenario.startedAtEpochMs()) / halfPeriod) % 2 == 0;
        double amplitude = scenario.amplitude() == null ? 0 : scenario.amplitude();
        double center = targetOf(scenario, metric);
        double target = highPhase ? center + amplitude : center - amplitude;
        return clamp(target + (random.nextDouble() - 0.5) * metric.getStep() * 0.3, metric);
    }

    private boolean allMetricsInNormalRange(DeviceState state) {
        for (MetricConfig metric : state.device().getMetrics()) {
            if (metric.isString()) {
                continue; // строковые метрики не участвуют в условии завершения RECOVERY
            }
            // у счётчиков в норму должна вернуться скорость прироста — само значение монотонно
            double value = metric.isCounter()
                    ? state.counterRate(metric.getType())
                    : state.doubleValue(metric.getType());
            if (value < metric.effectiveNormalLow() || value > metric.effectiveNormalHigh()) {
                return false;
            }
        }
        return true;
    }

    private double clamp(double value, MetricConfig metric) {
        return Math.max(metric.getMin(), Math.min(metric.getMax(), value));
    }
}