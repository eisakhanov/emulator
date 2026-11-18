# Метрики и пороги для справочников NMS

Этот документ — «мост» между эмулятором и системой: данные для таблиц
`metric_type` и `threshold` из доменной модели NMS.
Диапазоны совпадают с `emulator-devices.yml` эмулятора.

## Справочник числовых метрик (metric_type, kind=numeric)

| Код метрики        | Ед. изм. | Тип SNMP | Диапазон     | Норма       | У каких устройств     |
|--------------------|----------|----------|--------------|-------------|-----------------------|
| CPU_UTILIZATION    | %        | Gauge32  | 0..100       | 5..50       | ROUTER, OLT, BTS      |
| MEMORY_UTILIZATION | %        | Gauge32  | 0..100       | 20..70      | ROUTER, OLT, BTS      |
| IF_IN_UTILIZATION  | %        | Gauge32  | 0..100       | 10..60      | ROUTER                |
| IF_OUT_UTILIZATION | %        | Gauge32  | 0..100       | 10..60      | ROUTER                |
| TEMPERATURE        | °C       | Gauge32  | 15..80       | 20..50      | ROUTER, BTS           |
| ONT_COUNT          | шт       | Gauge32  | 0..1024      | 200..600    | OLT                   |
| PON_UTILIZATION    | %        | Gauge32  | 0..100       | 10..70      | OLT                   |
| RF_POWER           | dBm      | Integer32 (знаковый) | −60..−20 | −50..−35 | BTS        |
| ACTIVE_CALLS       | шт       | Gauge32  | 0..5000      | 100..800    | BTS                   |

«Норма» — диапазон, в котором значение болтается при сценарии NORMAL и куда
возвращается при RECOVERY. При сценариях HIGH_LOAD/FAILURE/FLAPPING значение
выходит за норму (но всегда остаётся в абсолютных границах диапазона).

## Строковые метрики (kind=string)

Пороговые правила к строковым метрикам неприменимы — это статус устройства
и инвентарные данные. В REST `value` — строка; числовые поля описания метрики
(min/max/норма/decimals) — null; в SNMP — OCTET STRING.

| Код метрики      | Поведение | Значения |
|------------------|-----------|----------|
| DEVICE_STATUS    | сценарное — следует за активным сценарием устройства | OK, DEGRADED, CRITICAL, RECOVERING, UNREACHABLE (словарь задаётся в emulator-devices.yml) |
| FIRMWARE_VERSION | статичное | ROUTER «IOS-XR 24.1.1», OLT «PON-OS 16.1.4», BTS «5G-RAN 3.1» |
| LOCATION         | статичное | MSK-DC1 / SPB-DC2 / KLN-DC3 |

DEVICE_STATUS по кодам сценариев: 0 NORMAL → OK, 1 HIGH_LOAD → DEGRADED,
2 FAILURE → CRITICAL, 3 RECOVERY → RECOVERING, 4 SILENT → UNREACHABLE,
5 FLAPPING → DEGRADED. Сценарии HIGH_LOAD/FAILURE/FLAPPING по строковым
метрикам отклоняются (400).

## Счётчики (kind=counter)

Монотонно растущие значения — как ifInOctets в реальных MIB; NMS вычисляет
скорость по дельтам за период опроса. Конфиг описывает скорость прироста
(min/max/норма/step, единиц/с), наружу (REST/SNMP) отдаётся накопленная сумма.
В SNMP — Counter64 (по умолчанию) или Counter32 со срезкой по модулю 2^32.
Счётчик стартует с нуля вместе с эмулятором (согласовано с sysUpTime от нуля).

| Код метрики    | Устройства | Норма скорости   | SNMP-тип |
|----------------|------------|------------------|----------|
| IF_IN_OCTETS   | ROUTER     | 1–5 МБ/с (линия 100 Мбит/с) | Counter64 |
| IF_OUT_OCTETS  | ROUTER     | 1–5 МБ/с         | Counter64 |
| PON_CRC_ERRORS | OLT        | 0–2 ошибок/с     | Counter32 |
| DROPPED_CALLS  | BTS        | 0–1 обрывов/с    | Counter64 |

Сценарии применяются к скорости прироста (targetValue — целевая скорость):
HIGH_LOAD — плавный разгон трафика к цели, FAILURE — скачок скорости
(например, ошибки CRC до сотен в секунду), RECOVERY — возврат скорости
в норму. Завершение RECOVERY проверяет скорость: само значение счётчика
вниз не ходит.

## Рекомендуемые пороговые правила (threshold)

Стартовые значения — согласованы с динамикой эмулятора
(tick = 1 c, HIGH_LOAD закрывает 20% расстояния до цели за тик):

