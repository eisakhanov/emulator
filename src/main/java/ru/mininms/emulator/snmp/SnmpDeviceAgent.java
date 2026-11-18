package ru.mininms.emulator.snmp;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.agent.CommandProcessor;
import org.snmp4j.agent.DefaultMOServer;
import org.snmp4j.agent.DuplicateRegistrationException;
import org.snmp4j.agent.MOServer;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.agent.mo.snmp.CoexistenceInfo;
import org.snmp4j.agent.mo.snmp.CoexistenceInfoProvider;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.config.SnmpConfig;
import ru.mininms.emulator.model.DeviceState;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SNMP v2c-агент одного виртуального устройства (snmp4j-agent), слушает
 * собственный UDP-порт — с точки зрения NMS это отдельный хост в сети.
 *
 * <p>Дерево агента:
 * <ul>
 *   <li>стандартная system-группа MIB-2 (sysDescr, sysUpTime, sysName) — чтобы
 *       проходили обычные SNMP-сканеры и discovery NMS;</li>
 *   <li>поддерево проекта {@code base-oid}: метрики устройства (только чтение),
 *       deviceId, код активного сценария и флаг SILENT — «служебный экран»,
 *       по которому e2e-тесты NMS видят состояние эмулятора по SNMP.</li>
 * </ul>
 *
 * <p>Обновление значений — push-модель: агент подписан на тики
 * {@link ru.mininms.emulator.simulator.StateListener движка симуляции} и после
 * каждого тика кладёт свежие числа в Managed Objects (см. {@link #onTick}).
 *
 * <p>Сценарий SILENT: пакеты дропаются до CommandProcessor — клиент
 * получает таймаут, как при недоступности реального устройства.
 */
public class SnmpDeviceAgent implements ru.mininms.emulator.simulator.StateListener {

    /** sysDescr — MIB-2. */
    private static final OID SYS_DESCR_OID = new OID("1.3.6.1.2.1.1.1.0");
    /** sysUpTime — MIB-2. */
    private static final OID SYS_UP_TIME_OID = new OID("1.3.6.1.2.1.1.3.0");
    /** sysName — MIB-2. */
    private static final OID SYS_NAME_OID = new OID("1.3.6.1.2.1.1.5.0");

    private final DeviceState state;
    private final OidMapper oidMapper;
    private final SnmpConfig config;
    private final OctetString engineId;
    private final long startedAtMs = System.currentTimeMillis();

    private Snmp snmp;
    private final Map<String, MOScalar<Variable>> metricMos = new LinkedHashMap<>();
    private final Map<String, MetricConfig> metricConfigs = new LinkedHashMap<>();
    private MOScalar<TimeTicks> sysUpTimeMo;
    private MOScalar<Integer32> scenarioCodeMo;
    private MOScalar<Integer32> silentFlagMo;

    public SnmpDeviceAgent(DeviceState state, OidMapper oidMapper, SnmpConfig config) {
        this.state = state;
        this.oidMapper = oidMapper;
        this.config = config;
        this.engineId = new OctetString(MPv3.createLocalEngineID());
        for (MetricConfig metric : state.device().getMetrics()) {
            metricConfigs.put(metric.getType(), metric);
        }
    }

    public int port() {
        return state.device().getSnmpPort();
    }

    public DeviceState state() {
        return state;
    }

    /** Поднять UDP-транспорт и зарегистрировать MIB-объекты. */
    public void start() throws IOException {
        UdpAddress listenAddress = new UdpAddress(config.getBindAddress() + "/" + port());
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping(listenAddress);
        MessageDispatcher dispatcher = new MessageDispatcherImpl();
        dispatcher.addMessageProcessingModel(new MPv1());
        dispatcher.addMessageProcessingModel(new MPv2c());
        snmp = new Snmp(dispatcher, transport);

        CommandProcessor processor = new CommandProcessor(engineId);
        processor.setVacm(new PermissiveVacm());
        processor.setCoexistenceProvider(new DeviceCommunityProvider());

        MOServer server = new DefaultMOServer();
        processor.addMOServer(server);
        try {
            registerManagedObjects(server);
        } catch (DuplicateRegistrationException e) {
            // конфликт OID возможен только при ошибке конфигурации — падаем громко
            throw new IllegalStateException(
                    "Конфликт OID при регистрации MIB-объектов устройства " + state.id(), e);
        }

        // SILENT-сценарий: пакеты дропаются до CommandProcessor — клиент
        // получает таймаут, как при недоступности реального устройства.
        snmp.addCommandResponder(new DropWhenSilent(processor));
        snmp.listen();
    }

    /** Остановить транспорт. */
    public void stop() throws IOException {
        if (snmp != null) {
            snmp.close();
        }
    }

    /** Зарегистрировать MIB-объекты агента в MOServer (контекст — default). */
    private void registerManagedObjects(MOServer server) throws DuplicateRegistrationException {
        OctetString context = new OctetString();
        DeviceConfig device = state.device();

        // Стандартная system-группа MIB-2 — чтобы эмулятор был виден обычным
        // SNMP-сканерам и discovery-механизму NMS.
        register(server, context, new MOScalar<>(SYS_DESCR_OID, MOAccessImpl.ACCESS_READ_ONLY,
                new OctetString("device-emulator " + device.getType() + " '" + device.getName() + "'")));
        register(server, context, new MOScalar<>(SYS_NAME_OID, MOAccessImpl.ACCESS_READ_ONLY,
                new OctetString(device.getName())));
        sysUpTimeMo = new MOScalar<>(SYS_UP_TIME_OID, MOAccessImpl.ACCESS_READ_ONLY, new TimeTicks(0));
        register(server, context, sysUpTimeMo);

        // Служебные значения эмулятора в собственном поддереве.
        register(server, context, new MOScalar<>(new OID(oidMapper.deviceIdOid()),
                MOAccessImpl.ACCESS_READ_ONLY, new Integer32((int) state.id())));
        scenarioCodeMo = new MOScalar<>(new OID(oidMapper.scenarioCodeOid()),
                MOAccessImpl.ACCESS_READ_ONLY, new Integer32(0));
        register(server, context, scenarioCodeMo);
        silentFlagMo = new MOScalar<>(new OID(oidMapper.silentFlagOid()),
                MOAccessImpl.ACCESS_READ_ONLY, new Integer32(0));
        register(server, context, silentFlagMo);

        // Метрики устройства — только для чтения.
        // По конвенции SNMP регистрируется экземпляр «OID типа + .0»
        // (scope MO = [тип, тип+1), поэтому метрики не конфликтуют с .0.x).
        for (MetricConfig metric : device.getMetrics()) {
            OID oid = new OID(oidMapper.instanceOidFor(metric.getType()));
            MOScalar<Variable> mo = new MOScalar<>(oid, MOAccessImpl.ACCESS_READ_ONLY,
                    snmpVariable(metric, state.value(metric.getType())));
            register(server, context, mo);
            metricMos.put(metric.getType(), mo);
        }
    }

    private void register(MOServer server, OctetString context, MOScalar<?> mo)
            throws DuplicateRegistrationException {
        server.register(mo, context);
    }

    /**
     * Значение метрики → SNMP-переменная: строка → OctetString, счётчик →
     * Counter64 (или Counter32 со срезкой по модулю 2^32), число → gauge
     * (беззнаковая) или integer (отрицательные значения).
     */
    private Variable snmpVariable(MetricConfig metric, Object value) {
        if (metric.isString()) {
            return new OctetString(String.valueOf(value));
        }
        if (metric.isCounter()) {
            long counter = (Long) value;
            return metric.isCounter32SnmpType()
                    ? new Counter32(counter & 0xFFFFFFFFL)
                    : new Counter64(counter);
        }
        long rounded = Math.round((Double) value);
        return metric.isIntegerSnmpType() ? new Integer32((int) rounded) : new Gauge32(rounded);
    }

    /**
     * Тик движка симуляции: пушим свежие значения в Managed Objects.
     * Блокировка на самом MO — тот же монитор, что используют synchronized-методы
     * MOScalar, поэтому SNMP-потоки и поток движка не сталкиваются.
     */
    @Override
    public void onTick(DeviceState newState) {
        if (newState.id() != state.id()) {
            return; // тик чужого устройства — реестр оповещает всех слушателей
        }
        for (Map.Entry<String, MOScalar<Variable>> entry : metricMos.entrySet()) {
            MOScalar<Variable> mo = entry.getValue();
            Variable variable = snmpVariable(metricConfigs.get(entry.getKey()),
                    newState.value(entry.getKey()));
            synchronized (mo) {
                mo.setValue(variable);
            }
        }
        synchronized (scenarioCodeMo) {
            scenarioCodeMo.setValue(new Integer32(newState.scenario().type().code()));
        }
        synchronized (silentFlagMo) {
            silentFlagMo.setValue(new Integer32(newState.isSilent() ? 1 : 0));
        }
        synchronized (sysUpTimeMo) {
            sysUpTimeMo.setValue(new TimeTicks(
                    (int) ((System.currentTimeMillis() - startedAtMs) / 10)));
        }
    }

    /**
     * Обёртка над CommandProcessor: реализация сценария SILENT.
     * Пакеты дропаются до обработки — клиент видит таймаут, как при
     * недоступности реального устройства (pollStatus=TIMEOUT в NMS).
     *
     * <p>Именованный класс вместо лямбды: processPdu — generic-метод
     * (&lt;A extends Address&gt;), для таких SAM лямбды в Java запрещены.
     */
    private final class DropWhenSilent implements CommandResponder {

        private final CommandProcessor processor;

        DropWhenSilent(CommandProcessor processor) {
            this.processor = processor;
        }

        @Override
        public <A extends Address> void processPdu(CommandResponderEvent<A> event) {
            if (!state.isSilent()) {
                processor.processPdu(event);
            }
        }
    }

    /**
     * Ко-экзистенс community-моделей v1/v2c с моделью безопасности v3
     * (RFC 2576): маппинг community &rarr; контекст.
     *
     * <p>Заодно здесь проверяется community: запрос с чужой community получает
     * пустой массив ко-экзистенс-информации и молча дропается CommandProcessor'ом —
     * клиент видит таймаут, в точности как реальное устройство.
     */
    private final class DeviceCommunityProvider implements CoexistenceInfoProvider {

        @Override
        public CoexistenceInfo[] getCoexistenceInfo(OctetString community) {
            if (community == null) {
                return new CoexistenceInfo[0];
            }
            boolean communityOk = !config.isValidateCommunity()
                    || state.device().getCommunity().equals(community.toString());
            if (!communityOk) {
                return new CoexistenceInfo[0];
            }
            return new CoexistenceInfo[]{new CoexistenceInfo(community, engineId, new OctetString())};
        }

        @Override
        public OctetString getCommunity(OctetString securityName, OctetString contextEngineID,
                                        OctetString contextName) {
            return null; // обратное преобразование нужно только для исходящих трапов
        }

        @Override
        public boolean passesFilter(Address address, CoexistenceInfo coexistenceInfo) {
            return true; // фильтрацию по исходным адресам не используем
        }
    }
}