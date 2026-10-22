package com.community.point.service;

import com.community.point.dto.PointRules;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 지급·회수·조정의 <b>유일한 길</b> — docs/features/point-contract.md. 모든 쓰기는 회원의 잔액 행을 잠근 뒤(트랜잭션의 마지막 잠금) 원장 한 줄 + 잔액을 같은 트랜잭션에서 바꾼다.
 *
 * <ul>
 *   <li><b>적립</b>({@link #earn}·{@link #likeBonus}): 글·댓글·추천 트랜잭션 안에서 부른다. <b>적립 실패가 글쓰기를 깨지 않는다</b> — {@link IsolatedSideWork}(세이브포인트) 안에서 하고
 *       실패하면 0 이다(로그만). community 는 같은 트랜잭션으로 묶어 실패하면 글도 되돌렸지만 roboyam 은 「적립 실패가 글쓰기를 깨지 않게」로 정했다(계약 가정).</li>
 *   <li><b>하루 상한</b>은 한국 시간 0시부터 그 사유로 <b>지급한 횟수</b>(원장 줄) — 지운 글·댓글도 센다. 상한에 닿으면 글은 그대로 올라가고 포인트만 없다. 잔액 행 잠금 아래에서 세므로 동시 요청도 넘지 않는다.</li>
 *   <li><b>받는 사람</b>: 정상(ACTIVE) 회원만. 같은 대상에 두 번 지급·회수하지 않는다(멱등 — 원장 유일 색인이 마지막 방어).</li>
 *   <li><b>회수</b>({@link #revokeContent}): 지운 글·댓글의 <b>실제로 지급했던 양</b>만(글·댓글 포인트 + 그 대상이 받은 추천 보너스), <b>0P 까지만</b>(마이너스 없음), 반영 0 이면 줄을 만들지 않는다.
 *       본인 삭제라 알림은 없다. 숨김은 회수하지 않는다.</li>
 *   <li><b>조정</b>({@link #adjust}): 운영자의 명시적 조치 — 정지 회원에게도 한다. 차감은 0P 까지라 반영값이 요청보다 작을 수 있다. 이 일 자체가 업무라 격리하지 않는다(실패하면 요청이 실패한다).</li>
 * </ul>
 */
@Service
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static final String REF_POST = "POST";
    public static final String REF_COMMENT = "COMMENT";

    private final PointLedgerWriter ledger;
    private final PointPolicy policy;
    private final IsolatedSideWork isolated;
    private final SnowflakeIdGenerator ids;

    public PointService(PointLedgerWriter ledger, PointPolicy policy, IsolatedSideWork isolated, SnowflakeIdGenerator ids) {
        this.ledger = ledger;
        this.policy = policy;
        this.isolated = isolated;
        this.ids = ids;
    }

    /** 조정 결과 — {@code applied} 는 실제 반영값(부호 포함, 0 이면 원장 줄 없음) */
    public record Adjusted(long applied, long balance) {
    }

    /**
     * 글·댓글 작성 적립 — {@link PointReason#POST}·{@link PointReason#COMMENT} 만. 비회원 글은 부르지 않는다.
     *
     * @return 지급한 포인트(없으면 0 — 꺼짐·상한·정지·이미 지급·실패)
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

    /**
     * 지운 글·댓글의 포인트 회수 — 작성 포인트와 그 대상이 받은 추천 보너스를 따로 회수한다(원장 줄도 따로).
     *
     * @param refType {@link #REF_POST} · {@link #REF_COMMENT}
     * @return 회수한 합(양수, 없으면 0)
     */
    public long revokeContent(long userId, String refType, long refId) {
        PointReason earned = REF_POST.equals(refType) ? PointReason.POST : PointReason.COMMENT;
        PointReason revoke = REF_POST.equals(refType) ? PointReason.POST_REVOKE : PointReason.COMMENT_REVOKE;
        return isolated.run("point:revoke", () -> revoke(userId, earned, revoke, refType, refId) + revoke(userId, PointReason.LIKE_BONUS, PointReason.LIKE_BONUS_REVOKE,
                refType, refId), 0L);
    }

    /**
     * 운영 조정 — 부르는 서비스의 트랜잭션 안에서만(감사 기록·알림과 한 트랜잭션).
     *
     * @param delta 0 이 아닌 요청 값(검사는 부르는 쪽)
     * @param note  회원에게 공개되는 사유
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Adjusted adjust(long userId, int delta, String note, long operatorId) {
        Instant now = Instant.now();
        long balance = ledger.lockBalance(userId, now);
        long applied = delta >= 0 ? delta : -Math.min(-(long) delta, balance);
        if (applied == 0) {
            return new Adjusted(0, balance);
        }
        long after = balance + applied;
        ledger.append(ids.nextId(), userId, PointReason.ADMIN_ADJUST.kindOf(applied), PointReason.ADMIN_ADJUST.name(), (int) applied, after, note, null, null,
                operatorId, now);
        return new Adjusted(applied, after);
    }

    /** 오늘(한국 시간 0시부터) 그 사유로 지급한 횟수 */
    public int countToday(long userId, PointReason reason) {
        return ledger.countSince(userId, reason.name(), todayStartId());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 잔액 행을 잠근 뒤: 받을 수 있는 회원인가 → 이미 지급했나 → 하루 상한 → 지급 */
    private long grant(long userId, PointReason reason, String refType, long refId, int amount, int cap) {
        Instant now = Instant.now();
        if (!ledger.earnable(userId)) {
            return 0L;
        }
        long balance = ledger.lockBalance(userId, now);
        if (ledger.exists(reason.name(), refType, refId)) {
            return 0L;
        }
        if (ledger.countSince(userId, reason.name(), todayStartId()) >= cap) {
            log.debug("하루 상한이라 지급하지 않는다 userId={} reason={}", userId, reason);
            return 0L;
        }
        ledger.append(ids.nextId(), userId, reason.kindOf(amount), reason.name(), amount, balance + amount, null, refType, refId, null, now);
        return amount;
    }

    /** 지급했던 양만, 0P 까지 회수한다. 반영 0 이면 줄을 만들지 않는다 */
    private long revoke(long userId, PointReason earned, PointReason revoke, String refType, long refId) {
        long granted = ledger.granted(userId, earned.name(), refType, refId);
        if (granted <= 0 || ledger.exists(revoke.name(), refType, refId)) {
            return 0L;
        }
        Instant now = Instant.now();
        long balance = ledger.lockBalance(userId, now);
        long take = Math.min(granted, balance);
        if (take == 0) {
            return 0L;
        }
        ledger.append(ids.nextId(), userId, revoke.kindOf(-take), revoke.name(), (int) -take, balance - take, null, refType, refId, null, now);
        return take;
    }

    /** 한국 시간 오늘 0시 이후의 원장 ID 하한(Snowflake 는 시각순) */
    private static long todayStartId() {
        Instant start = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        return SnowflakeIdGenerator.floorIdAt(start);
    }
}
