package ru.mininms.emulator.snmp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.EmulatorProperties;
import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.config.SnmpConfig;
import ru.mininms.emulator.dto.ScenarioRequest;
import ru.mininms.emulator.model.ScenarioType;
import ru.mininms.emulator.simulator.DeviceRegistry;
import ru.mininms.emulator.simulator.SimulationEngine;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: живой SNMP-агент на localhost + настоящий SNMP-клиент.
 * Spring-контекст не нужен — компоненты эмулятора собираются вручную.
 */
class SnmpAgentEndToEndTest {

    private static final int PORT = 21161;
    private static final String BASE_OID = "1.3.6.1.4.1.99990";
    private static final String SYS_DESCR = "1.3.6.1.2.1.1.1.0";
    private static final String SYS_UP_TIME = "1.3.6.1.2.1.1.3.0";
    private static final String SYS_NAME = "1.3.6.1.2.1.1.5.0";

    private DeviceRegistry registry;
    private SimulationEngine engine;
    private OidMapper oidMapper;
    private SnmpDeviceAgent agent;
    private SnmpTestClient client;

    @BeforeEach
    void setUp() throws Exception {
        MetricConfig cpu = new MetricConfig();
        cpu.setType("CPU_UTILIZATION");
        cpu.setUnit("%");
        cpu.setMin(0.0);
        cpu.setMax(100.0);
        cpu.setNormalLow(5.0);
        cpu.setNormalHigh(40.0);
        cpu.setStep(1.0);

        MetricConfig rfPower = new MetricConfig();
        rfPower.setType("RF_POWER");
        rfPower.setUnit("dBm");
        rfPower.setMin(-60.0);
        rfPower.setMax(-20.0);
        rfPower.setNormalLow(-50.0);
        rfPower.setNormalHigh(-35.0);
        rfPower.setStep(1.0);
        rfPower.setSnmpType("integer");

        MetricConfig status = new MetricConfig();
        status.setType("DEVICE_STATUS");
        status.setKind("string");
        status.setMode("scenario");
        status.setValues(List.of("OK", "DEGRADED", "CRITICAL", "RECOVERING", "UNREACHABLE", "DEGRADED"));

        MetricConfig firmware = new MetricConfig();
        firmware.setType("FIRMWARE_VERSION");
        firmware.setKind("string");
        firmware.setMode("static");
        firmware.setValue("e2e-fw-1.0");

        MetricConfig inOctets = new MetricConfig();
        inOctets.setType("IF_IN_OCTETS");
        inOctets.setUnit("окт");
        inOctets.setKind("counter");
        inOctets.setMin(0.0);
        inOctets.setMax(12500000.0);
        inOctets.setNormalLow(1000000.0);
        inOctets.setNormalHigh(5000000.0);
        inOctets.setStep(200000.0);

        MetricConfig crcErrors = new MetricConfig();
        crcErrors.setType("PON_CRC_ERRORS");
        crcErrors.setUnit("шт");
        crcErrors.setKind("counter");
        crcErrors.setMin(0.0);
        crcErrors.setMax(1000.0);
        crcErrors.setNormalLow(0.0);
        crcErrors.setNormalHigh(2.0);
        crcErrors.setStep(1.0);
        crcErrors.setSnmpType("counter32");

        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("e2e-device");
        device.setType("ROUTER");
        device.setSnmpPort(PORT);
        device.setCommunity("public");
        device.setMetrics(List.of(cpu, rfPower, status, firmware, inOctets, crcErrors));

        SnmpConfig snmpConfig = new SnmpConfig();
        snmpConfig.setEnabled(true);
        snmpConfig.setBindAddress("127.0.0.1");
        snmpConfig.setBaseOid(BASE_OID);
        snmpConfig.setValidateCommunity(true);

        EmulatorProperties properties = new EmulatorProperties();
        properties.setDevices(List.of(device));
        properties.setSnmp(snmpConfig);

        registry = new DeviceRegistry(properties);
        engine = new SimulationEngine(registry);
        oidMapper = new OidMapper(BASE_OID, properties.getDevices());
        agent = new SnmpDeviceAgent(registry.required(1L), oidMapper, snmpConfig);
        agent.start();
        registry.addListener(agent);
        client = new SnmpTestClient("127.0.0.1", PORT, "public");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (agent != null) {
            agent.stop();
        }
    }

    @Test
    void getReturnsGaugeMetric() throws Exception {
        engine.tick();

        VariableBinding vb = client.get(new OID(oidMapper.instanceOidFor("CPU_UTILIZATION")));

        assertThat(vb).isNotNull();
        assertThat(vb.getOid().toString()).isEqualTo(oidMapper.instanceOidFor("CPU_UTILIZATION"));
        assertThat(vb.getVariable().toLong()).isBetween(0L, 100L);
    }

