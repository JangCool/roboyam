package com.community.popup.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.popup.dto.SitePopupFields;
import com.community.popup.dto.request.SitePopupLayoutRequest;
import com.community.popup.dto.request.SitePopupOrderRequest;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.popup.dto.request.SitePopupSaveRequest;
import com.community.popup.dto.request.SitePopupVisibleRequest;
import com.community.popup.dto.response.SitePopupEditorView;
import com.community.popup.dto.response.SitePopupItemView;
import com.community.popup.dto.response.SitePopupListView;
import com.community.popup.entity.SitePopupEntity;
import com.community.popup.exception.SitePopupNotFoundException;
import com.community.popup.repository.master.SitePopupRepository;
import com.community.site.service.SiteImageFiles;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 팝업 관리(docs/features/site-popup-contract.md) — 목록은 {@code site.read}, 만들기·고치기·차례·노출·지우기는 {@code site.manage}(컨트롤러가 선언). 규칙은 {@link SitePopups}.
 *
 * <ul>
 *   <li><b>칸 검사</b>(모아서 {@code 400 VALIDATION}): {@code fields.name|kind|imageFileId|imageAlt|link|heading|body|buttonLabel|target|devices|hideOption|windowNo|showUntil}.
 *       종류별 칸(IMAGE 의 그림·대체 글, TEXT 의 제목·내용·단추)은 지금 종류일 때만 검사한다. 지금 종류가 아닌 칸은 그대로 저장하되, 칸 길이를 넘으면 저장하지 않는다(null —
 *       보이지 않는 칸이라 오류로 막지 않는다. DB 제약을 넘지 않게).</li>
 *   <li><b>그림</b>: 하이라이트 대표 이미지와 같다 — {@code POST /api/admin/files}(purpose SITE_POPUP_IMAGE)로 올린 PENDING 파일을 지정하면 그 파일이 쓰는 중(IN_USE)이 되고
 *       (「올리다 만 파일 정리」에서 빠진다), 바꾸거나 지우면(팝업을 지울 때도) 예전 파일의 장부 행을 지우고 저장소 파일은 커밋 뒤에 지운다. 맞지 않는 파일이면 {@code fields.imageFileId}.</li>
 *   <li><b>차례</b>: 새 팝업은 맨 끝. 차례 바꾸기는 전체 ID 를 보낸다(집합이 다르면 {@code fields.ids}). 만들기·차례는 팝업 전체를 잠가 줄 세운다.</li>
 *   <li>모든 변경은 감사(SITE_POPUP_CREATE · UPDATE · DELETE · ORDER · VISIBLE, 대상 SITE_POPUP)에 남는다 — 고치기·차례·노출은 바뀐 때만.</li>
 *   <li>공개 사이트(api)는 자기 사본({@code PublicSitePopupService}, 20초)을 다시 읽을 때 따라온다.</li>
 * </ul>
 */
@Service
public class SitePopupAdminService {

    private static final Logger log = LoggerFactory.getLogger(SitePopupAdminService.class);
    private static final String TARGET_TYPE = "SITE_POPUP";

    private final SitePopupRepository popups;
    private final FileRepository files;
    private final ImageStorage storage;
    private final SiteImageFiles imageFiles;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final SiteSettingRepository settings;

