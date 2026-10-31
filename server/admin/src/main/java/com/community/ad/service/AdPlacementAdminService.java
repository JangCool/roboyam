package com.community.ad.service;

import com.community.ad.dto.AdPlacementAdmin;
import com.community.ad.dto.AdPlacements;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고 노출 자리 설정(admin-web {@code /ads/placements}, docs/features/ad-contract.md 「공개 노출 목업」 확정 2026-11-01) — 조회 {@code ad.read} · 저장 {@code ad.settings}
 * (권한은 컨트롤러). 저장은 한 행(V118 {@code ad_placement_settings})을 잠가 덮고, 바뀐 칸만 감사 {@code AD_PLACEMENT_UPDATE} before/after 로 남긴다(처음 저장이면 기본값에서 —
 * 바뀐 칸이 없어도 저장한다). 공개 사이트는 같은 행을 읽는다(공개 광고 목록 사본과 달리 요청마다 읽는다).
 *
 * <ul>
 *   <li>네 묶음({@code postInline}·{@code wings}·{@code boardList}·{@code adult}) 모두 필요 — 빠지면 400 {@code fields.<묶음>}. {@code adult.enabled} 는 「19금 게시판에도
 *       광고 보이기」(사용자 결정 2026-11-01 2차, 기본 끔).</li>
 *   <li>켜기 칸(불리언)이 비면 {@code fields.<묶음>.<칸>}(가정 — 칸을 빠뜨린 요청을 기본값으로 메우지 않는다).</li>
 *   <li>{@code postInline.adType} TEXT_LINE·STRIP_BANNER·SQUARE_BANNER · {@code postInline.position} BETWEEN_CARDS·AFTER_BODY · {@code wings.screens} HOME·PAGE·BOARD·POST 중
 *       0~4개(겹치면 하나로, 보일 차례로 저장 — 비어도 된다: 가정) · {@code boardList.after} 1~20.</li>
 * </ul>
 */
@Service
public class AdPlacementAdminService {

    static final String AUDIT_TARGET = "AD_PLACEMENTS";

    private final AdPlacementStore store;
    private final AuditLogService audit;

    public AdPlacementAdminService(AdPlacementStore store, AuditLogService audit) {
        this.store = store;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdPlacementAdmin.View get() {
        return view(store.find().orElse(null));
    }

    @Transactional
    public AdPlacementAdmin.View save(AdminActor actor, AdPlacementAdmin.Save body) {
        AdPlacements next = validate(body);
        AdPlacementStore.Stored was = store.lock().orElse(null);
        AdPlacements before = was == null ? AdPlacements.DEFAULTS : was.value();
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        Map<String, Object> a = flat(before);
        Map<String, Object> b = flat(next);
        for (String key : a.keySet()) {
            if (!Objects.equals(a.get(key), b.get(key))) {
                from.put(key, a.get(key));
                to.put(key, b.get(key));
            }
        }
        if (was != null && to.isEmpty()) {
            return get();
        }
        Instant now = Instant.now();
        store.save(next, actor.operatorId(), now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", from);
        detail.put("after", to);
        if (was == null) {
            detail.put("fromDefaults", true);
        }
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), AuditActions.AD_PLACEMENT_UPDATE, AuditResult.SUCCESS, AUDIT_TARGET, "1", actor.ip(),
                detail));
        return get();
    }

    private static AdPlacementAdmin.View view(AdPlacementStore.Stored stored) {
        AdPlacements p = stored == null ? AdPlacements.DEFAULTS : stored.value();
        return new AdPlacementAdmin.View(p.postInline(), p.wings(), p.boardList(), p.adult(), stored == null ? null : stored.updatedAt().toString(),
                stored == null ? null : new AdPlacementAdmin.Operator(Long.toString(stored.updatedBy()), stored.updatedByName()));
    }

    private static AdPlacements validate(AdPlacementAdmin.Save body) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef required = new MessageRef("ad.placement.required");
        MessageRef option = new MessageRef("ad.placement.option");
        AdPlacementAdmin.PostInline pi = body == null ? null : body.postInline();
        AdPlacementAdmin.Wings w = body == null ? null : body.wings();
        AdPlacementAdmin.BoardList bl = body == null ? null : body.boardList();
        AdPlacementAdmin.Adult ad = body == null ? null : body.adult();
        if (pi == null) {
            fields.put("postInline", required);
        } else {
            if (pi.enabled() == null) {
                fields.put("postInline.enabled", required);
            }
            if (pi.adType() == null || !AdPlacements.INLINE_TYPES.contains(pi.adType())) {
                fields.put("postInline.adType", option);
            }
            if (pi.position() == null || !AdPlacements.INLINE_POSITIONS.contains(pi.position())) {
                fields.put("postInline.position", option);
            }
        }
        List<String> screens = null;
        if (w == null) {
            fields.put("wings", required);
        } else {
            if (w.left() == null) {
                fields.put("wings.left", required);
            }
            if (w.right() == null) {
                fields.put("wings.right", required);
            }
            if (w.screens() == null || w.screens().stream().anyMatch(s -> s == null || !AdPlacements.WING_SCREENS.contains(s))) {
                fields.put("wings.screens", option);
            } else {
                Set<String> picked = new LinkedHashSet<>(w.screens());
                screens = AdPlacements.WING_SCREENS.stream().filter(picked::contains).toList();
            }
        }
        if (bl == null) {
            fields.put("boardList", required);
        } else {
            if (bl.enabled() == null) {
                fields.put("boardList.enabled", required);
            }
            if (bl.after() == null || bl.after() < AdPlacements.AFTER_MIN || bl.after() > AdPlacements.AFTER_MAX) {
                fields.put("boardList.after", new MessageRef("ad.placement.after", String.valueOf(AdPlacements.AFTER_MIN), String.valueOf(AdPlacements.AFTER_MAX)));
            }
        }
        if (ad == null) {
            fields.put("adult", required);
        } else if (ad.enabled() == null) {
            fields.put("adult.enabled", required);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new AdPlacements(new AdPlacements.PostInline(pi.enabled(), pi.adType(), pi.position()), new AdPlacements.Wings(w.left(), w.right(), screens),
                new AdPlacements.BoardList(bl.enabled(), bl.after()), new AdPlacements.Adult(ad.enabled()));
    }

    /** 감사 비교용 칸 이름 → 값 */
    private static Map<String, Object> flat(AdPlacements p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("postInline.enabled", p.postInline().enabled());
        out.put("postInline.adType", p.postInline().adType());
        out.put("postInline.position", p.postInline().position());
        out.put("wings.left", p.wings().left());
        out.put("wings.right", p.wings().right());
        out.put("wings.screens", p.wings().screens());
        out.put("boardList.enabled", p.boardList().enabled());
        out.put("boardList.after", p.boardList().after());
        out.put("adult.enabled", p.adult().enabled());
        return out;
    }
}
