package com.community.hotdeal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.exception.HotdealStateChangedException;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.service.DealModeration.Actor;
import com.community.hotdeal.service.DealModeration.Result;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 핫딜 종료·재개(hotdeal-contract 「권한·정책」) — 담당 판정·상태 바꾸기·보상·작성자 알림은 shared {@link DealModeration}·{@link DealEndService} 한 길이고, 여기서는 칸 검사와
 * 오류 코드·감사({@code HOTDEAL_END}·{@code HOTDEAL_REOPEN}, 대상 POST)를 맡는다.
 *
 * <ul>
 *   <li>검사 순서: 사유 0~200자({@code 400 fields.reason}) → 대상(핫딜 게시판의 살아 있는 글이고 딜 줄 — 아니면 {@code 404 POST_NOT_FOUND}) → 담당(운영자 {@code hotdeal.manage},
 *       아니면 {@code 403 PERMISSION_DENIED}) → 상태(종료: 이미 종료 · 재개: 종료가 아님 → {@code 409 HOTDEAL_STATE_CHANGED}).</li>
 *   <li>종료 = 진행·의심·기간 지남 → ENDED MANAGER + 의심 구간 요청자 보상 + 작성자 알림 {@code DEAL_MANAGER_ENDED}(사유). 재개 = ENDED → ACTIVE(의심·keep·요청 리셋, 기간이 지났으면
 *       작성자 알림 {@code DEAL_REOPENED_FIX_ENDS}). 재개 사유는 감사에만 남는다.</li>
 * </ul>
 */
@Service
public class HotdealModerationService {

    private static final Logger log = LoggerFactory.getLogger(HotdealModerationService.class);

    public static final int REASON_MAX = 200;
    public static final String TARGET_POST = "POST";

    private final DealModeration moderation;
    private final PostDealRepository deals;
    private final DealSettingsReader settings;
    private final AuditLogService audit;

    public HotdealModerationService(DealModeration moderation, PostDealRepository deals, DealSettingsReader settings, AuditLogService audit) {
        this.moderation = moderation;
        this.deals = deals;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional
    public void end(AdminActor actor, String idText, HotdealAdminRequest.Action body) {
        String reason = reason(body);
        long postId = requireId(idText);
        PostDealEntity before = deals.findById(postId).orElse(null);
        String beforeView = before == null ? null : before.view(settings.current().autoExpireDays(), Instant.now()).name();
        check(moderation.end(postId, actorOf(actor), reason));
        record(actor, AuditActions.HOTDEAL_END, postId, before, Map.of("before", String.valueOf(beforeView)), reason);
        log.info("핫딜 운영진 종료 postId={} by operatorId={}", postId, actor.operatorId());
    }

    @Transactional
    public void reopen(AdminActor actor, String idText, HotdealAdminRequest.Action body) {
        String reason = reason(body);
        long postId = requireId(idText);
        PostDealEntity before = deals.findById(postId).orElse(null);
        String endedBy = before == null ? null : before.getEndedBy();
        check(moderation.reopen(postId, actorOf(actor)));
        record(actor, AuditActions.HOTDEAL_REOPEN, postId, before, endedBy == null ? Map.of() : Map.of("endedBy", endedBy), reason);
        log.info("핫딜 운영진 재개 postId={} by operatorId={}", postId, actor.operatorId());
    }

    static Actor actorOf(AdminActor actor) {
        return Actor.operator(actor.operatorId(), actor.can(Permission.HOTDEAL_MANAGE));
    }

    private static void check(Result result) {
        switch (result) {
            case NOT_FOUND -> throw new AdminPostNotFoundException();
            case FORBIDDEN -> throw new PermissionDeniedException();
            case STATE_CHANGED -> throw new HotdealStateChangedException();
            case CHANGED -> {
            }
        }
    }

    private void record(AdminActor actor, String action, long postId, PostDealEntity d, Map<String, String> extra, String reason) {
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET_POST, Long.toString(postId));
        if (d != null) {
            event = event.with("boardId", Long.toString(d.getBoardId()));
        }
        for (Map.Entry<String, String> e : extra.entrySet()) {
            event = event.with(e.getKey(), e.getValue());
        }
        if (reason != null) {
            event = event.with("reason", reason);
        }
        audit.record(event);
    }

    /** 사유 — 앞뒤 공백·NUL 을 떼고 0~200자(비면 null) */
    static String reason(HotdealAdminRequest.Action body) {
        String raw = body == null ? null : body.reason();
        String text = raw == null ? null : raw.replace("\u0000", "").strip();
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("hotdeal.admin.reason.tooLong", String.valueOf(REASON_MAX)));
        }
        return text;
    }

    private static long requireId(String idText) {
        Long id = HotdealAdminQueryService.parseId(idText);
        if (id == null) {
            throw new AdminPostNotFoundException();
        }
        return id;
    }
}
