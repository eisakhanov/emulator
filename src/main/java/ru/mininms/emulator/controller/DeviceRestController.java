package ru.mininms.emulator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mininms.emulator.common.DeviceSilentException;
import ru.mininms.emulator.dto.DeviceDto;
import ru.mininms.emulator.dto.MetricReadingDto;
import ru.mininms.emulator.model.DeviceState;
import ru.mininms.emulator.simulator.DeviceRegistry;
import ru.mininms.emulator.snmp.OidMapper;

import java.util.List;

/**
 * REST API эмулятора (критерий №3 заявки): список устройств и их текущие метрики.
 *
 * <p>Именно этот API опрашивал NMS Poller до разворота на SNMP; теперь он
 * используется для отладки и сценарных проверок, а основной транспорт — SNMP.
 */
@RestController
@RequestMapping("/api/emulator")
@Tag(name = "Devices", description = "Виртуальные устройства и их метрики")
public class DeviceRestController {

    private final DeviceRegistry registry;
    private final OidMapper oidMapper;

    public DeviceRestController(DeviceRegistry registry, OidMapper oidMapper) {
        this.registry = registry;
        this.oidMapper = oidMapper;
    }

    @Operation(summary = "Список устройств парка (id, тип, SNMP-порт, community, метрики)")
    @GetMapping("/devices")
    public List<DeviceDto> devices() {
        return registry.all().stream()
                .map(state -> DeviceDto.from(state, oidMapper))
                .toList();
    }

    @Operation(summary = "Одно устройство по id")
    @GetMapping("/devices/{id}")
    public DeviceDto device(@PathVariable long id) {
        return DeviceDto.from(registry.required(id), oidMapper);
    }

    @Operation(summary = "Текущие метрики устройства (критерий №3); 503 при сценарии SILENT")
    @GetMapping("/devices/{id}/metrics")
    public List<MetricReadingDto> metrics(@PathVariable long id) {
        DeviceState state = registry.required(id);
        if (state.isSilent()) {
            throw new DeviceSilentException(id);
        }
        return state.device().getMetrics().stream()
                .map(metric -> MetricReadingDto.from(state, metric))
                .toList();
    }
}