    public SitePopupAdminService(SitePopupRepository popups, FileRepository files, ImageStorage storage, SiteImageFiles imageFiles, OperatorRepository operators,
                                 AuditLogService audit, SnowflakeIdGenerator ids, SiteSettingRepository settings) {
        this.popups = popups;
        this.files = files;
        this.storage = storage;
        this.imageFiles = imageFiles;
        this.operators = operators;
        this.audit = audit;
        this.ids = ids;
        this.settings = settings;
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SitePopupListView list() {
        return listOf(popups.findAllByOrderBySortOrderAscIdAsc());
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    /** 여러 창 배치(사이트 전체 하나)를 바꾼다 — 새 목록. 바뀐 때만 감사 {@code SITE_POPUP_LAYOUT} */
    @Transactional
    public SitePopupListView layout(AdminActor actor, SitePopupLayoutRequest body) {
        String layout = body == null || body.layout() == null ? null : body.layout().strip();
        if (!SitePopups.LAYOUTS.contains(layout)) {
            throw ValidationException.of("layout", new MessageRef("sitePopup.layout"));
        }
        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        String before = SitePopups.layoutOrDefault(row.getPopupLayout());
        if (!before.equals(layout)) {
            row.changePopupLayout(layout, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_POPUP_LAYOUT, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, "layout").with("from", before).with("to", layout));
            log.info("팝업 배치를 바꿨다 {} -> {} by operatorId={}", before, layout, actor.operatorId());
        }
        return listOf(popups.findAllByOrderBySortOrderAscIdAsc(), layout);
    }

    @Transactional
    public SitePopupItemView create(AdminActor actor, SitePopupSaveRequest body) {
        SitePopupFields f = check(body == null ? SitePopupSaveRequest.empty() : body, true);
        Instant now = Instant.now();
        List<SitePopupEntity> all = popups.findAllWithLockByOrderBySortOrderAscIdAsc(); // 만들기를 줄 세운다(맨 끝 차례)
        FileEntity image = attachImage(f.imageFileId(), null, now);
        int order = all.stream().mapToInt(SitePopupEntity::getSortOrder).max().orElse(0) + 1;
        SitePopupEntity row = SitePopupEntity.create(ids.nextId(), f, image == null ? null : image.getId(), order, actor.operatorId(), now);
        popups.persist(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_POPUP_CREATE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(row.getId())).with("after", snapshot(row)));
        log.info("팝업을 만들었다 id={} kind={} by operatorId={}", row.getId(), row.getKind(), actor.operatorId());
        return views(List.of(row), all.size() + 1, now).getFirst();
    }

