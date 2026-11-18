# device-emulator

Эмулятор сетевого оборудования для учебного проекта **«Мини-NMS»**.
Референс для сценарных тестов системы мониторинга без железа.

Один движок симуляции — два интерфейса:

```
                         ┌────────────────────────────┐
   GET /devices          │  Simulation Engine         │   POST /scenarios
   GET /devices/{id}/…   │  random walk + сценарии    │◄── «пульт» сценарных
        │                │                            │    тестов
        ▼                └──────────┬─────────────────┘
   ┌─────────┐   REST (8085)        │  push значений на каждый тик
   │  NMS    │                      ▼
   │ Poller  │◄─── SNMP v2c ┌──────────────────────────┐
   └─────────┘  (UDP 1161+) │ SNMP-агент на устройство │
                             └──────────────────────────┘
```

* **REST API** — список устройств, текущие метрики, управление сценариями
  (Swagger UI: `http://localhost:8085/swagger-ui.html`);
* **SNMP v2c** — по агенту на каждое устройство (свой UDP-порт, community),
  включая стандартную system-группу MIB-2 — эмулятор виден обычным
  SNMP-сканерам и поллеру NMS.

Состояние живёт в памяти (своей БД у эмулятора нет),
парк устройств описывается файлом `emulator-devices.yml`.

## Быстрый старт

Требования: **JDK 21+ и Maven 3.9+** (для сборки) или Docker (для запуска).

```bash
# вариант 1: Maven
mvn spring-boot:run

# вариант 2: Docker
docker compose up --build
```

> Docker: базовые образы качаются с docker.io — при ограниченном доступе
> в интернет настройте зеркало реестра (registry mirror).

Проверка:

```bash
curl http://localhost:8085/api/emulator/devices | jq
snmpwalk -v 2c -c public localhost:1161 1.3.6.1.4.1.99990   # если есть net-snmp
```

## Парк устройств по умолчанию

6 устройств: 2 ROUTER (101, 102), 2 OLT (201, 202), 2 BTS (301, 302) —
16 типов метрик (9 числовых, 3 строковых, 4 счётчика), полный список и пороги — в
[docs/metrics-and-thresholds.md](docs/metrics-and-thresholds.md).

| id  | name            | type   | SNMP (UDP) | REST         |
|-----|-----------------|--------|------------|--------------|
| 101 | msk-core-rtr-01 | ROUTER | 1161       | /devices/101 |
| 102 | msk-core-rtr-02 | ROUTER | 1162       | /devices/102 |
| 201 | spb-olt-01      | OLT    | 1163       | /devices/201 |
| 202 | spb-olt-02      | OLT    | 1164       | /devices/202 |
| 301 | kln-bts-01      | BTS    | 1165       | /devices/301 |
| 302 | kln-bts-02      | BTS    | 1166       | /devices/302 |

## REST API

База: `http://localhost:8085/api/emulator`. Ошибки — RFC 7807 (ProblemDetail).

| Метод   | Путь                            | Назначение                                        |
|---------|---------------------------------|---------------------------------------------------|
| GET     | `/devices`                      | список устройств (id, тип, SNMP-порт, метрики)    |
| GET     | `/devices/{id}`                 | одно устройство                                   |
| GET     | `/devices/{id}/metrics`         | текущие значения метрик (503 при SILENT)         |
| GET     | `/devices/{id}/scenarios`       | активный сценарий устройства                      |
| POST    | `/devices/{id}/scenarios`       | применить сценарий                                |
| DELETE  | `/devices/{id}/scenarios`       | сбросить в NORMAL                                 |
| GET     | `/scenarios`                    | сценарии всех устройств (для e2e-проверок NMS)   |

Примеры:

```bash
# метрики роутера 101
curl -s localhost:8085/api/emulator/devices/101/metrics | jq
# [
#   { "metricType": "CPU_UTILIZATION", "value": 17, "unit": "%",
#     "timestamp": "2026-11-13T14:59:01.123Z" },
#   { "metricType": "DEVICE_STATUS", "value": "OK", "unit": "",
#     "timestamp": "..." },
#   ...
# ]

# рост CPU до 95 на 2 минуты
curl -s -X POST localhost:8085/api/emulator/devices/101/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"type":"HIGH_LOAD","metricType":"CPU_UTILIZATION","targetValue":95,"durationSec":120}' | jq
```

Ошибки: `404` — устройства нет; `400` — некорректный сценарий (нет метрики,
targetValue вне диапазона) с описанием в `detail`; `503` — устройство в SILENT.

## SNMP v2c

Каждое устройство — отдельный агент на своём UDP-порту (как отдельный хост).
Community по умолчанию `public`, проверяется строго: чужая community —
молчание и таймаут, как у реального устройства.

