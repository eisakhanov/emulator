package ru.mininms.emulator.model;

/**
 * Применённый к устройству сценарий (неизменяемое значение).
 *
 * @param type            тип сценария
 * @param metricType      целевая метрика (обязательна для HIGH_LOAD / FAILURE / FLAPPING)
 * @param targetValue     целевое значение (для HIGH_LOAD — куда расти; для FLAPPING — центр колебаний)
 * @param amplitude       размах колебаний для FLAPPING (половина вниз/вверх от цели)
 * @param periodSec       период колебаний для FLAPPING
 * @param durationSec     авто-возврат в NORMAL через N секунд (null — до явного сброса)
 * @param startedAtEpochMs момент применения сценария (epoch milliseconds)
 */
public record ActiveScenario(
        ScenarioType type,
        String metricType,
        Double targetValue,
        Double amplitude,
        Long periodSec,
        Long durationSec,
        long startedAtEpochMs) {

    /** Сценарий по умолчанию — штатная работа. */
    public static ActiveScenario normal() {
        return new ActiveScenario(ScenarioType.NORMAL, null, null, null, null, null, 0L);
    }

    /** Истёк ли срок действия сценария (для авто-возврата в NORMAL). */
    public boolean expired(long nowEpochMs) {
        return durationSec != null && durationSec > 0
                && nowEpochMs - startedAtEpochMs >= durationSec * 1000L;
    }
}