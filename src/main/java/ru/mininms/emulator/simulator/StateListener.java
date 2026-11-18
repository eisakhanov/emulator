package ru.mininms.emulator.simulator;

import ru.mininms.emulator.model.DeviceState;

/**
 * Подписчик на тики движка симуляции.
 *
 * <p>Единственная реализация в проде — SNMP-агенты: после каждого тика они
 * пушат свежие значения метрик в свои Managed Objects. Разделение через
 * интерфейс позволяет движку не знать про SNMP.
 */
@FunctionalInterface
public interface StateListener {

    /** Вызывается после обновления значений метрик устройства. */
    void onTick(DeviceState state);
}