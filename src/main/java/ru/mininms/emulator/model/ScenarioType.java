package ru.mininms.emulator.model;

/**
 * Сценарии симуляции — инструмент сценарных тестов (критерии №6, №7, №9 заявки).
 *
 * <p>Сценарий применяется к устройству через
 * {@code POST /api/emulator/devices/{id}/scenarios} и действует до истечения
 * {@code durationSec} либо до явного сброса {@code DELETE .../scenarios}.
 */
public enum ScenarioType {

    /** Штатная работа: случайное блуждание внутри нормы. */
    NORMAL(0),

    /** Плавный рост выбранной метрики к целевому значению (например, CPU &rarr; 95%) — «рост нагрузки». */
    HIGH_LOAD(1),

    /** Резкий выход за порог и удержание — «авария». */
    FAILURE(2),

    /** Плавный возврат к норме — тест авто-закрытия аларма (критерий №7). */
    RECOVERY(3),

    /**
     * Устройство «молчит»: SNMP-агент дропает пакеты без ответа, REST отдаёт 503.
     * Poller фиксирует таймаут &rarr; pollStatus=TIMEOUT (критерий №9).
     */
    SILENT(4),

    /** Колебания вокруг порога — тест повторного открытия аларма после авто-закрытия. */
    FLAPPING(5);

    /** Код сценария в SNMP-метаданных устройства (base-oid.0.2.0). */
    private final int code;

    ScenarioType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}