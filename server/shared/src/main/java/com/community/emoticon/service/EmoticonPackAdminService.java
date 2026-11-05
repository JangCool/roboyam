package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.dto.request.EmoticonOfficialPackSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonReviewKind;
import com.community.emoticon.entity.EmoticonSaleWish;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.repository.master.EmoticonImageStore;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.ItemRow;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonPackStore.RevisionRow;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPriceEventStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.emoticon.repository.master.EmoticonReviewStore;
import com.community.emoticon.repository.master.EmoticonReviewStore.ReviewRow;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsRow;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로보티콘 팩 관리(관리자 — admin-web {@code useEmoticonAdminApi} 의 팩·운영 팩·작가 요약, S1). 조회 {@code emoticon.read}, 판매 중단·다시 판매·운영 팩 {@code emoticon.review}.
 *
 * <ul>
 *   <li><b>목록·상세</b>: 임시 팩은 빠진다. 보이는 판 = 상점 판(없으면 마지막으로 낸 판). 운영 팩이 위, 그다음 최근 고친 순. 받기 보상 합계는 작가 팩만(운영 팩 null),
 *       판매 칸은 null(실결제 S3). 상세에 작가의 유료 판매 준비({@code paidReady} — 지금 판 계약 동의 · 정산 정보)와 가격 전환 기록({@code priceEvents}, 최근이 앞 — S2).</li>
 *   <li><b>가격 바꾸기</b>(S2 — {@link #changePrice}): 판매 중 작가 팩의 유료↔무료 · 단계 바꾸기, 사유 · 기록 · 감사 · 작가 알림.</li>
 *   <li><b>판매 중단</b> {@code { reason }}: 사유 5~200자 → 없는 팩 404 → 판매 중이 아니면 {@code 409 EMOTICON_PACK_NOT_ON_SALE}. 이미 가진 사람은 계속 쓴다(카탈로그에 남는다).
 *       작가 팩이면 작가 알림(사유). <b>다시 판매</b>: 작가 탈퇴로 멈춘 팩이면 {@code 409 EMOTICON_PACK_CREATOR_WITHDRAWN}(후속 결정 6 — 가격 바꾸기도 같은 코드) →
 *       판매 중단이 아니면 {@code 409 EMOTICON_PACK_NOT_SUSPENDED}. 목록·상세의 {@code suspendKind}(OPERATOR·CREATOR_WITHDRAWN) · 상세의 {@code resumable}(다시 판매 단추).</li>
 *   <li><b>운영 팩 만들기·고치기</b>: 칸 검사(이름 2~20자 · 태그 5개 · 개수 설정 최소~최대 · 이름표 1~10자·겹침 없음 · 대표 필수 — admin-web 목업 {@code officialFields}) → (고치기) 없는 팩 404 ·
 *       운영 팩이 아니면 {@code 409 EMOTICON_PACK_NOT_OFFICIAL} → 대표·로보티콘이 지금 것이거나 운영자가 올린 것인지({@code fields.cover}·{@code fields.items}). 만들기는 심사 없이 판매 중,
 *       고치기는 새 판(판 번호 + 1)이 바로 상점 판이 된다(판매 중단이면 그대로 판매 중단). 로보티콘 ID 는 판이 바뀌어도 그대로(지금 로보티콘 ID 로 가리킨 칸은 그 로보티콘,
 *       올린 그림은 같은 지문이면 그 로보티콘, 아니면 새 ID). 감사.</li>
 * </ul>
 */
@Service
public class EmoticonPackAdminService {

    public static final String TARGET = "EMOTICON_PACK";

    private final EmoticonPackStore packs;
    private final EmoticonReviewStore reviews;
    private final EmoticonImageStore images;
    private final EmoticonReadStore reads;
    private final EmoticonSettingsService settings;
    private final EmoticonContent content;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;
    private final EmoticonImageService imageService;
    private final EmoticonContractService contracts;
    private final EmoticonPayoutStore payouts;
    private final EmoticonPriceEventStore priceEvents;
    private final EmoticonPurchaseStore purchases;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonPackAdminService(EmoticonPackStore packs, EmoticonReviewStore reviews, EmoticonImageStore images, EmoticonReadStore reads,
                                    EmoticonSettingsService settings, EmoticonContent content, AuditLogService audit, MemberNotificationPublisher memberNotices,
                                    EmoticonImageService imageService, EmoticonContractService contracts, EmoticonPayoutStore payouts,
                                    EmoticonPriceEventStore priceEvents, EmoticonPurchaseStore purchases, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.purchases = purchases;
        this.contracts = contracts;
        this.payouts = payouts;
        this.priceEvents = priceEvents;
        this.imageService = imageService;
        this.packs = packs;
        this.reviews = reviews;
        this.images = images;
        this.reads = reads;
        this.settings = settings;
        this.content = content;
        this.audit = audit;
        this.memberNotices = memberNotices;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EmoticonAdminView.PackList list(String sourceText, String statusText, String qText, String creatorText) {
        EmoticonPackSource source = "OFFICIAL".equals(sourceText) ? EmoticonPackSource.OFFICIAL : "CREATOR".equals(sourceText) ? EmoticonPackSource.CREATOR : null;
        EmoticonPackStatus status = null;
        if (statusText != null) {
            for (EmoticonPackStatus s : EmoticonPackStatus.values()) {
                if (s != EmoticonPackStatus.DRAFT && s.name().equals(statusText)) {
                    status = s;
                }
            }
        }
        String q = qText == null || qText.isBlank() ? null : qText.trim();
        Long creator = null;
        if (creatorText != null && !creatorText.isBlank()) {
            long parsed = EmoticonShopService.parse(creatorText.trim());
            if (parsed < 0) {
                return new EmoticonAdminView.PackList(List.of());
            }
            creator = parsed;
        }
        List<Long> found = reads.adminPacks(source, status, q, creator);
        List<PackRow> rows = new ArrayList<>();
        Map<Long, PackRow> byId = new HashMap<>();
        packs.byIds(found).forEach(p -> byId.put(p.id(), p));
        for (Long id : found) {
            if (byId.containsKey(id)) {
                rows.add(byId.get(id));
            }
        }
        return new EmoticonAdminView.PackList(details(rows, false).stream().map(EmoticonPackAdminService::row).toList());
    }

    @Transactional(readOnly = true)
    public EmoticonAdminView.PackDetail detail(String idText) {
        PackRow p = find(idText);
        return details(List.of(p), true).get(0);
    }

    /** 회원 상세의 작가 한 줄 — 작가가 아니면(낸 팩이 없으면) null */
    @Transactional(readOnly = true)
    public EmoticonAdminView.CreatorSummary creator(String memberNoText) {
        long memberNo = EmoticonShopService.parse(memberNoText);
        if (memberNo < 0) {
            return null;
        }
        int count = reads.creatorPackCount(memberNo);
        if (count == 0) {
            return null;
        }
        return new EmoticonAdminView.CreatorSummary(Long.toString(memberNo), count, reads.rewardSum(memberNo, null), purchases.creatorPaidAmount(memberNo));
    }

    // ── 판매 중단 · 다시 판매 ───────────────────────────────────────────────

    @Transactional
    public EmoticonAdminView.PackDetail suspend(long operatorId, String ip, String idText, String reasonText) {
        String reason = reason(reasonText);
        PackRow p = lock(idText);
        if (p.status() != EmoticonPackStatus.APPROVED) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_NOT_ON_SALE);
        }
        Instant now = clock.instant();
        packs.suspend(p.id(), reason, operatorId, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_PACK_SUSPEND, AuditResult.SUCCESS).target(TARGET, Long.toString(p.id())).with("reason", reason));
        if (p.creatorId() != null) {
            memberNotices.emoticonPack(p.creatorId(), "SUSPENDED", p.id(), liveName(p), reason);
        }
        return detail(Long.toString(p.id()));
    }

    @Transactional
    public EmoticonAdminView.PackDetail resume(long operatorId, String ip, String idText) {
        PackRow p = lock(idText);
        if (p.creatorWithdrawn()) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_CREATOR_WITHDRAWN); // 작가 탈퇴로 멈춘 팩은 되살리지 않는다(후속 결정 6)
        }
        if (p.status() != EmoticonPackStatus.SUSPENDED) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_NOT_SUSPENDED);
        }
        packs.resume(p.id(), clock.instant());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_PACK_RESUME, AuditResult.SUCCESS).target(TARGET, Long.toString(p.id())));
        if (p.creatorId() != null) {
            memberNotices.emoticonPack(p.creatorId(), "RESUMED", p.id(), liveName(p), null);
        }
        return detail(Long.toString(p.id()));
    }

    // ── 운영 팩 ─────────────────────────────────────────────────────────────

    /**
     * 운영 팩 로보티콘 그림 올리기 — 검사는 작가 올리기와 같고(규격을 어기면 {@code 400 fields.file} = 이유 코드), GIF 한 번만 재생·권장 용량 넘음은 받아 둔다(화면이 경고).
     * 저장 전까지는 팩에 붙지 않는다. 이름표 처음 값은 파일 이름(확장자 뺀 앞 10자). 트랜잭션 밖에서 돈다.
     */
    public EmoticonAdminView.ItemView uploadItem(long operatorId, MultipartFile file) {
        ImageRow row = imageService.uploadByOperator(operatorId, file, EmoticonImageKind.EMOTICON);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replaceAll("\\.[^.]+$", "").trim();
        String alt = name.length() > EmoticonSpec.LABEL_MAX ? name.substring(0, EmoticonSpec.LABEL_MAX) : name;
        return content.adminItem(Long.toString(row.id()), row, alt);
    }

    /** 운영 팩 대표 이미지 올리기 — 200×200 PNG · 1MB({@code 400 fields.cover} = 이유 코드). 저장 때 {@code coverId} 로 붙인다 */
    public EmoticonAdminView.CoverView uploadCover(long operatorId, MultipartFile file) {
        return content.adminCover(imageService.uploadByOperator(operatorId, file, EmoticonImageKind.COVER));
    }

    @Transactional
    public EmoticonAdminView.PackDetail createOfficial(long operatorId, String ip, EmoticonOfficialPackSaveRequest body) {
        Official o = officialFields(body, settings.current());
        Resolved resolved = resolve(null, o);
        Instant now = clock.instant();
        long packId = ids.nextId();
        long revisionId = ids.nextId();
        packs.insert(packId, EmoticonPackSource.OFFICIAL, null, EmoticonPackStatus.APPROVED, revisionId, now, now);
        writeSealed(packId, revisionId, 1, o, resolved, null, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_PACK_CREATE, AuditResult.SUCCESS).target(TARGET, Long.toString(packId))
                .with("name", o.name()).with("count", o.items().size()));
        return detail(Long.toString(packId));
    }

    @Transactional
    public EmoticonAdminView.PackDetail updateOfficial(long operatorId, String ip, String idText, EmoticonOfficialPackSaveRequest body) {
        Official o = officialFields(body, settings.current());
        PackRow p = lock(idText);
        if (p.source() != EmoticonPackSource.OFFICIAL) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_NOT_OFFICIAL);
        }
        EmoticonContent.Loaded live = content.load(p.liveRevisionId());
        Resolved resolved = resolve(p, o);
        Instant now = clock.instant();
        long revisionId = ids.nextId();
        int version = packs.maxVersion(p.id()) + 1;
        writeSealed(p.id(), revisionId, version, o, resolved, live, now);
        packs.replaceLive(p.id(), revisionId, now);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put("name", live == null ? null : live.revision().name());
        before.put("count", live == null ? 0 : live.items().size());
        after.put("name", o.name());
        after.put("count", o.items().size());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_PACK_UPDATE, AuditResult.SUCCESS).target(TARGET, Long.toString(p.id()))
                .with("version", version).with("before", before).with("after", after));
        return detail(Long.toString(p.id()));
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 반려·판매 중단 사유 — 앞뒤 공백을 떼고 5~200자({@code 400 fields.reason}) */
    static String reason(String text) {
        String v = text == null ? "" : text.trim();
        if (EmoticonSpec.length(v) < EmoticonSpec.REASON_MIN || EmoticonSpec.length(v) > EmoticonSpec.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("emoticon.reason.length", EmoticonSpec.REASON_MIN, EmoticonSpec.REASON_MAX));
        }
        return v;
    }

    private PackRow find(String idText) {
        long id = EmoticonShopService.parse(idText);
        PackRow p = id < 0 ? null : packs.find(id).orElse(null);
        if (p == null || p.status() == EmoticonPackStatus.DRAFT) {
            throw new EmoticonPackNotFoundException();
        }
        return p;
    }

    private PackRow lock(String idText) {
        long id = EmoticonShopService.parse(idText);
        PackRow p = id < 0 ? null : packs.lock(id).orElse(null);
        if (p == null || p.status() == EmoticonPackStatus.DRAFT) {
            throw new EmoticonPackNotFoundException();
        }
        return p;
    }

    private String liveName(PackRow p) {
        return p.liveRevisionId() == null ? "" : packs.revision(p.liveRevisionId()).map(RevisionRow::name).orElse("");
    }

    /** 운영 팩 본문(검사를 지난 값) */
    private record Official(String name, String description, List<String> tags, List<Long> items, List<String> alts, long coverId) {
    }

    /** 대표·로보티콘이 가리키는 것 — 칸마다 지금 로보티콘 ID(있으면) · 그림 */
    private record Resolved(ImageRow cover, List<Long> keptEmoticons, List<ImageRow> itemImages) {
    }

    private Official officialFields(EmoticonOfficialPackSaveRequest body, SettingsRow s) {
        EmoticonOfficialPackSaveRequest b = body == null ? new EmoticonOfficialPackSaveRequest(null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        if (EmoticonSpec.length(name) < EmoticonSpec.NAME_MIN || EmoticonSpec.length(name) > EmoticonSpec.NAME_MAX) {
            fields.put("name", new MessageRef("emoticon.studio.name.length", EmoticonSpec.NAME_MIN, EmoticonSpec.NAME_MAX));
        }
        List<String> tags = new ArrayList<>();
        for (String raw : b.tags() == null ? List.<String>of() : b.tags()) {
            String tag = raw == null ? "" : raw.trim();
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        if (tags.size() > EmoticonSpec.MAX_TAGS) {
            fields.put("tags", new MessageRef("emoticon.studio.tags.tooMany", EmoticonSpec.MAX_TAGS));
        } else if (tags.stream().anyMatch(t -> EmoticonSpec.length(t) > EmoticonSpec.TAG_MAX)) {
            fields.put("tags", new MessageRef("emoticon.studio.tags.tooLong", EmoticonSpec.TAG_MAX));
        }
        String description = b.description() == null ? "" : b.description().trim();
        if (EmoticonSpec.length(description) > EmoticonSpec.DESCRIPTION_MAX) {
            fields.put("description", new MessageRef("emoticon.studio.description.length", EmoticonSpec.DESCRIPTION_MAX));
        }
        List<EmoticonOfficialPackSaveRequest.Item> items = b.items() == null ? List.of() : b.items();
        if (items.size() < s.itemMin() || items.size() > s.itemMax()) {
            fields.put("items", new MessageRef("emoticon.studio.count", s.itemMin(), s.itemMax()));
        }
        List<Long> itemIds = new ArrayList<>();
        List<String> alts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            EmoticonOfficialPackSaveRequest.Item item = items.get(i);
            String alt = item == null || item.alt() == null ? "" : item.alt().trim();
            if (EmoticonSpec.length(alt) < EmoticonSpec.LABEL_MIN || EmoticonSpec.length(alt) > EmoticonSpec.LABEL_MAX) {
                fields.put("items[" + i + "].alt", new MessageRef("emoticon.studio.label.length", EmoticonSpec.LABEL_MIN, EmoticonSpec.LABEL_MAX));
            } else if (!seen.add(alt)) {
                fields.put("items[" + i + "].alt", new MessageRef("emoticon.official.alt.duplicate"));
            }
            alts.add(alt);
            itemIds.add(item == null ? -1 : EmoticonShopService.parse(item.id()));
        }
        long coverId = b.coverId() == null || b.coverId().isBlank() ? -1 : EmoticonShopService.parse(b.coverId());
        if (b.coverId() == null || b.coverId().isBlank()) {
            fields.put("cover", new MessageRef("emoticon.studio.cover.required"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Official(name, description, tags, itemIds, alts, coverId);
    }

    /** 대표·로보티콘을 찾는다 — 지금 것(이 팩) 또는 운영자가 올린 것만 */
    private Resolved resolve(PackRow p, Official o) {
        Set<Long> packImages = p == null ? Set.of() : packs.imageIds(p.id());
        Set<Long> packEmoticons = p == null ? Set.of() : packs.emoticonIds(p.id());
        Map<Long, Long> liveImageOf = new HashMap<>();
        if (p != null && p.liveRevisionId() != null) {
            for (ItemRow item : packs.items(p.liveRevisionId())) {
                liveImageOf.put(item.emoticonId(), item.imageId());
            }
        }
        Set<Long> wanted = new HashSet<>(o.items());
        wanted.add(o.coverId());
        wanted.addAll(liveImageOf.values());
        Map<Long, ImageRow> found = images.byIds(wanted);
        ImageRow cover = found.get(o.coverId());
        if (cover == null || cover.kind() != EmoticonImageKind.COVER || !(packImages.contains(cover.id()) || cover.uploadedByOperator() != null)) {
            throw ValidationException.of("cover", new MessageRef("emoticon.official.cover.unknown"));
        }
        List<Long> kept = new ArrayList<>();
        List<ImageRow> itemImages = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long id : o.items()) {
            if (packEmoticons.contains(id) && liveImageOf.containsKey(id) && used.add(id)) {
                kept.add(id);
                itemImages.add(found.get(liveImageOf.get(id)));
                continue;
            }
            ImageRow image = found.get(id);
            if (image == null || image.kind() != EmoticonImageKind.EMOTICON || !(packImages.contains(id) || image.uploadedByOperator() != null)
                    || itemImages.stream().anyMatch(x -> x != null && x.id() == image.id())) {
                throw ValidationException.of("items", new MessageRef("emoticon.official.items.unknown"));
            }
            kept.add(null);
            itemImages.add(image);
        }
        return new Resolved(cover, kept, itemImages);
    }

    /** 새 판을 넣고 차례를 채운 뒤 낸다(판 번호 {@code version}) */
    private void writeSealed(long packId, long revisionId, int version, Official o, Resolved r, EmoticonContent.Loaded live, Instant now) {
        packs.insertDraft(revisionId, packId, o.name(), o.description(), o.tags(), r.cover().id(), EmoticonSaleWish.FREE, null, now);
        Set<Long> used = new HashSet<>();
        r.keptEmoticons().forEach(id -> {
            if (id != null) {
                used.add(id);
            }
        });
        List<ItemRow> items = new ArrayList<>();
        for (int i = 0; i < o.items().size(); i++) {
            Long emoticonId = r.keptEmoticons().get(i);
            ImageRow image = r.itemImages().get(i);
            if (emoticonId == null) {
                Optional<Long> byHash = live == null ? Optional.empty() : packs.emoticonByHash(packId, image.contentHash()).filter(id -> !used.contains(id));
                if (byHash.isPresent()) {
                    emoticonId = byHash.get();
                } else {
                    emoticonId = ids.nextId();
                    packs.insertEmoticon(emoticonId, packId, now);
                }
                used.add(emoticonId);
            }
            items.add(new ItemRow(revisionId, i + 1, emoticonId, image.id(), o.alts().get(i)));
        }
        packs.replaceItems(revisionId, packId, items);
        packs.seal(revisionId, version, now);
    }

    /** 상세 모양들(입력 차례) — 보이는 판 = 상점 판, 없으면 마지막으로 낸 판 */
    private List<EmoticonAdminView.PackDetail> details(List<PackRow> rows, boolean withEvents) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(PackRow::id).toList();
        Map<Long, RevisionRow> sealed = packs.latestSealed(ids);
        List<Long> shownIds = new ArrayList<>();
        for (PackRow p : rows) {
            RevisionRow last = sealed.get(p.id());
            shownIds.add(p.liveRevisionId() != null ? p.liveRevisionId() : last == null ? null : last.id());
        }
        Map<Long, EmoticonContent.Loaded> loaded = content.load(shownIds);
        Map<Long, Long> rewards = reads.rewardTotals(ids);
        Set<Long> creators = new HashSet<>();
        rows.forEach(p -> {
            if (p.creatorId() != null) {
                creators.add(p.creatorId());
            }
        });
        Map<Long, MemberRef> members = reads.members(creators);
        Set<Long> agreed = contracts.agreedCurrent(creators);
        Set<Long> registered = payouts.registered(creators);
        Map<Long, ReviewRow> latest = reviews.latestOf(ids);
        Map<Long, EmoticonPurchaseStore.PackSales> sales = purchases.packSales(ids);
        List<EmoticonAdminView.PackDetail> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PackRow p = rows.get(i);
            EmoticonContent.Loaded shown = shownIds.get(i) == null ? null : loaded.get(shownIds.get(i));
            ReviewRow review = latest.get(p.id());
            boolean pending = review != null && review.status() == com.community.emoticon.entity.EmoticonReviewStatus.PENDING;
            boolean official = p.source() == EmoticonPackSource.OFFICIAL;
            RevisionRow r = shown == null ? null : shown.revision();
            // 유료 판매(S3) — 유료이거나 판매가 있었던 팩만: 환불 뺀 결제 완료 수·금액. 무료로만 나온 팩·운영 팩은 null(목업과 같다)
            EmoticonPurchaseStore.PackSales sold = sales.get(p.id());
            boolean hasSales = !official && (p.price() > 0 || sold != null);
            Integer salesCount = hasSales ? Integer.valueOf(sold == null ? 0 : sold.paidCount()) : null;
            Long salesAmount = hasSales ? Long.valueOf(sold == null ? 0 : sold.paidAmount()) : null;
            out.add(new EmoticonAdminView.PackDetail(Long.toString(p.id()), r == null ? "" : r.name(), content.coverUrl(shown), p.source().name(),
                    EmoticonReviewService.creatorRef(p.creatorId(), members), shown == null ? 0 : shown.items().size(), p.price(), p.acquireCount(),
                    official ? null : rewards.getOrDefault(p.id(), 0L), salesCount, salesAmount, p.status().name(), pending && review.kind() == EmoticonReviewKind.UPDATE, p.updatedAt(),
                    r == null ? "" : r.description(), r == null ? List.of() : r.tags(), shown == null ? null : content.adminCover(shown.cover()),
                    shown == null ? List.of() : content.adminItems(shown),
                    p.suspendReason() == null ? null : new EmoticonAdminView.Suspension(p.suspendReason(), p.suspendedAt(), p.suspendedByName()),
                    pending ? Long.toString(review.id()) : null, p.publishedAt(),
                    official ? null : new EmoticonAdminView.PaidReady(agreed.contains(p.creatorId()), registered.contains(p.creatorId())), withEvents ? events(p.id()) : List.of(),
                    p.suspendKind() == null ? null : p.suspendKind().name(),
                    p.status() == EmoticonPackStatus.SUSPENDED && p.suspendKind() == com.community.emoticon.entity.EmoticonSuspendKind.OPERATOR));
        }
        return out;
    }

    private List<EmoticonAdminView.PriceEvent> events(long packId) {
        return priceEvents.byPack(packId).stream().map(e -> new EmoticonAdminView.PriceEvent(e.changedAt(), e.changedByName(), e.fromPrice(), e.toPrice(), e.reason())).toList();
    }

    /**
     * 판매 중 작가 팩의 가격 바꾸기 {@code { price, reason }}(계약 「유료 ↔ 무료 전환」 — 사용자 확정 2026-11-05). 차례: 사유 5~200자 {@code 400 fields.reason} → 없는 팩 404 →
     * 판매 중(APPROVED) 작가 팩이 아니면 {@code 409 EMOTICON_PACK_NOT_ON_SALE} → 가격 없음·지금과 같음 {@code 400 fields.price} → (설정 행 나눔 잠금) 유료면 가격 단계 · 유료 판매 켜짐,
     * 무료 → 유료면 작가의 유료 조건(정산 정보 · 지금 판 계약 동의 — 유료 승인과 같다, 이미 유료인 팩의 단계 바꾸기는 보지 않는다 — 목업 {@code mockEmoticonChangePrice}) → 바꾼다.
     * 가격 전환 기록 · 감사 {@code EMOTICON_PACK_PRICE}(돈 관련) · 작가 알림 {@code PRICE_CHANGED}(사유).
     *
     * <p>이미 가진 사람은 그대로 쓴다(받기 줄·구매는 손대지 않는다 — 다시 사지 않는다). 무료가 된 뒤의 받기는 작가 받기 보상이 생기고, 유료가 되면 새 받기를 받지 않아 보상이 멈춘다
     * (받기의 가격 판정은 그때의 팩 가격 — {@link EmoticonAcquireService}). 이미 산 사람의 금액·환불 규칙과 지난 정산은 구매 기록(S3)·정산(S4)이 그 때 값으로 갖는다.
     */
    @Transactional
    public EmoticonAdminView.PackDetail changePrice(long operatorId, String ip, String idText, EmoticonReviewDecisionRequest body) {
        String reason = reason(body == null ? null : body.reason());
        PackRow p = lock(idText);
        if (p.creatorWithdrawn()) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_CREATOR_WITHDRAWN, "error.EMOTICON_PACK_CREATOR_WITHDRAWN.price");
        }
        if (p.status() != EmoticonPackStatus.APPROVED || p.source() != EmoticonPackSource.CREATOR || p.creatorId() == null) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_NOT_ON_SALE, "error.EMOTICON_PACK_NOT_ON_SALE.price");
        }
        Integer price = body.price();
        if (price == null) {
            throw ValidationException.of("price", new MessageRef("emoticon.review.price.required"));
        }
        if (price == p.price()) {
            throw ValidationException.of("price", new MessageRef("emoticon.review.price.same"));
        }
        if (price != 0) {
            SettingsRow s = settings.currentForPricing();
            EmoticonReviewService.priceFields(s, price);
            if (p.price() == 0) {
                MessageRef blocked = !payouts.registered(p.creatorId()) ? new MessageRef("emoticon.review.price.noPayout")
                        : !contracts.agreedCurrent(p.creatorId()) ? new MessageRef("emoticon.review.price.noContract") : null;
                if (blocked != null) {
                    throw ValidationException.of("price", blocked);
                }
            }
        }
        Instant now = clock.instant();
        packs.setPrice(p.id(), price, now);
        priceEvents.insert(ids.nextId(), p.id(), p.price(), price, reason, null, now, operatorId);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_PACK_PRICE, AuditResult.SUCCESS).target(TARGET, Long.toString(p.id()))
                .with("from", p.price()).with("to", price).with("reason", reason));
        memberNotices.emoticonPackPrice(p.creatorId(), p.id(), liveName(p), p.price(), price, reason);
        return detail(Long.toString(p.id()));
    }

    private static EmoticonAdminView.PackRow row(EmoticonAdminView.PackDetail d) {
        return new EmoticonAdminView.PackRow(d.id(), d.name(), d.coverUrl(), d.source(), d.creator(), d.itemCount(), d.price(), d.acquireCount(), d.rewardTotal(),
                d.salesCount(), d.salesAmount(), d.status(), d.pendingUpdate(), d.updatedAt(), d.suspendKind());
    }
}