Дерево OID (enterprise-плейсхолдер `1.3.6.1.4.1.99990`):

```
1.3.6.1.2.1.1.1.0        sysDescr   — "device-emulator ROUTER 'msk-core-rtr-01'"
1.3.6.1.2.1.1.3.0        sysUpTime  — время работы агента (единицы по 10 мс)
1.3.6.1.2.1.1.5.0        sysName    — имя устройства
1.3.6.1.4.1.99990.0.1.0  deviceId
1.3.6.1.4.1.99990.0.2.0  код активного сценария (0..5)
1.3.6.1.4.1.99990.0.3.0  флаг SILENT (0/1)
1.3.6.1.4.1.99990.<n>.0  метрики: .1 ACTIVE_CALLS, .2 CPU, .3 IF_IN, .4 IF_OUT,
                                     .5 MEMORY, .6 ONT_COUNT, .7 PON, .8 RF_POWER,
                                     .9 TEMPERATURE — числа (gauge/integer);
                                     .10 DEVICE_STATUS, .11 FIRMWARE_VERSION,
                                     .12 LOCATION — строки (OCTET STRING);
                                     .13 IF_IN_OCTETS, .14 IF_OUT_OCTETS,
                                     .15 PON_CRC_ERRORS, .16 DROPPED_CALLS —
                                     счётчики (Counter64; PON_CRC_ERRORS — Counter32)
```

Значение метрики читается по **экземпляру «OID типа + .0»** — стандартная
конвенция SNMP для скаляров (как `sysUpTime`).

```bash
# CPU роутера 101
snmpget  -v 2c -c public localhost:1161 1.3.6.1.4.1.99990.2.0
# имя устройства
snmpget  -v 2c -c public localhost:1161 1.3.6.1.2.1.1.5.0
# статус устройства (строка, следует за активным сценарием)
snmpget  -v 2c -c public localhost:1161 1.3.6.1.4.1.99990.10.0
# счётчик трафика (Counter64, монотонно растёт — NMS считает дельты)
snmpget  -v 2c -c public localhost:1161 1.3.6.1.4.1.99990.13.0
# всё дерево
snmpwalk -v 2c -c public localhost:1161 1.3.6.1.4.1.99990
```

## Сценарии — «пульт» сценарных тестов

Сценарий применяется к устройству целиком (одновременно на REST и SNMP).
Статусная метрика `DEVICE_STATUS` (OID `.10.0`, строка) следует за активным
сценарием: `OK` → `DEGRADED` (HIGH_LOAD / FLAPPING) → `CRITICAL` (FAILURE) →
`RECOVERING` → `UNREACHABLE` (SILENT) — готовый индикатор для дашборда NMS.
Счётчики (`.13`–`.16`) монотонно растут, сценарии действуют на их скорость
прироста: HIGH_LOAD плавно разгоняет трафик к цели, FAILURE даёт скачок
скорости (например, ошибок CRC), RECOVERY возвращает скорость в норму.
Типы (см. `ScenarioType`): `NORMAL`, `HIGH_LOAD`, `FAILURE`, `RECOVERY`,
`SILENT`, `FLAPPING`.

Поля запроса: `type` (обязателен), `metricType` (для HIGH_LOAD/FAILURE/FLAPPING),
`targetValue`, `amplitude`+`periodSec` (FLAPPING), `durationSec` (авто-возврат
в NORMAL через N секунд).

### Цепочка 1 — рост нагрузки и аларм (критерий №6: порог + окно)

Типовая драма «пороговое правило срабатывает и держится»:

```bash
# 1. плавный рост CPU до 95% (≈30–60 тиков; при тике 1с — до минуты)
curl -s -X POST localhost:8085/api/emulator/devices/101/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"type":"HIGH_LOAD","metricType":"CPU_UTILIZATION","targetValue":95}' | jq

# 2. NMS опрашивает (REST или SNMP), правило CPU > 80 в окне 60с должно открыть WARNING
snmpget -v 2c -c public localhost:1161 1.3.6.1.4.1.99990.2.0

# 3. вернуть в норму
curl -s -X DELETE localhost:8085/api/emulator/devices/101/scenarios | jq
```

### Цепочка 2 — авария и авто-закрытие (критерий №7)

```bash
# резкий выход за порог и удержание
curl -s -X POST localhost:8085/api/emulator/devices/102/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"type":"FAILURE","metricType":"TEMPERATURE"}' | jq
# (targetValue не указан → берётся max метрики, 80°C)

# ...дожидаемся открытия аларма в NMS...

# плавный возврат к норме: когда ВСЕ метрики вернулись в норму,
# сценарий сам переключается в NORMAL
curl -s -X POST localhost:8085/api/emulator/devices/102/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"type":"RECOVERY"}' | jq

# аларм должен закрыться автоматически (auto-resolve)
```

