package com.community.adultboard.service;

import com.community.adultboard.entity.AdultBoardAccessEntity;
import com.community.adultboard.exception.AdultBoardPinRequiredException;
import com.community.adultboard.repository.master.AdultBoardAccessRepository;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BoardNotFoundException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.service.AdultContentGate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * <b>성인 전용 게시판</b>({@code boards.adult_only}) 접근 판정 한 곳 — docs/features/adult-board-pin-contract.md. 일반 게시판 안의 19금 글 판정은
 * 여기가 아니라 그대로 {@link AdultContentGate}(본인인증 성인이면 보인다)다.
 *
 * <ul>
 *   <li>{@link Access#HIDDEN} — 비회원 · 성인 아님 · 「19금 게시판 접근」 꺼짐(행 없음 포함). 성인 전용 게시판은 <b>없는 게시판</b>이다(404, 메뉴·목록에서 빠짐).</li>
 *   <li>{@link Access#PIN_REQUIRED} — 성인 + 접근 켜짐 + 이 기기(로그인 세션)에서 핀 미확인·만료. 메뉴·게시판 목록에는 보이고, 열면
 *       {@code 403 ADULT_BOARD_PIN_REQUIRED}(틀림 잠금 중이면 {@code lockedUntil}). 내용을 내는 곳(19금 전용 가젯·게시판 안 검색)은 성인 아님과 같이 다룬다.</li>
 *   <li>{@link Access#OPEN} — 핀 확인이 살아 있다. 다시 묻기 기준이 {@code IDLE} 이면 열 때마다({@link #requireBoard}·{@link #requirePost}·{@link #viewable})
 *       끝 시각을 지금 + 1시간으로 늘린다(세션 쓰기는 {@link AdultBoardPinPolicy#EXTEND_STEP} 마다).</li>
 * </ul>
 *
 * <p><b>질의</b>: 설정 행(PK 한 번)을 먼저 읽어 꺼져 있으면 본인인증 표를 읽지 않는다. 한 요청 안에서는 판정을 요청 속성에 적어 두어 여러 번 불려도
 * 한 번만 읽는다(요청 밖 — 배치·시험의 직접 호출 — 이면 부를 때마다 읽고, 세션이 없어 {@code OPEN} 이 되지 않는다). 성인 전용 게시판이 아닌 게시판에는
 * 아무것도 읽지 않는다 — 부르는 쪽도 성인 전용 게시판을 만났을 때만 부른다.
 */
@Component
public class AdultBoardGate {

    private static final String MEMO = "irobit.adultBoard.gate";

    /** 성인 전용 게시판을 이 사람에게 어떻게 다루는가 */
    public enum Access { HIDDEN, PIN_REQUIRED, OPEN }

    private final AdultBoardAccessRepository settings;
    private final AdultContentGate adultGate;
    private final AdultBoardUnlocks unlocks;
    private final Clock clock;

    public AdultBoardGate(AdultBoardAccessRepository settings, AdultContentGate adultGate, AdultBoardUnlocks unlocks, ObjectProvider<Clock> clocks) {
        this.settings = settings;
        this.adultGate = adultGate;
        this.unlocks = unlocks;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** @param viewerId 보는 회원(비회원 null) */
    public Access access(Long viewerId) {
        return state(viewerId).access();
    }

    /** 메뉴·게시판 목록·글쓰기 게시판 고르기에 성인 전용 게시판을 보이는가 — 성인 + 접근 켜짐(핀은 보지 않는다) */
    public boolean listed(Long viewerId) {
        return access(viewerId) != Access.HIDDEN;
    }

    /** 성인 전용 게시판의 내용을 지금 낼 수 있는가(19금 전용 가젯) — 핀 확인까지. 「보는 동안 연장」은 하지 않는다 */
    public boolean open(Long viewerId) {
        return access(viewerId) == Access.OPEN;
    }

    /** 목록에 보이는 게시판인가(성인 전용 게시판만 본다) — 판정은 성인 전용 게시판을 처음 만날 때 읽는다 */
    public Predicate<EffectiveBoard> listedBoards(Long viewerId) {
        return b -> !b.adultOnly() || listed(viewerId);
    }

    /**
     * 성인 전용 게시판을 연다(게시판 정보·글 목록·태그 추천·게시판 레이아웃·글쓰기) — 숨김이면 없는 게시판과 같은 {@code 404 BOARD_NOT_FOUND},
     * 핀이 필요하면 {@code 403 ADULT_BOARD_PIN_REQUIRED}. 성인 전용 게시판이 아니면 아무것도 읽지 않는다.
     */
    public EffectiveBoard requireBoard(EffectiveBoard b, Long viewerId) {
        if (b.adultOnly()) {
            require(viewerId, true);
        }
        return b;
    }

    /**
     * 성인 전용 게시판의 글을 연다(상세·댓글·투표·추천·신고·고치기) — 숨김이면 없는 글과 같은 {@code 404 POST_NOT_FOUND}, 핀이 필요하면
     * {@code 403 ADULT_BOARD_PIN_REQUIRED}.
     *
     * @param b 글이 놓인 게시판(소모임 글처럼 없으면 null — 판정하지 않는다)
     */
    public void requirePost(EffectiveBoard b, Long viewerId) {
        if (b != null && b.adultOnly()) {
            require(viewerId, false);
        }
    }

    /**
     * 오류 없이 — 이 게시판의 내용을 낼 수 있는가(게시판 안 검색의 게시판 고르기). 성인 전용 게시판이 아니면 true, 맞으면 핀 확인까지 됐을 때만 true 이고
     * 그때 「보는 동안 연장」도 한다.
     */
    public boolean viewable(EffectiveBoard b, Long viewerId) {
        if (!b.adultOnly()) {
            return true;
        }
        State state = state(viewerId);
        if (state.access() != Access.OPEN) {
            return false;
        }
        touch(viewerId, state);
        return true;
    }

    /** 이 요청에 적어 둔 판정을 버린다 — 같은 요청 안에서 설정·핀 확인을 바꾼 뒤 */
    public void forget() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.removeAttribute(MEMO, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private void require(Long viewerId, boolean board) {
        State state = state(viewerId);
        switch (state.access()) {
            case HIDDEN -> throw board ? new BoardNotFoundException() : new PostNotFoundException();
            case PIN_REQUIRED -> throw new AdultBoardPinRequiredException(state.lockedUntil());
            case OPEN -> touch(viewerId, state);
        }
    }

    /** {@code IDLE} 이면 끝 시각을 지금 + 유지 시간으로 늘린다 — 남은 시간이 {@link AdultBoardPinPolicy#EXTEND_STEP} 넘게 줄었을 때만 세션에 쓴다 */
    private void touch(Long viewerId, State state) {
        if (!state.idle() || state.unlock() == null) {
            return;
        }
        Instant now = clock.instant();
        Instant extended = now.plus(AdultBoardPinPolicy.UNLOCK_TTL);
        if (Duration.between(state.unlock().until(), extended).compareTo(AdultBoardPinPolicy.EXTEND_STEP) < 0) {
            return;
        }
        AdultBoardUnlocks.Unlock renewed = state.unlock().until(extended);
        unlocks.store(renewed);
        remember(viewerId, new State(Access.OPEN, null, true, renewed));
    }

    private State state(Long viewerId) {
        if (viewerId == null) {
            return State.HIDDEN;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getAttribute(MEMO, RequestAttributes.SCOPE_REQUEST) instanceof Memo memo && memo.viewerId() == viewerId) {
            return memo.state();
        }
        State state = read(viewerId);
        remember(viewerId, state);
        return state;
    }

    private static void remember(long viewerId, State state) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(MEMO, new Memo(viewerId, state), RequestAttributes.SCOPE_REQUEST);
        }
    }

    private State read(long viewerId) {
        AdultBoardAccessEntity setting = settings.findById(viewerId).orElse(null);
        if (setting == null || !setting.isEnabled() || !adultGate.adult(viewerId)) {
            return State.HIDDEN; // 꺼져 있으면 본인인증 표는 읽지 않는다. 켠 뒤 성인이 아니게 되는 일은 없지만(나이는 늘기만 한다) 판정은 늘 같이 본다
        }
        Instant now = clock.instant();
        AdultBoardUnlocks.Unlock unlock = unlocks.current();
        if (unlock != null && unlock.validFor(viewerId, setting.getPinSetAt(), now)) {
            return new State(Access.OPEN, null, AdultBoardAccessEntity.RELOCK_IDLE.equals(setting.getRelockMode()), unlock);
        }
        return new State(Access.PIN_REQUIRED, AdultBoardPinPolicy.activeLock(setting.getLockedUntil(), now), false, null);
    }

    /**
     * @param lockedUntil {@code PIN_REQUIRED} 일 때 틀림 잠금이 끝나는 때(잠기지 않았으면 null)
     * @param idle        {@code OPEN} 일 때 다시 묻기 기준이 {@code IDLE} 인가(보는 동안 연장)
     * @param unlock      {@code OPEN} 일 때 이 세션의 핀 확인
     */
    private record State(Access access, Instant lockedUntil, boolean idle, AdultBoardUnlocks.Unlock unlock) {

        static final State HIDDEN = new State(Access.HIDDEN, null, false, null);
    }

    private record Memo(long viewerId, State state) {
    }
}
