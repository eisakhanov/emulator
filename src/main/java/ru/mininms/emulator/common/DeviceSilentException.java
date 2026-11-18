package ru.mininms.emulator.common;

/**
 * Устройство в сценарии SILENT («молчит») — метрики недоступны (&rarr; HTTP 503).
 * Poller при этом фиксирует таймаут: pollStatus=TIMEOUT (критерий №9 заявки).
 */
public class DeviceSilentException extends RuntimeException {

    public DeviceSilentException(long deviceId) {
        super("Устройство " + deviceId + " не отвечает (сценарий SILENT)");
    }
}