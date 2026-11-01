package com.community.hotdeal.service;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.repository.master.DealEndRequestStore;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.point.service.PointService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 딜 종료·재개의 공용 길(shared — 공개 서버 작성자 API·관리자 서버 3단계·배치가 같이 쓴다). docs/features/hotdeal-contract.md 「권한·정책」, 서버 단계 고정 규칙 5·6·9·11.
 *
 * <ul>
 *   <li><b>잠금</b>: 늘 딜 행을 잠가({@link PostDealRepository#findWithLockByPostId}) 다시 읽고 바꾼다 — 작성자 종료·운영진 종료·자동 종료·종료 요청 문턱·keep 이 같은 행에서 줄을 선다.
 *       먼저 끝낸 쪽만 종료·보상하고, 뒤에 온 쪽은 이미 끝난 딜을 보고 아무것도 하지 않는다(동시 종료 경합).</li>
 *   <li><b>보상</b>(규칙 5): 종료 경로 셋(작성자 — 직접·재고 품절 저장·탈퇴 · 운영진 · 자동) 모두, <b>의심 구간 요청자에게만</b>({@link DealEndRequestStore} — 의심이 된 회차와 의심인 동안의 요청),
 *       {@link PointService#dealEndRequestReward} 한 길(사유 {@code DEAL_END_REQUEST}, 대상 = 요청 줄, 원장 유일 · 하루 지급 횟수는 포인트 정책). 의심이 아닌 딜의 종료는 보상이 없다
 *       (켜진 줄이 없다).</li>
 *   <li><b>알림</b>: 운영진 종료 → 작성자 {@code DEAL_MANAGER_ENDED}(사유) · 자동 종료 → {@code DEAL_AUTO_ENDED} · 재개했는데 기간이 지났으면 → {@code DEAL_REOPENED_FIX_ENDS}.
 *       작성자 자신의 종료는 알리지 않는다.</li>
 * </ul>
 * 감사 기록·권한 판정(게시판 매니저·스텝·{@code hotdeal.manage})·관리자 오류 코드는 부르는 쪽(3단계 관리자 서비스)의 몫이다 — 여기서는 결과({@link Outcome})만 돌려준다.
 */
@Service
public class DealEndService {

    private final PostDealRepository deals;
    private final DealEndRequestStore requests;
    private final PointService points;
    private final DealSettingsReader settings;
    private final DealNotices notices;
    private final Clock clock;

    public DealEndService(PostDealRepository deals, DealEndRequestStore requests, PointService points, DealSettingsReader settings, DealNotices notices,
                          ObjectProvider<Clock> clocks) {
        this.deals = deals;
        this.requests = requests;
        this.points = points;
        this.settings = settings;
        this.notices = notices;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 바꾸기 결과 — {@link #CHANGED} 바꿨다 · {@link #NOT_FOUND} 딜이 없다 · {@link #STATE_CHANGED} 지금 상태에서는 할 수 없다(이미 종료·이미 진행 중 — 관리자 {@code 409 HOTDEAL_STATE_CHANGED}) */
    public enum Outcome {
        CHANGED, NOT_FOUND, STATE_CHANGED
    }

    /** 지금(마이크로초 — DB 와 같은 정밀도) */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 딜 행을 잠가 읽는다 */
    @Transactional
    public Optional<PostDealEntity> lock(long postId) {
        return deals.findWithLockByPostId(postId);
    }

    /**
     * 작성자 종료(ACTIVE·FLAGGED → ENDED AUTHOR) + 의심 구간 요청자 보상. 이미 끝났으면 아무것도 하지 않는다. 부르는 쪽이 잠근 딜을 넘긴다(작성자 확인·기간 종료 거절은 부르는 쪽).
     *
     * @return 이번에 끝냈다
     */
    @Transactional
    public boolean endByAuthor(PostDealEntity locked, Long userId, Instant now) {
        if (locked.isEnded()) {
            return false;
        }
        locked.endByAuthor(userId, now);
        rewardRequesters(locked);
        return true;
    }

    /**
     * 딜이 방금 끝났다(다른 길 — 고치기에서 재고를 품절로 저장한 작성자 종료) — 의심 구간 요청자 보상만 한다. 딜이 끝나지 않았으면 아무것도 하지 않는다.
     */
    @Transactional
    public void rewardRequesters(PostDealEntity ended) {
        if (!ended.isEnded()) {
            return;
        }
        for (DealEndRequestStore.Requester r : requests.flaggedRequesters(ended.getPostId())) {
            points.dealEndRequestReward(r.memberId(), r.requestId());
        }
    }

    /**
     * 운영진 종료(3단계 관리자 — 게시판 매니저·스텝·{@code hotdeal.manage}, 신고 사건 조치 {@code DEAL_END}) — 진행·의심·기간 지남 → ENDED MANAGER + 보상 + 작성자 알림(사유).
     *
     * @param reason 작성자에게 가는 사유(≤200자 — 검사는 부르는 쪽, 없으면 null)
     */
    @Transactional
    public Outcome endByManager(long postId, long actorId, String reason) {
        PostDealEntity d = deals.findWithLockByPostId(postId).orElse(null);
        if (d == null) {
            return Outcome.NOT_FOUND;
        }
        if (d.isEnded()) {
            return Outcome.STATE_CHANGED;
        }
        String note = reason == null || reason.isBlank() ? null : reason.strip();
        d.endByManager(actorId, note, now());
        rewardRequesters(d);
        notices.managerEnded(d, note);
        return Outcome.CHANGED;
    }

    /**
     * 자동 종료(배치 {@code hotdeal-auto-end}) — 아직 진행 중이고 의심이며 유예({@code endGraceHours}, 0 = 자동 종료 안 함)가 지났을 때만(잠근 뒤 다시 본다 — 그새 keep·종료됐으면 그대로).
     * 기간이 지난 딜도 의심이면 끝낸다(의심 구간 요청자의 보상). 보상 + 작성자 알림.
     *
     * @return 이번에 끝냈다
     */
    @Transactional
    public boolean autoEnd(long postId, Instant now) {
        PostDealEntity d = deals.findWithLockByPostId(postId).orElse(null);
        int grace = settings.current().endGraceHours();
        if (d == null || d.isEnded() || d.getFlaggedAt() == null || grace <= 0 || d.getFlaggedAt().plus(Duration.ofHours(grace)).isAfter(now)) {
            return false;
        }
        d.autoEnd(now);
        rewardRequesters(d);
        notices.autoEnded(d);
        return true;
    }

    /**
     * 운영진 재개(3단계 관리자) — ENDED → ACTIVE, 의심·keep 을 처음으로 돌리고 종료 요청 줄을 지운다(의심·요청 리셋). 재고가 품절이었으면 「모름」. 기간이 이미 지났으면 작성자에게
     * 「마감을 고쳐 주세요」({@code DEAL_REOPENED_FIX_ENDS}).
     */
    @Transactional
    public Outcome reopenByManager(long postId) {
        PostDealEntity d = deals.findWithLockByPostId(postId).orElse(null);
        if (d == null) {
            return Outcome.NOT_FOUND;
        }
        if (!d.isEnded()) {
            return Outcome.STATE_CHANGED;
        }
        Instant now = now();
        d.reopenByManager(now);
        requests.deleteForPost(postId);
        notifyIfExpired(d, now);
        return Outcome.CHANGED;
    }

    /**
     * 마감 없는 딜의 기간 종료 알림 한 번(배치 {@code hotdeal-expiry-notice}) — 저장 상태 ACTIVE · 마감 없음 · 아직 안 알림 · 기간({@code autoExpireDays}, 0 = 끔)이 지났을 때만
     * (잠근 뒤 다시 본다). 상태는 읽기 계산이라 저장 상태는 바꾸지 않고 {@code expiry_notified_at} 만 남긴다. 글이 공개 글이 아니면(지움·숨김) 알리지 않고 표시만 한다.
     *
     * @return 이번에 알렸다
     */
    @Transactional
    public boolean noticeExpired(long postId, Instant now, java.util.function.LongPredicate publicPost) {
        PostDealEntity d = deals.findWithLockByPostId(postId).orElse(null);
        int days = settings.current().autoExpireDays();
        if (d == null || d.isEnded() || d.getEndsAt() != null || d.getExpiryNotifiedAt() != null || days <= 0) {
            return false;
        }
        Instant expires = d.expiresAt(days);
        if (expires == null || now.isBefore(expires)) {
            return false;
        }
        d.markExpiryNotified(now);
        if (!publicPost.test(postId)) {
            return false;
        }
        notices.expired(d);
        return true;
    }

    /** 다시 진행 중이 됐는데 기간이 이미 지났으면 작성자에게 「마감을 고쳐 주세요」 — 작성자 되돌리기(D2)도 쓴다 */
    public void notifyIfExpired(PostDealEntity reopened, Instant now) {
        DealSettings s = settings.current();
        Instant expires = reopened.expiresAt(s.autoExpireDays());
        if (expires != null && !now.isBefore(expires)) {
            notices.reopenedFixEnds(reopened);
        }
    }
}
