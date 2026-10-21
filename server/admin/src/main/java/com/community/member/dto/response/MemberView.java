package com.community.member.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 회원 목록 한 줄 — admin-web 의 {@code MemberRow} 모양이다. ID 와 시각은 {@code memberNo} 가 문자열(Snowflake)이고 시각은 UTC ISO 순간이다.
 *
 * @param memberNo       회원번호(회원 ID)
 * @param email          이메일. 소셜 가입자가 안 줬거나 탈퇴한 회원은 null. {@code member.pii} 가 없으면 가려서 준다({@code ab***@gmail.com})
 * @param emailMasked    이메일을 가려서 줬는가
 * @param grade          등급 키(이름·뱃지는 등급 목록에서 읽는다)
 * @param memberType     {@code GENERAL} · {@code BUSINESS}
 * @param status         {@code ACTIVE} · {@code SUSPENDED} · {@code BANNED} · {@code WITHDRAWN}({@code DORMANT} 는 휴면 기능이 생길 때)
 * @param authMethod     가입 방식 — 비밀번호가 있으면 {@code EMAIL}, 아니면 가장 먼저 연결한 소셜({@code KAKAO} · {@code NAVER} · {@code GOOGLE})
 * @param point          포인트 — 포인트 기능이 아직 없어 늘 0
 * @param dormantAt      휴면 전환 시각 — 휴면 기능이 아직 없어 늘 null
 * @param statusChangedAt 가장 최근 상태 변경 시각 — 탈퇴 시각만 안다(정지 이력 표가 아직 없다)
 * @param boardRole      {@code MANAGER} · {@code STAFF} · {@code NONE}
 * @param sanctionCount  제재 이력 수 — 제재 표가 아직 없어 늘 0
 */
public record MemberView(String memberNo, String email, boolean emailMasked, String nickname, String grade, String memberType, String status, String authMethod,
                         Instant emailVerifiedAt, Instant joinedAt, Instant lastLoginAt, long point, List<MemberRoleRef> roles, Instant dormantAt,
                         Instant statusChangedAt, String boardRole, long sanctionCount) {
}
