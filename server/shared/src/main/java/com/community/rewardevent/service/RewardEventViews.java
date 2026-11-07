package com.community.rewardevent.service;

import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.RewardCommentDrawConfig;
import com.community.rewardevent.dto.RewardFirstComeConfig;
import com.community.rewardevent.dto.RewardLineDto;
import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.RewardOperatorRef;
import com.community.rewardevent.dto.RewardSelectionConfig;
import com.community.rewardevent.dto.RewardTierDto;
import com.community.rewardevent.dto.response.RewardEventActionsView;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.dto.response.RewardEventRowView;
import com.community.rewardevent.dto.response.RewardEventStatsView;
import com.community.rewardevent.dto.response.RewardRecipientView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventRunEntity;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardRunKind;
import com.community.rewardevent.entity.RewardRunStatus;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardEventStore.TierRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardMemberQueries.MemberRow;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientRow;
import com.community.rewardevent.repository.master.RewardRecipientStore.ResultRow;
import com.community.rewardevent.repository.master.RewardRecipientStore.StatsRow;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 보상 이벤트 응답 만들기 한 곳 — 목록 줄 · 상세(숫자·동작) · 받는 사람 · 실행. 읽기만 한다(부르는 쪽의 트랜잭션) */
@Component
public class RewardEventViews {

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;

    public RewardEventViews(RewardEventStore events, RewardRecipientStore recipients, RewardMemberQueries members) {
        this.events = events;
        this.recipients = recipients;
        this.members = members;
    }

    public RewardEventDetailView detail(RewardEventEntity e, Instant now) {
        long id = e.getId();
        List<TierRow> tiers = events.tiers(id);
        List<LineRow> lines = events.lines(id);
        StatsRow stats = recipients.stats(List.of(id)).getOrDefault(id, StatsRow.EMPTY);
        Map<Long, Long> byTier = recipients.countByTier(id);
        RewardEventRunEntity running = events.runningRun(id).orElse(null);
        boolean granted = !events.grantedEvents(List.of(id)).isEmpty();
        RewardEventStatus status = RewardEventRules.effectiveStatus(e, now);
        Map<Long, String> names = members.operatorNames(nonNull(e.getCreatedBy(), running == null ? null : running.getOperatorId()));
        Map<String, Long> recipientsByTier = new LinkedHashMap<>();
        for (TierRow t : tiers) {
            recipientsByTier.put(Long.toString(t.id()), byTier.getOrDefault(t.id(), 0L));
        }
        return new RewardEventDetailView(Long.toString(id), e.getNo(), e.getName(), e.getMemo(), e.getStartAt(), e.getEndAt(), e.getNoticeUrl(), e.getMode().name(),
                firstCome(e), selection(e), bulk(e), e.isExcludeOperators(), tierDtos(tiers, lines), status.name(), statsView(stats), recipientsByTier,
                actions(e, status, stats, tiers, byTier, running != null, granted), running == null ? null : run(running, names), e.getCancelReason(), e.getCanceledAt(),
                operator(e.getCreatedBy(), names), e.getCreatedAt(), e.getUpdatedAt());
    }

    public List<RewardEventRowView> rows(List<RewardEventEntity> page, Instant now) {
        List<Long> ids = page.stream().map(RewardEventEntity::getId).toList();
        Map<Long, List<TierRow>> tiers = events.tiersOf(ids);
        Map<Long, List<LineRow>> lines = events.linesOf(ids);
        Map<Long, StatsRow> stats = recipients.stats(ids);
        Map<Long, String> names = members.operatorNames(page.stream().map(RewardEventEntity::getCreatedBy).filter(Objects::nonNull).toList());
        return page.stream().map(e -> new RewardEventRowView(Long.toString(e.getId()), e.getNo(), e.getName(), e.getMode().name(),
                RewardEventRules.effectiveStatus(e, now).name(), e.getStartAt(), e.getEndAt(), tierDtos(tiers.getOrDefault(e.getId(), List.of()),
                lines.getOrDefault(e.getId(), List.of())), e.getFirstComeLimit(), statsView(stats.getOrDefault(e.getId(), StatsRow.EMPTY)),
                operator(e.getCreatedBy(), names), e.getCreatedAt())).toList();
    }

