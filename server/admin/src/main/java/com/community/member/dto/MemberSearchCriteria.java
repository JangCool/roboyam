package com.community.member.dto;

import com.community.user.entity.UserStatus;
import java.time.Instant;
import java.util.List;

/**
 * 해석을 마친 회원 목록 조회 조건 — {@code MemberSearchParser} 가 요청({@code MemberSearchRequest})에서 만든다. 비어 있는(null) 조건은 "거르지 않음"이다.
 *
 * <p>서버에 아직 없는 데이터(휴면·포인트·제재 이력)와 관련된 조건은 "그런 회원이 없다"로 판정한다 — 조건 값이 있으면 결과가 비고({@code statuses} 가 비어 있음 등) 조회 쪽이 처리한다.
 *
 * @param statuses 거를 계정 상태. null 이면 거르지 않고, 비어 있으면(예: {@code DORMANT} 만 골랐다 — 휴면 상태는 아직 없다) 결과가 없다
 * @param grades   거를 등급 키
 * @param dormantRange 휴면 전환 기간이 주어졌다 — 휴면 이력이 없어 결과가 없다
 * @param businessVerified 승인된 사업자(사업자 신원 {@code APPROVED})만 · 아닌 회원만 — null 이면 거르지 않는다(역할 기준 {@code memberType} 과 따로)
 */
public record MemberSearchCriteria(
        SearchField searchField, String keyword, List<UserStatus> statuses, List<String> roleIds,
        Instant joinedFrom, Instant joinedTo, Instant lastLoginFrom, Instant lastLoginTo, boolean noLoginHistory,
        List<String> grades, MemberType memberType, Boolean emailVerified, List<AuthMethod> authMethods,
        boolean dormantRange, StatusChangeKind statusChangedKind, Instant statusChangedFrom, Instant statusChangedTo,
        Long pointMin, Long pointMax, BoardRole boardRole, Boolean hasSanction, Boolean businessVerified,
        SortField sortField, boolean sortAscending, int page, int size) {

    /** 검색 대상 */
    public enum SearchField { ALL, EMAIL, NICKNAME, MEMBER_NO }

    /** 회원 유형 — 일반 회원 · 사업자 회원 */
    public enum MemberType { GENERAL, BUSINESS }

    /** 가입 방식 */
    public enum AuthMethod { EMAIL, KAKAO, NAVER, GOOGLE }

    /** 상태 변경일의 기준 — 정지(기간·영구) · 탈퇴 · 휴면 */
    public enum StatusChangeKind { SUSPENDED, WITHDRAWN, DORMANT }

    /** 게시판 매니저·스텝 여부 */
    public enum BoardRole { MANAGER, STAFF, NONE }

    /** 정렬 가능한 칸 */
    public enum SortField { MEMBER_NO, EMAIL, NICKNAME, GRADE, STATUS, JOINED_AT, LAST_LOGIN_AT, POINT, DORMANT_AT }

    public boolean hasKeyword() {
        return keyword != null;
    }
}
