package ru.mininms.emulator.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Описание одной метрики устройства из {@code emulator-devices.yml}.
 *
 * <p>Пример:
 * <pre>
 * - type: CPU_UTILIZATION
 *   unit: "%"
 *   min: 0
 *   max: 100
 *   normal-low: 5
 *   normal-high: 40
 *   step: 3
 *   oid: 1.3.6.1.4.1.99990.2
 * </pre>
 *
 * <p>Строковая метрика (например, статус устройства или инвентарные данные):
 * <pre>
 * - type: DEVICE_STATUS
 *   kind: string
 *   mode: scenario
 *   values: [OK, DEGRADED, CRITICAL, RECOVERING, UNREACHABLE, DEGRADED]
 * </pre>
 *
 * <p>Счётчик (kind=counter): min/max/норма/step задают скорость прироста,
 * наружу отдаётся монотонно растущая накопленная сумма:
 * <pre>
 * - type: IF_IN_OCTETS
 *   kind: counter
 *   min: 0
 *   max: 12500000
 *   normal-low: 1000000
 *   normal-high: 5000000
 *   step: 200000
 * </pre>
 */
public class MetricConfig {

    /** Код метрики — тот же metric_type, что в справочнике NMS (например, CPU_UTILIZATION). */
    @NotBlank
    private String type;

    /** Единица измерения ("%", "dBm", "шт") — только для отображения, в SNMP не кодируется. */
    private String unit = "";

    /** Абсолютная нижняя граница значения (только для kind=numeric). */
    private Double min;

    /** Абсолютная верхняя граница значения (только для kind=numeric). */
    private Double max;

    /** Нижняя граница нормы; если не задана — совпадает с {@code min}. */
    private Double normalLow;

    /** Верхняя граница нормы; если не задана — совпадает с {@code max}. */
    private Double normalHigh;

    /** Максимальный сдвиг значения за один тик симуляции (шум случайного блуждания). */
    private Double step = 3.0;

    /** Знаков после запятой в REST-ответе; 0 — целые значения (как в реальных MIB). */
    private Integer decimals = 0;

    /**
     * Полный OID метрики в SNMP-дереве агента. Задаётся явно для синхронизации
     * со справочником metric_type&rarr;OID в NMS; если не задан — вычисляется
     * автоматически: base-oid + порядковый номер типа в отсортированном списке всех типов.
     */
    private String oid;

    /**
     * Тип SNMP-переменной: gauge (по умолчанию, беззнаковая), integer (для
     * отрицательных значений) либо counter32/counter64 (для kind=counter,
     * по умолчанию counter64).
     */
    @Pattern(regexp = "gauge|integer|counter32|counter64",
            message = "допустимы только gauge, integer, counter32, counter64")
    private String snmpType = "gauge";

    /** Вид значения метрики: numeric (по умолчанию), string или counter. */
    @Pattern(regexp = "numeric|string|counter", message = "допустимы только numeric, string или counter")
    private String kind = "numeric";

    /**
     * Режим строковой метрики: scenario — значение следует за активным сценарием
     * устройства, static — постоянное значение. Для kind=numeric не используется.
     */
    @Pattern(regexp = "scenario|static", message = "допустимы только scenario или static")
    private String mode;

    /** Словарь сценарной строковой метрики — по одному значению на код сценария (0..5). */
    private java.util.List<String> values;

    /** Постоянное значение статичной строковой метрики (kind=string, mode=static). */
    private String value;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getNormalLow() {
        return normalLow;
    }

    public void setNormalLow(Double normalLow) {
        this.normalLow = normalLow;
    }

    public Double getNormalHigh() {
        return normalHigh;
    }

    public void setNormalHigh(Double normalHigh) {
        this.normalHigh = normalHigh;
    }

    public Double getStep() {
        return step;
    }

    public void setStep(Double step) {
        this.step = step;
    }

    public Integer getDecimals() {
        return decimals;
    }