    @Transactional
    public SitePopupItemView update(AdminActor actor, String idText, SitePopupSaveRequest body) {
        SitePopupEntity row = requireLocked(idText);
        SitePopupFields checked = check(body == null ? SitePopupSaveRequest.empty() : body, false);
        SitePopupFields f = body == null || body.visible() == null ? withVisible(checked, row.isVisible()) : checked;
        Instant now = Instant.now();
        Map<String, Object> before = snapshot(row);
        Long oldImage = row.getImageFileId();
        Long newImage = f.imageFileId();
        if (!Objects.equals(oldImage, newImage)) {
            attachImage(newImage, oldImage, now);
        }
        row.change(f, newImage, actor.operatorId(), now);
        Map<String, Object> after = snapshot(row);
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                changedBefore.put(k, before.get(k));
                changedAfter.put(k, v);
            }
        });
        if (!changedAfter.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_POPUP_UPDATE, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, Long.toString(row.getId())).with("before", changedBefore).with("after", changedAfter));
            log.info("팝업을 고쳤다 id={} 칸={} by operatorId={}", row.getId(), changedAfter.keySet(), actor.operatorId());
        }
        return viewOf(row, now);
    }

    /** 차례 바꾸기 — ID 집합은 지금 팝업과 같아야 한다. 새 목록을 돌려준다 */
    @Transactional
    public SitePopupListView order(AdminActor actor, SitePopupOrderRequest body) {
        List<SitePopupEntity> all = popups.findAllWithLockByOrderBySortOrderAscIdAsc();
        List<String> wanted = body == null || body.ids() == null ? List.of() : body.ids();
        Map<Long, SitePopupEntity> byId = new HashMap<>();
        all.forEach(p -> byId.put(p.getId(), p));
        List<Long> order = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String raw : wanted) {
            Long id = parseId(raw);
            if (id == null || !byId.containsKey(id) || !seen.add(id)) {
                order = null;
                break;
            }
            order.add(id);
        }
        if (order == null || order.size() != all.size()) {
            throw ValidationException.of("ids", new MessageRef("sitePopup.order.mismatch"));
        }
        List<String> before = all.stream().map(p -> Long.toString(p.getId())).toList();
        List<SitePopupEntity> sorted = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            SitePopupEntity p = byId.get(order.get(i));
            p.moveTo(i + 1);
            sorted.add(p);
        }
        List<String> after = order.stream().map(id -> Long.toString(id)).toList();
        if (!before.equals(after)) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_POPUP_ORDER, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, "order").with("before", before).with("after", after));
            log.info("팝업 차례를 바꿨다 by operatorId={}", actor.operatorId());
        }
        return listOf(sorted);
    }

    @Transactional
    public SitePopupItemView visible(AdminActor actor, String idText, SitePopupVisibleRequest body) {
        if (body == null || body.visible() == null) {
            throw ValidationException.of("visible", new MessageRef("sitePopup.visible.required"));
        }
        SitePopupEntity row = requireLocked(idText);
        Instant now = Instant.now();
        if (row.isVisible() != body.visible()) {
            boolean from = row.isVisible();
            row.changeVisible(body.visible(), actor.operatorId(), now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_POPUP_VISIBLE, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, Long.toString(row.getId())).with("name", row.getName()).with("from", from).with("to", body.visible()));
        }
        return viewOf(row, now);
    }

    /** 지운다 — 그림 파일도 장부 행을 지우고 저장소 파일은 커밋 뒤에 지운다. 남은 팝업의 차례 번호는 목록이 1부터 다시 센다 */
    @Transactional
    public void delete(AdminActor actor, String idText) {
        SitePopupEntity row = requireLocked(idText);
        Long image = row.getImageFileId();
        Map<String, Object> before = snapshot(row);
        popups.delete(row);
        if (image != null) {
            retireImage(image);
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_POPUP_DELETE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(row.getId())).with("before", before));
        log.info("팝업을 지웠다 id={} by operatorId={}", row.getId(), actor.operatorId());
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /**
     * 칸을 검사·정리한다 — 문제는 모아 {@code 400 VALIDATION}. 노출은 만들 때 비우면 켬, 고칠 때 비우면 부르는 쪽이 지금 값으로 바꾼다.
     */
    private static SitePopupFields check(SitePopupSaveRequest b, boolean creating) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = SitePopups.normalize(b.name());
        SitePopups.nameViolation(name).ifPresent(v -> fields.put("name", v));
        String kind = SitePopups.choice(b.kind(), SitePopups.KINDS);
        if (kind == null) {
            fields.put("kind", new MessageRef("sitePopup.kind"));
        }
        boolean image = SitePopups.isImage(kind);
        boolean text = SitePopups.KIND_TEXT.equals(kind);

        Long imageFileId = null;
        if (b.imageFileId() != null && !b.imageFileId().isBlank()) {
            imageFileId = parseId(b.imageFileId());
            if (imageFileId == null) {
                fields.put("imageFileId", new MessageRef("sitePopup.image.notFound"));
            }
        } else if (image) {
            fields.put("imageFileId", new MessageRef("sitePopup.image.required"));
        }
        String imageAlt = kindField("imageAlt", SitePopups.normalize(b.imageAlt()), SitePopups.IMAGE_ALT_MAX, image, fields);

        String link = SitePopups.normalize(b.link());
        SitePopups.linkViolation(link).ifPresent(v -> fields.put("link", v));

        String heading = kindField("heading", SitePopups.normalize(b.heading()), SitePopups.HEADING_MAX, text, fields);
        String body = SitePopups.normalizeBody(b.body());
        if (text) {
            SitePopups.bodyViolation(body).ifPresent(v -> fields.put("body", v));
        } else {
            body = fitsOrNull(body, SitePopups.BODY_MAX);
        }
        String buttonLabel = kindField("buttonLabel", SitePopups.normalize(b.buttonLabel()), SitePopups.BUTTON_MAX, text, fields);

        String target = SitePopups.choice(b.target(), SitePopups.TARGETS);
        if (target == null) {
            fields.put("target", new MessageRef("sitePopup.target"));
        }
        boolean[] devices = SitePopups.devices(b.devices());
        if (devices == null) {
            fields.put("devices", new MessageRef("sitePopup.devices.required"));
        }
        String hideOption = SitePopups.choice(b.hideOption(), SitePopups.HIDE_OPTIONS);
        if (hideOption == null) {
            fields.put("hideOption", new MessageRef("sitePopup.hideOption"));
        }
        int windowNo = b.windowNo() == null ? 1 : b.windowNo();
        if (windowNo < 1 || windowNo > SitePopups.WINDOW_MAX) {
            fields.put("windowNo", new MessageRef("sitePopup.windowNo"));
        }
        if (b.showFrom() != null && b.showUntil() != null && !b.showUntil().isAfter(b.showFrom())) {
            fields.put("showUntil", new MessageRef("sitePopup.showUntil.beforeFrom"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        boolean visible = b.visible() == null ? creating : b.visible();
        return new SitePopupFields(name, kind, imageFileId, imageAlt, link, heading, body, buttonLabel, target, devices[0], devices[1], hideOption, windowNo, visible,
                b.showFrom(), b.showUntil());
    }

    /** 종류에 딸린 글자 칸 — 지금 종류면 길이 검사(칸 오류), 아니면 길이를 넘을 때 저장하지 않는다(null) */
    private static String kindField(String field, String value, int max, boolean current, Map<String, MessageRef> fields) {
        if (current) {
            SitePopups.tooLong(field, value, max).ifPresent(v -> fields.put(field, v));
            return value;
        }
        return fitsOrNull(value, max);
    }

    private static String fitsOrNull(String value, int max) {
        return value != null && value.codePointCount(0, value.length()) > max ? null : value;
    }

    private static SitePopupFields withVisible(SitePopupFields f, boolean visible) {
        return new SitePopupFields(f.name(), f.kind(), f.imageFileId(), f.imageAlt(), f.link(), f.heading(), f.body(), f.buttonLabel(), f.target(), f.showPc(),
                f.showMobile(), f.hideOption(), f.windowNo(), visible, f.showFrom(), f.showUntil());
    }

    /**
     * 그림을 바꾼다 — 새 파일(올려 둔 SITE_POPUP_IMAGE)을 쓰는 중으로 만들고, 예전 파일은 장부 행을 지우고 저장소 파일은 커밋 뒤에 지운다. 새 파일이 맞지 않으면 {@code 400 fields.imageFileId}.
     *
     * @return 새 파일(없으면 null)
     */
    private FileEntity attachImage(Long newId, Long oldId, Instant now) {
        FileEntity file = null;
        if (newId != null) {
            file = files.findWithLockById(newId).filter(f -> f.getPurpose() == FilePurpose.SITE_POPUP_IMAGE && f.getStatus() == FileStatus.PENDING)
                    .orElseThrow(() -> ValidationException.of("imageFileId", new MessageRef("sitePopup.image.notFound")));
            file.markUsed(now);
        }
        if (oldId != null) {
            retireImage(oldId);
        }
        return file;
    }

    /** 예전 그림 — 장부 행을 지우고(팝업 행이 먼저 다른 값을 가리킨 뒤 플러시 때 지워진다) 저장소 파일은 커밋 뒤에 지운다 */
    private void retireImage(long fileId) {
        files.findById(fileId).ifPresent(old -> {
            String key = old.getStorageKey();
            files.delete(old);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        imageFiles.deleteQuietly(List.of(key));
                    }
                });
            }
        });
    }

    private SitePopupEntity requireLocked(String idText) {
        Long id = parseId(idText);
        if (id == null) {
            throw new SitePopupNotFoundException();
        }
        return popups.findWithLockById(id).orElseThrow(SitePopupNotFoundException::new);
    }

    /** 감사용 칸 값 */
    private static Map<String, Object> snapshot(SitePopupEntity row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", row.getName());
        map.put("kind", row.getKind());
        map.put("imageFileId", row.getImageFileId() == null ? null : Long.toString(row.getImageFileId()));
        map.put("imageAlt", row.getImageAlt());
        map.put("link", row.getLink());
        map.put("heading", row.getHeading());
        map.put("body", row.getBody());
        map.put("buttonLabel", row.getButtonLabel());
        map.put("target", row.getTarget());
        map.put("devices", String.join(",", SitePopups.devicesOf(row)));
        map.put("hideOption", row.getHideOption());
        map.put("windowNo", row.getWindowNo());
        map.put("visible", row.isVisible());
        map.put("showFrom", row.getShowFrom() == null ? null : row.getShowFrom().toString());
        map.put("showUntil", row.getShowUntil() == null ? null : row.getShowUntil().toString());
        return map;
    }

    private SitePopupListView listOf(List<SitePopupEntity> rows) {
        return listOf(rows, SitePopups.layoutOrDefault(settings.findById(SiteSettingEntity.ROW_ID).map(SiteSettingEntity::getPopupLayout).orElse(null)));
    }

    private SitePopupListView listOf(List<SitePopupEntity> rows, String layout) {
        List<SitePopupItemView> items = views(rows, 1, Instant.now());
        int showing = (int) items.stream().filter(i -> SitePopups.SHOWING.equals(i.status())).count();
        return new SitePopupListView(items, items.size(), showing, layout);
    }

    /** 한 줄 — 차례 번호는 전체 차례에서 센다 */
    private SitePopupItemView viewOf(SitePopupEntity row, Instant now) {
        int order = 1;
        for (SitePopupEntity p : popups.findAllByOrderBySortOrderAscIdAsc()) {
            if (p.getId().equals(row.getId())) {
                break;
            }
            order++;
        }
        return views(List.of(row), order, now).getFirst();
    }

    /** 줄들 — 그림 주소·운영자 이름을 한 번에 읽는다. {@code firstOrder} 부터 번호를 붙인다 */
    private List<SitePopupItemView> views(List<SitePopupEntity> rows, int firstOrder, Instant now) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> imageUrls = new HashMap<>();
        List<Long> imageIds = rows.stream().map(SitePopupEntity::getImageFileId).filter(Objects::nonNull).toList();
        if (!imageIds.isEmpty()) {
            files.findAllById(imageIds).forEach(f -> imageUrls.put(f.getId(), storage.publicUrl(f.getStorageKey())));
        }
        Map<Long, String> operatorNames = new HashMap<>();
        List<Long> operatorIds = rows.stream().map(SitePopupEntity::getUpdatedBy).filter(Objects::nonNull).distinct().toList();
        if (!operatorIds.isEmpty()) {
            operators.findAllById(operatorIds).forEach(o -> operatorNames.put(o.getId(), o.getOperatorName()));
        }
        List<SitePopupItemView> out = new ArrayList<>(rows.size());
        int order = firstOrder;
        for (SitePopupEntity p : rows) {
            Long image = p.getImageFileId();
            out.add(new SitePopupItemView(Long.toString(p.getId()), order++, p.getName(), p.getKind(), image == null ? null : Long.toString(image),
                    image == null ? null : imageUrls.get(image), p.getImageAlt(), p.getLink(), p.getHeading(), p.getBody(), p.getButtonLabel(), p.getTarget(),
                    SitePopups.devicesOf(p), p.getHideOption(), p.getWindowNo(), p.isVisible(), p.getShowFrom(), p.getShowUntil(), SitePopups.status(p, now), p.getUpdatedAt(),
                    p.getUpdatedBy() == null ? null : new SitePopupEditorView(Long.toString(p.getUpdatedBy()), operatorNames.get(p.getUpdatedBy()))));
        }
        return out;
    }

    /** 문자열 ID → long. 형식이 틀리면 null */
    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
