package com.community.post.dto.response;

/**
 * 댓글 많은 글 보호(user-web {@code PostProtection}) — 서버가 지금 상태로 정한다.
 *
 * @param active       보이는 댓글 수가 기준 이상이라 보호 중
 * @param editBlocked  보호 중이라 고치기를 막는다
 * @param deleteMode   보호 중일 때 지우기 방식 BLOCK · DELAY · ALLOW
 * @param delayMinutes 예약 삭제까지의 분
 * @param threshold    보호 기준(댓글 수), 0 = 끔
 */
public record PostProtectionView(boolean active, boolean editBlocked, String deleteMode, int delayMinutes, int threshold) {
}
