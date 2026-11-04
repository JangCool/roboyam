package com.community.post.service;

import com.community.point.service.PointService;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostModerationEventEntity;
import com.community.post.exception.PostPurgedException;
import com.community.post.exception.PostStateInvalidException;
import com.community.sanction.repository.master.BoardStaffActionStore;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 글 숨기기 · 숨김 해제 · 삭제 · 복구의 몸통 — 운영자(관리자 게시글 관리 · 신고 처리)와 게시판 매니저·스텝(공개 사이트 스텝 처리 — board-staff-action-contract 정책 2)이
 * <b>같은 효과</b>를 내도록 한 곳에 둔다. 부르는 서비스의 트랜잭션 안, <b>글 행을 잠근 뒤</b> 부른다. 감사 기록·작성자 알림은 부르는 쪽이 한다.
 *
 * <ul>
 *   <li><b>숨기기</b>: 공개 글만(아니면 409 POST_STATE_INVALID), 까닭 STAFF·고정 해제, 조치 기록 HIDDEN.</li>
 *   <li><b>숨김 해제</b>: 숨긴 글만(까닭 무엇이든), 조치 기록 UNHIDDEN(덧붙임 풀기 전 까닭). 그 글의 되돌리지 않은 스텝 숨김 기록을 되돌림으로 채운다(정책 7).</li>
 *   <li><b>삭제</b>: 지우지 않은 글만, 고정 해제 · 받은 포인트를 본인 삭제와 같은 규칙으로 회수, 조치 기록 DELETED(덧붙임 회수한 포인트).</li>
 *   <li><b>복구</b>: 지운 글만(파기 409 POST_PURGED, 임시저장 409 POST_STATE_INVALID), 예약 삭제도 거두고 숨김은 그대로, 회수한 포인트를 되돌린다. 조치 기록 RESTORED.
 *       그 글의 되돌리지 않은 스텝 삭제 기록을 채운다(정책 7).</li>
 * </ul>
 * 조치 기록의 행위자: 운영자 {@code OPERATOR}(운영자 ID) · 매니저·스텝 {@code BOARD_STAFF}(회원 ID + 덧붙임 {@code staffRole}, 정책 8).
 */
@Component
public class PostModerationActions {

    /** 행위자 — 운영자(운영자 ID) 또는 그 게시판 매니저·스텝(회원 ID + 자리 MANAGER · STAFF) */
    public record ModerationActor(String kind, long id, String staffRole) {

        public static ModerationActor operator(long operatorId) {
            return new ModerationActor(PostModerationEventEntity.BY_OPERATOR, operatorId, null);
        }

        public static ModerationActor boardStaff(long userId, String role) {
            return new ModerationActor(PostModerationEventEntity.BY_BOARD_STAFF, userId, role);
        }

        public boolean operator() {
            return PostModerationEventEntity.BY_OPERATOR.equals(kind);
        }
    }

    private final PostModerationLog moderation;
    private final PointService points;
    private final BoardStaffActionStore staffActions;
    private final Messages messages;

    public PostModerationActions(PostModerationLog moderation, PointService points, BoardStaffActionStore staffActions, Messages messages) {
        this.moderation = moderation;
        this.points = points;
        this.staffActions = staffActions;
        this.messages = messages;
    }

    /** 숨기기 — 공개 글만, 까닭 STAFF·고정 해제. 조치 기록 HIDDEN(덧붙임 = {@code extra} + 고정 해제). 덧붙임을 돌려준다 */
    public Map<String, Object> hide(ModerationActor actor, PostEntity post, String reason, Map<String, Object> extra, Instant now) {
        requirePublic(post, now);
        boolean wasPinned = post.getPinnedAt() != null;
        post.staffHide(now);
        Map<String, Object> detail = new LinkedHashMap<>(extra);
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        record(actor, post.getId(), PostModerationEventEntity.HIDDEN, reason, detail, now);
        return detail;
    }

