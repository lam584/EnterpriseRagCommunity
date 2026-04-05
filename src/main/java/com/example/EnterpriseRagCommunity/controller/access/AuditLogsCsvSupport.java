package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;

import java.nio.charset.StandardCharsets;

public final class AuditLogsCsvSupport {

    private AuditLogsCsvSupport() {
    }

    public static byte[] toCsvBytes(Iterable<AuditLogsViewDTO> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,actorId,actorName,action,entityType,entityId,result,traceId,method,path,autoCrud,message\n");
        for (AuditLogsViewDTO it : items) {
            sb.append(csv(it.id())).append(',')
                    .append(csv(it.createdAt())).append(',')
                    .append(csv(it.actorId())).append(',')
                    .append(csv(it.actorName())).append(',')
                    .append(csv(it.action())).append(',')
                    .append(csv(it.entityType())).append(',')
                    .append(csv(it.entityId())).append(',')
                    .append(csv(it.result())).append(',')
                    .append(csv(it.traceId())).append(',')
                    .append(csv(it.method())).append(',')
                    .append(csv(it.path())).append(',')
                    .append(csv(it.autoCrud())).append(',')
                    .append(csv(it.message()))
                    .append('\n');
        }
        sb.insert(0, '\uFEFF');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csv(Object v) {
        if (v == null) return "\"\"";
        String s = String.valueOf(v).replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
