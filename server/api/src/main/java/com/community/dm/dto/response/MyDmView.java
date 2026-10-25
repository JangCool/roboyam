package com.community.dm.dto.response;

/** {@code GET /api/me/dm} — user-web {@code MyDm} */
public record MyDmView(boolean acceptDm, DmSenderStateView sender, DmQuotaView quota, int unreadThreads, DmRulesView rules) {
}
