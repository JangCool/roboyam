package com.community.dm.dto.response;

import java.time.Instant;

/** {@code GET /api/users/{id}/dm} — user-web {@code DmCanSend}(읽기만) */
public record DmCanSendView(boolean canSend, String reason, Instant availableFrom, String threadId, boolean newPartner, boolean contactAllowed,
                            DmQuotaView quota) {
}
