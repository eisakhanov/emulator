package ru.mininms.emulator.simulator;

import org.junit.jupiter.api.Test;
import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.EmulatorProperties;
import ru.mininms.emulator.config.MetricConfig;
import ru.mininms.emulator.dto.ScenarioRequest;
import ru.mininms.emulator.model.ActiveScenario;
import ru.mininms.emulator.model.ScenarioType;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты движка симуляции: поведение моделей сценариев.
 * Spring-контекст не нужен — тики гоняем вручную с контролируемым временем.
 */
class SimulationEngineTest {

    private DeviceRegistry registryWithCpu() {
        MetricConfig cpu = new MetricConfig();
        cpu.setType("CPU_UTILIZATION");
        cpu.setUnit("%");
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
        properties.setTickInterval(Duration.ofSeconds(1));
        properties.setDevices(List.of(device));
        return new DeviceRegistry(properties);
    }

    private void runTicks(DeviceRegistry registry, SimulationEngine engine, int count, long startEpochMs) {
        for (int i = 0; i < count; i++) {
            engine.stepDevice(registry.required(1L), startEpochMs + i * 1000L);
        }
    }

    @Test
    void highLoadDrivesTargetMetricTowardsTarget() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);
        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.HIGH_LOAD, "CPU_UTILIZATION", 95.0, null, null, null));

        runTicks(registry, engine, 300, System.currentTimeMillis());

        assertThat(registry.required(1L).doubleValue("CPU_UTILIZATION")).isGreaterThan(80.0);
        assertThat(registry.required(1L).doubleValue("CPU_UTILIZATION")).isLessThanOrEqualTo(100.0);
    }

    @Test
    void failureJumpsImmediatelyAndStaysHigh() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);
        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));

        runTicks(registry, engine, 5, System.currentTimeMillis());

        // FAILURE — мгновенный прыжок: уже после первого тика значение у цели
        assertThat(registry.required(1L).doubleValue("CPU_UTILIZATION")).isGreaterThan(90.0);
    }

    @Test
    void recoveryReturnsMetricsToNormalRangeAndFinishes() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);
        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));
        long now = System.currentTimeMillis();
        runTicks(registry, engine, 10, now);

        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.RECOVERY, null, null, null, null, null));
        runTicks(registry, engine, 50, now + 10_000L);

        assertThat(registry.required(1L).doubleValue("CPU_UTILIZATION"))
                .isBetween(5.0, 40.0);
        assertThat(registry.required(1L).scenario().type()).isEqualTo(ScenarioType.NORMAL);
    }

    @Test
    void flappingOscillatesAroundTarget() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);
        ActiveScenario scenario = registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FLAPPING, "CPU_UTILIZATION", 50.0, 20.0, 4L, null));

        // покрываем обе фазы: период 4с = 2с выше центра, 2с ниже
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (long t = 0; t <= 8000; t += 500) {
            engine.stepDevice(registry.required(1L), scenario.startedAtEpochMs() + t);
            double value = registry.required(1L).doubleValue("CPU_UTILIZATION");
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        assertThat(max - min).isGreaterThan(20.0);
        assertThat(min).isGreaterThanOrEqualTo(0.0);
        assertThat(max).isLessThanOrEqualTo(100.0);
    }

    @Test
    void silentScenarioKeepsSimulatingInsideBounds() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);
        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.SILENT, null, null, null, null, null));

        runTicks(registry, engine, 100, System.currentTimeMillis());

        assertThat(registry.required(1L).isSilent()).isTrue();
        assertThat(registry.required(1L).doubleValue("CPU_UTILIZATION")).isBetween(0.0, 100.0);
    }

    @Test
    void scenarioExpiresAfterDuration() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);
        long now = System.currentTimeMillis();
        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, 5L));

        engine.stepDevice(registry.required(1L), now + 10_000L);

        assertThat(registry.required(1L).scenario().type()).isEqualTo(ScenarioType.NORMAL);
    }

    @Test
    void normalRandomWalkStaysInsideBounds() {
        DeviceRegistry registry = registryWithCpu();
        SimulationEngine engine = new SimulationEngine(registry);

        runTicks(registry, engine, 1000, System.currentTimeMillis());

        assertThat(registry.required(1L).doubleValue("CPU_UTILIZATION")).isBetween(0.0, 100.0);
    }

    /** Устройство с числовой метрикой и полным набором строковых (статус + инвентарь). */
    private DeviceRegistry registryWithCpuAndStrings() {
        MetricConfig cpu = new MetricConfig();
        cpu.setType("CPU_UTILIZATION");
        cpu.setUnit("%");
        cpu.setMin(0.0);
        cpu.setMax(100.0);
        cpu.setNormalLow(5.0);
        cpu.setNormalHigh(40.0);
        cpu.setStep(1.0);

        MetricConfig status = new MetricConfig();
        status.setType("DEVICE_STATUS");
        status.setKind("string");
        status.setMode("scenario");
        status.setValues(List.of("OK", "DEGRADED", "CRITICAL", "RECOVERING", "UNREACHABLE", "DEGRADED"));

        MetricConfig firmware = new MetricConfig();
        firmware.setType("FIRMWARE_VERSION");
        firmware.setKind("string");
        firmware.setMode("static");
        firmware.setValue("test-fw-1.0");

        MetricConfig location = new MetricConfig();
        location.setType("LOCATION");
        location.setKind("string");
        location.setMode("static");
        location.setValue("MSK-DC1");

        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("test-device");
        device.setType("ROUTER");
        device.setMetrics(List.of(cpu, status, firmware, location));

        EmulatorProperties properties = new EmulatorProperties();
        properties.setTickInterval(Duration.ofSeconds(1));
        properties.setDevices(List.of(device));
        return new DeviceRegistry(properties);
    }

    @Test
    void deviceStatusFollowsActiveScenario() {
        DeviceRegistry registry = registryWithCpuAndStrings();
        SimulationEngine engine = new SimulationEngine(registry);
        long now = System.currentTimeMillis();

        engine.stepDevice(registry.required(1L), now);
        assertThat(registry.required(1L).stringValue("DEVICE_STATUS")).isEqualTo("OK");

        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));
        engine.stepDevice(registry.required(1L), now + 1000);
        assertThat(registry.required(1L).stringValue("DEVICE_STATUS")).isEqualTo("CRITICAL");

        registry.resetScenario(1L);
        engine.stepDevice(registry.required(1L), now + 2000);
        assertThat(registry.required(1L).stringValue("DEVICE_STATUS")).isEqualTo("OK");
    }

    @Test
    void staticStringMetricsNeverChange() {
        DeviceRegistry registry = registryWithCpuAndStrings();
        SimulationEngine engine = new SimulationEngine(registry);

        runTicks(registry, engine, 50, System.currentTimeMillis());

        assertThat(registry.required(1L).stringValue("FIRMWARE_VERSION")).isEqualTo("test-fw-1.0");
        assertThat(registry.required(1L).stringValue("LOCATION")).isEqualTo("MSK-DC1");
    }

    @Test
    void recoveryCompletesWithStringMetricsPresent() {
        DeviceRegistry registry = registryWithCpuAndStrings();
        SimulationEngine engine = new SimulationEngine(registry);
        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "CPU_UTILIZATION", 95.0, null, null, null));
        long now = System.currentTimeMillis();
        runTicks(registry, engine, 5, now);

        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.RECOVERY, null, null, null, null, null));
        runTicks(registry, engine, 50, now + 5000);

        assertThat(registry.required(1L).scenario().type()).isEqualTo(ScenarioType.NORMAL);
        assertThat(registry.required(1L).stringValue("DEVICE_STATUS")).isEqualTo("OK");
    }

    /** Устройство с числовой метрикой и счётчиком трафика. */
    private DeviceRegistry registryWithCpuAndCounter() {
        MetricConfig cpu = new MetricConfig();
        cpu.setType("CPU_UTILIZATION");
        cpu.setUnit("%");
        cpu.setMin(0.0);
        cpu.setMax(100.0);
        cpu.setNormalLow(5.0);
        cpu.setNormalHigh(40.0);
        cpu.setStep(1.0);

        MetricConfig octets = new MetricConfig();
        octets.setType("IF_IN_OCTETS");
        octets.setUnit("окт");
        octets.setKind("counter");
        octets.setMin(0.0);
        octets.setMax(12500000.0);
        octets.setNormalLow(1000000.0);
        octets.setNormalHigh(5000000.0);
        octets.setStep(200000.0);

        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("test-device");
        device.setType("ROUTER");
        device.setMetrics(List.of(cpu, octets));

        EmulatorProperties properties = new EmulatorProperties();
        properties.setTickInterval(Duration.ofSeconds(1));
        properties.setDevices(List.of(device));
        return new DeviceRegistry(properties);
    }

    @Test
    void counterGrowsMonotonically() {
        DeviceRegistry registry = registryWithCpuAndCounter();
        SimulationEngine engine = new SimulationEngine(registry);
        long now = System.currentTimeMillis();

        long previous = -1;
        for (int i = 0; i <= 20; i++) {
            engine.stepDevice(registry.required(1L), now + i * 1000L);
            long current = registry.required(1L).counterValue("IF_IN_OCTETS");
            assertThat(current).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
        assertThat(previous).isGreaterThan(0L);
    }

    @Test
    void counterRateStaysWithinAbsoluteBounds() {
        DeviceRegistry registry = registryWithCpuAndCounter();
        SimulationEngine engine = new SimulationEngine(registry);

        runTicks(registry, engine, 100, System.currentTimeMillis());

        assertThat(registry.required(1L).counterRate("IF_IN_OCTETS"))
                .isBetween(0.0, 12500000.0);
    }

    @Test
    void highLoadAcceleratesCounterGrowth() {
        DeviceRegistry registry = registryWithCpuAndCounter();
        SimulationEngine engine = new SimulationEngine(registry);
        long now = System.currentTimeMillis();
        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.HIGH_LOAD, "IF_IN_OCTETS", 12000000.0, null, null, null));

        runTicks(registry, engine, 10, now); // разгон скорости к цели
        long before = registry.required(1L).counterValue("IF_IN_OCTETS");
        runTicks(registry, engine, 10, now + 10_000L);
        long after = registry.required(1L).counterValue("IF_IN_OCTETS");

        // скорость у цели ~12М окт/с → прирост за 10 секунд тиков сильно выше нормы
        assertThat(after - before).isGreaterThan(50_000_000L);
    }

    @Test
    void recoveryCompletesByCounterRate() {
        DeviceRegistry registry = registryWithCpuAndCounter();
        SimulationEngine engine = new SimulationEngine(registry);
        long now = System.currentTimeMillis();
        registry.applyScenario(1L,
                new ScenarioRequest(ScenarioType.FAILURE, "IF_IN_OCTETS", 12000000.0, null, null, null));
        runTicks(registry, engine, 5, now);

        registry.applyScenario(1L, new ScenarioRequest(ScenarioType.RECOVERY, null, null, null, null, null));
        runTicks(registry, engine, 50, now + 5000);

        // завершение RECOVERY проверяет скорость прироста — само значение монотонно
        assertThat(registry.required(1L).scenario().type()).isEqualTo(ScenarioType.NORMAL);
        assertThat(registry.required(1L).counterRate("IF_IN_OCTETS")).isBetween(1000000.0, 5000000.0);
    }
}