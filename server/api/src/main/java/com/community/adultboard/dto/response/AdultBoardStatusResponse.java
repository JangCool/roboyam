package com.community.adultboard.dto.response;

import java.time.Instant;

/**
 * 내 19금 게시판 접근 설정과 이 기기(로그인 세션)의 핀 확인 상태 — {@code /api/me/adult-board…} 여섯 API 가 모두 이 모양을 돌려준다(adult-board-pin-contract).
 * 핀번호·해시·틀린 횟수는 싣지 않는다.
 *
 * @param enabled           「19금 게시판 접근」(기본 꺼짐)
 * @param pinSetAt          핀번호를 정한 때 — 꺼져 있으면 null
 * @param relockMode        핀번호를 다시 묻는 기준 {@code IDLE|FIXED}(꺼져 있어도 남는다)
 * @param unlocked          이 기기에서 핀 확인이 살아 있는가
 * @param unlockedUntil     핀 확인이 끝나는 때 — 잠겨 있으면 null
 * @param lockedUntil       연속으로 틀려 핀 입력이 막힌 끝 시각(회원 단위) — 막히지 않았으면 null
 * @param passwordAvailable 계정 비밀번호가 있는 회원인가 — 소셜 로그인만 쓰면 false(본인 확인이 「최근 로그인」으로 바뀐다)
 */
public record AdultBoardStatusResponse(boolean enabled, Instant pinSetAt, String relockMode, boolean unlocked, Instant unlockedUntil, Instant lockedUntil,
                                       boolean passwordAvailable) {
}
