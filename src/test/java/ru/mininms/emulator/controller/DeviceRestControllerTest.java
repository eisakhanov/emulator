package ru.mininms.emulator.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты REST API (контракт из repo-structure.md).
 * SNMP-агенты отключены — тестируем только веб-слой.
 *
 * <p>MockMvc собирается вручную из WebApplicationContext — стабильно
 * вне зависимости от тестовых стартеров Boot.
 */
@SpringBootTest(properties = "emulator.snmp.enabled=false")
class DeviceRestControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void devicesListContainsWholePark() throws Exception {
        mockMvc.perform(get("/api/emulator/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].type").value("ROUTER"))
                .andExpect(jsonPath("$[0].snmpPort").value(1161))
                .andExpect(jsonPath("$[0].community").value("public"))
                .andExpect(jsonPath("$[0].metrics[0].oid").exists())
                .andExpect(jsonPath("$[0].metrics[0].kind").value("numeric"))
                .andExpect(jsonPath("$[0].metrics[5].kind").value("string"))
                .andExpect(jsonPath("$[0].metrics[5].values", hasSize(6)));
    }

    @Test
    void singleDeviceIsAvailableById() throws Exception {
        mockMvc.perform(get("/api/emulator/devices/301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.name").value("kln-bts-01"));
    }

    @Test
    void metricsReturnCurrentValues() throws Exception {
        mockMvc.perform(get("/api/emulator/devices/101/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].metricType").value("CPU_UTILIZATION"))
                .andExpect(jsonPath("$[0].value").isNumber())
                .andExpect(jsonPath("$[0].unit").isNotEmpty())
                .andExpect(jsonPath("$[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$[5].metricType").value("DEVICE_STATUS"))
                .andExpect(jsonPath("$[5].value").value("OK"))
                .andExpect(jsonPath("$[6].metricType").value("FIRMWARE_VERSION"))
                .andExpect(jsonPath("$[6].value").value("IOS-XR 24.1.1"))
                .andExpect(jsonPath("$[7].metricType").value("LOCATION"))
                .andExpect(jsonPath("$[7].value").value("MSK-DC1"))
                .andExpect(jsonPath("$[8].metricType").value("IF_IN_OCTETS"))
                .andExpect(jsonPath("$[8].value").isNumber())
                .andExpect(jsonPath("$[9].metricType").value("IF_OUT_OCTETS"))
                .andExpect(jsonPath("$[9].value").isNumber());
    }

    @Test
    void unknownDeviceGivesProblemDetail404() throws Exception {
        mockMvc.perform(get("/api/emulator/devices/999/metrics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Устройство не найдено"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void silentDeviceGives503AndRecoversAfterReset() throws Exception {
        applyScenario(101, "{\"type\":\"SILENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SILENT"));

        mockMvc.perform(get("/api/emulator/devices/101/metrics"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Устройство недоступно"));

        mockMvc.perform(delete("/api/emulator/devices/101/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NORMAL"));

        mockMvc.perform(get("/api/emulator/devices/101/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    void highLoadWithoutTargetValueIsRejected() throws Exception {
        applyScenario(101, "{\"type\":\"HIGH_LOAD\",\"metricType\":\"CPU_UTILIZATION\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный сценарий"));
    }

    @Test
    void highLoadWithUnknownMetricIsRejected() throws Exception {
        applyScenario(101, "{\"type\":\"HIGH_LOAD\",\"metricType\":\"NO_SUCH\",\"targetValue\":90}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void highLoadOnStringMetricIsRejected() throws Exception {
        applyScenario(101, "{\"type\":\"HIGH_LOAD\",\"metricType\":\"DEVICE_STATUS\",\"targetValue\":90}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный сценарий"));
    }

    @Test
    void highLoadOnCounterMetricIsAllowed() throws Exception {
        // targetValue для счётчика — целевая скорость прироста
        applyScenario(101, "{\"type\":\"HIGH_LOAD\",\"metricType\":\"IF_IN_OCTETS\",\"targetValue\":12000000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("HIGH_LOAD"));

        mockMvc.perform(delete("/api/emulator/devices/101/scenarios"))
                .andExpect(status().isOk());
    }

    @Test
    void emptyBodyIsRejectedWithFieldErrors() throws Exception {
        applyScenario(101, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ошибка валидации"))
                .andExpect(jsonPath("$.errors.type").isNotEmpty());
    }

    @Test
    void scenariosOverviewListsAllDevices() throws Exception {
        mockMvc.perform(get("/api/emulator/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['101']").exists())
                .andExpect(jsonPath("$['302']").exists());
    }

    @Test
    void swaggerIsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    private ResultActions applyScenario(long deviceId, String body) throws Exception {
        return mockMvc.perform(post("/api/emulator/devices/" + deviceId + "/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}