package com.community.debate.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.debate.dto.DebateVotesView;
import com.community.debate.dto.request.DebateSaveRequest;
import com.community.debate.dto.response.DebateItemView;
import com.community.debate.entity.PostDebateEntity;
import com.community.debate.exception.DebateNotFoundException;
import com.community.debate.exception.DebatePostNotFoundException;
import com.community.debate.repository.master.DebateAdminRepositoryQDSL;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.repository.master.PostDebateRepository;
import com.community.highlight.dto.response.HighlightCommentCountsView;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightCommentRepositoryQDSL;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.permission.dto.AdminActor;
import com.community.post.dto.AdminPostRow;
import com.community.post.dto.response.AdminPostRefView;
import com.community.post.service.AdminPostSummaries;
import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 글 찬반 토론 — 단건({@code post.read})·열기/설정/닫기({@code post.manage}, 컨트롤러가 선언). docs/features/post-debate-contract.md.
 * 토론 목록·글 찾기 화면은 게시글 관리(다음 작업)의 글 상세 서랍 토론 구획으로 들어가 따로 두지 않는다(사용자 결정 2026-10-24).
 *
 * <ul>
 *   <li><b>열기·바꾸기·닫기</b>: 저장은 공개 사이트·하이라이트와 같은 {@link PostDebateWriter}. 운영자는 어느 글이든 열고(지운 글은 404 POST_NOT_FOUND — 숨김·19금·꺼진 게시판 글은 연다),
 *       투표가 있어도 닫는다. 연 사람 OPERATOR. 감사 DEBATE_OPEN · DEBATE_UPDATE(바뀐 칸만) · DEBATE_CLOSE(대상 POST). 하이라이트 만들기·고치기도 {@link #apply} 로 같은 감사를 남긴다
 *       ({@code via HIGHLIGHT}).</li>
 * </ul>
 */
@Service
public class DebateAdminService {

    private static final Logger log = LoggerFactory.getLogger(DebateAdminService.class);

    private final PostDebateRepository debates;
    private final PostDebateWriter writer;
    private final DebateQueries debateQueries;
    private final DebateAdminRepositoryQDSL debateCounts;
    private final HighlightRepository highlights;
    private final HighlightCommentRepositoryQDSL commentCounts;
    private final AdminPostSummaries summaries;
    private final BoardCatalog catalog;
    private final AuditLogService audit;

    public DebateAdminService(PostDebateRepository debates, PostDebateWriter writer, DebateQueries debateQueries, DebateAdminRepositoryQDSL debateCounts,
                              HighlightRepository highlights, HighlightCommentRepositoryQDSL commentCounts, AdminPostSummaries summaries,
                              BoardCatalog catalog, AuditLogService audit) {
        this.debates = debates;
        this.writer = writer;
        this.debateQueries = debateQueries;
        this.debateCounts = debateCounts;
        this.highlights = highlights;
        this.commentCounts = commentCounts;
        this.summaries = summaries;
        this.catalog = catalog;
        this.audit = audit;
    }

    /** 그 글의 열린 토론 — 없으면 404 DEBATE_NOT_FOUND */
    @Transactional(readOnly = true)
    public DebateItemView get(String postIdText) {
        Long postId = parseId(postIdText);
        PostDebateEntity row = postId == null ? null : debates.findById(postId).orElse(null);
        if (row == null) {
            throw new DebateNotFoundException();
        }
        return views(List.of(row)).get(0);
    }

    /**
     * 열기·설정 바꾸기·닫기 — 열려 있으면 그 줄, 닫았으면(또는 이미 닫혀 있었으면) 빈 값(컨트롤러가 204).
     * {@code enabled} 가 비면 400 {@code fields.enabled}. 열 때 글이 없거나 지웠으면 404 POST_NOT_FOUND.
     */
    @Transactional
    public Optional<DebateItemView> save(AdminActor actor, String postIdText, DebateSaveRequest body) {
        if (body == null || body.enabled() == null) {
            throw ValidationException.of("enabled", new MessageRef("debate.enabled.required"));
        }
        Long postId = parseId(postIdText);
        Instant now = Instant.now();
        if (body.enabled()) {
            AdminPostRow post = postId == null ? null : summaries.rows(List.of(postId)).get(postId);
            if (post == null || post.goneAt(now)) {
                throw new DebatePostNotFoundException();
            }
        } else if (postId == null) {
            return Optional.empty();
        }
        apply(actor, postId, body.enabled(), body.neutralEnabled(), body.stanceChangeAllowed(), null);
        if (!body.enabled()) {
            return Optional.empty();
        }
        return Optional.of(views(List.of(debates.findById(postId).orElseThrow())).get(0));
    }

    /**
     * 운영자의 토론 열기·바꾸기·닫기 + 감사 — 관리자 토론 PUT 과 하이라이트 만들기·고치기가 함께 쓴다. 글 확인은 부르는 쪽이 한다.
     *
     * @param via 감사에 남길 경로(하이라이트에서 했으면 {@code HIGHLIGHT}, 아니면 null)
     */
    @Transactional
    public PostDebateWriter.Change apply(AdminActor actor, long postId, boolean enabled, Boolean neutralEnabled, Boolean stanceChangeAllowed, String via) {
        PostDebateWriter.Change change = writer.apply(postId, enabled, neutralEnabled, stanceChangeAllowed, PostDebateEntity.BY_OPERATOR, actor.operatorId(),
                Instant.now());
        AuditEvent event = null;
        if (change.opened()) {
            event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.DEBATE_OPEN, AuditResult.SUCCESS).with("after", change.after().toMap());
        } else if (change.closed()) {
            event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.DEBATE_CLOSE, AuditResult.SUCCESS).with("before", change.before().toMap());
        } else if (change.updated()) {
            Map<String, Object> before = change.before().toMap();
            Map<String, Object> changedBefore = new LinkedHashMap<>();
            Map<String, Object> changedAfter = new LinkedHashMap<>();
            change.after().toMap().forEach((k, v) -> {
                if (!Objects.equals(before.get(k), v)) {
                    changedBefore.put(k, before.get(k));
                    changedAfter.put(k, v);
                }
            });
            event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.DEBATE_UPDATE, AuditResult.SUCCESS).with("before", changedBefore)
                    .with("after", changedAfter);
        }
        if (event != null) {
            event = event.target("POST", Long.toString(postId));
            audit.record(via == null ? event : event.with("via", via));
            log.info("글 토론 {} postId={} by operatorId={}", change.opened() ? "열기" : change.closed() ? "닫기" : "설정 바꾸기", postId, actor.operatorId());
        }
        return change;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 줄들 — 글·작성자·투표·의견 수·대기 고정 요청·하이라이트를 한 번에 읽는다 */
    private List<DebateItemView> views(List<PostDebateEntity> rows) {
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> postIds = rows.stream().map(PostDebateEntity::getPostId).toList();
        Map<Long, AdminPostRow> postRows = summaries.rows(postIds);
        Map<Long, ProfileEntity> members = summaries.authors(postRows.values());
        Map<Long, DebateVotesView> votes = debateQueries.votesOf(postIds);
        Map<Long, HighlightCommentCountsView> counts = commentCounts.counts(postIds, Map.of(), votes, now);
        Map<Long, Integer> pending = debateCounts.pendingPinRequests(postIds, now);
        Map<Long, Long> highlightIds = new HashMap<>();
        highlights.findByPostIdIn(postIds).forEach(h -> highlightIds.put(h.getPostId(), h.getId()));
        List<DebateItemView> out = new ArrayList<>();
        for (PostDebateEntity d : rows) {
            AdminPostRow post = postRows.get(d.getPostId());
            AdminPostRefView postView = post == null ? null : summaries.view(post, s, members, now);
            HighlightCommentCountsView c = counts.get(d.getPostId());
            Long highlightId = highlightIds.get(d.getPostId());
            out.add(new DebateItemView(Long.toString(d.getPostId()), postView, d.isNeutralEnabled(), d.isStanceChangeAllowed(), d.openedByView(),
                    votes.getOrDefault(d.getPostId(), DebateVotesView.EMPTY), c == null ? 0 : c.pro() + c.con() + c.neutral(),
                    pending.getOrDefault(d.getPostId(), 0), highlightId == null ? null : Long.toString(highlightId), d.getCreatedAt(), d.getUpdatedAt()));
        }
        return out;
    }

    /** 이 글의 하이라이트 ID(없으면 null) */
    Long highlightIdOf(long postId) {
        return highlights.findByPostId(postId).map(HighlightEntity::getId).orElse(null);
    }

    /** 문자열 ID → long. 형식이 틀리면 null */
    static Long parseId(String raw) {
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
