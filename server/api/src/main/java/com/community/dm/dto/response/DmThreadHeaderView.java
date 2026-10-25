package com.community.dm.dto.response;

import java.time.Instant;

/** 대화방 머리 — user-web {@code DmThreadHeader} */
public record DmThreadHeaderView(String id, DmPartnerView partner, boolean canSend, String reason, Instant availableFrom, boolean contactAllowed) {
}
