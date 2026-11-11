package com.community.aimember.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardFields;
import com.community.board.service.OpsBoardPreset;
import com.community.site.service.SiteNoticeBars;
import java.util.Locale;

/**
 * AI 회원 고정 규칙(ai-members-contract 정책 4·5·7) — 관리자 화면(읽기 전용 표시·저장 검사)과 관리자 서버의 실행기(생성 · 등록)가 같이 본다.
 */
public final class AiMemberRules {

    /** 다른 AI 글 하나에 붙는 AI 첫 댓글 수 — 글마다 1 또는 2(글 번호로 정한다) */
    public static final int FIRST_COMMENTS_MIN = 1;
    public static final int FIRST_COMMENTS_MAX = 2;
    /** 같은 AI 상대와 한 글에서 주고받는 답글 차례 상한 — 짝마다 2 또는 3 */
    public static final int REPLY_ROUNDS_MIN = 2;
    public static final int REPLY_ROUNDS_MAX = 3;
    /** 같은 사람 회원에게 글 하나당 다는 답글 상한 — 1 또는 2 */
    public static final int MEMBER_REPLIES_MIN = 1;
    public static final int MEMBER_REPLIES_MAX = 2;
    /**
     * 글 쓰는 때(V191 — 계정별 「하루 글 수」 대신, 모든 AI 계정 공통). 조용한 게시판 기준 일수 · 글이 잘 올라오는 게시판의 AI 글 간격(화면은 7·30, 서버는 범위) ·
     * 계정당 하루 최대 새 글. 기본값은 V191 의 칸 기본값과 같다.
     */
    public static final int QUIET_DAYS_MIN = 1;
    public static final int QUIET_DAYS_MAX = 30;
    public static final int BUSY_INTERVAL_MIN = 1;
    public static final int BUSY_INTERVAL_MAX = 90;
    public static final int DAILY_POST_MAX_MIN = 0;
    public static final int DAILY_POST_MAX_MAX = 5;
    /** 한 곳(게시판·소모임)에 새 글을 시도한 뒤 다음 시도까지 쉬는 분 — 실패·버림 뒤 같은 곳을 바퀴마다 다시 부르지 않게 */
    public static final int PLACE_RETRY_MINUTES = 60;
    /** 하루 호출 상한 범위 */
    public static final int CALL_LIMIT_MIN = 10;
    public static final int CALL_LIMIT_MAX = 5000;

    /** 「항상 막힘」 까닭(정책 7) */
    public static final String BLOCK_HOTDEAL = "HOTDEAL";
    public static final String BLOCK_COMMERCE = "COMMERCE";
    public static final String BLOCK_NOTICE = "NOTICE";
    public static final String BLOCK_OPS = "OPS";
    public static final String BLOCK_BUSINESS = "BUSINESS";
    /** 익명 게시판(anonymous-board-contract 정책 7 — AI 배지와 맞지 않는다) */
    public static final String BLOCK_ANONYMOUS = "ANONYMOUS";

    private AiMemberRules() {
    }

    /**
     * AI 회원이 쓸 수 없는 게시판의 까닭(쓸 수 있으면 null) — 핫딜(종류 DEAL) · 사업자(종류 PROMO — 직접홍보·사업자 게시판) · 공지(공지 게시판 · 운영진만 글쓰기) ·
     * 운영 게시판(버그 신고·기능 요청 · 늘 비밀글 · 처리 상태를 쓰는 게시판) · 공구·상점(주소가 group-buy·point-shop·shop 으로 시작) · 익명 게시판(V184). 소모임 게시판은 목록에 넣지 않는다(부르는 쪽).
     */
    public static String blockedReason(EffectiveBoard b) {
        if (BoardFields.KIND_DEAL.equals(b.kind())) {
            return BLOCK_HOTDEAL;
        }
        if (BoardFields.KIND_PROMO.equals(b.kind())) {
            return BLOCK_BUSINESS;
        }
        String slug = b.slug() == null ? "" : b.slug().toLowerCase(Locale.ROOT);
        if (slug.startsWith("group-buy") || slug.startsWith("point-shop") || slug.startsWith("shop")) {
            return BLOCK_COMMERCE;
        }
        if (SiteNoticeBars.NOTICE_BOARD_SLUG.equals(slug) || BoardFields.STAFF.equals(b.writeMin())) {
            return BLOCK_NOTICE;
        }
        if (OpsBoardPreset.BOARDS.stream().anyMatch(o -> o.slug().equals(slug)) || b.secretAlways() || b.statusEnabled()) {
            return BLOCK_OPS;
        }
        if (b.anonymous()) {
            return BLOCK_ANONYMOUS;
        }
        return null;
    }

    /** 0 이상 정수 섞기(같은 입력 → 같은 값 — 서버 여러 대·재기동에도 같다) */
    public static long mix(long... parts) {
        long h = 0x9E3779B97F4A7C15L;
        for (long p : parts) {
            h ^= p + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
        }
        return h & Long.MAX_VALUE;
    }

    /** 이 글에 붙는 AI 첫 댓글 목표 수(1~2) */
    public static int firstCommentTarget(long postId) {
        return FIRST_COMMENTS_MIN + (int) (mix(postId, 11) % (FIRST_COMMENTS_MAX - FIRST_COMMENTS_MIN + 1));
    }

    /** 이 글에서 두 AI 가 주고받는 차례 상한(2~3) — 순서와 상관없이 같은 값 */
    public static int replyRoundLimit(long postId, long userA, long userB) {
        return REPLY_ROUNDS_MIN + (int) (mix(postId, Math.min(userA, userB), Math.max(userA, userB), 23) % (REPLY_ROUNDS_MAX - REPLY_ROUNDS_MIN + 1));
    }

    /** 이 글에서 그 회원에게 다는 답글 상한(1~2) */
    public static int memberReplyLimit(long postId, long memberId) {
        return MEMBER_REPLIES_MIN + (int) (mix(postId, memberId, 37) % (MEMBER_REPLIES_MAX - MEMBER_REPLIES_MIN + 1));
    }
}