    public void setDecimals(Integer decimals) {
        this.decimals = decimals;
    }

    public String getOid() {
        return oid;
    }

    public void setOid(String oid) {
        this.oid = oid;
    }

    public String getSnmpType() {
        return snmpType;
    }

    public void setSnmpType(String snmpType) {
        this.snmpType = snmpType;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public java.util.List<String> getValues() {
        return values;
    }

    public void setValues(java.util.List<String> values) {
        this.values = values;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /** Строковая ли эта метрика (kind=string). */
    public boolean isString() {
        return "string".equals(kind);
    }

    /** Счётчик ли эта метрика (kind=counter). */
    public boolean isCounter() {
        return "counter".equals(kind);
    }

    /** 32-битный ли счётчик (со срезкой значения по модулю 2^32); по умолчанию счётчики 64-битные. */
    public boolean isCounter32SnmpType() {
        return isCounter() && "counter32".equals(snmpType);
    }

    /** Сценарная строковая метрика — значение следует за активным сценарием устройства. */
    public boolean isScenarioDriven() {
        return isString() && "scenario".equals(mode);
    }

    /** Эффективный режим строковой метрики (null трактуется как static). */
    public String effectiveMode() {
        return mode == null ? "static" : mode;
    }

    /** Стартовое значение строковой метрики: сценарная — первый элемент словаря, статичная — value. */
    public String initialStringValue() {
        return isScenarioDriven() ? scenarioValue(0) : value;
    }

    /** Значение сценарной строковой метрики по коду сценария (0..5). */
    public String scenarioValue(int scenarioCode) {
        int index = Math.min(Math.max(scenarioCode, 0), values.size() - 1);
        return values.get(index);
    }

    /** Эффективная нижняя граница нормы (null интерпретируется как min). */
    public double effectiveNormalLow() {
        return normalLow == null ? min : normalLow;
    }

    /** Эффективная верхняя граница нормы (null интерпретируется как max). */
    public double effectiveNormalHigh() {
        return normalHigh == null ? max : normalHigh;
    }

    /** SNMP-тип по умолчанию подстраивается под знак значений: отрицательный min требует integer. */
    public boolean isIntegerSnmpType() {
        if ("integer".equals(snmpType)) {
            return true;
        }
        return min != null && min < 0;
    }

    /**
     * Кросс-полевая валидация: числовые метрики требуют min/max и согласованные
     * диапазоны; строковые — корректный сценарный словарь или статичное значение.
     */
    @jakarta.validation.constraints.AssertTrue(
            message = "для числовой метрики (kind=numeric) обязательны min и max")
    public boolean isNumericBoundsPresent() {
        return isString() || (min != null && max != null);
    }

    @jakarta.validation.constraints.AssertTrue(
            message = "строковая метрика (kind=string): mode=scenario требует values из 6 непустых"
                    + " значений (по кодам сценариев 0..5), mode=static — непустое value")
    public boolean isStringSpecConsistent() {
        if (!isString()) {
            return true;
        }
        if (isScenarioDriven()) {
            return values != null && values.size() == 6
                    && values.stream().allMatch(v -> v != null && !v.isBlank());
        }
        return value != null && !value.isBlank();
    }

    @jakarta.validation.constraints.AssertTrue(
            message = "счётчик (kind=counter): скорость прироста (min) не может быть отрицательной")
    public boolean isCounterSpecConsistent() {
        return !isCounter() || (min != null && min >= 0);
    }

    @jakarta.validation.constraints.AssertTrue(
            message = "метрика должна удовлетворять: min < max, норма внутри [min, max], normal-low <= normal-high")
    public boolean isRangeConsistent() {
        if (min == null || max == null) {
            return true; // отсутствие границ у числовой метрики отловит isNumericBoundsPresent
        }
        if (min >= max) {
            return false;
        }
        double lo = effectiveNormalLow();
        double hi = effectiveNormalHigh();
        return lo >= min && hi <= max && lo <= hi;
    }
}