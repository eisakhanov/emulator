package ru.mininms.emulator.snmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import ru.mininms.emulator.config.EmulatorProperties;
import ru.mininms.emulator.model.DeviceState;
import ru.mininms.emulator.simulator.DeviceRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Поднимает по одному {@link SnmpDeviceAgent} на каждое устройство с заданным
 * {@code snmp-port} и подписывает их на тики движка симуляции.
 *
 * <p>Жизненный цикл привязан к Spring ({@link SmartLifecycle}): агенты
 * стартуют после инициализации контекста и останавливаются при shutdown —
 * UDP-порты освобождаются корректно, без висячих процессов.
 *
 * <p>Отключается свойством {@code emulator.snmp.enabled=false} (удобно для
 * отладки REST и в части тестов).
 */
@Component
@ConditionalOnProperty(prefix = "emulator.snmp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SnmpAgentsLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SnmpAgentsLifecycle.class);

    private final DeviceRegistry registry;
    private final EmulatorProperties properties;
    private final OidMapper oidMapper;
    private final List<SnmpDeviceAgent> agents = new ArrayList<>();
    private volatile boolean running;

    public SnmpAgentsLifecycle(DeviceRegistry registry, EmulatorProperties properties, OidMapper oidMapper) {
        this.registry = registry;
        this.properties = properties;
        this.oidMapper = oidMapper;
    }

    @Override
    public void start() {
        for (DeviceState state : registry.all()) {
            Integer port = state.device().getSnmpPort();
            if (port == null) {
                continue;
            }
            SnmpDeviceAgent agent = new SnmpDeviceAgent(state, oidMapper, properties.getSnmp());
            try {
                agent.start();
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось поднять SNMP-агента устройства "
                        + state.id() + " на UDP-порту " + port + " (порт занят?)", e);
            }
            registry.addListener(agent);
            agents.add(agent);
            log.info("SNMP-агент устройства {} '{}' слушает udp:{}:{} (community '{}')",
                    state.id(), state.device().getName(), properties.getSnmp().getBindAddress(), port,
                    state.device().getCommunity());
        }
        running = true;
    }

    @Override
    public void stop() {
        for (SnmpDeviceAgent agent : agents) {
            try {
                agent.stop();
            } catch (IOException e) {
                log.warn("Ошибка остановки SNMP-агента устройства {}", agent.state().id(), e);
            }
        }
        agents.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}