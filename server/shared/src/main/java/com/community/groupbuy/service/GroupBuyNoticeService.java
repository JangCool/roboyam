package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyNoticeStore;
import com.community.groupbuy.repository.master.GroupBuyNoticeStore.NoticeRow;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.OptionRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품정보제공고시 양식 저장·검증(계약 결정 87 — 공정위 「전자상거래 등에서의 상품 등의 정보제공에 관한 고시」).
 *
 * <ul>
 *   <li>공구마다 양식 여러 장(품목별로 따로 — 기본 상품 + 별개 상품 옵션, 일반원칙 ⑦). 적용 옵션이 빈 양식은 공구 전체 = 기본 상품이고 다른 양식에 없는 옵션을 덮는다(하나까지).
 *       한 옵션은 양식 하나에만(DB 유일 + 저장 검사).</li>
 *   <li>저장은 통째로 바꾼다. 각 양식은 만든 때의 카탈로그 버전을 그대로 쓴다(같은 자리·같은 품목이면 버전 유지, 새 양식은 지금 버전) — 고시가 바뀌어도 기존 공구는 옛 항목.</li>
 *   <li><b>완성 검사</b>({@link #assertComplete}): 양식이 하나 이상이고, 모든 양식의 모든 항목이 「값」 또는 「제공 불가 + 구체적 사유」(일반원칙 ③), 옵션이 있으면 모든 옵션이 어느 양식에 속한다.
 *       아니면 409 {@code GROUP_BUY_NOTICE_INCOMPLETE}(빈 양식·항목·속하지 않은 옵션을 함께). 모집 시작(공개) 때와, 공개 뒤 저장 때 쓴다(공개된 고시를 비울 수 없다).</li>
 *   <li>공개 뒤 수정은 정보 정정으로 받고(결정 87) 모집 중 이후면 수정 이력(표시·광고 기록 6개월)에 사본을 남긴다.</li>
 *   <li>「상세페이지 참조」만 적은 항목은 막지 않되 경고로 돌려준다(일반원칙 ③ 취지 — 항목마다 직접 적기 권장).</li>
 * </ul>
 */
@Service
public class GroupBuyNoticeService {

    public static final int NOTICES_MAX = 20;
    public static final int VALUE_MAX = 2000;
    public static final int REASON_MAX = 1000;
    public static final int TITLE_MAX = 60;
    /** 「상세페이지 참조」 경고를 띄우는 문구(공백 무시) */
    static final List<String> DETAIL_PAGE_PHRASES = List.of("상세페이지참조", "상세페이지참고", "상세설명참조", "상세정보참조", "상품상세참조");

    /** 비었거나 빠진 것 — 양식 차례(0부터)·제목·품목·빈 항목 키 */
    public record IncompleteNotice(int index, String title, String categoryCode, List<String> missingKeys) {
    }

    /** 완성 검사 결과 */
    public record Completeness(boolean noNotice, List<IncompleteNotice> notices, List<String> unassignedOptions) {

        public boolean complete() {
            return !noNotice && notices.isEmpty() && unassignedOptions.isEmpty();
        }
    }

    /** 「상세페이지 참조」만 적은 항목 경고 */
    public record DetailPageWarning(int index, String itemKey) {
    }

    private final GroupBuyNoticeStore store;
    private final GroupBuyStore groups;
    private final GroupBuyNoticeCatalog catalog;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuyNoticeService(GroupBuyNoticeStore store, GroupBuyStore groups, GroupBuyNoticeCatalog catalog, AuditLogService audit, SnowflakeIdGenerator ids,
                                 PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.groups = groups;
        this.catalog = catalog;
        this.audit = audit;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    public List<NoticeRow> notices(long groupBuyId) {
        return store.notices(groupBuyId);
    }

    /**
     * 양식을 통째로 저장한다 — 관리자 공구 등록·수정의 「고시 양식」 구획.
     *
     * @return 「상세페이지 참조」 경고(막지 않는다)
     * @throws ValidationException 품목·항목·옵션·제공 불가 사유·길이
     * @throws GroupBuyException   {@code NOTICE_INCOMPLETE} — 공개 뒤인데 빈 항목·속하지 않은 옵션이 남는다
     */
    public List<DetailPageWarning> save(long groupBuyId, List<NoticeDraft> drafts, Long operatorId, String ip) {
        return tx.execute(status -> {
            Instant now = now();
            GroupBuyRow group = groups.lock(groupBuyId).orElseThrow(GroupBuyException::notFound);
            List<OptionRow> options = groups.options(groupBuyId);
            List<NoticeRow> existing = store.notices(groupBuyId);
            List<Prepared> prepared = prepare(drafts == null ? List.of() : drafts, existing, options);
            if (group.publishedAt() != null) {
                Completeness completeness = completeness(prepared, options);
                if (!completeness.complete()) {
                    throw incomplete(completeness);
                }
            }
            store.deleteAll(groupBuyId);
            insert(groupBuyId, prepared, now);
            if (group.status() != GroupBuyStatus.DRAFT) {
                groups.snapshotRevision(ids.nextId(), groupBuyId, groups.nextRevision(groupBuyId, now), "NOTICE", operatorId, now);
            }
            audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, AuditActions.GROUPBUY_UPDATE,
                    AuditResult.SUCCESS, "GROUP_BUY", Long.toString(groupBuyId), ip, Map.of("stage", "NOTICE", "notices", prepared.size())));
            return warnings(prepared);
        });
    }

    /** 지금 저장된 양식의 완성 검사 */
    public Completeness completeness(long groupBuyId) {
        List<OptionRow> options = groups.options(groupBuyId);
        List<Prepared> rows = store.notices(groupBuyId).stream().map(n -> new Prepared(n.categoryCode(), n.title(), n.catalogVersion(), n.optionNames(),
                n.values())).toList();
        return completeness(rows, options);
    }

    /** 모집 시작 때 — 완성이 아니면 409 {@code GROUP_BUY_NOTICE_INCOMPLETE} */
    public void assertComplete(long groupBuyId) {
        Completeness result = completeness(groupBuyId);
        if (!result.complete()) {
            throw incomplete(result);
        }
    }

    /** 추가 차수 복사 — 양식·값·카탈로그 버전 그대로, 적용 옵션은 옵션 이름으로 새 공구의 옵션에 잇는다. 부르는 쪽 트랜잭션 안에서 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void copy(long sourceId, long targetId, Instant now) {
        List<Prepared> rows = store.notices(sourceId).stream().map(n -> new Prepared(n.categoryCode(), n.title(), n.catalogVersion(), n.optionNames(),
                n.values())).toList();
        insert(targetId, rows, now);
    }

    /** DRAFT 의 옵션을 다시 넣은 뒤 — 양식의 적용 옵션을 옵션 이름으로 다시 잇는다(이름이 사라진 옵션은 빠진다) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void relinkOptions(long groupBuyId, Map<Long, List<String>> namesByNotice) {
        Map<String, Long> byName = new HashMap<>();
        groups.options(groupBuyId).forEach(o -> byName.put(o.name(), o.id()));
        namesByNotice.forEach((noticeId, names) -> names.stream().map(byName::get).filter(java.util.Objects::nonNull)
                .forEach(optionId -> store.insertOption(noticeId, groupBuyId, optionId)));
    }

    /** 저장 직전의 양식 한 장(검사를 마친 값) */
    record Prepared(String categoryCode, String title, String catalogVersion, List<String> optionNames, Map<String, GroupBuyNoticeStore.ValueRow> values) {
    }

    private List<Prepared> prepare(List<NoticeDraft> drafts, List<NoticeRow> existing, List<OptionRow> options) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        if (drafts.size() > NOTICES_MAX) {
            f.put("notices", new MessageRef("groupbuy.notice.tooMany"));
        }
        Set<String> optionNames = new HashSet<>();
        options.forEach(o -> optionNames.add(o.name()));
        Set<String> usedOptions = new HashSet<>();
        int baseNotices = 0;
        List<Prepared> out = new ArrayList<>();
        for (int i = 0; i < drafts.size() && f.isEmpty(); i++) {
            NoticeDraft d = drafts.get(i);
            String field = "notices[" + i + "]";
            if (d == null) {
                f.put(field, new MessageRef("groupbuy.notice.invalid"));
                break;
            }
            String version = i < existing.size() && existing.get(i).categoryCode().equals(d.categoryCode()) ? existing.get(i).catalogVersion()
                    : catalog.current().version();
            Optional<GroupBuyNoticeCatalog.Category> category = catalog.version(version).flatMap(v -> v.category(d.categoryCode() == null ? "" : d.categoryCode()));
            if (category.isEmpty()) {
                f.put(field + ".categoryCode", new MessageRef("groupbuy.notice.category.unknown"));
                break;
            }
            if (d.title() == null || d.title().isBlank() || d.title().strip().length() > TITLE_MAX) {
                f.put(field + ".title", new MessageRef("groupbuy.notice.title.invalid"));
                break;
            }
            List<String> names = d.optionNames() == null ? List.of() : d.optionNames().stream().map(String::strip).toList();
            if (names.isEmpty()) {
                baseNotices++;
            }
            for (String name : names) {
                if (!optionNames.contains(name)) {
                    f.put(field + ".optionNames", new MessageRef("groupbuy.notice.option.unknown"));
                } else if (!usedOptions.add(name)) {
                    f.put(field + ".optionNames", new MessageRef("groupbuy.notice.option.duplicate")); // 한 옵션은 양식 하나에만
                }
            }
            Map<String, GroupBuyNoticeStore.ValueRow> values = new LinkedHashMap<>();
            for (NoticeDraft.Value v : d.values() == null ? List.<NoticeDraft.Value>of() : d.values()) {
                if (v == null || v.itemKey() == null || category.get().item(v.itemKey()).isEmpty() || values.containsKey(v.itemKey())) {
                    f.put(field + ".values", new MessageRef("groupbuy.notice.item.unknown"));
                    break;
                }
                String reason = v.unavailableReason() == null || v.unavailableReason().isBlank() ? null : v.unavailableReason().strip();
                String value = v.value() == null || v.value().isBlank() ? null : v.value().strip();
                if (v.unavailable() && reason == null) {
                    f.put(field + ".values." + v.itemKey(), new MessageRef("groupbuy.notice.unavailable.reasonRequired")); // 일반원칙 ③
                    break;
                }
                if ((value != null && value.length() > VALUE_MAX) || (reason != null && reason.length() > REASON_MAX)) {
                    f.put(field + ".values." + v.itemKey(), new MessageRef("groupbuy.notice.value.tooLong"));
                    break;
                }
                values.put(v.itemKey(), v.unavailable() ? new GroupBuyNoticeStore.ValueRow(v.itemKey(), null, true, reason)
                        : new GroupBuyNoticeStore.ValueRow(v.itemKey(), value, false, null));
            }
            out.add(new Prepared(d.categoryCode(), d.title().strip(), version, names, values));
        }
        if (f.isEmpty() && baseNotices > 1 && !options.isEmpty()) {
            f.put("notices", new MessageRef("groupbuy.notice.base.duplicate")); // 공구 전체(기본 상품) 양식은 하나
        }
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        return out;
    }

    private Completeness completeness(List<Prepared> notices, List<OptionRow> options) {
        List<IncompleteNotice> incomplete = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        boolean base = false;
        for (int i = 0; i < notices.size(); i++) {
            Prepared n = notices.get(i);
            assigned.addAll(n.optionNames());
            base |= n.optionNames().isEmpty();
            GroupBuyNoticeCatalog.Category category = catalog.version(n.catalogVersion()).flatMap(v -> v.category(n.categoryCode()))
                    .orElseThrow(() -> new IllegalStateException("양식의 카탈로그 품목이 없다: " + n.catalogVersion() + "/" + n.categoryCode()));
            List<String> missing = new ArrayList<>();
            for (GroupBuyNoticeCatalog.Item item : category.items()) {
                GroupBuyNoticeStore.ValueRow v = n.values().get(item.key());
                boolean filled = v != null && ((v.unavailable() && v.unavailableReason() != null && !v.unavailableReason().isBlank())
                        || (!v.unavailable() && v.value() != null && !v.value().isBlank()));
                if (item.required() && !filled) {
                    missing.add(item.key());
                }
            }
            if (!missing.isEmpty()) {
                incomplete.add(new IncompleteNotice(i, n.title(), n.categoryCode(), missing));
            }
        }
        List<String> unassigned = base ? List.of() : options.stream().map(OptionRow::name).filter(name -> !assigned.contains(name)).toList();
        return new Completeness(notices.isEmpty(), incomplete, unassigned);
    }

    private void insert(long groupBuyId, List<Prepared> notices, Instant now) {
        Map<String, Long> byName = new HashMap<>();
        groups.options(groupBuyId).forEach(o -> byName.put(o.name(), o.id()));
        for (int i = 0; i < notices.size(); i++) {
            Prepared n = notices.get(i);
            long id = ids.nextId();
            store.insertNotice(id, groupBuyId, i, n.categoryCode(), n.title(), n.catalogVersion(), now);
            for (String name : n.optionNames()) {
                Long optionId = byName.get(name);
                if (optionId != null) {
                    store.insertOption(id, groupBuyId, optionId);
                }
            }
            for (GroupBuyNoticeStore.ValueRow v : n.values().values()) {
                store.insertValue(id, v.itemKey(), v.value(), v.unavailable(), v.unavailableReason());
            }
        }
    }

    private static List<DetailPageWarning> warnings(List<Prepared> notices) {
        List<DetailPageWarning> out = new ArrayList<>();
        for (int i = 0; i < notices.size(); i++) {
            for (GroupBuyNoticeStore.ValueRow v : notices.get(i).values().values()) {
                if (!v.unavailable() && v.value() != null && DETAIL_PAGE_PHRASES.contains(v.value().replaceAll("[\\s.。]", ""))) {
                    out.add(new DetailPageWarning(i, v.itemKey()));
                }
            }
        }
        return out;
    }

    static GroupBuyException incomplete(Completeness result) {
        List<Map<String, Object>> notices = new ArrayList<>();
        for (IncompleteNotice n : result.notices()) {
            notices.add(Map.of("index", n.index(), "title", n.title(), "categoryCode", n.categoryCode(), "missingKeys", n.missingKeys()));
        }
        return GroupBuyException.noticeIncomplete(result.noNotice(), notices, result.unassignedOptions());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