    @Test
    void walkReturnsSystemGroupMetricsAndMeta() throws Exception {
        engine.tick();

        List<String> oids = client.walk(new OID("1.3.6.1")).stream()
                .map(vb -> vb.getOid().toString())
                .toList();

        assertThat(oids).contains(SYS_DESCR, SYS_UP_TIME, SYS_NAME);
        assertThat(oids).contains(oidMapper.instanceOidFor("CPU_UTILIZATION"),
                oidMapper.instanceOidFor("RF_POWER"),
                oidMapper.instanceOidFor("DEVICE_STATUS"),
                oidMapper.instanceOidFor("FIRMWARE_VERSION"),
                oidMapper.instanceOidFor("IF_IN_OCTETS"),
                oidMapper.instanceOidFor("PON_CRC_ERRORS"));
        assertThat(oids).contains(BASE_OID + ".0.1.0", BASE_OID + ".0.2.0", BASE_OID + ".0.3.0");
    }

    @Test
    void sysNameMatchesDeviceName() throws Exception {
        VariableBinding vb = client.get(new OID(SYS_NAME));

        assertThat(vb).isNotNull();
        assertThat(vb.getVariable().toString()).isEqualTo("e2e-device");
    }

    @Test
    void getNextStepsToNextRegisteredOid() throws Exception {
        engine.tick();

        VariableBinding vb = client.getNext(new OID(SYS_DESCR));

        assertThat(vb).isNotNull();
        assertThat(vb.getOid().toString()).isEqualTo(SYS_UP_TIME);
    }

    @Test
    void negativeMetricIsExposedAsSignedInteger() throws Exception {
        engine.tick();

        VariableBinding vb = client.get(new OID(oidMapper.instanceOidFor("RF_POWER")));

        assertThat(vb.getVariable()).isInstanceOf(Integer32.class);
        assertThat(vb.getVariable().toInt()).isBetween(-60, -20);
    }

    @Test
    void wrongCommunityTimesOut() throws Exception {
        engine.tick();

        try (SnmpTestClient badClient = new SnmpTestClient("127.0.0.1", PORT, "wrong-community")) {
            assertThat(badClient.get(new OID(oidMapper.instanceOidFor("CPU_UTILIZATION")))).isNull();
        }
    }

    @Test
    void silentScenarioDropsPacketsUntilReset() throws Exception {
        engine.tick();
        assertThat(client.get(new OID(oidMapper.instanceOidFor("CPU_UTILIZATION")))).isNotNull();

        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.SILENT, null, null, null, null, null));
        assertThat(client.get(new OID(oidMapper.instanceOidFor("CPU_UTILIZATION")))).isNull();

        registry.resetScenario(1L);
        assertThat(client.get(new OID(oidMapper.instanceOidFor("CPU_UTILIZATION")))).isNotNull();
    }

    @Test
    void scenarioCodeIsVisibleOverSnmp() throws Exception {
        engine.tick();
        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));
        engine.tick();

        VariableBinding vb = client.get(new OID(BASE_OID + ".0.2.0"));

        assertThat(vb).isNotNull();
        assertThat(vb.getVariable().toInt()).isEqualTo(ScenarioType.FAILURE.code());
    }

    @Test
    void staticStringMetricIsExposedAsOctetString() throws Exception {
        engine.tick();

        VariableBinding vb = client.get(new OID(oidMapper.instanceOidFor("FIRMWARE_VERSION")));

        assertThat(vb).isNotNull();
        assertThat(vb.getVariable()).isInstanceOf(OctetString.class);
        assertThat(vb.getVariable().toString()).isEqualTo("e2e-fw-1.0");
    }

    @Test
    void deviceStatusFollowsScenarioOverSnmp() throws Exception {
        engine.tick();
        assertThat(client.get(new OID(oidMapper.instanceOidFor("DEVICE_STATUS"))).getVariable().toString())
                .isEqualTo("OK");

        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));
        engine.tick();
        assertThat(client.get(new OID(oidMapper.instanceOidFor("DEVICE_STATUS"))).getVariable().toString())
                .isEqualTo("CRITICAL");
    }

    @Test
    void counterIsExposedAsCounter64AndGrows() throws Exception {
        engine.tick(); // первый тик: dt=0, счётчик со старта нулевой
        VariableBinding firstVb = client.get(new OID(oidMapper.instanceOidFor("IF_IN_OCTETS")));
        Thread.sleep(50);
        engine.tick();
        VariableBinding secondVb = client.get(new OID(oidMapper.instanceOidFor("IF_IN_OCTETS")));

        assertThat(firstVb.getVariable()).isInstanceOf(Counter64.class);
        assertThat(firstVb.getVariable().toLong()).isZero();
        assertThat(secondVb.getVariable().toLong()).isGreaterThan(firstVb.getVariable().toLong());
    }

    @Test
    void counter32WrapsAroundMod2Pow32() throws Exception {
        // засеиваем значение за пределами 2^32 — NMS обязан уметь обрабатывать срезку
        registry.required(1L).incrementCounter("PON_CRC_ERRORS", 4294967303.0); // 2^32 + 7
        engine.tick(); // dt=0 → приращение нулевое; значения пушатся в MO

        VariableBinding vb = client.get(new OID(oidMapper.instanceOidFor("PON_CRC_ERRORS")));

        assertThat(vb.getVariable()).isInstanceOf(Counter32.class);
        assertThat(vb.getVariable().toLong()).isEqualTo(7L);
    }
}