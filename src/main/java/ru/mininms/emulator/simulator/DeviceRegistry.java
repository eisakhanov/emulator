package ru.mininms.emulator.simulator;

import org.springframework.stereotype.Component;
import ru.mininms.emulator.common.DeviceNotFoundException;
import ru.mininms.emulator.common.ScenarioValidationException;
import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.EmulatorProperties;
import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.dto.ScenarioRequest;
import ru.mininms.emulator.model.ActiveScenario;
import ru.mininms.emulator.model.DeviceState;
import ru.mininms.emulator.model.ScenarioType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Реестр виртуальных устройств: строится один раз из {@code emulator-devices.yml}
 * при старте приложения и живёт до остановки (состояние — в памяти).
 *
 * <p>Отвечает за применение сценариев: валидирует бизнес-правила
 * (метрика существует у устройства, targetValue в диапазоне) и дефолты.
 */
@Component
public class DeviceRegistry {

    private final Map<Long, DeviceState> states = new LinkedHashMap<>();
    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    public DeviceRegistry(EmulatorProperties properties) {
        for (DeviceConfig device : properties.getDevices()) {
            DeviceState previous = states.put(device.getId(), new DeviceState(device));
            if (previous != null) {
                throw new IllegalStateException(
                        "Дубликат id устройства в emulator-devices.yml: " + device.getId());
            }
        }
    }

    public Collection<DeviceState> all() {
        return Collections.unmodifiableCollection(states.values());
    }

    public DeviceState required(long id) {
        DeviceState state = states.get(id);
        if (state == null) {
            throw new DeviceNotFoundException(id);
        }
        return state;
    }

    public void addListener(StateListener listener) {
        listeners.add(listener);
    }

    /** Сообщить подписчикам (SNMP-агентам), что значения обновились. */
    public void notifyListeners(DeviceState state) {
        for (StateListener listener : listeners) {
            listener.onTick(state);
        }
    }

    /**
     * Применить сценарий к устройству с валидацией бизнес-правил.
     *
     * @return применённый сценарий (с подставленными дефолтами)
     */
    public ActiveScenario applyScenario(long deviceId, ScenarioRequest request) {
        DeviceState state = required(deviceId);
        validate(state, request);
        ActiveScenario scenario = buildScenario(state, request);
        state.setScenario(scenario);
        return scenario;
    }

    /** Сбросить устройство в NORMAL. */
    public ActiveScenario resetScenario(long deviceId) {
        DeviceState state = required(deviceId);
        ActiveScenario normal = ActiveScenario.normal();
        state.setScenario(normal);
        return normal;
    }

    private void validate(DeviceState state, ScenarioRequest request) {
        ScenarioType type = request.type();
        if (type != ScenarioType.NORMAL && type != ScenarioType.RECOVERY && type != ScenarioType.SILENT) {
            if (request.metricType() == null || request.metricType().isBlank()) {
                throw new ScenarioValidationException(
                        "Для сценария " + type + " укажите metricType");
            }
            MetricConfig metric = findMetric(state, request.metricType());
            if (metric == null) {
                throw new ScenarioValidationException(
                        "У устройства " + state.id() + " нет метрики " + request.metricType());
            }
            if (metric.isString()) {
                throw new ScenarioValidationException(
                        "Сценарий " + type + " применим только к числовым метрикам, а "
                                + request.metricType() + " — строковая (kind=string)");
            }
            if (type == ScenarioType.HIGH_LOAD && request.targetValue() == null) {
                throw new ScenarioValidationException(
                        "Для сценария HIGH_LOAD укажите targetValue (например, 95 для CPU)");
            }
            if (request.targetValue() != null
                    && (request.targetValue() < metric.getMin() || request.targetValue() > metric.getMax())) {
                throw new ScenarioValidationException(
                        "targetValue должен быть в диапазоне [" + metric.getMin() + "; "
                                + metric.getMax() + "] метрики " + metric.getType());
            }
        }
    }

    private ActiveScenario buildScenario(DeviceState state, ScenarioRequest request) {
        MetricConfig metric = request.metricType() == null ? null : findMetric(state, request.metricType());
        Double target = request.targetValue();
        if (target == null && metric != null && request.type() == ScenarioType.FAILURE) {
            target = metric.getMax();
        }
        Double amplitude = request.amplitude();
        if (amplitude == null && metric != null && request.type() == ScenarioType.FLAPPING) {
            amplitude = (metric.getMax() - metric.getMin()) / 8;
        }
        Long periodSec = request.periodSec();
        if (periodSec == null && request.type() == ScenarioType.FLAPPING) {
            periodSec = 60L;
        }
        return new ActiveScenario(request.type(), request.metricType(), target, amplitude, periodSec,
                request.durationSec(), System.currentTimeMillis());
    }

    private MetricConfig findMetric(DeviceState state, String metricType) {
        return state.device().getMetrics().stream()
                .filter(metric -> metric.getType().equals(metricType))
                .findFirst()
                .orElse(null);
    }
}