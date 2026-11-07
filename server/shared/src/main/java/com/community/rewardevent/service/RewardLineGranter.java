package com.community.rewardevent.service;

import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore.EventGrant;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.exception.PointFrozenException;
import com.community.point.service.PointService;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.rewardevent.entity.RewardKind;
import com.community.rewardevent.entity.RewardLineReason;
import com.community.rewardevent.entity.RewardLineResultStatus;
import com.community.rewardevent.entity.RewardRecipientStatus;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardMemberQueries.MemberRow;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientRow;
import com.community.rewardevent.repository.master.RewardRecipientStore.ResultRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 받는 사람 한 명의 보상 줄 지급·회수(event-admin-contract 「보상 줄」 — 커머스). 업무 트랜잭션(덩어리 처리 · 선착순 자동 지급 · 회수 요청) 안에서 부른다.
 *
 * <ul>
 *   <li><b>줄마다 세이브포인트</b>({@link IsolatedSideWork}) — 한 줄의 실패가 다른 줄을 되돌리지 않는다. 세이브포인트 안의 쓰기는 모두 JDBC(포인트 원장 · 보유 아이템 · 로보티콘 받기 —
 *       각 저장소의 「세이브포인트 안 JDBC」 문장)라 실패해도 업무 트랜잭션은 계속 쓸 수 있다. 예상한 실패(동결 · 판매 불가 · 이미 가짐)는 던지지 않고 결과로 돌려준다 —
 *       예상하지 못한 실패만 세이브포인트를 되돌리고 {@code SERVER_ERROR}.</li>
 *   <li>탈퇴·정지 회원은 줄을 지급하지 않고 모든 대상 줄이 실패({@code WITHDRAWN}·{@code SUSPENDED}).</li>
 *   <li>포인트 줄은 마지막에 — 잔액 행 잠금이 그 사람 일의 마지막 잠금이다(commerce.md 5-4).</li>
 *   <li>같은 줄은 한 번만: 결과 칸은 대기·실패 줄만 고치고({@link RewardRecipientStore#applyResult}), 원장은 (사유, 줄 결과) 유일, 보유 아이템은 (줄 결과, 순번) 유일,
 *       로보티콘 받기는 (회원, 팩) 하나에 줄 하나.</li>
 * </ul>
 */
@Component
public class RewardLineGranter {

    /** 지급한 한 사람의 결과 — 실행 합계용(새로 지급한 포인트·아이템 · 새 직접 전달) */
    public record Granted(RewardRecipientStatus status, long points, int items, int manual) {

        /** 실행 합계의 갈래 — 실패·일부 실패 → failed · 모두 건너뜀 → skipped · 그 밖 → success */
        public int failed() {
            return status == RewardRecipientStatus.FAILED || status == RewardRecipientStatus.PARTIAL ? 1 : 0;
        }

        public int skipped() {
            return status == RewardRecipientStatus.SKIPPED ? 1 : 0;
        }

        public int success() {
            return 1 - failed() - skipped();
        }
    }

    /** 회수한 한 사람의 결과 */
    public record Revoked(long points, int items, boolean failed) {
    }

    /** 이벤트 맥락 — 원장·알림에 쓰는 이름, 지급한 운영자(자동이면 null) */
    public record EventContext(long eventId, String name, Long operatorId) {
    }

    private record Outcome(RewardLineResultStatus status, RewardLineReason reason, int points, int items) {

        static Outcome failed(RewardLineReason reason) {
            return new Outcome(RewardLineResultStatus.FAILED, reason, 0, 0);
        }

        static Outcome skipped(RewardLineReason reason) {
            return new Outcome(RewardLineResultStatus.SKIPPED, reason, 0, 0);
        }
    }

    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;
    private final PointService points;
    private final PointShopItemStore items;
    private final UserItemStore userItems;
    private final EmoticonPackStore packs;
    private final EmoticonAcquisitionStore acquisitions;
    private final IsolatedSideWork isolated;
    private final SnowflakeIdGenerator ids;
    private final MemberNotificationPublisher memberNotices;

    public RewardLineGranter(RewardRecipientStore recipients, RewardMemberQueries members, PointService points, PointShopItemStore items, UserItemStore userItems,
                             EmoticonPackStore packs, EmoticonAcquisitionStore acquisitions, IsolatedSideWork isolated, SnowflakeIdGenerator ids,
                             MemberNotificationPublisher memberNotices) {
        this.recipients = recipients;
        this.members = members;
        this.points = points;
        this.items = items;
        this.userItems = userItems;
        this.packs = packs;
        this.acquisitions = acquisitions;
        this.isolated = isolated;
        this.ids = ids;
        this.memberNotices = memberNotices;
    }

    /**
     * 한 사람의 줄을 지급한다 — 대기 줄(재시도면 실패 줄)만. 결과 칸·받는 사람 상태를 고치고 대기열에서 내린 뒤, 새로 받은 것이 있으면 회원 알림.
     *
     * @param lines 그 이벤트의 보상 줄(번호 → 줄)
     */
    public Granted grant(EventContext event, RecipientRow recipient, Map<Long, LineRow> lines, boolean retry, Instant now) {
        List<ResultRow> all = recipients.results(recipient.id());
        RewardLineResultStatus target = retry ? RewardLineResultStatus.FAILED : RewardLineResultStatus.PENDING;
        List<ResultRow> todo = new ArrayList<>(all.stream().filter(r -> r.status() == target).toList());
        todo.sort(Comparator.comparing((ResultRow r) -> r.kind() == RewardKind.POINT).thenComparing(ResultRow::id)); // 포인트 줄이 마지막
        Optional<MemberRow> member = members.member(recipient.userId());
        RewardLineReason blocked = member.isEmpty() || member.get().withdrawn() ? RewardLineReason.WITHDRAWN
                : member.get().suspended() ? RewardLineReason.SUSPENDED : null;
        long newPoints = 0;
        int newItems = 0;
        int newManual = 0;
        for (ResultRow result : todo) {
            LineRow line = lines.get(result.lineId());
            Outcome outcome = blocked != null ? Outcome.failed(blocked)
                    : line == null ? Outcome.failed(RewardLineReason.SERVER_ERROR)
                    : line.kind() == RewardKind.MANUAL ? new Outcome(RewardLineResultStatus.DELIVERY_PENDING, null, 0, 0)
                    : isolated.run("reward-event:line", () -> grantLine(event, recipient.userId(), result.id(), line, now), Outcome.failed(RewardLineReason.SERVER_ERROR));
            if (recipients.applyResult(result.id(), outcome.status(), outcome.reason(), outcome.points(), outcome.items(), now)) {
                if (outcome.status() == RewardLineResultStatus.SUCCESS) {
                    newPoints += outcome.points();
                    newItems += outcome.items();
                } else if (outcome.status() == RewardLineResultStatus.DELIVERY_PENDING) {
                    newManual++;
                }
            }
        }
        RewardRecipientStatus status = settle(recipients.results(recipient.id()).stream().map(ResultRow::status).toList());
        recipients.settle(recipient.id(), status);
        if (newPoints > 0 || newItems > 0 || newManual > 0) {
            memberNotices.rewardEventGranted(recipient.userId(), event.eventId(), event.name(), newPoints, newItems, newManual);
        }
        return new Granted(status, newPoints, newItems, newManual);
    }

    /** 한 줄 지급(세이브포인트 안) — 예상한 실패는 결과로 */
    private Outcome grantLine(EventContext event, long userId, long resultId, LineRow line, Instant now) {
        return switch (line.kind()) {
            case POINT -> {
                try {
                    points.eventReward(userId, resultId, line.points(), event.name(), event.operatorId());
                    yield new Outcome(RewardLineResultStatus.SUCCESS, null, line.points(), 0);
                } catch (PointFrozenException e) {
                    yield Outcome.failed(RewardLineReason.POINT_FROZEN); // 잔액 행을 잡기 전에 던진다 — 쓴 것이 없다
                }
            }
            case SHOP_ITEM -> grantItem(userId, resultId, line, now);
            case EMOTICON_PACK -> grantPack(userId, resultId, line, now);
            case MANUAL -> new Outcome(RewardLineResultStatus.DELIVERY_PENDING, null, 0, 0);
        };
    }

    /** 포인트 상점 아이템 — 영구는 이미 가졌으면 건너뜀, 소모형은 수량만큼, 기간제는 지급 순간부터(같은 품목 기간이 남았으면 앞 줄 끝에서) */
    private Outcome grantItem(long userId, long resultId, LineRow line, Instant now) {
        Optional<ItemRow> found = items.find(line.itemId());
        if (found.isEmpty() || found.get().kind() == PointShopItemKind.PROFILE_AVATAR) {
            return Outcome.failed(RewardLineReason.ITEM_UNAVAILABLE);
        }
        ItemRow item = found.get();
        PointShopUsageType usage = item.usageType();
        if (usage == PointShopUsageType.PERMANENT) {
            if (userItems.ownsPermanent(userId, item.id()) || userItems.grantFromEvent(ids.nextId(), userId, item.id(), resultId, 1, usage, now, null, now) == 0) {
                return Outcome.skipped(RewardLineReason.ALREADY_OWNED);
            }
            return new Outcome(RewardLineResultStatus.SUCCESS, null, 0, 1);
        }
        if (usage == PointShopUsageType.PERIOD) {
            if (item.durationDays() == null || item.durationDays() <= 0) {
                return Outcome.failed(RewardLineReason.ITEM_UNAVAILABLE);
            }
            Instant startsAt = userItems.periodEnd(userId, item.id(), now).filter(end -> end.isAfter(now)).orElse(now);
            userItems.grantFromEvent(ids.nextId(), userId, item.id(), resultId, 1, usage, startsAt, startsAt.plus(Duration.ofDays(item.durationDays())), now);
            return new Outcome(RewardLineResultStatus.SUCCESS, null, 0, 1);
        }
        int quantity = line.quantity() == null ? 1 : Math.max(1, Math.min(10, line.quantity()));
        for (int seq = 1; seq <= quantity; seq++) {
            userItems.grantFromEvent(ids.nextId(), userId, item.id(), resultId, seq, usage, now, null, now);
        }
        return new Outcome(RewardLineResultStatus.SUCCESS, null, 0, quantity);
    }

    /** 로보티콘 팩 — 판매 중인 운영 팩·무료 팩만(작가 유료 팩은 정산 때문에 거부), 이미 가진 팩(작가 자기 팩 포함)은 건너뜀. 작가 받기 보상 포인트는 없다 */
    private Outcome grantPack(long userId, long resultId, LineRow line, Instant now) {
        Optional<PackRow> found = packs.find(line.packId());
        if (found.isEmpty() || found.get().status() != EmoticonPackStatus.APPROVED || found.get().source() == EmoticonPackSource.CREATOR && found.get().price() > 0) {
            return Outcome.failed(RewardLineReason.ITEM_UNAVAILABLE);
        }
        if (acquisitions.creatorOwns(userId, line.packId())) {
            return Outcome.skipped(RewardLineReason.PACK_ALREADY_OWNED);
        }
        EventGrant granted = acquisitions.grantFromEvent(ids.nextId(), userId, line.packId(), resultId, now);
        return granted == EventGrant.ALREADY_OWNED ? Outcome.skipped(RewardLineReason.PACK_ALREADY_OWNED) : new Outcome(RewardLineResultStatus.SUCCESS, null, 0, 1);
    }

    /**
     * 한 사람을 회수한다 — 성공 줄: 포인트는 무상에서만 0P 까지(덜 회수되면 {@code POINT_PARTIAL}), 아이템은 안 쓴 줄만(쓴 줄이 있으면 {@code ITEM_USED} — 안 쓴 줄만 회수),
     * 팩은 이벤트로 받은 줄만 뺀다. 전달 대기 줄은 회수됨, 전달 완료 줄은 기록만({@code MANUAL_RECORD_ONLY}). 받는 사람 상태 REVOKED + 회원 알림.
     */
    public Revoked revoke(EventContext event, RecipientRow recipient, String reason, long operatorId, Instant now) {
        long revokedPoints = 0;
        int revokedItems = 0;
        boolean failed = false;
        List<ResultRow> rows = new ArrayList<>(recipients.results(recipient.id()));
        rows.sort(Comparator.comparing((ResultRow r) -> r.kind() == RewardKind.POINT).thenComparing(ResultRow::id)); // 포인트 줄이 마지막
        for (ResultRow result : rows) {
            if (result.status() == RewardLineResultStatus.DELIVERY_PENDING) {
                recipients.revokeResult(result.id(), null, null, now);
                continue;
            }
            if (result.status() == RewardLineResultStatus.DELIVERED) {
                recipients.markReason(result.id(), RewardLineReason.MANUAL_RECORD_ONLY, now);
                continue;
            }
            if (result.status() != RewardLineResultStatus.SUCCESS) {
                continue;
            }
            switch (result.kind()) {
                case POINT -> {
                    Long taken = isolated.run("reward-event:revoke-point",
                            () -> points.eventRewardRevoke(recipient.userId(), result.id(), event.name(), operatorId), null);
                    if (taken == null) {
                        failed = true;
                        continue;
                    }
                    int applied = (int) Math.min(taken, result.points());
                    recipients.revokeResult(result.id(), applied, applied < result.points() ? RewardLineReason.POINT_PARTIAL : null, now);
                    revokedPoints += applied;
                }
                case SHOP_ITEM -> {
                    Integer done = isolated.run("reward-event:revoke-item", () -> {
                        boolean used = userItems.grantedUsed(result.id());
                        int n = userItems.revokeGrantedUnused(result.id(), now);
                        return used ? -1 - n : n;
                    }, null);
                    if (done == null) {
                        failed = true;
                    } else if (done < 0) {
                        recipients.markReason(result.id(), RewardLineReason.ITEM_USED, now); // 쓴 줄은 회수하지 못한다(안 쓴 줄만 회수했다)
                        revokedItems += -1 - done;
                    } else {
                        recipients.revokeResult(result.id(), null, null, now);
                        revokedItems += done;
                    }
                }
                case EMOTICON_PACK -> {
                    Integer done = isolated.run("reward-event:revoke-pack", () -> acquisitions.revokeEventGrant(result.id(), now), null);
                    if (done == null) {
                        failed = true;
                    } else {
                        recipients.revokeResult(result.id(), null, null, now);
                        revokedItems += done;
                    }
                }
                case MANUAL -> {
                }
            }
        }
        recipients.revoke(recipient.id(), reason, operatorId, now);
        memberNotices.rewardEventRevoked(recipient.userId(), event.eventId(), event.name(), reason);
        return new Revoked(revokedPoints, revokedItems, failed);
    }

    /** 줄 결과 → 받는 사람 상태(대기가 남으면 대기 · 실패가 있으면 일부 실패/실패 · 받은 것이 없으면 건너뜀 · 그 밖 완료) */
    public static RewardRecipientStatus settle(List<RewardLineResultStatus> results) {
        if (results.stream().anyMatch(s -> s == RewardLineResultStatus.PENDING)) {
            return RewardRecipientStatus.PENDING;
        }
        long ok = results.stream().filter(s -> s == RewardLineResultStatus.SUCCESS || s == RewardLineResultStatus.DELIVERY_PENDING || s == RewardLineResultStatus.DELIVERED).count();
        long failed = results.stream().filter(s -> s == RewardLineResultStatus.FAILED).count();
        if (failed > 0) {
            return ok > 0 ? RewardRecipientStatus.PARTIAL : RewardRecipientStatus.FAILED;
        }
        return ok == 0 ? RewardRecipientStatus.SKIPPED : RewardRecipientStatus.GRANTED;
    }
}
