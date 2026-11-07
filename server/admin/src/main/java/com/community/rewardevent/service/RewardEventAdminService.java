package com.community.rewardevent.service;

import com.community.member.service.MemberMasking;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.RewardOperator;
import com.community.rewardevent.dto.request.RewardCancelRequest;
import com.community.rewardevent.dto.request.RewardCommentPreviewRequest;
import com.community.rewardevent.dto.request.RewardDeliveredRequest;
import com.community.rewardevent.dto.request.RewardDrawRequest;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.dto.request.RewardGrantRequest;
import com.community.rewardevent.dto.request.RewardRecipientAddRequest;
import com.community.rewardevent.dto.request.RewardResolveRequest;
import com.community.rewardevent.dto.request.RewardRevokeRequest;
import com.community.rewardevent.dto.request.RewardTargetPreviewRequest;
import com.community.rewardevent.dto.response.RewardCommentPreviewView;
import com.community.rewardevent.dto.response.RewardDrawView;
import com.community.rewardevent.dto.response.RewardEventAuditView;
import com.community.rewardevent.dto.response.RewardEventCatalogView;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.dto.response.RewardEventPageView;
import com.community.rewardevent.dto.response.RewardGrantPreviewView;
import com.community.rewardevent.dto.response.RewardRecipientPageView;
import com.community.rewardevent.dto.response.RewardRecipientResolveView;
import com.community.rewardevent.dto.response.RewardRecipientView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.dto.response.RewardTargetPreviewView;
import com.community.rewardevent.exception.RewardEventNotFoundException;
import com.community.rewardevent.exception.RewardRecipientNotFoundException;
import java.util.List;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

/**
 * 관리자 보상 이벤트 API 의 서비스 — 운영자({@code AdminActor})에서 고액 권한·거부 감사·이메일 가리개를 뽑아 shared 보상 이벤트 서비스에 넘긴다(docs/features/event-admin-contract.md 「API」).
 * 입구 권한(읽기 {@code reward-event.read} · 처리 {@code reward-event.manage} · 지급 {@code reward-event.grant})은 컨트롤러가 선언한다. 트랜잭션은 shared 서비스가 연다.
 */
@Service
public class RewardEventAdminService {

    private final RewardEventQueryService queries;
    private final RewardEventEditService edits;
    private final RewardSelectionService selection;
    private final RewardGrantService grants;
    private final AccessDeniedRecorder denied;

    public RewardEventAdminService(RewardEventQueryService queries, RewardEventEditService edits, RewardSelectionService selection, RewardGrantService grants,
                                   AccessDeniedRecorder denied) {
        this.queries = queries;
        this.edits = edits;
        this.selection = selection;
        this.grants = grants;
        this.denied = denied;
    }

    public RewardEventPageView list(String status, String mode, String q, Integer page, Integer size) {
        return queries.list(status, mode, q, page, size);
    }

    public RewardEventDetailView detail(String id) {
        return queries.detail(eventId(id));
    }

    public RewardEventCatalogView catalog() {
        return queries.catalog();
    }

    public List<RewardMemberRef> searchMembers(AdminActor actor, String q) {
        return queries.searchMembers(q, emailView(actor));
    }

    public RewardEventDetailView create(AdminActor actor, RewardEventSaveRequest body) {
        return edits.create(operator(actor, "new"), body);
    }

    public RewardEventDetailView update(AdminActor actor, String id, RewardEventSaveRequest body) {
        long eventId = eventId(id);
        return edits.update(operator(actor, id), eventId, body);
    }

    public RewardEventDetailView start(AdminActor actor, String id) {
        long eventId = eventId(id);
        return edits.start(operator(actor, id), eventId);
    }

    public RewardEventDetailView close(AdminActor actor, String id) {
        long eventId = eventId(id);
        return edits.close(operator(actor, id), eventId);
    }

    public RewardEventDetailView cancel(AdminActor actor, String id, RewardCancelRequest body) {
        long eventId = eventId(id);
        return edits.cancel(operator(actor, id), eventId, body == null ? null : body.reason());
    }