    /** 서버가 상태로 정한 동작 — 확정 화면의 규칙(목업 {@code actionsOf})과 같다 */
    static RewardEventActionsView actions(RewardEventEntity e, RewardEventStatus status, StatsRow stats, List<TierRow> tiers, Map<Long, Long> byTier, boolean running,
                                          boolean granted) {
        boolean open = RewardEventRules.open(status);
        boolean comment = e.getMode() == RewardEventMode.SELECTION && e.isSourceComment();
        boolean manual = e.getMode() == RewardEventMode.SELECTION && e.isSourceManual();
        boolean canGrant = false;
        String grantBlocked = null;
        if (e.getMode() == RewardEventMode.SELECTION && open && status != RewardEventStatus.DRAFT) {
            if (status != RewardEventStatus.CLOSED) {
                grantBlocked = "NOT_CLOSED";
            } else if (running) {
                grantBlocked = "RUNNING";
            } else if (stats.pending() == 0) {
                grantBlocked = "NO_PENDING";
            } else {
                canGrant = true;
            }
        } else if (e.getMode() == RewardEventMode.BULK && (status == RewardEventStatus.ACTIVE || status == RewardEventStatus.CLOSED)) {
            if (running) {
                grantBlocked = "RUNNING";
            } else if (granted) {
                grantBlocked = "NO_PENDING";
            } else {
                canGrant = true;
            }
        }
        boolean room = tiers.stream().anyMatch(t -> byTier.getOrDefault(t.id(), 0L) < t.quota());
        return new RewardEventActionsView(RewardEventRules.editScope(status).name(), status == RewardEventStatus.DRAFT, status == RewardEventStatus.ACTIVE,
                comment && status == RewardEventStatus.CLOSED && !running && room, canGrant,
                !running && status != RewardEventStatus.CANCELED && stats.failed() + stats.partial() > 0, open && !granted, manual && open && !running,
                open && granted ? "HAS_GRANTS" : null, grantBlocked);
    }

    public static RewardEventStatsView statsView(StatsRow s) {
        return new RewardEventStatsView(s.targets(), s.pending(), s.granted(), s.partial(), s.failed(), s.skipped(), s.revoked(), s.skippedLines(), s.grantedPoints(),
                s.grantedItems(), s.manualPending(), s.manualDelivered());
    }

    public static RewardFirstComeConfig firstCome(RewardEventEntity e) {
        return e.getMode() != RewardEventMode.FIRST_COME ? null : new RewardFirstComeConfig(e.getFirstComeTrigger().name(), e.getFirstComeLimit());
    }

    public RewardSelectionConfig selection(RewardEventEntity e) {
        if (e.getMode() != RewardEventMode.SELECTION) {
            return null;
        }
        List<String> sources = new ArrayList<>();
        if (e.isSourceManual()) {
            sources.add("MANUAL");
        }
        if (e.isSourceComment()) {
            sources.add("COMMENT_DRAW");
        }
        RewardCommentDrawConfig draw = null;
        if (e.isSourceComment()) {
            String title = members.postTitles(List.of(e.getDrawPostId())).get(e.getDrawPostId());
            draw = new RewardCommentDrawConfig(e.getDrawPostRef(), Long.toString(e.getDrawPostId()), title, e.getDrawFrom(), e.getDrawTo(), e.isDrawExcludeAuthor());
        }
        return new RewardSelectionConfig(sources, draw);
    }

    public static RewardBulkConfig bulk(RewardEventEntity e) {
        return e.getMode() != RewardEventMode.BULK ? null : new RewardBulkConfig(e.getBulkScope().name(), e.getBulkMinGrade(), e.getBulkJoinedFrom(), e.getBulkJoinedTo(),
                e.isBulkIdentityOnly(), e.getBulkActiveDays());
    }

    public static List<RewardTierDto> tierDtos(List<TierRow> tiers, List<LineRow> lines) {
        Map<Long, List<RewardLineDto>> byTier = new LinkedHashMap<>();
        for (LineRow l : lines) {
            byTier.computeIfAbsent(l.tierId(), k -> new ArrayList<>()).add(line(l));
        }
        return tiers.stream().map(t -> new RewardTierDto(Long.toString(t.id()), t.name(), t.quota(), byTier.getOrDefault(t.id(), List.of()))).toList();
    }

