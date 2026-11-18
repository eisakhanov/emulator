package ru.mininms.emulator.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Корневые настройки эмулятора (префикс {@code emulator} в application.yml / emulator-devices.yml).
 *
 * <p>Парк устройств подключается из отдельного файла через
 * {@code spring.config.import=optional:classpath:emulator-devices.yml} —
 * так конфигурацию парка можно переопределить снаружи (volume в docker-compose),
 * не пересобирая приложение.
 */
@Validated
@ConfigurationProperties(prefix = "emulator")
public class EmulatorProperties {

    /** Период тика движка симуляции (обновление значений метрик). */
    @NotNull
    private Duration tickInterval = Duration.ofSeconds(1);

    /** Парк виртуальных устройств. */
    @Valid
    @NotEmpty(message = "в emulator-devices.yml должен быть описан хотя бы один сценарий device")
    private List<DeviceConfig> devices = new ArrayList<>();

    /** Настройки SNMP-интерфейса. */
    @Valid
    private SnmpConfig snmp = new SnmpConfig();

    public Duration getTickInterval() {
        return tickInterval;
    }

    public void setTickInterval(Duration tickInterval) {
        this.tickInterval = tickInterval;
    }

    public List<DeviceConfig> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceConfig> devices) {
        this.devices = devices;
    }

    public SnmpConfig getSnmp() {
        return snmp;
    }

    public void setSnmp(SnmpConfig snmp) {
        this.snmp = snmp;
    }
}