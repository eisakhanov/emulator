package ru.mininms.emulator.model;

import java.time.Instant;

/**
 * Текущее значение метрики (единица выдачи REST API).
 *
 * @param metricType код метрики (тот же metric_type, что в NMS)
 * @param value      значение: число (округлено до decimals) или строка для kind=string
 * @param unit       единица измерения
 * @param timestamp  момент последнего обновления, UTC
 */
public record MetricReading(String metricType, Object value, String unit, Instant timestamp) {
}