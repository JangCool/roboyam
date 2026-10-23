package com.community.mfa.dto.response;

import java.util.List;

/**
 * 등록 현황 — 활성 운영자(지금 로그인할 수 있는 사람, 루트 포함) 기준.
 *
 * @param inGrace      켜져 있고 미등록·유예 안(꺼져 있으면 0)
 * @param graceExpired 켜져 있고 미등록·유예 지남(꺼져 있으면 0)
 * @param pending      미등록 운영자(유예 마감이 이른 차례, 같으면 ID 차례, 최대 10명)
 */
public record LoginSecuritySummary(int operators, int enrolled, int inGrace, int graceExpired, List<LoginSecurityPendingOperator> pending) {
}
