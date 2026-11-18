package ru.mininms.emulator.snmp;

import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.MetricConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Справочник «код метрики &rarr; OID» для SNMP-агентов (критерий №18 — собственные OID).
 *
 * <p>Поддерево телеком-метрик проекта (по умолчанию {@code 1.3.6.1.4.1.99990},
 * замените на согласованный номер предприятия):
 * <pre>
 * 1.3.6.1.4.1.99990.1   — активные звонки (BTS)          [авто]
 * 1.3.6.1.4.1.99990.2   — загрузка CPU                    [авто]
 * 1.3.6.1.4.1.99990.3   — загрузка входящего интерфейса   [авто]
 * 1.3.6.1.4.1.99990.4   — загрузка исходящего интерфейса  [авто]
 * 1.3.6.1.4.1.99990.5   — загрузка памяти                 [авто]
 * 1.3.6.1.4.1.99990.6   — число подключённых ONT (OLT)    [авто]
 * 1.3.6.1.4.1.99990.7   — загрузка PON (OLT)              [авто]
 * 1.3.6.1.4.1.99990.8   — мощность радиосигнала (BTS, dBm)[авто]
 * 1.3.6.1.4.1.99990.9   — температура                     [авто]
 * 1.3.6.1.4.1.99990.0.x — служебные значения эмулятора:
 *   .0.1.0 deviceId, .0.2.0 код активного сценария, .0.3.0 признак SILENT
 * </pre>
 *
 * <p>OID можно задать явно в {@code emulator-devices.yml} (поле {@code oid} метрики) —
 * тогда он должен совпадать со справочником metric_type в NMS. Типы без явного OID
 * получают последовательные номера по алфавиту кодов (предсказуемо и стабильно).
 */
public final class OidMapper {

    /** Поддерево метаданных эмулятора (deviceId / сценарий / SILENT). */
    static final String META_BRANCH = "0";

    private final String baseOid;
    private final Map<String, String> typeToOid;

    public OidMapper(String baseOid, java.util.List<DeviceConfig> devices) {
        this.baseOid = Objects.requireNonNull(baseId(baseOid), "baseOid обязателен");
        Objects.requireNonNull(devices, "devices обязательны");

        Set<String> allTypes = new TreeSet<>();
        Map<String, String> explicit = new HashMap<>();
        for (DeviceConfig device : devices) {
            for (MetricConfig metric : device.getMetrics()) {
                allTypes.add(metric.getType());
                if (metric.getOid() != null && !metric.getOid().isBlank()) {
                    String previous = explicit.put(metric.getType(), baseId(metric.getOid()));
                    if (previous != null && !previous.equals(baseId(metric.getOid()))) {
                        throw new IllegalStateException(
                                "Метрика " + metric.getType() + " объявлена с разными OID: "
                                        + previous + " и " + metric.getOid());
                    }
                }
            }
        }

        Map<String, String> mapping = new HashMap<>();
        int index = 1;
        for (String type : allTypes) {
            String explicitOid = explicit.get(type);
            mapping.put(type, explicitOid != null ? explicitOid : this.baseOid + "." + index);
            index++;
        }

        Set<String> seenOids = new HashSet<>();
        mapping.values().forEach(oid -> requireUnique(seenOids, oid, null));
        requireUnique(seenOids, deviceIdOid(), null);
        requireUnique(seenOids, scenarioCodeOid(), null);
        requireUnique(seenOids, silentFlagOid(), null);
        this.typeToOid = Map.copyOf(mapping);
    }

    public String baseOid() {
        return baseOid;
    }

    /** OID типа метрики по её коду (справочник «metric_type → OID»). */
    public String oidFor(String metricType) {
        String oid = typeToOid.get(metricType);
        if (oid == null) {
            throw new IllegalStateException("Для метрики " + metricType + " не назначен OID");
        }
        return oid;
    }

    /**
     * OID экземпляра для опроса: по конвенции SNMP значение скаляра доступно
     * по адресу «OID типа + .0» (так же, как sysUpTime = 1.3.6.1.2.1.1.3.0).
     */
    public String instanceOidFor(String metricType) {
        return oidFor(metricType) + ".0";
    }

    /** Весь справочник «код метрики &rarr; OID» (для REST и документации). */
    public Map<String, String> asMap() {
        return typeToOid;
    }

    public String deviceIdOid() {
        return baseOid + "." + META_BRANCH + ".1.0";
    }

    public String scenarioCodeOid() {
        return baseOid + "." + META_BRANCH + ".2.0";
    }

    public String silentFlagOid() {
        return baseOid + "." + META_BRANCH + ".3.0";
    }

    private static void requireUnique(Set<String> seen, String oid, String type) {
        if (!seen.add(oid)) {
            throw new IllegalStateException("OID " + oid + " назначен более чем одной метрике"
                    + (type == null ? "" : " (конфликт на " + type + ")"));
        }
    }

    /** Убирает ведущую точку, чтобы "1.3.6..." и ".1.3.6..." считались одинаковыми. */
    private static String baseId(String oid) {
        if (oid == null) {
            return null;
        }
        String trimmed = oid.trim();
        return trimmed.startsWith(".") ? trimmed.substring(1) : trimmed;
    }
}