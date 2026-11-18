package ru.mininms.emulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Эмулятор сетевых устройств для «Мини-NMS».
 *
 * <p>Одно приложение — три слоя:
 * <ul>
 *   <li>{@code simulator} — движок симуляции метрик (random walk + сценарии);</li>
 *   <li>{@code web} — REST API (список устройств, метрики, управление сценариями);</li>
 *   <li>{@code snmp} — SNMP v2c-агент на каждое устройство (snmp4j-agent).</li>
 * </ul>
 *
 * <p>Состояние живёт в памяти (своей БД у эмулятора нет — см. architecture.md),
 * парк устройств описывается в {@code emulator-devices.yml}.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class EmulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmulatorApplication.class, args);
    }
}