    /** 숨김 해제 — 숨긴 글만(까닭 무엇이든). 조치 기록 UNHIDDEN(덧붙임 = 풀기 전 까닭 + {@code extra}). 열린 스텝 숨김 기록을 채운다(정책 7) */
    public Map<String, Object> unhide(ModerationActor actor, PostEntity post, String reason, Map<String, Object> extra, Instant now) {
        if (post.isGoneAt(now) || !post.isHiddenAt(now)) {
            throw new PostStateInvalidException();
        }
        String before = post.getHiddenReason();
        post.unhide(now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("hiddenReason", before);
        detail.putAll(extra);
        record(actor, post.getId(), PostModerationEventEntity.UNHIDDEN, reason, detail, now);
        closeStaffActions(actor, post.getId(), BoardStaffActionStore.ACTION_HIDE, reason, now);
        return detail;
    }

    /** 삭제 — 지우지 않은 글만, 고정 해제·포인트 회수. 조치 기록 DELETED(덧붙임 = 회수한 포인트 + 고정 해제 + {@code extra}) */
    public Map<String, Object> delete(ModerationActor actor, PostEntity post, String reason, Map<String, Object> extra, Instant now) {
        if (post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        boolean wasPinned = post.getPinnedAt() != null;
        post.staffDelete(now);
        // 운영자·스텝 삭제 — 본인 삭제와 같은 규칙으로 글 포인트·추천 보너스를 0P 까지 회수한다(이미 회수했으면 0)
        long revoked = post.getAuthorId() == null ? 0 : points.revokeContent(post.getAuthorId(), PointService.REF_POST, post.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revokedPoints", revoked);
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        detail.putAll(extra);
        record(actor, post.getId(), PostModerationEventEntity.DELETED, reason, detail, now);
        return detail;
    }

    /** 복구 — 지운 글만(파기 409 POST_PURGED · 임시저장 409 POST_STATE_INVALID). 회수한 포인트를 되돌린다. 조치 기록 RESTORED(덧붙임 = 되돌린 포인트 + {@code extra}) */
    public Map<String, Object> restore(ModerationActor actor, PostEntity post, String reason, Map<String, Object> extra, Instant now) {
        if (!post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        if (post.isPurged()) {
            throw new PostPurgedException();
        }
        if (!PostEntity.PUBLISHED.equals(post.getStatus())) {
            throw new PostStateInvalidException(); // 임시저장은 지운 글이 아니다
        }
        post.restore(now);
        long restored = post.getAuthorId() == null ? 0 : points.restoreContent(post.getAuthorId(), post.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("restoredPoints", restored);
        detail.putAll(extra);
        record(actor, post.getId(), PostModerationEventEntity.RESTORED, reason, detail, now);
        closeStaffActions(actor, post.getId(), BoardStaffActionStore.ACTION_DELETE, reason, now);
        return detail;
    }

    /** 덧붙임의 회수한 포인트 */
    public static long revokedOf(Map<String, Object> detail) {
        return detail.get("revokedPoints") instanceof Number n ? n.longValue() : 0;
    }

    /** 공개 글(지우지 않음 · 숨김 아님)만 */
    public static void requirePublic(PostEntity post, Instant now) {
        if (post.isGoneAt(now) || post.isHiddenAt(now)) {
            throw new PostStateInvalidException();
        }
    }

    private void record(ModerationActor actor, long postId, String type, String reason, Map<String, Object> detail, Instant now) {
        if (actor.operator()) {
            moderation.operator(postId, type, actor.id(), reason, detail, now);
            return;
        }
        Map<String, Object> withRole = new LinkedHashMap<>(detail);
        withRole.put("staffRole", actor.staffRole());
        moderation.boardStaff(postId, type, actor.id(), reason, withRole, now);
    }

    /**
     * 정책 7 — 글을 풀면 그 글의 되돌리지 않은 스텝 처리 기록(숨김 해제 → HIDE, 복구 → DELETE)을 되돌림으로 채운다. 운영자 = OPERATOR(운영자 ID), 매니저 = MANAGER(회원 ID).
     * 사유가 없으면(신고 무시의 빈 처리 글) 번들 문구.
     */
    private void closeStaffActions(ModerationActor actor, long postId, String action, String reason, Instant now) {
        String byKind = actor.operator() ? BoardStaffActionStore.BY_OPERATOR : BoardStaffActionStore.BY_MANAGER;
        String text = reason == null || reason.isBlank() ? messages.get(new MessageRef("boardStaffAction.restore.byModeration"), Locale.KOREAN) : cut(reason.strip(), 200);
        staffActions.restoreOpenForPost(postId, action, byKind, actor.id(), text, now);
    }

    private static String cut(String text, int max) {
        return text.codePointCount(0, text.length()) <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }
}
