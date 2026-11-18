package ru.mininms.emulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.mininms.emulator.snmp.OidMapper;

/**
 * Сборка служебных бинов эмулятора.
 */
@Configuration
public class EmulatorConfig {

    /**
     * Справочник «код метрики &rarr; OID» строится один раз из конфигурации
     * и используется и SNMP-агентами, и REST (поле oid в DeviceDto).
     */
    @Bean
    public OidMapper oidMapper(EmulatorProperties properties) {
        return new OidMapper(properties.getSnmp().getBaseOid(), properties.getDevices());
    }
}