package ru.mininms.emulator.snmp;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Минимальный SNMP v2c-клиент для e2e-тестов — тот же snmp4j, что и агент,
 * но «с другой стороны провода». Именно так будет ходить NMS Poller.
 */
class SnmpTestClient implements AutoCloseable {

    private final Snmp snmp;
    private final CommunityTarget<UdpAddress> target;

    SnmpTestClient(String host, int port, String community) throws IOException {
        // Конструктор с transport сам регистрирует MPv1/MPv2c/MPv3 и вешает
        // клиентский UDP-сокет на свободный порт.
        this.snmp = new Snmp(new DefaultUdpTransportMapping());
        this.snmp.listen();
        this.target = new CommunityTarget<>(new UdpAddress(host + "/" + port), new OctetString(community));
        this.target.setVersion(SnmpConstants.version2c);
        this.target.setTimeout(1500);
        this.target.setRetries(0);
    }

    /** GET одного OID; null — таймаут (SILENT или неверная community). */
    VariableBinding get(OID oid) throws IOException {
        return send(PDU.GET, oid);
    }

    /** GETNEXT одного OID. */
    VariableBinding getNext(OID oid) throws IOException {
        return send(PDU.GETNEXT, oid);
    }

    private VariableBinding send(int pduType, OID oid) throws IOException {
        PDU pdu = new PDU();
        pdu.setType(pduType);
        pdu.add(new VariableBinding(oid));
        // Snmp.get/getNext сами проверяют тип PDU — шлём через подходящий метод
        ResponseEvent<UdpAddress> event = switch (pduType) {
            case PDU.GET -> snmp.get(pdu, target);
            case PDU.GETNEXT -> snmp.getNext(pdu, target);
            default -> throw new IllegalArgumentException("Тип PDU не поддерживается: " + pduType);
        };
        if (event.getResponse() == null) {
            return null;
        }
        PDU response = event.getResponse();
        if (response.getErrorStatus() != PDU.noError) {
            throw new IllegalStateException("SNMP-ошибка: " + response.getErrorStatusText());
        }
        return response.get(0);
    }

    /** Полный walk поддерева (GETBULK-обход — как у поллеров NMS). */
    List<VariableBinding> walk(OID root) throws IOException {
        TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());
        List<VariableBinding> result = new ArrayList<>();
        for (TreeEvent event : treeUtils.getSubtree(target, root)) {
            if (event.getVariableBindings() != null) {
                result.addAll(Arrays.asList(event.getVariableBindings()));
            }
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        snmp.close();
    }
}