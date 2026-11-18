package ru.mininms.emulator.dto;

import ru.mininms.emulator.model.ActiveScenario;
import ru.mininms.emulator.model.ScenarioType;

import java.time.Instant;

/**
 * Активный сценарий устройства (ответ на POST/GET/DELETE .../scenarios).
 */
public record ActiveScenarioDto(
        ScenarioType type,
        String metricType,
        Double targetValue,
        Double amplitude,
        Long periodSec,
        Long durationSec,
        Instant startedAt) {

    public static ActiveScenarioDto from(ActiveScenario scenario) {
        return new ActiveScenarioDto(
                scenario.type(),
                scenario.metricType(),
                scenario.targetValue(),
                scenario.amplitude(),
                scenario.periodSec(),
                scenario.durationSec(),
                Instant.ofEpochMilli(scenario.startedAtEpochMs()));
    }
}