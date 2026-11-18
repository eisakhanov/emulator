package ru.mininms.emulator.snmp;

import org.junit.jupiter.api.Test;
import ru.mininms.emulator.config.DeviceConfig;
import ru.mininms.emulator.config.MetricConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidMapperTest {

    private static final String BASE = "1.3.6.1.4.1.99990";

    private DeviceConfig device(MetricConfig... metrics) {
        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setName("d");
        device.setType("ROUTER");
        device.setMetrics(List.of(metrics));
        return device;
    }

    private MetricConfig metric(String type, String oid) {
        MetricConfig metric = new MetricConfig();
        metric.setType(type);
        metric.setMin(0.0);
        metric.setMax(100.0);
        if (oid != null) {
            metric.setOid(oid);
        }
        return metric;
    }

    @Test
    void autoNumbersAreAssignedInAlphabeticalOrder() {
        OidMapper mapper = new OidMapper(BASE, List.of(device(metric("B_METRIC", null), metric("A_METRIC", null))));

        assertThat(mapper.oidFor("A_METRIC")).isEqualTo(BASE + ".1");
        assertThat(mapper.oidFor("B_METRIC")).isEqualTo(BASE + ".2");
    }

    @Test
    void explicitOidWinsOverAutoNumbering() {
        OidMapper mapper = new OidMapper(BASE, List.of(device(metric("A_METRIC", "1.3.6.1.4.1.99990.42"))));

        assertThat(mapper.oidFor("A_METRIC")).isEqualTo("1.3.6.1.4.1.99990.42");
    }

    @Test
    void sameTypeWithDifferentExplicitOidsIsRejected() {
        assertThatThrownBy(() -> new OidMapper(BASE, List.of(
                device(metric("A_METRIC", BASE + ".42")),
                device(metric("A_METRIC", BASE + ".43")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("разными OID");
    }

    @Test
    void duplicateOidsAreRejected() {
        assertThatThrownBy(() -> new OidMapper(BASE, List.of(
                device(metric("A_METRIC", BASE + ".42")),
                device(metric("B_METRIC", BASE + ".42")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("более чем одной метрике");
    }

    @Test
    void metaOidsLiveInZeroBranch() {
        OidMapper mapper = new OidMapper(BASE, List.of(device(metric("A_METRIC", null))));

        assertThat(mapper.deviceIdOid()).isEqualTo(BASE + ".0.1.0");
        assertThat(mapper.scenarioCodeOid()).isEqualTo(BASE + ".0.2.0");
        assertThat(mapper.silentFlagOid()).isEqualTo(BASE + ".0.3.0");
    }

    @Test
    void unknownMetricTypeIsRejected() {
        OidMapper mapper = new OidMapper(BASE, List.of(device(metric("A_METRIC", null))));

        assertThatThrownBy(() -> mapper.oidFor("UNKNOWN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не назначен OID");
    }

    @Test
    void stringMetricsGetOidsLikeNumericOnes() {
        MetricConfig status = new MetricConfig();
        status.setType("DEVICE_STATUS");
        status.setKind("string");
        status.setOid(BASE + ".10");

        OidMapper mapper = new OidMapper(BASE, List.of(device(metric("A_METRIC", null), status)));

        assertThat(mapper.oidFor("DEVICE_STATUS")).isEqualTo(BASE + ".10");
        assertThat(mapper.instanceOidFor("DEVICE_STATUS")).isEqualTo(BASE + ".10.0");
    }

    @Test
    void counterMetricsGetOidsLikeNumericOnes() {
        MetricConfig octets = new MetricConfig();
        octets.setType("IF_IN_OCTETS");
        octets.setKind("counter");
        octets.setOid(BASE + ".13");

        OidMapper mapper = new OidMapper(BASE, List.of(device(metric("A_METRIC", null), octets)));

        assertThat(mapper.oidFor("IF_IN_OCTETS")).isEqualTo(BASE + ".13");
        assertThat(mapper.instanceOidFor("IF_IN_OCTETS")).isEqualTo(BASE + ".13.0");
    }
}