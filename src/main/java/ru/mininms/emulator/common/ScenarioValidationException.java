package ru.mininms.emulator.common;

/**
 * Сценарий некорректен для этого устройства (&rarr; HTTP 400):
 * не указана метрика, метрики нет у устройства, targetValue вне диапазона и т.п.
 */
public class ScenarioValidationException extends RuntimeException {

    public ScenarioValidationException(String message) {
        super(message);
    }
}