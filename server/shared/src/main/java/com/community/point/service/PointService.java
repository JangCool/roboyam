package com.community.point.service;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.exception.PointFrozenException;
import com.community.point.exception.PointInsufficientException;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.repository.master.PointLedgerWriter.Balance;
import com.community.point.repository.master.PointLedgerWriter.Entry;
import com.community.point.repository.master.PointLedgerWriter.EntryLot;
import com.community.point.repository.master.PointLedgerWriter.Lot;
import com.community.point.repository.master.PointLedgerWriter.NewLot;
import com.community.point.repository.master.PointLedgerWriter.Shortfall;
import com.community.point.service.PointDeductionPolicy.Take;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 지급·회수·조정·사용·사용 취소·소멸의 <b>유일한 길</b> — docs/features/point-contract.md, docs/features/commerce-stage1-contract.md 「A」·「1-1 서버 세부」,
 * docs/domain/commerce.md 5절. 모든 쓰기는 회원의 잔액 행을 잠근 뒤(트랜잭션의 마지막 잠금) 원장·묶음·잔액을 같은 트랜잭션에서 바꾼다 — 묶음은 그 잠금 아래에서만 고친다.
 *
 * <ul>
 *   <li><b>묶음</b>: 적립 하나 = 무상 묶음 하나({@code free_valid_days} 뒤 소멸). 원장 줄마다 어느 묶음에 얼마 반영됐는지({@code point_ledger_lots})와 무상·유상 몫을 남긴다.
 *       <b>소멸일이 지난 묶음은 쓰지 않는다</b> — 사용·회수·차감 조정·미회수 상계 전에 그 회원의 지난 묶음을 먼저 소멸(EXPIRE) 처리한다(배치가 늦어도). 차감 순서는 {@link PointDeductionPolicy}.</li>
 *   <li><b>적립</b>({@link #earn}·{@link #likeBonus}): 글·댓글·추천 트랜잭션 안에서 부른다. <b>적립 실패가 글쓰기를 깨지 않는다</b> — {@link IsolatedSideWork}(세이브포인트) 안에서 하고
 *       실패하면 0 이다(로그만). 하루 상한은 한국 시간 0시부터 그 사유로 <b>지급한 횟수</b>(원장 줄 — 지운 글·댓글도 센다). 정상(ACTIVE) 회원만, <b>포인트 동결</b> 중이면 조용히 건너뛴다.
 *       <b>미회수가 있으면</b> 같은 트랜잭션에서 적립 줄 다음에 {@code REVOKE_DEBT_OFFSET} 줄로 min(빚, 무상 잔액)을 방금 만든 묶음부터 뺀다(대상 = 적립 줄 {@code LEDGER}).</li>
 *   <li><b>회수</b>({@link #revokeContent}): 지운 글·댓글의 <b>실제로 지급했던 양</b>만(작성 포인트 + 추천 보너스, 줄 따로), <b>무상에서만</b> — 그 적립 줄이 만든 묶음 먼저 → 다른 무상(차감 순서).
 *       0P 바닥이고 모자라면 미회수(잔액 행 {@code revoke_debt} + {@code point_revoke_shortfalls}, 같은 대상은 한 번만). 반영 0 이면 원장 줄은 없다. 숨김은 회수하지 않는다.</li>
 *   <li><b>복구 되돌림</b>({@link #restoreContent}): 회수 줄이 뺀 묶음으로 되돌리고(만료면 {@code restored_lot_valid_days} 새 묶음), 그 글의 미회수 기록을 지우며 빚을 줄인다.
 *       이미 상계로 갚은 몫은 새 무상 묶음으로 준다. {@code POST_RESTORE} 한 줄(합 0 이면 줄 없음), 같은 글에 한 번.</li>
 *   <li><b>조정</b>({@link #adjust}): 무상만 — +는 무상 묶음(미회수가 있으면 상계 — 5-4 「운영 지급」 포함), −는 무상에서만 0P 바닥(빚을 만들지 않는다). 정지·동결 회원에게도 한다.</li>
 *   <li><b>사용</b>({@link #use})·<b>사용 취소</b>({@link #cancelUse}): 돈 흐름이라 격리하지 않는다({@code MANDATORY} — 실패하면 업무도 실패). 동결이면 사용 거절. 사용 취소는 원래 묶음으로
 *       (만료된 묶음이면 같은 종류 새 묶음 {@code restored_lot_valid_days}).</li>
 *   <li><b>소멸</b>({@link #expireLot}): 소멸 배치가 묶음마다 부른다(조건부·멱등).</li>
 * </ul>
 */
@Service
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    public static final String REF_POST = "POST";
    public static final String REF_COMMENT = "COMMENT";
    /** 미회수 상계 줄의 대상 — 그 적립 줄 */
    public static final String REF_LEDGER = "LEDGER";
    /** 소멸 줄의 대상 — 그 묶음 */
    public static final String REF_LOT = "LOT";
    /** 포인트 상점 구매·환불의 대상(1-2) */
    public static final String REF_POINT_SHOP_PURCHASE = "POINT_SHOP_PURCHASE";

    private static final EnumSet<PointLotKind> FREE_ONLY = EnumSet.of(PointLotKind.FREE);
    private static final EnumSet<PointLotKind> ALL_KINDS = EnumSet.allOf(PointLotKind.class);

    private final PointLedgerWriter ledger;
    private final PointPolicy policy;
    private final IsolatedSideWork isolated;
    private final SnowflakeIdGenerator ids;
    private final MemberRestrictionService restrictions;
    private final Clock clock;

    public PointService(PointLedgerWriter ledger, PointPolicy policy, IsolatedSideWork isolated, SnowflakeIdGenerator ids, MemberRestrictionService restrictions,
                        ObjectProvider<Clock> clocks) {
        this.ledger = ledger;
        this.policy = policy;
        this.isolated = isolated;
        this.ids = ids;
        this.restrictions = restrictions;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 조정 결과 — {@code applied} 는 실제 반영값(부호 포함, 0 이면 원장 줄 없음), {@code balance} 는 반영(과 미회수 상계) 뒤 합계 */
    public record Adjusted(long applied, long balance) {
    }

    // ── 적립 ────────────────────────────────────────────────────────────────

    /**
     * 글·댓글 작성 적립 — {@link PointReason#POST}·{@link PointReason#COMMENT} 만. 비회원 글은 부르지 않는다.
     *
     * @return 지급한 포인트(없으면 0 — 꺼짐·상한·정지·동결·이미 지급·실패). 미회수 상계로 바로 빠진 양도 지급한 양에 든다
     */
    public long earn(long userId, PointReason reason, long refId) {
        if (reason != PointReason.POST && reason != PointReason.COMMENT) {
            throw new IllegalArgumentException("작성 적립 사유가 아니다: " + reason);
        }
        // 정책 읽기도 세이브포인트 안에서 — 읽다 실패한 문장이 업무 트랜잭션을 망가뜨리지 않게
        return isolated.run("point:earn", () -> {
            PointRules rules = policy.current();
            boolean post = reason == PointReason.POST;
            int amount = post ? rules.postPoints() : rules.commentPoints();
            int cap = post ? rules.postDailyCap() : rules.commentDailyCap();
            if (amount <= 0 || cap <= 0) {
                return 0L;
            }
            return grant(userId, reason, post ? REF_POST : REF_COMMENT, refId, amount, cap);
        }, 0L);
    }

    /**
     * 추천 받음 보너스 — 추천을 저장하고 추천 수를 올린 <b>같은 트랜잭션</b>에서(글·댓글 행 갱신으로 같은 대상의 추천이 줄을 선 뒤) 부른다.
     * 방금 추천한 회원이 인정되는 회원이고, 그 대상의 인정된 추천이 <b>정확히 문턱 개</b>가 됐을 때만 작성자에게 한 번 준다(문턱 뒤의 추천·나중에 인정된 추천은 소급하지 않는다).
     *
     * @param refType {@link #REF_POST} · {@link #REF_COMMENT}
     * @return 지급한 포인트(없으면 0)
     */
    public long likeBonus(Long authorId, String refType, long refId, long likerId) {
        if (authorId == null || authorId == likerId) {
            return 0;
        }
        return isolated.run("point:like-bonus", () -> {
            PointRules rules = policy.current();
            if (rules.likeBonusPoints() <= 0 || rules.likeBonusDailyLimit() <= 0) {
                return 0L;
            }
            int threshold = rules.likeBonusThreshold();
            if (!ledger.recognizedLiker(likerId)) {
                return 0L; // 인정되지 않는 추천은 수를 바꾸지 않는다 — 문턱을 채울 수 없다
            }
            if (ledger.recognizedLikes(refType, refId, authorId, threshold + 1) != threshold) {
                return 0L;
            }
            return grant(authorId, PointReason.LIKE_BONUS, refType, refId, rules.likeBonusPoints(), rules.likeBonusDailyLimit());
        }, 0L);
    }

    /** 오늘(한국 시간 0시부터) 그 사유로 지급한 횟수 */
    public int countToday(long userId, PointReason reason) {
        return ledger.countSince(userId, reason.name(), todayStartId());
    }

    // ── 회수·복구 ───────────────────────────────────────────────────────────

    /**
     * 지운 글·댓글의 포인트 회수 — 작성 포인트와 그 대상이 받은 추천 보너스를 따로 회수한다(원장 줄·미회수 기록도 따로).
     *
     * @param refType {@link #REF_POST} · {@link #REF_COMMENT}
     * @return 실제로 회수한 합(양수, 없으면 0 — 모자란 양은 미회수로 남고 여기에 들지 않는다)
     */
    public long revokeContent(long userId, String refType, long refId) {
        PointReason earned = REF_POST.equals(refType) ? PointReason.POST : PointReason.COMMENT;
        PointReason revoke = REF_POST.equals(refType) ? PointReason.POST_REVOKE : PointReason.COMMENT_REVOKE;
        return isolated.run("point:revoke", () -> revoke(userId, earned, revoke, refType, refId) + revoke(userId, PointReason.LIKE_BONUS, PointReason.LIKE_BONUS_REVOKE,
                refType, refId), 0L);
    }

    /**
     * 지운 글을 복구했다 — 그 글로 회수했던 만큼 {@link PointReason#POST_RESTORE} 한 줄로 되돌려 준다(결정 7, 계약 「1-1 서버 세부」 — 관리자 게시글 관리, 상한 없음).
     *
     * <ul>
     *   <li>회수 줄(글 포인트·추천 보너스)이 뺀 묶음으로 되돌린다 — 소멸일이 지난 묶음이면 같은 종류의 새 묶음({@code restored_lot_valid_days}, {@code restored_from_lot_id}).
     *       묶음 추적 전(V85 이전)의 회수 줄은 어느 묶음인지 몰라 새 무상 묶음({@code free_valid_days})으로 준다.</li>
     *   <li>그 글의 미회수 기록을 지우고 빚을 min(빚, 모자란 양 합)만큼 줄인다. 모자란 양 중 이미 상계로 갚은 몫(모자란 양 합 − 줄인 빚)은 새 무상 묶음({@code free_valid_days})으로 준다.</li>
     *   <li>합이 0 이면 원장 줄은 없다(미회수 정리만). 탈퇴·삭제된 회원에게는 하지 않는다(정지 회원은 받는다). 같은 글에 한 번만(원장 유일 색인). 되돌림은 적립이 아니라 미회수 상계를 하지 않는다.</li>
     * </ul>
     * <b>되돌림 실패가 복구를 깨지 않는다</b>(세이브포인트 — 실패하면 0, 로그만).
     *
     * @return 되돌려 준 포인트(없으면 0)
     */
    public long restoreContent(long userId, long postId) {
        return isolated.run("point:restore", () -> restore(userId, postId), 0L);
    }

    // ── 조정 ────────────────────────────────────────────────────────────────

    /**
     * 운영 조정 — 부르는 서비스의 트랜잭션 안에서만(감사 기록·알림과 한 트랜잭션). <b>무상만</b>(5-4): +는 무상 묶음 하나({@code free_valid_days}, 미회수가 있으면 상계),
     * −는 무상에서만 0P 까지(반영값이 요청보다 작을 수 있다, 빚을 만들지 않는다). 범위·권한·큰 금액 문턱 검사는 부르는 쪽(관리자 서버)이 한다.
     *
     * @param delta 0 이 아닌 요청 값
     * @param note  회원에게 공개되는 사유
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Adjusted adjust(long userId, int delta, String note, long operatorId) {
        if (delta == 0) {
            throw new IllegalArgumentException("조정 값은 0 이 아니어야 한다");
        }
        Account a = open(userId);
        if (delta > 0) {
            if (a.debt > 0) {
                expireDue(a);
            }
            Credit credit = creditNewLot(a, PointReason.ADMIN_ADJUST, delta, note, null, null, operatorId);
            offsetDebt(a, credit);
            return new Adjusted(delta, a.total());
        }
        expireDue(a);
        long take = Math.min(-(long) delta, a.free);
        if (take == 0) {
            return new Adjusted(0, a.total());
        }
        debit(a, PointKind.REVOKE, PointReason.ADMIN_ADJUST, PointDeductionPolicy.allocate(ledger.usableLots(userId, FREE_ONLY, a.now), take, null), note, null, null,
                operatorId);
        return new Adjusted(-take, a.total());
    }

    // ── 사용·사용 취소 ──────────────────────────────────────────────────────

    /**
     * 포인트 사용 — 업무(포인트 상점 구매 등)의 트랜잭션 안에서만, 업무의 다른 잠금(재고 등) 뒤에 마지막으로. 소멸일이 지난 묶음을 먼저 소멸 처리한 뒤 무상(소멸 임박순) → 유상(먼저 만든 순)으로 뺀다.
     *
     * @param amount 양수(1 줄 금액 — int 범위)
     * @param reason 사용 사유({@link PointKind#USE} — 예: {@link PointReason#POINT_SHOP_PURCHASE})
     * @param refType 대상 종류(예: {@link #REF_POINT_SHOP_PURCHASE}) — 사유·대상은 유일(같은 대상에 두 번 쓰지 않는다)
     * @return 사용 원장 줄 ID(사용 취소에 넘긴다)
     * @throws PointFrozenException       포인트 동결 중
     * @throws PointInsufficientException 쓸 수 있는 포인트가 모자라다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long use(long userId, long amount, PointReason reason, String refType, long refId) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("사용 양이 범위 밖이다: " + amount);
        }
        if (reason.kind(-amount) != PointKind.USE) {
            throw new IllegalArgumentException("사용 사유가 아니다: " + reason);
        }
        if (restrictions.hasActive(userId, RestrictionType.POINT_FREEZE)) {
            throw new PointFrozenException();
        }
        Account a = open(userId);
        expireDue(a);
        if (a.total() < amount) {
            throw new PointInsufficientException();
        }
        return debit(a, PointKind.USE, reason, PointDeductionPolicy.allocate(ledger.usableLots(userId, ALL_KINDS, a.now), amount, null), null, refType, refId, null);
    }

    /**
     * 사용 취소 — 원래 사용 줄이 뺀 묶음으로 같은 양을 되돌린다. 소멸일이 지난 묶음이면 같은 종류의 새 묶음({@code restored_lot_valid_days}, {@code restored_from_lot_id})으로 준다
     * (유상은 소멸이 없어 늘 원래 묶음). 동결 중에도 한다. <b>멱등</b>: 같은 사유·대상 줄이 이미 있으면 그 줄 ID 를 돌려준다.
     *
     * @param useLedgerId {@link #use} 가 돌려준 줄 ID
     * @param reason      사용 취소 사유({@link PointKind#CANCEL_USE} — 예: {@link PointReason#POINT_SHOP_REFUND})
     * @return 사용 취소 원장 줄 ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long cancelUse(long useLedgerId, PointReason reason, String refType, long refId) {
        if (reason.kind(1) != PointKind.CANCEL_USE) {
            throw new IllegalArgumentException("사용 취소 사유가 아니다: " + reason);
        }
        Entry use = ledger.entry(useLedgerId).filter(entry -> entry.kind() == PointKind.USE)
                .orElseThrow(() -> new IllegalArgumentException("사용 줄이 아니다: " + useLedgerId));
        Account a = open(use.userId());
        Optional<Long> done = ledger.entryId(reason.name(), refType, refId);
        if (done.isPresent()) {
            return done.get();
        }
        Map<Long, Long> back = new LinkedHashMap<>();
        long tracked = 0;
        for (EntryLot part : ledger.entryLots(useLedgerId)) {
            back.merge(part.lotId(), -part.amount(), Long::sum);
            tracked += -part.amount();
        }
        return creditBack(a, PointKind.CANCEL_USE, reason, back, -(long) use.delta() - tracked, refType, refId);
    }

    // ── 소멸 ────────────────────────────────────────────────────────────────

    /**
     * 묶음 하나를 소멸 — 소멸 배치가 묶음마다 부른다. 무상이고 소멸일이 지났고 남은 양이 있을 때만 남은 양 전부를 {@code EXPIRE} 줄(대상 = 묶음)로 뺀다. <b>조건부·멱등</b>:
     * 이미 소멸했거나(남은 양 0) 아직 소멸일 전이면 0. 회원의 잔액 행을 잠근 뒤 묶음을 다시 읽는다(사용·회수와 줄을 선다).
     *
     * @return 소멸한 양(없으면 0)
     */
    @Transactional
    public long expireLot(long lotId) {
        Optional<Lot> found = ledger.lot(lotId);
        if (found.isEmpty()) {
            return 0;
        }
        Account a = open(found.get().userId());
        Lot lot = ledger.lot(lotId).orElseThrow();
        if (lot.kind() != PointLotKind.FREE || lot.remaining() == 0 || !lot.expiredAt(a.now)) {
            return 0;
        }
        return expire(a, lot);
    }

    // ── 안쪽: 적립·회수·복구 ────────────────────────────────────────────────

    /** 받을 수 있는 회원인가(정상 회원 · 포인트 동결 아님) → 잔액 행 잠금 → 이미 지급했나 → 하루 상한 → 지급(+ 미회수 상계) */
    private long grant(long userId, PointReason reason, String refType, long refId, int amount, int cap) {
        if (!ledger.earnable(userId)) {
            return 0L;
        }
        if (restrictions.hasActive(userId, RestrictionType.POINT_FREEZE)) {
            log.debug("포인트 동결 중이라 지급하지 않는다 userId={} reason={}", userId, reason); // 글쓰기는 그대로 성공한다(commerce.md 12-3, 계약 「0-4」)
            return 0L;
        }
        Account a = open(userId);
        if (ledger.exists(reason.name(), refType, refId)) {
            return 0L;
        }
        if (ledger.countSince(userId, reason.name(), todayStartId()) >= cap) {
            log.debug("하루 상한이라 지급하지 않는다 userId={} reason={}", userId, reason);
            return 0L;
        }
        if (a.debt > 0) {
            expireDue(a); // 상계는 차감이다 — 지난 묶음을 먼저 소멸
        }
        Credit credit = creditNewLot(a, reason, amount, null, refType, refId, null);
        offsetDebt(a, credit);
        return amount;
    }

    /** 지급했던 양만 무상에서 회수한다 — 그 적립 줄의 묶음 먼저, 0P 바닥, 모자라면 미회수. 같은 대상에 한 번 */
    private long revoke(long userId, PointReason earned, PointReason revoke, String refType, long refId) {
        Optional<Entry> earnedEntry = ledger.entry(userId, earned.name(), refType, refId);
        if (earnedEntry.isEmpty() || earnedEntry.get().delta() <= 0) {
            return 0L;
        }
        Account a = open(userId);
        if (ledger.exists(revoke.name(), refType, refId) || ledger.shortfallExists(revoke.name(), refType, refId)
                || REF_POST.equals(refType) && ledger.exists(PointReason.POST_RESTORE.name(), REF_POST, refId)) {
            return 0L; // 이미 회수했거나(반영 0 이어도 미회수 기록이 남는다) 복구로 되돌려 준 글 — 두 번째 회수는 없다
        }
        expireDue(a);
        long granted = earnedEntry.get().delta();
        long take = Math.min(granted, a.free);
        if (take > 0) {
            Long first = ledger.lotMadeBy(earnedEntry.get().id()).orElse(null);
            debit(a, PointKind.REVOKE, revoke, PointDeductionPolicy.allocate(ledger.usableLots(userId, FREE_ONLY, a.now), take, first), null, refType, refId, null);
        }
        if (take < granted) {
            ledger.insertShortfall(ids.nextId(), userId, revoke.name(), refType, refId, granted, take, a.now);
            a.debt += granted - take;
            save(a);
            log.info("잔액이 모자라 미회수로 남겼다 userId={} reason={} 요청={} 반영={}", userId, revoke, granted, take);
        }
        return take;
    }

    private long restore(long userId, long postId) {
        if (!ledger.restorable(userId)) {
            return 0L;
        }
        Account a = open(userId);
        if (ledger.exists(PointReason.POST_RESTORE.name(), REF_POST, postId)) {
            return 0L;
        }
        Map<Long, Long> back = new LinkedHashMap<>();
        long untracked = 0;
        for (PointReason reason : List.of(PointReason.POST_REVOKE, PointReason.LIKE_BONUS_REVOKE)) {
            Optional<Entry> revoked = ledger.entry(userId, reason.name(), REF_POST, postId);
            if (revoked.isEmpty()) {
                continue;
            }
            long tracked = 0;
            for (EntryLot part : ledger.entryLots(revoked.get().id())) {
                back.merge(part.lotId(), -part.amount(), Long::sum);
                tracked += -part.amount();
            }
            untracked += -(long) revoked.get().delta() - tracked; // 묶음 추적 전(V85 이전) 줄
        }
        List<Shortfall> shortfalls = ledger.shortfalls(userId, List.of(PointReason.POST_REVOKE.name(), PointReason.LIKE_BONUS_REVOKE.name()), REF_POST, postId);
        long shortSum = shortfalls.stream().mapToLong(Shortfall::shortAmount).sum();
        long debtCut = Math.min(a.debt, shortSum);
        long repaid = shortSum - debtCut; // 이미 상계로 갚은 몫 — 되돌려 준다
        for (Shortfall shortfall : shortfalls) {
            ledger.deleteShortfall(shortfall.id());
        }
        if (debtCut > 0) {
            a.debt -= debtCut;
            save(a);
        }
        long fresh = untracked + repaid;
        if (back.isEmpty() && fresh == 0) {
            return 0L;
        }
        long total = back.values().stream().mapToLong(Long::longValue).sum() + fresh;
        creditBack(a, PointKind.EARN, PointReason.POST_RESTORE, back, fresh, REF_POST, postId);
        return total;
    }

    // ── 안쪽: 원장·묶음 한 걸음 ─────────────────────────────────────────────

    /** 잠근 잔액 행의 지금 값 — 이 흐름 안에서만 바꾼다 */
    private static final class Account {
        final long userId;
        final Instant now;
        long free;
        long paid;
        long debt;

        Account(long userId, Instant now, Balance balance) {
            this.userId = userId;
            this.now = now;
            this.free = balance.free();
            this.paid = balance.paid();
            this.debt = balance.debt();
        }

        long total() {
            return free + paid;
        }
    }

    /** 새 묶음을 만든 적립 줄 */
    private record Credit(long lineId, long lotId) {
    }

    private Account open(long userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // DB 는 마이크로초
        return new Account(userId, now, ledger.lockBalance(userId, now));
    }

    private void save(Account a) {
        ledger.saveBalance(a.userId, a.free, a.paid, a.debt, a.now);
    }

    /** 원장 한 줄 + 잔액(묶음 반영은 부르는 쪽) */
    private long line(Account a, PointKind kind, PointReason reason, long freeDelta, long paidDelta, String note, String refType, Long refId, Long actor) {
        long delta = freeDelta + paidDelta;
        if (delta == 0 || Math.abs(delta) > Integer.MAX_VALUE) {
            throw new IllegalStateException("원장 한 줄 금액이 범위 밖이다: " + delta);
        }
        a.free += freeDelta;
        a.paid += paidDelta;
        long id = ids.nextId();
        ledger.insertEntry(id, a.userId, kind, reason.name(), (int) delta, freeDelta, paidDelta, a.total(), note, refType, refId, actor, a.now);
        save(a);
        return id;
    }

    /** 무상 묶음 하나를 새로 만드는 지급 줄(적립 하나 = 묶음 하나, {@code free_valid_days} 뒤 소멸) */
    private Credit creditNewLot(Account a, PointReason reason, long amount, String note, String refType, Long refId, Long actor) {
        long lineId = line(a, PointKind.EARN, reason, amount, 0, note, refType, refId, actor);
        long lotId = ids.nextId();
        ledger.insertLot(new NewLot(lotId, a.userId, PointLotKind.FREE, amount, a.now.plus(Duration.ofDays(lots().freeValidDays())), reason.name(), refType, refId,
                lineId, null, a.now));
        ledger.insertEntryLot(lineId, lotId, amount);
        return new Credit(lineId, lotId);
    }

    /**
     * 되돌림 줄 하나 — {@code back}(묶음 → 양)은 원래 묶음으로(소멸일이 지났으면 같은 종류 새 묶음 {@code restored_lot_valid_days}), {@code fresh} 는 새 무상 묶음({@code free_valid_days}).
     */
    private long creditBack(Account a, PointKind kind, PointReason reason, Map<Long, Long> back, long fresh, String refType, long refId) {
        List<Lot> lots = ledger.lots(back.keySet());
        Map<Long, Lot> byId = new LinkedHashMap<>();
        lots.forEach(lot -> byId.put(lot.id(), lot));
        long free = fresh;
        long paid = 0;
        for (Map.Entry<Long, Long> part : back.entrySet()) {
            if (byId.get(part.getKey()).kind() == PointLotKind.FREE) {
                free += part.getValue();
            } else {
                paid += part.getValue();
            }
        }
        long lineId = line(a, kind, reason, free, paid, null, refType, refId, null);
        PointLotRules rules = lots();
        for (Map.Entry<Long, Long> part : back.entrySet()) {
            Lot lot = byId.get(part.getKey());
            long amount = part.getValue();
            if (lot.expiredAt(a.now)) {
                long restoredId = ids.nextId();
                ledger.insertLot(new NewLot(restoredId, a.userId, lot.kind(), amount, a.now.plus(Duration.ofDays(rules.restoredLotValidDays())), reason.name(), refType,
                        refId, null, lot.id(), a.now));
                ledger.insertEntryLot(lineId, restoredId, amount);
            } else {
                ledger.changeRemaining(lot.id(), amount);
                ledger.insertEntryLot(lineId, lot.id(), amount);
            }
        }
        if (fresh > 0) {
            long freshId = ids.nextId();
            ledger.insertLot(new NewLot(freshId, a.userId, PointLotKind.FREE, fresh, a.now.plus(Duration.ofDays(rules.freeValidDays())), reason.name(), refType, refId,
                    lineId, null, a.now));
            ledger.insertEntryLot(lineId, freshId, fresh);
        }
        return lineId;
    }

    /** 차감 줄 하나 — 나눈 묶음마다 남은 양을 빼고 반영을 남긴다 */
    private long debit(Account a, PointKind kind, PointReason reason, List<Take> takes, String note, String refType, Long refId, Long actor) {
        long free = takes.stream().filter(take -> take.lot().kind() == PointLotKind.FREE).mapToLong(Take::amount).sum();
        long paid = takes.stream().filter(take -> take.lot().kind() == PointLotKind.PAID).mapToLong(Take::amount).sum();
        long lineId = line(a, kind, reason, -free, -paid, note, refType, refId, actor);
        for (Take take : takes) {
            ledger.changeRemaining(take.lot().id(), -take.amount());
            ledger.insertEntryLot(lineId, take.lot().id(), -take.amount());
        }
        return lineId;
    }

    /** 미회수 상계 — 방금 적립한 묶음부터(그다음 차감 순서) min(빚, 무상 잔액)을 뺀다. 대상 = 그 적립 줄 */
    private void offsetDebt(Account a, Credit credit) {
        long amount = Math.min(a.debt, a.free);
        if (amount <= 0) {
            return;
        }
        a.debt -= amount;
        debit(a, PointKind.REVOKE, PointReason.REVOKE_DEBT_OFFSET, PointDeductionPolicy.allocate(ledger.usableLots(a.userId, FREE_ONLY, a.now), amount, credit.lotId()),
                null, REF_LEDGER, credit.lineId(), null);
    }

    /** 그 회원의 소멸일이 지난 무상 묶음을 모두 소멸 처리한다(차감 전에 — 지난 포인트가 쓰이지 않게) */
    private void expireDue(Account a) {
        List<Lot> due = new ArrayList<>(ledger.dueLots(a.userId, a.now));
        for (Lot lot : due) {
            expire(a, lot);
        }
    }

    private long expire(Account a, Lot lot) {
        long amount = lot.remaining();
        boolean free = lot.kind() == PointLotKind.FREE;
        long lineId = line(a, PointKind.EXPIRE, PointReason.EXPIRE, free ? -amount : 0, free ? 0 : -amount, null, REF_LOT, lot.id(), null);
        ledger.changeRemaining(lot.id(), -amount);
        ledger.insertEntryLot(lineId, lot.id(), -amount);
        return amount;
    }

    private PointLotRules lots() {
        return policy.lots();
    }

    /** 한국 시간 오늘 0시 이후의 원장 ID 하한(Snowflake 는 시각순 — 발급기의 시계가 실제 시계라 오늘도 실제 시계로 정한다) */
    private static long todayStartId() {
        Instant start = LocalDate.now(ServiceTime.ZONE).atStartOfDay(ServiceTime.ZONE).toInstant();
        return SnowflakeIdGenerator.floorIdAt(start);
    }
}
