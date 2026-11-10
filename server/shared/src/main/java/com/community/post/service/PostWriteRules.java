package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardWriteRestriction;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.service.BoardAccessPolicy;
import com.community.post.dto.WriteRateData;
import com.community.post.exception.DuplicatePostException;
import com.community.post.exception.PostDailyLimitException;
import com.community.post.exception.PostTooFastException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 새 글·댓글 쓰기 판정 중 공개 사이트(api {@code PostWriteService}·{@code CommentService}·{@code GroupPostService})와 관리자 서버에서 도는 AI 회원 쓰기({@code AiContentWriter})가
 * 같이 쓰는 것 — 쓰기 막힘을 오류로, 게시판 제재의 쓰기 제한, 새 글의 비밀글 여부, 글 작성 간격·하루 한도·같은 글 반복. 2026-11-10 AI 실행기를 admin 으로 옮기며
 * {@code PostWriteService} 에서 그대로 떼어 냈다(규칙이 두 군데로 갈라지지 않게).
 */
@Component
public class PostWriteRules {

    private final PostCounterQueries counters;
    private final PostRepositoryQDSL postQueries;

    public PostWriteRules(PostCounterQueries counters, PostRepositoryQDSL postQueries) {
        this.counters = counters;
        this.postQueries = postQueries;
    }

    /** 쓰기 판정 결과를 오류로 — 로그인 필요는 401, 나머지는 403 까닭 코드 */
    public static void requireWritable(String reason) {
        if (reason == null) {
            return;
        }
        if (BoardAccessPolicy.LOGIN_REQUIRED.equals(reason)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        throw new BoardWriteBlockedException(reason);
    }

    /** 새 글 쓰기 판정 — {@code GRADE_TOO_LOW} 면 오류 본문에 필요한 등급({@code minGrade}·{@code minGradeLabel} — 핫딜 게시판은 첫 등급 바로 위)을 싣는다 */
    public static void requireWritable(String reason, EffectiveBoard b, BoardCatalogSnapshot s) {
        if (BoardAccessPolicy.GRADE_TOO_LOW.equals(reason)) {
            String grade = BoardAccessPolicy.requiredWriteGrade(b, s);
            throw BoardWriteBlockedException.gradeTooLow(reason, grade, s.gradeLabel(grade));
        }
        requireWritable(reason);
    }

    /**
     * 게시판 제재로 이 게시판에서 쓰기 제한 중이면 403 {@code BOARD_WRITE_RESTRICTED}(+{@code until}) — 새 글·댓글·답글만 막는다(수정·삭제·추천은 막지 않는다, 결정 5).
     * 판정은 {@link BoardActor} 를 만들 때 읽은 진행 중 제한(요청당 색인 질의 하나)이다.
     */
    public static void requireNotRestricted(BoardActor actor, long boardId) {
        BoardWriteRestriction restriction = actor.writeRestriction(boardId);
        if (restriction != null) {
            throw BoardWriteBlockedException.boardRestricted(restriction);
        }
    }

    /**
     * 새 글의 비밀글 여부(V166 · V168) — 게시판 ALWAYS 면 true, OPTIONAL 이면 요청 값(없으면 false), OFF 면 false(요청 값은 무시한다). 비회원 글도 같다(V168 — 비밀번호로 연다).
     */
    public static boolean secretOnCreate(EffectiveBoard b, Boolean requested) {
        if (b.secretAlways()) {
            return true;
        }
        return b.secretOptional() && Boolean.TRUE.equals(requested);
    }

    /**
     * 작성 간격({@code 429 POST_TOO_FAST}) → 하루 한도({@code 429 POST_DAILY_LIMIT}) → 같은 글 반복({@code 409 DUPLICATE_POST}) — 작성자 단위 잠금 아래(같은 글은 제목 + 보이는 글자로 본다).
     * 부르는 쪽 트랜잭션 안에서 돈다. 작성자 열쇠는 회원 ID, 비회원은 작성 IP 다. 하루 = 최근 24시간.
     *
     * @param rateExempt AI 회원 실행기(ai-members-contract 「쓰기 제한 면제」) — 작성 간격·하루 한도는 보지 않고 같은 글 반복만 본다
     */
    public void checkRate(BoardCatalogSnapshot s, Long authorId, String ip, String title, String content, Instant now, boolean rateExempt) {
        counters.lockWriter("post:" + (authorId != null ? "m:" + authorId : "g:" + ip));
        int gap = rateExempt ? 0 : s.defaults().postGapSeconds();
        int daily = rateExempt ? Integer.MAX_VALUE : s.defaults().postDailyLimit();
        WriteRateData rate = postQueries.rate(authorId, ip, now.minus(Duration.ofDays(1)));
        if (gap > 0 && rate.lastAt() != null) {
            long elapsedMs = Duration.between(rate.lastAt(), now).toMillis();
            if (elapsedMs < gap * 1000L) {
                throw new PostTooFastException(gap, Math.max(1, (gap * 1000L - elapsedMs + 999) / 1000));
            }
        }
        if (rate.countInWindow() >= daily) {
            throw new PostDailyLimitException(daily);
        }
        int window = s.security().duplicatePostWindowMinutes();
        if (window > 0 && postQueries.duplicate(authorId, ip, now.minus(Duration.ofMinutes(window)), title, content)) {
            throw new DuplicatePostException(window);
        }
    }
}
