package ru.mininms.emulator.dto;

import ru.mininms.emulator.config.MetricConfig;

import java.util.List;

/**
 * Описание метрики устройства (в составе {@link DeviceDto}).
 *
 * <p>Числовые поля (min/max/норма/decimals) — null для строковых метрик;
 * {@code values} — словарь сценарной строковой метрики (по кодам сценариев).
 *
 * @param oid OID экземпляра для SNMP-опроса (OID типа + ".0" — конвенция скаляров)
 */
public record MetricSpecDto(
        String metricType,
        String unit,
        String kind,
        String mode,
        Double min,
        Double max,
        Double normalLow,
        Double normalHigh,
        Integer decimals,
        List<String> values,
        String oid) {

    public static MetricSpecDto from(MetricConfig metric, String oid) {
        boolean string = metric.isString();
        return new MetricSpecDto(
                metric.getType(),
                metric.getUnit(),
                metric.getKind(),
                string ? metric.effectiveMode() : null,
                string ? null : metric.getMin(),
                string ? null : metric.getMax(),
                string ? null : metric.effectiveNormalLow(),
                string ? null : metric.effectiveNormalHigh(),
                string ? null : metric.getDecimals(),
                metric.isScenarioDriven() ? List.copyOf(metric.getValues()) : null,
                oid);
    }
}