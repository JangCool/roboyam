package com.community.permission.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 회원 권한 카탈로그 — 공개 사이트 회원의 능력(운영자 권한 {@link Permission} 과 다른 체계다). 코드가 원천이고 서버가 뜰 때 {@code permissions} 표와 맞춘다.
 * 목록·분류는 admin-web 의 {@code util-permission-catalog.ts} {@code MEMBER_PERMISSIONS} 와 같다. 사이트 기능이 아직 없는 능력은 {@code featureReady=false} 다.
 * {@code managerOnly} 는 게시판 매니저에게만 후보로 나온다(게시판 스텝에는 나오지 않는다).
 */
public enum MemberPermission {

    POST_WRITE("post.write", MemberPermissionGroup.USE, true, false),
    COMMENT_WRITE("comment.write", MemberPermissionGroup.USE, true, false),
    MESSAGE_SEND("message.send", MemberPermissionGroup.USE, true, false),
    FILE_ATTACH("file.attach", MemberPermissionGroup.USE, true, false),
    POST_REACT("post.react", MemberPermissionGroup.USE, true, false),
    REPORT_CREATE("report.create", MemberPermissionGroup.USE, true, false),
    /** 포인트 상점 이용(구매) — 0-5 의 shop.use 를 커머스 1-2 에서 이름만 바꿨다(V86 이 역할에 준 것도 옮긴다) */
    POINTSHOP_USE("pointshop.use", MemberPermissionGroup.USE, true, false),
    /** 소모임 개설 신청(V89 — 글쓰기를 가진 역할에 같이 줬다) */
    GROUP_CREATE("group.create", MemberPermissionGroup.USE, true, false),
    HOTDEAL_WRITE("hotdeal.write", MemberPermissionGroup.USE, false, false),
    COMMERCE_PURCHASE("commerce.purchase", MemberPermissionGroup.USE, false, false),
    AD_APPLY("ad.apply", MemberPermissionGroup.USE, false, false),
    BOARD_POST_HIDE("board.post.hide", MemberPermissionGroup.BOARD, true, false),
    BOARD_POST_DELETE("board.post.delete", MemberPermissionGroup.BOARD, true, false),
    BOARD_COMMENT_DELETE("board.comment.delete", MemberPermissionGroup.BOARD, true, false),
    BOARD_SANCTION_ISSUE("board.sanction.issue", MemberPermissionGroup.BOARD, true, false),
    BOARD_NOTICE_PIN("board.notice.pin", MemberPermissionGroup.BOARD, true, true),
    BOARD_STAFF_APPOINT("board.staff.appoint", MemberPermissionGroup.BOARD, true, true),
    BOARD_STAFF_ACTION_RESTORE("board.staff-action.restore", MemberPermissionGroup.BOARD, true, true);

    /** 게시판 스텝의 역할 아이디 — 스텝에게는 {@code managerOnly} 권한이 후보로 나오지 않는다 */
    public static final String BOARD_STAFF_ROLE_ID = "ROLE_BOARD_STAFF";

    private static final Map<String, MemberPermission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(MemberPermission::code, Function.identity()));

    private final String code;
    private final MemberPermissionGroup group;
    private final boolean featureReady;
    private final boolean managerOnly;

    MemberPermission(String code, MemberPermissionGroup group, boolean featureReady, boolean managerOnly) {
        this.code = code;
        this.group = group;
        this.featureReady = featureReady;
        this.managerOnly = managerOnly;
    }

    public String code() {
        return code;
    }

    public MemberPermissionGroup group() {
        return group;
    }

    public boolean featureReady() {
        return featureReady;
    }

    public boolean managerOnly() {
        return managerOnly;
    }

    /** {@code permissions.type_id} — 이용 권한은 {@code memberSite}, 게시판 운영 권한은 {@code memberBoard} */
    public String typeId() {
        return group == MemberPermissionGroup.BOARD ? "memberBoard" : "memberSite";
    }

    public static Optional<MemberPermission> ofCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /**
     * 이 회원 역할이 고를 수 있는 권한 후보인가 — 사이트 범위 역할은 이용 묶음, 게시판 범위 역할(매니저·스텝)은 게시판 운영 묶음.
     * 게시판 스텝에게는 매니저 전용 권한이 나오지 않는다.
     */
    public boolean isCandidate(boolean boardScope, String roleId) {
        MemberPermissionGroup wanted = boardScope ? MemberPermissionGroup.BOARD : MemberPermissionGroup.USE;
        boolean staff = BOARD_STAFF_ROLE_ID.equals(roleId);
        return group == wanted && !(staff && managerOnly);
    }
}
