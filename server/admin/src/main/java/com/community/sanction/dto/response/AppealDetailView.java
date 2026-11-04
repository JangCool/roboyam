package com.community.sanction.dto.response;

import java.time.Instant;
import java.util.List;

/** 이의제기 상세(admin-web {@code AppealDetail}) — {@code history} 는 그 회원의 제재(현황의 이력과 같다) */
public record AppealDetailView(String id, long no, String status, String reason, Instant createdAt, String answer, Instant answeredAt, SanctionActorRefView answeredBy,
                               SanctionUserRefView user, SanctionLineView sanction, List<SanctionLineView> history) {
}