### Цепочка 3 — таймаут поллера (критерий №9)

```bash
curl -s -X POST localhost:8085/api/emulator/devices/201/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"type":"SILENT"}' | jq
# SNMP-агент дропает пакеты без ответа → у поллера таймаут → pollStatus=TIMEOUT;
# REST при этом честно отвечает 503 ProblemDetail.

# «починить» устройство
curl -s -X DELETE localhost:8085/api/emulator/devices/201/scenarios | jq
```

### Цепочка 4 — flapping (повторное открытие аларма)

```bash
# колебания CPU вокруг 85 с размахом ±10, период 20с
curl -s -X POST localhost:8085/api/emulator/devices/301/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"type":"FLAPPING","metricType":"CPU_UTILIZATION","targetValue":85,
       "amplitude":10,"periodSec":20}' | jq
```

### Проверка состояния эмулятора из тестов NMS

```bash
curl -s localhost:8085/api/emulator/scenarios | jq   # сценарии всех устройств
snmpget -v 2c -c public localhost:1161 1.3.6.1.4.1.99990.0.2.0   # код сценария по SNMP
```

## Конфигурация

| Файл                   | Что настраивает                                    |
|------------------------|----------------------------------------------------|
| `application.yml`      | порт REST (8085), период тика, base-oid, SNMP вкл/выкл |
| `emulator-devices.yml` | парк устройств: id, имена, типы, порты, community, метрики и их диапазоны |

`emulator-devices.yml` подхватывается с classpath, а при наличии
`./emulator-devices.yml` рядом с приложением — переопределяется им
(в docker-compose можно смонтировать свой парк через volume).

Ключевые свойства:

```yaml
emulator:
  tick-interval: 1s        # период пересчёта метрик
  snmp:
    enabled: true          # false — поднять только REST (отладка)
    bind-address: 0.0.0.0
    base-oid: 1.3.6.1.4.1.99990
    validate-community: true
```

## Интеграция с NMS

1. **Реестр устройств** — заведите 6 устройств из таблицы выше
   (поле для истории: hostname `localhost`, SNMP-порт из таблицы, community `public`).
2. **Справочник метрик** — коды и OID из
   [docs/metrics-and-thresholds.md](docs/metrics-and-thresholds.md).
3. **Poller** — опрос по SNMP `GET`/`GETBULK` (walk). Эмулятор отвечает
   на v2c, поддерживает и v1. REST-эндпоинт `/metrics` оставлен для
   отладки и сценарных проверок.
4. **Пороговые правила** — стартовые пороги и окна см. в том же документе.
5. **Сценарные тесты** — цепочки из раздела выше: рост (№6), авто-закрытие (№7),
   таймаут (№9), flapping.

## Сборка и тесты

```bash
mvn verify        # юнит + интеграционные + SNMP e2e (живой агент на localhost)
mvn package       # jar в target/
```

Тесты: движок симуляции (модели сценариев), справочник OID (конфликты,
авто-нумерация), REST-контракт (MockMvc), SNMP end-to-end (агент + клиент
snmp4j на localhost: GET/GETNEXT/walk, неверная community → таймаут,
SILENT → таймаут, код сценария по SNMP).

## Структура проекта

```
device-emulator/
├── src/main/java/ru/mininms/emulator/
│   ├── EmulatorApplication.java
│   ├── common/                  # исключения → HTTP-коды
│   ├── config/                  # привязка yml к @ConfigurationProperties
│   ├── controller/              # REST (устройства, сценарии, ошибки RFC 7807)
│   ├── dto/                     # записи запросов/ответов
│   ├── model/                   # DeviceState, сценарии
│   ├── simulator/               # движок + реестр устройств
│   └── snmp/                    # OidMapper, агенты, жизненный цикл
├── src/main/resources/
│   ├── application.yml
│   └── emulator-devices.yml     # парк устройств
├── src/test/java/...            # юнит + MockMvc + SNMP e2e
├── docs/metrics-and-thresholds.md
├── Dockerfile                   # multi-stage: maven → temurin-21-jre
└── docker-compose.yml
```

## Известные ограничения

* Состояние в памяти: перезапуск сбрасывает значения и сценарии.
* SNMP v1/v2c без шифрования (v3 — за рамками проекта).
* Enterprise-OID `99990` — плейсхолдер; при получении номера предприятия
  поменяйте `emulator.snmp.base-oid` (и справочник в NMS).
* Трапы не отправляются: поллер — активная сторона (pull).