package ru.mininms.emulator.common;

/**
 * Устройство с таким id отсутствует в парке эмулятора (&rarr; HTTP 404).
 */
public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(long deviceId) {
        super("Устройство " + deviceId + " не найдено в парке эмулятора");
    }
}