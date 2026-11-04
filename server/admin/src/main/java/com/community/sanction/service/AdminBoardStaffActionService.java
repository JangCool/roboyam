package com.community.sanction.service;

import com.community.permission.dto.AdminActor;
import com.community.sanction.dto.request.BoardStaffActionRestoreReasonRequest;
import com.community.sanction.dto.response.AdminBoardStaffActionListView;
import com.community.sanction.dto.response.AdminBoardStaffActionView;
import com.community.sanction.exception.BoardStaffActionException;
import com.community.sanction.repository.master.AdminBoardStaffActionStore;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 「스텝 처리 감독」(docs/features/board-staff-action-contract.md 「API > 관리자」 · admin-web {@code useBoardStaffActionApi.ts}) — 목록 · 하나 · 되돌리기.
 * 규칙은 shared {@link BoardStaffActionService}.
 *
 * <ul>
 *   <li><b>목록</b>: {@code board}(게시판 ID — 숫자가 아니면 아무것도 맞지 않음) · {@code kind} ALL(기본) · POST_HIDE · POST_DELETE · COMMENT_DELETE · {@code restored} ALL(기본) ·
 *       OPEN · RESTORED · {@code q} 처리자 닉네임(앞뒤 공백 제거 · 50자 자름 · 최소 글자 없음 · 부분 일치) · {@code from}·{@code to}(처리 시각 UTC ISO, 양 끝 포함 — 형식 오류는 그 칸,
 *       시작 &gt; 끝은 {@code fields.from}) · {@code page} 0부터 · {@code size} 기본 20 최대 50. 모르는 값은 기본값.</li>
 *   <li><b>권한</b>: 조회 {@code keeper-action.read} · 되돌리기 {@code keeper-action.restore}(컨트롤러 선언). 감사 {@code BOARD_STAFF_ACTION_RESTORE}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminBoardStaffActionService {

    static final Set<String> KINDS = Set.of("POST_HIDE", "POST_DELETE", "COMMENT_DELETE");

    private final BoardStaffActionService actions;
    private final AdminBoardStaffActionStore store;

    public AdminBoardStaffActionService(BoardStaffActionService actions, AdminBoardStaffActionStore store) {
        this.actions = actions;
        this.store = store;
    }

    public AdminBoardStaffActionListView list(String boardRaw, String kindRaw, String restoredRaw, String qRaw, String fromRaw, String toRaw, String pageRaw,
                                              String sizeRaw) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Instant from = AdminSanctionService.instant(fromRaw, "from", invalid);
        Instant to = AdminSanctionService.instant(toRaw, "to", invalid);
        if (from != null && to != null && from.isAfter(to)) {
            invalid.put("from", new MessageRef("sanction.range.invalid"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        String kind = upper(kindRaw);
        String restored = upper(restoredRaw);
        String q = AdminSanctionService.trim(qRaw);
        String nicknameLike = q == null ? null
                : "%" + AdminSanctionService.likeEscape(AdminSanctionService.cut(q, AdminSanctionService.QUERY_MAX).toLowerCase(Locale.ROOT)) + "%";
        Long boardId = AdminSanctionService.trim(boardRaw) == null ? null : orNone(AdminSanctionService.parseId(boardRaw));
        int page = Math.max(0, AdminSanctionService.number(pageRaw, 0));
        int size = Math.min(AdminSanctionService.SIZE_MAX, Math.max(1, AdminSanctionService.number(sizeRaw, AdminSanctionService.SIZE_DEFAULT)));
        AdminBoardStaffActionStore.Filter filter = new AdminBoardStaffActionStore.Filter(boardId, kind != null && KINDS.contains(kind) ? kind : null,
                "OPEN".equals(restored) || "RESTORED".equals(restored) ? restored : null, nicknameLike, from, to);
        Instant now = actions.now();
        List<AdminBoardStaffActionView> items = store.page(filter, page, size).stream().map(r -> view(r, now)).toList();
        return new AdminBoardStaffActionListView(store.total(filter), page, size,
                store.boards().stream().map(b -> BoardSanctionViews.board(b.id(), b.slug(), b.name(), b.nameEn())).toList(), items);
    }

    /** 처리 하나 — 없으면(제재 기록 포함) 404 {@code BOARD_STAFF_ACTION_NOT_FOUND} */
    public AdminBoardStaffActionView get(String idText) {
        return view(actions.find(idOf(idText)).orElseThrow(BoardStaffActionException::actionNotFound), actions.now());
    }

    @Transactional
    public AdminBoardStaffActionView restore(AdminActor actor, String idText, BoardStaffActionRestoreReasonRequest body) {
        ActionRow row = actions.restoreByOperator(idOf(idText), body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        return view(row, actions.now());
    }

    static AdminBoardStaffActionView view(ActionRow r, Instant now) {
        AdminBoardStaffActionView.Actor actor = new AdminBoardStaffActionView.Actor(r.actorId() == null ? null : Long.toString(r.actorId()), r.actorNickname(),
                r.actorRole());
        AdminBoardStaffActionView.Author author = r.targetUserId() != null
                ? new AdminBoardStaffActionView.Author("MEMBER", Long.toString(r.targetUserId()), r.authorName())
                : new AdminBoardStaffActionView.Author("GUEST", null, r.targetAuthorName());
        AdminBoardStaffActionView.Target target = new AdminBoardStaffActionView.Target(Long.toString(r.postId()), r.commentId() == null ? null : Long.toString(r.commentId()),
                r.targetTitle(), r.targetExcerpt(), author, r.targetState(now));
        AdminBoardStaffActionView.Restored restored = r.restoredAt() == null ? null
                : new AdminBoardStaffActionView.Restored(r.restoredAt(), new AdminBoardStaffActionView.By(r.restoredByKind(), r.restoredByName()), r.restoreReason());
        return new AdminBoardStaffActionView(Long.toString(r.id()), BoardSanctionViews.board(r.boardId(), r.boardSlug(), r.boardName(), r.boardNameEn()), r.apiKind(),
                r.reasonCode(), actor, target, r.createdAt(), restored);
    }

    private static long idOf(String idText) {
        Long id = AdminSanctionService.parseId(idText);
        if (id == null) {
            throw BoardStaffActionException.actionNotFound();
        }
        return id;
    }

    private static String upper(String raw) {
        String s = AdminSanctionService.trim(raw);
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    private static Long orNone(Long id) {
        return id == null ? -1L : id;
    }
}
