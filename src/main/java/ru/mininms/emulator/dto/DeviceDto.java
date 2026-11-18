package ru.mininms.emulator.dto;

import ru.mininms.emulator.model.DeviceState;
import ru.mininms.emulator.snmp.OidMapper;

import java.util.List;

/**
 * Описание виртуального устройства — элемент ответа {@code GET /api/emulator/devices}.
 *
 * <p>Этих данных достаточно, чтобы завести устройство в реестре NMS:
 * ip/hostname эмулятора + {@code snmpPort} + {@code community}.
 */
public record DeviceDto(
        long id,
        String name,
        String type,
        Integer snmpPort,
        String community,
        ActiveScenarioDto activeScenario,
        List<MetricSpecDto> metrics) {

    public static DeviceDto from(DeviceState state, OidMapper oidMapper) {
        List<MetricSpecDto> metrics = state.device().getMetrics().stream()
                .map(metric -> MetricSpecDto.from(metric, oidMapper.instanceOidFor(metric.getType())))
                .toList();
        return new DeviceDto(
                state.id(),
                state.device().getName(),
                state.device().getType(),
                state.device().getSnmpPort(),
                state.device().getCommunity(),
                ActiveScenarioDto.from(state.scenario()),
                metrics);
    }
}