    public RewardCommentPreviewView previewComments(RewardCommentPreviewRequest body) {
        return selection.previewComments(body);
    }

    public RewardTargetPreviewView previewTargets(RewardTargetPreviewRequest body) {
        return selection.previewTargets(body);
    }

    public RewardRecipientResolveView resolve(AdminActor actor, String id, RewardResolveRequest body) {
        return selection.resolve(eventId(id), body == null ? null : body.lines(), emailView(actor));
    }

    public RewardEventDetailView addRecipients(AdminActor actor, String id, RewardRecipientAddRequest body) {
        long eventId = eventId(id);
        return selection.addRecipients(operator(actor, id), eventId, body);
    }

    public RewardEventDetailView removeRecipient(AdminActor actor, String id, String recipientId) {
        long eventId = eventId(id);
        long rid = RewardEventRules.parseId(recipientId).orElseThrow(RewardRecipientNotFoundException::new);
        return selection.removeRecipient(operator(actor, id), eventId, rid);
    }

    public RewardRecipientPageView recipients(AdminActor actor, String id, String status, String tierId, String q, Integer page, Integer size) {
        return queries.recipients(eventId(id), status, tierId, q, page, size, emailView(actor));
    }

    public RewardDrawView draw(AdminActor actor, String id, RewardDrawRequest body) {
        long eventId = eventId(id);
        return selection.draw(operator(actor, id), eventId, body, emailView(actor));
    }

    public List<RewardDrawView> draws(AdminActor actor, String id) {
        return queries.draws(eventId(id), emailView(actor));
    }

    public RewardGrantPreviewView grantPreview(String id) {
        return queries.grantPreview(eventId(id));
    }

    public RewardRunView grant(AdminActor actor, String id, RewardGrantRequest body) {
        long eventId = eventId(id);
        return grants.grant(operator(actor, id), eventId, body == null ? null : body.idempotencyKey());
    }

    public RewardRunView retry(AdminActor actor, String id) {
        long eventId = eventId(id);
        return grants.retry(operator(actor, id), eventId);
    }

    public RewardRunView revoke(AdminActor actor, String id, RewardRevokeRequest body) {
        long eventId = eventId(id);
        return grants.revoke(operator(actor, id), eventId, body == null ? null : body.recipientIds(), body == null ? null : body.reason());
    }

    public RewardRecipientView markDelivered(AdminActor actor, String id, String recipientId, RewardDeliveredRequest body) {
        long eventId = eventId(id);
        long rid = RewardEventRules.parseId(recipientId).orElseThrow(RewardRecipientNotFoundException::new);
        return grants.markDelivered(operator(actor, id), eventId, rid, body == null ? null : body.lineId(), body == null ? null : body.memo(), emailView(actor));
    }

    public List<RewardRunView> runs(String id) {
        return queries.runs(eventId(id));
    }

    public List<RewardEventAuditView> audit(String id) {
        return queries.audit(eventId(id));
    }

    /** 운영자 — 고액 권한 여부와 거부 감사(따로 커밋 — 권한 거부 관례, 대상 REWARD_EVENT + 이벤트 번호) */
    private RewardOperator operator(AdminActor actor, String targetId) {
        return new RewardOperator(actor.operatorId(), actor.ip(), actor.can(Permission.POINT_ADJUST_LARGE),
                () -> denied.record(actor, "MISSING_PERMISSION", RewardEventSupport.TARGET, targetId, List.of(Permission.POINT_ADJUST_LARGE.code())));
    }

    /** 이메일 — {@code member.pii} 가 없으면 가린다 */
    private static UnaryOperator<String> emailView(AdminActor actor) {
        return actor.can(Permission.MEMBER_PII) ? UnaryOperator.identity() : MemberMasking::email;
    }

    private static long eventId(String id) {
        return RewardEventRules.parseId(id).orElseThrow(RewardEventNotFoundException::new);
    }
}
