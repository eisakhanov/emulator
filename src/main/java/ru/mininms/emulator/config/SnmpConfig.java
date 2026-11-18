package ru.mininms.emulator.config;

/**
 * Настройки SNMP-интерфейса эмулятора.
 */
public class SnmpConfig {

    /** Поднимать ли SNMP-агентов вообще (false удобно для локальной отладки и части тестов). */
    private boolean enabled = true;

    /** Адрес, на котором агенты принимают UDP-запросы (обычно 0.0.0.0). */
    private String bindAddress = "0.0.0.0";

    /**
     * Корень собственного OID-дерева телеком-метрик (критерий №18 — «собственные OID»).
     * 1.3.6.1.4.1 — ветка private.enterprises; 99990 — плейсхолдер номера предприятия,
     * заменить на согласованный со справочником NMS.
     */
    private String baseOid = "1.3.6.1.4.1.99990";

    /**
     * Строгая проверка community: запрос с чужой community дропается без ответа —
     * клиент получает таймаут, как при обращении к реальному устройству.
     */
    private boolean validateCommunity = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public String getBaseOid() {
        return baseOid;
    }

    public void setBaseOid(String baseOid) {
        this.baseOid = baseOid;
    }

    public boolean isValidateCommunity() {
        return validateCommunity;
    }

    public void setValidateCommunity(boolean validateCommunity) {
        this.validateCommunity = validateCommunity;
    }
}