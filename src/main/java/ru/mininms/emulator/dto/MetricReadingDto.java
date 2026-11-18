package ru.mininms.emulator.dto;

import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.model.DeviceState;

import java.time.Instant;

/**
 * Текущее значение метрики — элемент ответа {@code GET /api/emulator/devices/{id}/metrics}.
 *
 * <p>Имена полей согласованы с контрактом MetricEvent NMS (deviceId передаётся
 * в пути запроса; poller знает, у кого спрашивал). {@code value} — число
 * (округлено до decimals) или строка для метрик kind=string.
 */
public record MetricReadingDto(String metricType, Object value, String unit, Instant timestamp) {

    public static MetricReadingDto from(DeviceState state, MetricConfig metric) {
        Object value;
        if (metric.isString()) {
            value = state.stringValue(metric.getType());
        } else if (metric.isCounter()) {
            value = state.counterValue(metric.getType());
        } else {
            double factor = Math.pow(10, metric.getDecimals());
            value = Math.round(state.doubleValue(metric.getType()) * factor) / factor;
        }
        return new MetricReadingDto(metric.getType(), value, metric.getUnit(),
                state.timestamp(metric.getType()));
    }
}