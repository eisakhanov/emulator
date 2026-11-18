package ru.mininms.emulator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mininms.emulator.dto.ActiveScenarioDto;
import ru.mininms.emulator.dto.ScenarioRequest;
import ru.mininms.emulator.simulator.DeviceRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Управление сценариями — «пульт» сценарных тестов.
 *
 * <p>Цепочка «рост нагрузки &rarr; авария &rarr; восстановление» для критериев
 * №6/№7 собирается тремя POST'ами (примеры — в README), сценарий SILENT даёт
 * poller'у таймаут для критерия №9.
 */
@RestController
@RequestMapping("/api/emulator")
@Tag(name = "Scenarios", description = "Сценарии симуляции для сценарных тестов NMS")
public class ScenarioRestController {

    private final DeviceRegistry registry;

    public ScenarioRestController(DeviceRegistry registry) {
        this.registry = registry;
    }

    @Operation(summary = "Применить сценарий к устройству")
    @PostMapping("/devices/{id}/scenarios")
    public ActiveScenarioDto apply(@PathVariable long id, @Valid @RequestBody ScenarioRequest request) {
        return ActiveScenarioDto.from(registry.applyScenario(id, request));
    }

    @Operation(summary = "Текущий сценарий устройства")
    @GetMapping("/devices/{id}/scenarios")
    public ActiveScenarioDto current(@PathVariable long id) {
        return ActiveScenarioDto.from(registry.required(id).scenario());
    }

    @Operation(summary = "Сбросить устройство в NORMAL")
    @DeleteMapping("/devices/{id}/scenarios")
    public ActiveScenarioDto reset(@PathVariable long id) {
        return ActiveScenarioDto.from(registry.resetScenario(id));
    }

    @Operation(summary = "Активные сценарии всех устройств (удобно для e2e-проверок NMS)")
    @GetMapping("/scenarios")
    public Map<Long, ActiveScenarioDto> allScenarios() {
        Map<Long, ActiveScenarioDto> result = new LinkedHashMap<>();
        registry.all().forEach(state -> result.put(state.id(), ActiveScenarioDto.from(state.scenario())));
        return result;
    }
}