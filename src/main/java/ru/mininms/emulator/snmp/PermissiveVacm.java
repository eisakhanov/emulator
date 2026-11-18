package ru.mininms.emulator.snmp;

import org.snmp4j.agent.security.VACM;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

/**
 * Минимальный разрешающий VACM (View-based Access Control Model, RFC 3415).
 *
 * <p>Эмулятор обслуживает только SNMP v1/v2c: аутентификация community
 * выполняется в {@code DeviceCommunityProvider} (см. SnmpDeviceAgent), а
 * полноценный VACM из v3 здесь не нужен — все запросы с правильной community
 * получают доступ на чтение ко всему дереву. Зарезервированная точка
 * расширения: запретить SET или отдельные ветки — достаточно поменять
 * реализацию этого интерфейса.
 */
final class PermissiveVacm implements VACM {

    private static final OctetString READ_VIEW = new OctetString("emulator-read-view");

    @Override
    public int isAccessAllowed(OctetString context, OctetString securityName, int securityModel,
                               int securityLevel, int viewType, OID oid) {
        return VACM_OK;
    }

    @Override
    public int isAccessAllowed(OctetString viewName, OID oid) {
        return VACM_OK;
    }

    @Override
    public OctetString getViewName(OctetString context, OctetString securityName, int securityModel,
                                   int securityLevel, int viewType) {
        return READ_VIEW;
    }
}