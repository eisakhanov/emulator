package ru.mininms.emulator.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.mininms.emulator.model.ScenarioType;

/**
 * Запрос на применение сценария к устройству.
 *
 * <p>Правила заполнения по типам сценариев:
 * <ul>
 *   <li>{@code NORMAL}, {@code RECOVERY}, {@code SILENT} — только type;</li>
 *   <li>{@code HIGH_LOAD} — обязательно metricType и targetValue
 *       (плавный рост к цели);</li>
 *   <li>{@code FAILURE} — metricType обязательно, targetValue по умолчанию
 *       берётся как максимум диапазона метрики;</li>
 *   <li>{@code FLAPPING} — metricType и targetValue (центр колебаний);
 *       amplitude по умолчанию 1/8 диапазона, periodSec — 60.</li>
 * </ul>
 *
 * <p>{@code durationSec} (опционально) — авто-возврат в NORMAL через N секунд.
 *
 * @param type        тип сценария (обязателен)
 * @param metricType  целевая метрика
 * @param targetValue целевое значение
 * @param amplitude   амплитуда колебаний (FLAPPING)
 * @param periodSec   период колебаний, секунд (FLAPPING)
 * @param durationSec время жизни сценария, секунд
 */
public record ScenarioRequest(
        @NotNull(message = "type — обязательное поле")
        ScenarioType type,

        String metricType,

        Double targetValue,

        Double amplitude,

        @Min(value = 1, message = "periodSec должен быть >= 1")
        Long periodSec,

        @Min(value = 1, message = "durationSec должен быть >= 1")
        Long durationSec) {
}