| Метрика             | Сравнение | Порог | Severity | Окно duration | Комментарий                    |
|---------------------|-----------|-------|----------|---------------|--------------------------------|
| CPU_UTILIZATION     | >         | 80    | WARNING  | 60 с          | рост при HIGH_LOAD             |
| CPU_UTILIZATION     | >         | 90    | CRITICAL| 30 с          | устойчивая перегрузка          |
| MEMORY_UTILIZATION  | >         | 85    | CRITICAL| 60 с          |                                |
| TEMPERATURE         | >         | 60    | WARNING  | 120 с         |                                |
| TEMPERATURE         | >         | 70    | CRITICAL| 60 с          |                                |
| IF_IN_UTILIZATION   | >         | 85    | WARNING  | 90 с          |                                |
| PON_UTILIZATION     | >         | 90    | CRITICAL| 60 с          |                                |
| RF_POWER            | <         | −45   | WARNING  | 60 с          | слабый сигнал (меньше — хуже)  |
| RF_POWER            | <         | −55   | CRITICAL| 30 с          |                                |
| ONT_COUNT           | <         | 50    | WARNING  | 180 с         | массовое отключение ONT        |

Примечания:

* Окно `duration` подобрано так, чтобы кратковременный шум случайного
  блуждания НЕ создавал аларм, а сценарный рост — создавал (критерий №6).
* Для RF_POWER порог «меньше» — в радиосети мощность падает при деградации.
* Значения SNMP всегда целые (decimals = 0), как в реальных MIB.

## Дерево OID эмулятора (enterprise 1.3.6.1.4.1.99990 — плейсхолдер)

Значение метрики читается по экземпляру «OID типа + .0» — стандартная
конвенция SNMP для скаляров (как `sysUpTime` = 1.3.6.1.2.1.1.3.0).

| OID                          | Что возвращает                          |
|------------------------------|-----------------------------------------|
| 1.3.6.1.2.1.1.1.0            | sysDescr — тип и имя устройства         |
| 1.3.6.1.2.1.1.3.0            | sysUpTime — время работы агента (10 мс) |
| 1.3.6.1.2.1.1.5.0            | sysName — имя устройства                |
| 1.3.6.1.4.1.99990.0.1.0      | deviceId устройства                     |
| 1.3.6.1.4.1.99990.0.2.0      | код активного сценария (0..5)           |
| 1.3.6.1.4.1.99990.0.3.0      | флаг SILENT (1 — устройство «молчит»)   |
| 1.3.6.1.4.1.99990.1.0        | ACTIVE_CALLS                           |
| 1.3.6.1.4.1.99990.2.0        | CPU_UTILIZATION                        |
| 1.3.6.1.4.1.99990.3.0        | IF_IN_UTILIZATION                      |
| 1.3.6.1.4.1.99990.4.0        | IF_OUT_UTILIZATION                     |
| 1.3.6.1.4.1.99990.5.0        | MEMORY_UTILIZATION                     |
| 1.3.6.1.4.1.99990.6.0        | ONT_COUNT                              |
| 1.3.6.1.4.1.99990.7.0        | PON_UTILIZATION                        |
| 1.3.6.1.4.1.99990.8.0        | RF_POWER (Integer32, может быть < 0)   |
| 1.3.6.1.4.1.99990.9.0        | TEMPERATURE                            |
| 1.3.6.1.4.1.99990.10.0       | DEVICE_STATUS (OCTET STRING)           |
| 1.3.6.1.4.1.99990.11.0       | FIRMWARE_VERSION (OCTET STRING)        |
| 1.3.6.1.4.1.99990.12.0       | LOCATION (OCTET STRING)                |
| 1.3.6.1.4.1.99990.13.0       | IF_IN_OCTETS (Counter64)               |
| 1.3.6.1.4.1.99990.14.0       | IF_OUT_OCTETS (Counter64)              |
| 1.3.6.1.4.1.99990.15.0       | PON_CRC_ERRORS (Counter32)             |
| 1.3.6.1.4.1.99990.16.0       | DROPPED_CALLS (Counter64)              |

Коды сценариев: 0 NORMAL, 1 HIGH_LOAD, 2 FAILURE, 3 RECOVERY, 4 SILENT,
5 FLAPPING (см. `ScenarioType`).

## Парк устройств по умолчанию

| id  | name            | type   | SNMP-порт | community |
|-----|-----------------|--------|-----------|-----------|
| 101 | msk-core-rtr-01 | ROUTER | 1161/udp  | public    |
| 102 | msk-core-rtr-02 | ROUTER | 1162/udp  | public    |
| 201 | spb-olt-01      | OLT    | 1163/udp  | public    |
| 202 | spb-olt-02      | OLT    | 1164/udp  | public    |
| 301 | kln-bts-01      | BTS    | 1165/udp  | public    |
| 302 | kln-bts-02      | BTS    | 1166/udp  | public    |