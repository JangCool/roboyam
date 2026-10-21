package com.community.member.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 동의 이력 — 가입 때 받은 동의만 안다.
 *
 * @param terms          이용약관 동의 시각·버전(가입 때 한 번 받는다)
 * @param privacy        개인정보 처리방침 동의 — 약관과 따로 저장하지 않아 null
 * @param ageConfirmedAt 만 14세 이상 확인 시각
 * @param optional       선택 동의 — 아직 없어 빈 목록
 * @param history        변경·철회 이력 — 아직 없어 빈 목록
 */
public record MemberConsents(MemberConsentRecord terms, MemberConsentRecord privacy, Instant ageConfirmedAt, List<MemberOptionalConsent> optional,
                             List<MemberConsentChange> history) {
}
