package ru.mininms.emulator.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Описание одного виртуального устройства из {@code emulator-devices.yml}.
 *
 * <p>SNMP-агент поднимается для каждого устройства на отдельном UDP-порту —
 * как в реальной сети, где устройства различаются по host:port.
 * Порт выше 1024 не требует прав администратора; в docker-compose можно
 * смапить на стандартный 161.
 */
public class DeviceConfig {

    /** Идентификатор — тот же id, что у устройства в реестре NMS. */
    @NotNull
    private Long id;

    /** Имя устройства (как sysName по SNMP). */
    @NotBlank
    private String name;

    /** Код типа устройства из справочника NMS: ROUTER / OLT / BTS ... */
    @NotBlank
    private String type;

    /** UDP-порт SNMP-агента этого устройства; null — SNMP-интерфейс не поднимать. */
    @Min(1)
    @Max(65535)
    private Integer snmpPort;

    /** Community string (только v2c); используется и для проверки входящих запросов. */
    @NotBlank
    private String community = "public";

    @Valid
    @jakarta.validation.constraints.NotEmpty(message = "у устройства должна быть хотя бы одна метрика")
    private List<MetricConfig> metrics = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSnmpPort() {
        return snmpPort;
    }

    public void setSnmpPort(Integer snmpPort) {
        this.snmpPort = snmpPort;
    }

    public String getCommunity() {
        return community;
    }

    public void setCommunity(String community) {
        this.community = community;
    }

    public List<MetricConfig> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<MetricConfig> metrics) {
        this.metrics = metrics;
    }
}