    public static RewardLineDto line(LineRow l) {
        return new RewardLineDto(Long.toString(l.id()), l.kind().name(), l.points(), l.itemId() == null ? null : Long.toString(l.itemId()), l.itemName(), l.itemKind(),
                l.itemUsage(), l.itemDurationDays(), l.quantity(), l.packId() == null ? null : Long.toString(l.packId()), l.packName(), l.manualName(), l.manualMethod());
    }

    public static RewardRunView run(RewardEventRunEntity r, Map<Long, String> names) {
        int total = r.getStatus() == RewardRunStatus.DONE ? r.getDoneCount() : Math.max(r.getTargetCount(), r.getDoneCount());
        return new RewardRunView(Long.toString(r.getId()), r.getCreatedAt(), operator(r.getOperatorId(), names), r.getKind().name(), r.getStatus().name(),
                r.getKind() == RewardRunKind.AUTO && r.getDay() != null ? r.getDay().toString() : null, Math.max(r.getTargetCount(), r.getDoneCount()), r.getSuccess(),
                r.getFailed(), r.getSkipped(), r.getPoints(), r.getItems(), new RewardRunView.Progress(r.getDoneCount(), total), r.getReason());
    }

    public List<RewardRunView> runs(List<RewardEventRunEntity> list) {
        Map<Long, String> names = members.operatorNames(list.stream().map(RewardEventRunEntity::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return list.stream().map(r -> run(r, names)).toList();
    }

    /** 받는 사람들 — 회원(이메일은 가리개를 거친다)·등수 이름·선정 운영자·줄 결과·전달 운영자 */
    public List<RewardRecipientView> recipients(long eventId, List<RecipientRow> rows, UnaryOperator<String> emailView) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tierNames = events.tiers(eventId).stream().collect(Collectors.toMap(TierRow::id, TierRow::name));
        Map<Long, MemberRow> people = members.members(rows.stream().map(RecipientRow::userId).toList());
        Map<Long, List<ResultRow>> results = recipients.resultsOf(rows.stream().map(RecipientRow::id).toList());
        Set<Long> operatorIds = new HashSet<>();
        rows.forEach(r -> {
            if (r.selectedBy() != null) {
                operatorIds.add(r.selectedBy());
            }
        });
        results.values().forEach(list -> list.forEach(x -> {
            if (x.deliveredBy() != null) {
                operatorIds.add(x.deliveredBy());
            }
        }));
        Map<Long, String> names = members.operatorNames(operatorIds);
        return rows.stream().map(r -> new RewardRecipientView(Long.toString(r.id()), member(r.userId(), people.get(r.userId()), emailView), Long.toString(r.tierId()),
                tierNames.get(r.tierId()), r.method().name(), r.selectedAt(), r.selectedBy() == null ? null : operator(r.selectedBy(), names),
                r.drawId() == null ? null : Long.toString(r.drawId()), r.status().name(),
                results.getOrDefault(r.id(), List.of()).stream().map(x -> new RewardRecipientView.Result(Long.toString(x.lineId()), x.kind().name(), x.status().name(),
                        x.reason() == null ? null : x.reason().name(), x.at(), x.revokedPoints(),
                        x.deliveredAt() == null ? null : new RewardRecipientView.Delivered(x.deliveredAt(), x.deliveredBy() == null ? null : operator(x.deliveredBy(), names),
                                x.deliveredMemo() == null ? "" : x.deliveredMemo()))).toList(),
                r.revokeReason())).toList();
    }

    public static RewardMemberRef member(long userId, MemberRow row, UnaryOperator<String> emailView) {
        if (row == null) {
            return new RewardMemberRef(Long.toString(userId), null, null, true);
        }
        return new RewardMemberRef(Long.toString(row.id()), row.nickname(), row.email() == null ? null : emailView.apply(row.email()), row.withdrawn());
    }

    public static RewardOperatorRef operator(Long id, Map<Long, String> names) {
        return id == null ? null : new RewardOperatorRef(Long.toString(id), names.get(id));
    }

    /** 표시 시간대 날짜 */
    public static String day(Instant at) {
        return at.atZone(ServiceTime.ZONE).toLocalDate().toString();
    }

    private static Collection<Long> nonNull(Long... ids) {
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
