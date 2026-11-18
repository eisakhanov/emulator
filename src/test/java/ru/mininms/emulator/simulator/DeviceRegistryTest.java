package ru.mininms.emulator.simulator;

import org.junit.jupiter.api.Test;
import ru.mininms.emulator.common.DeviceNotFoundException;
import ru.mininms.emulator.common.ScenarioValidationException;
import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.EmulatorProperties;
import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.dto.ScenarioRequest;
import ru.mininms.emulator.model.ActiveScenario;
import ru.mininms.emulator.model.ScenarioType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceRegistryTest {

    private DeviceRegistry registry() {
        MetricConfig cpu = new MetricConfig();
        cpu.setType("CPU_UTILIZATION");
        cpu.setMin(0.0);
        cpu.setMax(100.0);
        cpu.setNormalLow(5.0);
        cpu.setNormalHigh(40.0);
        cpu.setStep(1.0);

        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("test-device");
        device.setType("ROUTER");
        device.setMetrics(List.of(cpu));

        EmulatorProperties properties = new EmulatorProperties();
        properties.setDevices(List.of(device));
        return new DeviceRegistry(properties);
    }

    @Test
    void requiredThrowsForUnknownId() {
        assertThatThrownBy(() -> registry().required(999L))
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void duplicateDeviceIdsAreRejected() {
        MetricConfig cpu = new MetricConfig();
        cpu.setType("CPU_UTILIZATION");
        cpu.setMin(0.0);
        cpu.setMax(100.0);

        DeviceConfig first = new DeviceConfig();
        first.setId(1L);
        first.setName("first");
        first.setType("ROUTER");
        first.setMetrics(List.of(cpu));

        DeviceConfig second = new DeviceConfig();
        second.setId(1L);
        second.setName("second");
        second.setType("ROUTER");
        second.setMetrics(List.of(cpu));

        EmulatorProperties properties = new EmulatorProperties();
        properties.setDevices(List.of(first, second));

        assertThatThrownBy(() -> new DeviceRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Дубликат id");
    }

    @Test
    void unknownMetricIsRejected() {
        DeviceRegistry registry = registry();

        assertThatThrownBy(() -> registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.HIGH_LOAD, "NO_SUCH_METRIC", 95.0, null, null, null)))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("нет метрики");
    }

    @Test
    void highLoadRequiresTargetValue() {
        DeviceRegistry registry = registry();

        assertThatThrownBy(() -> registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.HIGH_LOAD, "CPU_UTILIZATION", null, null, null, null)))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("targetValue");
    }

    @Test
    void targetOutsideRangeIsRejected() {
        DeviceRegistry registry = registry();

        assertThatThrownBy(() -> registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.HIGH_LOAD, "CPU_UTILIZATION", 150.0, null, null, null)))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("диапазоне");
    }

    @Test
    void failureDefaultsTargetToMetricMax() {
        DeviceRegistry registry = registry();

        ActiveScenario scenario = registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", null, null, null, null));

        assertThat(scenario.targetValue()).isEqualTo(100.0);
    }

    @Test
    void flappingDefaultsAmplitudeAndPeriod() {
        DeviceRegistry registry = registry();

        ActiveScenario scenario = registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FLAPPING, "CPU_UTILIZATION", 50.0, null, null, null));

        assertThat(scenario.amplitude()).isEqualTo(12.5); // (100-0)/8
        assertThat(scenario.periodSec()).isEqualTo(60L);
    }

    @Test
    void resetScenarioReturnsToNormal() {
        DeviceRegistry registry = registry();
        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));

        ActiveScenario scenario = registry.resetScenario(1L);

        assertThat(scenario.type()).isEqualTo(ScenarioType.NORMAL);
        assertThat(registry.required(1L).scenario().type()).isEqualTo(ScenarioType.NORMAL);
    }

    @Test
    void numericScenarioOnStringMetricIsRejected() {
        MetricConfig status = new MetricConfig();
        status.setType("DEVICE_STATUS");
        status.setKind("string");
        status.setMode("scenario");
        status.setValues(List.of("OK", "DEGRADED", "CRITICAL", "RECOVERING", "UNREACHABLE", "DEGRADED"));

        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("string-device");
        device.setType("ROUTER");
        device.setMetrics(List.of(status));

        EmulatorProperties properties = new EmulatorProperties();
        properties.setDevices(List.of(device));
        DeviceRegistry registry = new DeviceRegistry(properties);

        assertThatThrownBy(() -> registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.HIGH_LOAD, "DEVICE_STATUS", 95.0, null, null, null)))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("строковая");
    }

    @Test
    void scenariosAreAllowedOnCounterMetrics() {
        MetricConfig octets = new MetricConfig();
        octets.setType("IF_IN_OCTETS");
        octets.setKind("counter");
        octets.setMin(0.0);
        octets.setMax(12500000.0);
        octets.setNormalLow(1000000.0);
        octets.setNormalHigh(5000000.0);
        octets.setStep(200000.0);

        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("counter-device");
        device.setType("ROUTER");
        device.setMetrics(List.of(octets));

        EmulatorProperties properties = new EmulatorProperties();
        properties.setDevices(List.of(device));
        DeviceRegistry registry = new DeviceRegistry(properties);

        // targetValue трактуется как целевая скорость прироста
        ActiveScenario scenario = registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.HIGH_LOAD, "IF_IN_OCTETS", 12000000.0, null, null, null));

        assertThat(scenario.targetValue()).isEqualTo(12000000.0);
    }
}