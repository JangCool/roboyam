package com.community.board.dto;

import java.util.Set;

/**
 * 글쓰기·댓글 판정({@link com.community.board.service.BoardAccessPolicy})이 보는 「지금 쓰려는 사람」. 요청마다 한 번 만든다.
 *
 * @param userId         로그인한 회원 ID — 비회원 null
 * @param gradeKey       회원 등급 키 — 비회원 null
 * @param gradeLevel     회원 등급의 서열 — 비회원·모르는 등급 0
 * @param restricted     정지·영구 정지 중
 * @param business       사업자 회원 역할이 있다(일반 게시판 글쓰기 불가)
 * @param permissions    회원 역할 권한의 합집합(일반 회원·등급·사용자 정의 역할). null = 역할 표가 아직 없어(관리자 서버가 내장 역할을 시드하기 전) 제한하지 않는다
 * @param staffBoardIds  매니저·스텝을 맡은 게시판
 */
public record BoardActor(Long userId, String gradeKey, int gradeLevel, boolean emailVerified, boolean restricted, boolean business,
                         Set<String> permissions, Set<Long> staffBoardIds) {

    public static BoardActor anonymous() {
        return new BoardActor(null, null, 0, false, false, false, Set.of(), Set.of());
    }

    public boolean guest() {
        return userId == null;
    }

    /** 이 게시판의 매니저·스텝인가 */
    public boolean staffOf(long boardId) {
        return userId != null && staffBoardIds.contains(boardId);
    }

    /** 회원 역할 권한이 있는가(비회원은 없다) */
    public boolean can(String permissionCode) {
        return userId != null && (permissions == null || permissions.contains(permissionCode));
    }
}
