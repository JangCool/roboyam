package com.community.grade.service;

import com.community.grade.repository.master.ProfileXpWriter;
import com.community.point.entity.PointReason;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 경험치(XP)·레벨 (docs/features/grade-level-contract.md 1절) — 경험치 = 활동으로 <b>적립된</b> 포인트의 누적. 포인트를 쓰거나 소멸해도 줄지 않는다.
 *
 * <ul>
 *   <li><b>원천</b>: 포인트 원장 줄. {@code PointService} 가 원장 줄을 쓰는 <b>같은 트랜잭션·같은 잠금</b>(잔액 행) 아래에서 {@link #onLedgerLine} 을 부른다 — 적립 경로의
 *       세이브포인트 격리 규칙 그대로라 실패해도 글쓰기를 깨지 않는다(그 적립과 함께 되돌아간다).</li>
 *   <li><b>사유</b>: 더함 {@link #GAIN} · 뺌 {@link #LOSS}(원장 줄의 금액 그대로 — 회수 줄은 실제로 뺀 양). 그 밖의 사유(운영 조정·충전·가입·이벤트·사용·소멸 …)는 경험치가 아니다.
 *       0 아래로 내려가지 않는다. 기존 회원의 값은 V196 이 같은 사유 목록으로 원장에서 다시 계산해 채웠다 — <b>목록을 고치면 V196 의 목록도 같은 뜻인지 본다</b>.</li>
 *   <li><b>레벨</b>: 경험치가 바뀌면 {@link XpCurve#levelOf} 로 다시 매긴다. 레벨이 <b>올랐으면</b> 커밋 뒤 별도 트랜잭션으로 즉시 승급을 판정한다
 *       ({@link GradePromotionService#promoteOnLevelUp} — 실패해도 업무는 그대로, 로그만). 한 트랜잭션에서 같은 회원은 한 번만 판정한다.</li>
 * </ul>
 */
@Service
public class XpService {

    private static final Logger log = LoggerFactory.getLogger(XpService.class);

    /** 경험치를 더하는 사유 */
    public static final Set<PointReason> GAIN = EnumSet.of(PointReason.POST, PointReason.COMMENT, PointReason.LIKE_BONUS, PointReason.ATTENDANCE,
            PointReason.ATTENDANCE_STREAK, PointReason.POPULAR, PointReason.POST_RESTORE);
    /** 경험치를 빼는 사유(0 아래로는 안 내려간다) */
    public static final Set<PointReason> LOSS = EnumSet.of(PointReason.POST_REVOKE, PointReason.COMMENT_REVOKE, PointReason.LIKE_BONUS_REVOKE);

    /** 트랜잭션 자원 키 — 이 트랜잭션에서 레벨이 오른 회원들(커밋 뒤 승급 판정) */
    private static final Object PENDING_KEY = new Object();

    private final ProfileXpWriter writer;
    private final GradePromotionService promotions;

    public XpService(ProfileXpWriter writer, GradePromotionService promotions) {
        this.writer = writer;
        this.promotions = promotions;
    }

    /** 이 사유가 경험치에 드는가 */
    public static boolean counts(PointReason reason) {
        return GAIN.contains(reason) || LOSS.contains(reason);
    }

    /**
     * 원장 줄 한 줄을 쓴 직후 — 부르는 쪽 트랜잭션(잔액 행 잠금 아래)에서. 경험치에 들지 않는 사유면 아무것도 하지 않는다.
     *
     * @param delta 원장 줄 금액(부호 포함 — 적립 +, 회수 −)
     */
    public void onLedgerLine(long userId, PointReason reason, long delta) {
        if (delta == 0 || !counts(reason)) {
            return;
        }
        long xpDelta = GAIN.contains(reason) ? Math.abs(delta) : -Math.abs(delta);
        Optional<ProfileXpWriter.Changed> changed = writer.add(userId, xpDelta);
        if (changed.isEmpty()) {
            return; // 프로필 행이 없다(있을 수 없지만 원장 쓰기를 깨지 않는다)
        }
        int base = XpCurve.baseOrDefault(writer.curveBase().orElse(null));
        int level = XpCurve.levelOf(changed.get().xp(), base);
        int previous = changed.get().previousLevel();
        if (level == previous) {
            return;
        }
        writer.changeLevel(userId, level);
        if (level > previous) {
            promoteAfterCommit(userId);
        }
    }

    /** 모든 회원의 경험치·레벨을 원장에서 다시 센다(데모 팩을 넣은 뒤 — 팩의 프로필에 경험치 칸이 없거나 낡았을 때). 부르는 쪽 트랜잭션에서. 바뀐 회원 수 */
    public int recomputeAllFromLedger() {
        java.util.List<String> reasons = java.util.stream.Stream.concat(GAIN.stream(), LOSS.stream()).map(Enum::name).toList();
        return writer.recomputeAllFromLedger(reasons);
    }

    /** 커밋 뒤 즉시 승급 판정을 건다(트랜잭션 동기화가 없으면 건너뛴다 — 다음 활동·일수 배치가 판정한다) */
    @SuppressWarnings("unchecked")
    private void promoteAfterCommit(long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        Set<Long> pending = (Set<Long>) TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (pending == null) {
            Set<Long> users = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(PENDING_KEY, users);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long id : users) {
                        try {
                            promotions.promoteOnLevelUp(id, Instant.now())
                                    .ifPresent(gradeKey -> log.info("레벨 조건으로 등급을 올렸다 userId={} grade={}", id, gradeKey));
                        } catch (RuntimeException e) {
                            // 예외 문구에 SQL 인자가 실릴 수 있어 클래스 이름만 남긴다
                            log.warn("레벨업 뒤 승급 판정에 실패했다 — 업무에는 영향이 없다 userId={} 원인={}", id, e.getClass().getSimpleName());
                        }
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_KEY);
                }
            });
            pending = users;
        }
        pending.add(userId);
    }
}
