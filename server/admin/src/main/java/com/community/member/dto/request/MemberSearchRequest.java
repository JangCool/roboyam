package com.community.member.dto.request;

import java.util.List;

/**
 * 회원 목록 조회 조건(쿼리 문자열) — 빈 조건은 보내지 않는다. 값 해석·범위 검사는 {@code MemberSearchParser} 가 해서 틀린 값을 칸 이름과 함께 400 으로 알린다
 * (스프링의 변환 오류 문구가 그대로 나가지 않게 모두 글자로 받는다). 목록은 {@code statuses=A,B} 처럼 쉼표로 이어도 되고 같은 이름을 반복해도 된다.
 *
 * @param searchField   {@code ALL}(기본) · {@code EMAIL} · {@code NICKNAME} · {@code MEMBER_NO}
 * @param keyword       검색어. 이메일은 {@code member.pii} 가 있으면 부분 일치, 없으면 전체 주소가 같을 때만 맞는다
 * @param statuses      {@code ACTIVE} · {@code SUSPENDED} · {@code BANNED} · {@code WITHDRAWN} · {@code DORMANT}
 * @param roleIds       회원 역할 아이디(하나라도 가진 회원)
 * @param joinedFrom    UTC ISO 순간(양 끝 포함) — 가입 시각
 * @param noLoginHistory {@code true} 면 로그인 이력이 없는 회원만
 * @param grades        등급 키
 * @param memberType    {@code GENERAL} · {@code BUSINESS}
 * @param emailVerified {@code true} 인증 완료 · {@code false} 미완료
 * @param authMethods   {@code EMAIL} · {@code KAKAO} · {@code NAVER} · {@code GOOGLE}
 * @param statusChangedKind {@code SUSPENDED}(기간·영구 정지) · {@code WITHDRAWN} · {@code DORMANT}
 * @param boardRole     {@code MANAGER} · {@code STAFF} · {@code NONE}
 * @param hasSanction   {@code true} 제재 이력 있음 · {@code false} 없음
 * @param sort          {@code 칸,asc|desc} — 칸은 memberNo · email · nickname · grade · status · joinedAt · lastLoginAt · point · dormantAt. 기본 {@code joinedAt,desc}
 * @param page          0 부터(기본 0)
 * @param size          1~100(기본 20)
 */
public record MemberSearchRequest(
        String searchField, String keyword, List<String> statuses, List<String> roleIds,
        String joinedFrom, String joinedTo, String lastLoginFrom, String lastLoginTo, String noLoginHistory,
        List<String> grades, String memberType, String emailVerified, List<String> authMethods,
        String dormantFrom, String dormantTo, String statusChangedKind, String statusChangedFrom, String statusChangedTo,
        String pointMin, String pointMax, String boardRole, String hasSanction,
        String sort, String page, String size) {
}
