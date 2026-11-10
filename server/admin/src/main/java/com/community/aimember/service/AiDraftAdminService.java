package com.community.aimember.service;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.AiDraftStatus;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.exception.AiDraftBlockedWordException;
import com.community.aimember.exception.AiDraftNotFoundException;
import com.community.aimember.llm.AiPrompts;
import com.community.aimember.repository.master.AiDraftRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.group.entity.GroupEntity;
import com.community.group.repository.master.GroupRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검수 대기함 · 활동 기록(정책 6·14).
 *
 * <ul>
 *   <li><b>게시</b>는 상태만 {@code PENDING → APPROVED} 로 바꾼다 — 실제 등록은 api 서버의 실행기가 기존 글·댓글 서비스로 한다(정책 9·11, 금칙어·제재·캐시·알림이 같게 돈다).
 *       그래서 게시한 줄은 다음 바퀴(기본 1분 안)에 사이트에 보인다. 등록에서 막히면(제재·글이 사라짐 등) 그 줄은 {@code FAILED} 와 까닭으로 남는다.</li>
 *   <li><b>수정 후 게시</b>는 고친 제목·본문으로 금칙어를 미리 본다(400 {@code BLOCKED_WORD}) — 금칙어 「가림」 방식이면 등록 때 가려진다.</li>
 *   <li>여러 건 게시·버림은 그 사이 처리된 줄을 건너뛰고 {@code {done, skipped}} 를 준다. 줄마다 감사 기록을 남긴다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AiDraftAdminService {

    static final String AUDIT_TARGET = "AI_DRAFT";
    static final int LIST_LIMIT = 300;
    static final int BATCH_MAX = 100;
    static final int PAGE_SIZE_MAX = 100;

    private final AiDraftRepository drafts;
    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries queries;
    private final BoardCatalog catalog;
    private final GroupRepository groups;
    private final AuditLogService audit;
    private final Messages messages;

    public AiDraftAdminService(AiDraftRepository drafts, AiMemberProfileRepository accounts, AiMemberQueries queries, BoardCatalog catalog, GroupRepository groups,
                               AuditLogService audit, Messages messages) {
        this.drafts = drafts;
        this.accounts = accounts;
        this.queries = queries;
        this.catalog = catalog;
        this.groups = groups;
        this.audit = audit;
        this.messages = messages;
    }

    // ── 대기함 ────────────────────────────────────────────────────────────

    public AiMemberViews.DraftList drafts(String kindRaw, String aiMemberIdRaw) {
        AiDraftKind kind = kindRaw == null || "ALL".equalsIgnoreCase(kindRaw) ? null : AiMemberAdminService.parseEnum(AiDraftKind.class, kindRaw);
        Long member = AiMemberAdminService.parseLong(aiMemberIdRaw);
        List<AiDraftEntity> rows = queries.pending(kind, member, LIST_LIMIT);
        return new AiMemberViews.DraftList(views(rows), queries.pendingTotal());
    }

    @Transactional
    public AiMemberViews.BatchResult publish(AdminActor actor, List<String> ids) {
        return settle(actor, ids, true);
    }

    @Transactional
    public AiMemberViews.BatchResult discard(AdminActor actor, List<String> ids) {
        return settle(actor, ids, false);
    }

    /** 수정 후 게시 — 글은 제목·본문, 댓글·답글은 본문만. 이미 처리된 줄이면 404 {@code AI_DRAFT_NOT_FOUND} */
    @Transactional
    public AiMemberViews.BatchResult publishEdited(AdminActor actor, String idText, AiMemberRequests.Edit body) {
        Long id = AiMemberAdminService.parseLong(idText);
        AiDraftEntity d = id == null ? null : drafts.findWithLockById(id).filter(x -> x.getStatus() == AiDraftStatus.PENDING).orElse(null);
        if (d == null) {
            throw new AiDraftNotFoundException();
        }
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String title = null;
        if (d.getKind() == AiDraftKind.POST) {
            title = body == null || body.title() == null ? "" : body.title().strip();
            if (title.isEmpty()) {
                fields.put("title", new MessageRef("aiMember.draft.title.required"));
            } else if (title.length() > AiPrompts.TITLE_MAX) {
                fields.put("title", new MessageRef("aiMember.draft.title.max", AiPrompts.TITLE_MAX));
            }
        }
        String text = body == null || body.body() == null ? "" : body.body().strip();
        int max = d.getKind() == AiDraftKind.POST ? AiPrompts.POST_BODY_MAX : AiPrompts.COMMENT_BODY_MAX;
        if (text.isEmpty()) {
            fields.put("body", new MessageRef("aiMember.draft.body.required"));
        } else if (text.length() > max) {
            fields.put("body", new MessageRef("aiMember.draft.body.max", max));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        BoardCatalogSnapshot snap = catalog.snapshot();
        Optional<String> word = title == null ? ContentRules.blockedWord(snap.security(), text) : ContentRules.blockedWord(snap.security(), title, text);
        if (word.isPresent()) {
            throw new AiDraftBlockedWordException(word.get());
        }
        d.approve(title, text, actor.operatorId(), now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_DRAFT_PUBLISH, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(d.getId()))
                .with("kind", d.getKind().name()).with("aiMemberId", Long.toString(d.getAiMemberId())).with("edited", true));
        return new AiMemberViews.BatchResult(1, 0);
    }

    private AiMemberViews.BatchResult settle(AdminActor actor, List<String> raw, boolean publish) {
        List<String> list = raw == null ? List.of() : raw;
        if (list.isEmpty() || list.size() > BATCH_MAX) {
            throw ValidationException.of("ids", new MessageRef("aiMember.draft.ids", BATCH_MAX));
        }
        int done = 0;
        Instant now = now();
        for (String text : new LinkedHashSet<>(list)) {
            Long id = AiMemberAdminService.parseLong(text);
            AiDraftEntity d = id == null ? null : drafts.findWithLockById(id).orElse(null);
            if (d == null || d.getStatus() != AiDraftStatus.PENDING) {
                continue; // 그 사이 다른 운영자가 처리했다
            }
            if (publish) {
                d.approve(null, null, actor.operatorId(), now);
            } else {
                d.discard(actor.operatorId(), now);
            }
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), publish ? AuditActions.AI_DRAFT_PUBLISH : AuditActions.AI_DRAFT_DISCARD, AuditResult.SUCCESS)
                    .target(AUDIT_TARGET, Long.toString(d.getId())).with("kind", d.getKind().name()).with("aiMemberId", Long.toString(d.getAiMemberId())));
            done++;
        }
        return new AiMemberViews.BatchResult(done, list.size() - done);
    }

    // ── 기록 ──────────────────────────────────────────────────────────────

    public AiMemberViews.LogPage logs(String aiMemberIdRaw, String resultRaw, String kindRaw, String pageRaw, String sizeRaw) {
        Long member = AiMemberAdminService.parseLong(aiMemberIdRaw);
        List<AiDraftStatus> statuses = switch (resultRaw == null ? "ALL" : resultRaw.strip().toUpperCase(java.util.Locale.ROOT)) {
            case "PUBLISHED" -> List.of(AiDraftStatus.PUBLISHED);
            case "PENDING" -> List.of(AiDraftStatus.PENDING, AiDraftStatus.APPROVED);
            case "DISCARDED" -> List.of(AiDraftStatus.DISCARDED);
            case "FAILED" -> List.of(AiDraftStatus.FAILED);
            default -> List.of();
        };
        AiDraftKind kind = kindRaw == null || "ALL".equalsIgnoreCase(kindRaw) ? null : AiMemberAdminService.parseEnum(AiDraftKind.class, kindRaw);
        Long pageL = AiMemberAdminService.parseLong(pageRaw);
        Long sizeL = AiMemberAdminService.parseLong(sizeRaw);
        int page = pageL == null || pageL < 1 ? 1 : (int) Math.min(pageL, 100_000);
        int size = sizeL == null || sizeL < 1 ? 20 : (int) Math.min(sizeL, PAGE_SIZE_MAX);
        AiMemberQueries.LogPage found = queries.logs(member, statuses, kind, page, size);
        Refs refs = refs(found.items());
        List<AiMemberViews.Log> items = found.items().stream().map(d -> log(d, refs)).toList();
        return new AiMemberViews.LogPage(items, page, found.total(), found.tokenSum());
    }

    private AiMemberViews.Log log(AiDraftEntity d, Refs r) {
        AiMemberProfileEntity a = r.accounts().get(d.getAiMemberId());
        String nickname = a == null ? "" : r.nicknames().getOrDefault(a.getUserId(), "");
        String result = switch (d.getStatus()) {
            case PENDING, APPROVED -> "PENDING";
            case PUBLISHED -> "PUBLISHED";
            case DISCARDED -> "DISCARDED";
            case FAILED -> "FAILED";
        };
        String reviewer = d.getReviewedBy() == null ? null : r.operators().get(d.getReviewedBy());
        String fail = d.getFailReason() == null ? null : messages.get(new MessageRef("aiMember.fail." + d.getFailReason()));
        return new AiMemberViews.Log(Long.toString(d.getId()), d.getCreatedAt(), new AiMemberViews.LogAccount(Long.toString(d.getAiMemberId()), nickname),
                d.getKind().name(), result, d.getProvider().name(), d.getModel(), new AiMemberViews.LogTokens(d.getInputTokens(), d.getOutputTokens()),
                target(d, r), reviewer, fail);
    }

    /** 대상 한 줄 — 「게시판 · 글 제목」(글이면 만든 제목) */
    private String target(AiDraftEntity d, Refs r) {
        String place = placeName(d, r);
        String title = d.getKind() == AiDraftKind.POST ? d.getTitle() : Optional.ofNullable(r.posts().get(d.getPostId())).map(AiMemberQueries.PostHead::title).orElse(null);
        return title == null || title.isBlank() ? place : place + " · " + title;
    }

    // ── 대기함 줄 ──────────────────────────────────────────────────────────

    /** 한 번에 읽어 둔 이름들 */
    private record Refs(Map<Long, AiMemberProfileEntity> accounts, Map<Long, String> nicknames, Set<Long> aiUsers, Map<Long, AiMemberQueries.PostHead> posts,
                        Map<Long, AiMemberQueries.CommentRow> comments, Map<Long, String> groupNames, Map<Long, String> operators, BoardCatalogSnapshot snap) {
    }

    private Refs refs(List<AiDraftEntity> rows) {
        Map<Long, AiMemberProfileEntity> byId = new HashMap<>();
        accounts.findAllById(rows.stream().map(AiDraftEntity::getAiMemberId).collect(java.util.stream.Collectors.toSet())).forEach(a -> byId.put(a.getId(), a));
        Set<Long> postIds = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();
        Set<Long> groupIds = new HashSet<>();
        Set<Long> operatorIds = new HashSet<>();
        for (AiDraftEntity d : rows) {
            if (d.getPostId() != null) {
                postIds.add(d.getPostId());
            }
            if (d.getReplyToCommentId() != null) {
                commentIds.add(d.getReplyToCommentId());
            }
            if (d.getGroupId() != null) {
                groupIds.add(d.getGroupId());
            }
            if (d.getReviewedBy() != null) {
                operatorIds.add(d.getReviewedBy());
            }
        }
        Map<Long, AiMemberQueries.PostHead> posts = queries.postHeads(postIds);
        Map<Long, AiMemberQueries.CommentRow> comments = queries.commentsByIds(commentIds);
        posts.values().forEach(p -> {
            if (p.groupId() != null) {
                groupIds.add(p.groupId());
            }
        });
        Set<Long> userIds = new HashSet<>();
        byId.values().forEach(a -> userIds.add(a.getUserId()));
        posts.values().stream().map(AiMemberQueries.PostHead::authorId).filter(Objects::nonNull).forEach(userIds::add);
        comments.values().stream().map(AiMemberQueries.CommentRow::authorId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, String> groupNames = new HashMap<>();
        groups.findAllById(groupIds).forEach((GroupEntity g) -> groupNames.put(g.getId(), g.getName()));
        return new Refs(byId, queries.nicknames(userIds), queries.authorsOf(userIds).keySet(), posts, comments, groupNames, queries.operatorNames(operatorIds),
                catalog.snapshot());
    }

    private List<AiMemberViews.Draft> views(List<AiDraftEntity> rows) {
        Refs r = refs(rows);
        List<AiMemberViews.Draft> out = new ArrayList<>();
        for (AiDraftEntity d : rows) {
            AiMemberProfileEntity a = r.accounts().get(d.getAiMemberId());
            AiMemberViews.DraftAccount account = new AiMemberViews.DraftAccount(Long.toString(d.getAiMemberId()),
                    a == null ? "" : r.nicknames().getOrDefault(a.getUserId(), ""), d.getProvider().name(), d.getModel());
            EffectiveBoard board = d.getBoardId() == null ? null : r.snap().board(d.getBoardId()).orElse(null);
            AiMemberViews.BoardRef boardRef = new AiMemberViews.BoardRef(board == null ? "" : board.slug(), board == null ? "" : board.name());
            Long groupId = d.getGroupId() != null ? d.getGroupId() : Optional.ofNullable(r.posts().get(d.getPostId())).map(AiMemberQueries.PostHead::groupId).orElse(null);
            AiMemberViews.DraftPost post = null;
            AiMemberQueries.PostHead head = d.getPostId() == null ? null : r.posts().get(d.getPostId());
            if (head != null) {
                post = new AiMemberViews.DraftPost(Long.toString(head.id()), head.title(), author(head.authorId(), r));
            }
            AiMemberViews.DraftParent parent = null;
            AiMemberQueries.CommentRow c = d.getReplyToCommentId() == null ? null : r.comments().get(d.getReplyToCommentId());
            if (c != null) {
                parent = new AiMemberViews.DraftParent(Long.toString(c.id()), author(c.authorId(), r), AiPrompts.plain(c.content(), c.format()));
            }
            out.add(new AiMemberViews.Draft(Long.toString(d.getId()), d.getKind().name(), account, boardRef, groupId == null ? null : r.groupNames().get(groupId),
                    d.getKind() == AiDraftKind.POST ? d.getTitle() : null, d.getBody(), post, parent, d.getRound(), d.getCreatedAt(),
                    (long) d.getInputTokens() + d.getOutputTokens()));
        }
        return out;
    }

    private static AiMemberViews.DraftAuthor author(Long userId, Refs r) {
        return new AiMemberViews.DraftAuthor(userId == null ? "" : r.nicknames().getOrDefault(userId, ""), userId != null && r.aiUsers().contains(userId));
    }

    private static String placeName(AiDraftEntity d, Refs r) {
        Long groupId = d.getGroupId() != null ? d.getGroupId() : Optional.ofNullable(r.posts().get(d.getPostId())).map(AiMemberQueries.PostHead::groupId).orElse(null);
        if (groupId != null && r.groupNames().containsKey(groupId)) {
            return r.groupNames().get(groupId);
        }
        return d.getBoardId() == null ? "" : r.snap().board(d.getBoardId()).map(EffectiveBoard::name).orElse("");
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
