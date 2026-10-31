package com.community.role.entity;

import com.community.permission.entity.MemberPermission;

/**
 * 제품이 넣는 내장 회원 역할의 아이디({@code BuiltinRoleSeeder} 시드). 등급 역할은 {@code GradeRoles} 가 정한다.
 * 일반 회원은 가입하면 갖는 기본 회원 유형이라 회원마다 행을 두지 않는다 — 탈퇴하지 않았고 사업자 회원 역할이 없는 회원이 일반 회원이다.
 */
public final class MemberRoleIds {

    /** 일반 회원 — 가입하면 기본으로 갖는 회원 유형(사업자 회원이 아닌 회원) */
    public static final String GENERAL = "ROLE_MEMBER_GENERAL";
    /** 홍보 전용 계정 — 따로 가입한 홍보용 계정(일반 활동 제한). 사업자 인증(뱃지 — 사업자 신원 {@code APPROVED})과 따로다(사용자 결정 2026-10-31) */
    public static final String BUSINESS = "ROLE_MEMBER_BUSINESS";
    public static final String BOARD_MANAGER = "ROLE_BOARD_MANAGER";
    public static final String BOARD_STAFF = MemberPermission.BOARD_STAFF_ROLE_ID;

    private MemberRoleIds() {
    }
}
