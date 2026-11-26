package com.community.counsel.service;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftStatus;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.comment.dto.response.CommentView;
import com.community.comment.entity.CommentEntity;
import com.community.comment.exception.CommentNotFoundException;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.service.CommentService;
import com.community.counsel.dto.response.CounselAiAnswerView;
import com.community.counsel.dto.response.CounselRankRowView;
import com.community.counsel.dto.response.CounselRankingView;
import com.community.counsel.dto.response.PostCounselView;
import com.community.counsel.entity.CounselPostEntity;
import com.community.counsel.entity.CounselState;
import com.community.counsel.exception.CounselAlreadyPickedException;
import com.community.counsel.exception.CounselNotFoundException;
import com.community.counsel.exception.CounselPickForbiddenException;
import com.community.counsel.exception.CounselPickOwnException;
import com.community.counsel.exception.CounselPickUnavailableException;
import com.community.counsel.repository.master.CounselPickStore;
import com.community.counsel.repository.master.CounselPostRepository;
import com.community.counsel.repository.master.CounselQueries;
import com.community.group.service.GroupPostGate;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 연애상담소 공개 API(ai-love-counsel-contract — 화면 타입 {@code useCounselApi.types.ts}·{@code PostRow.counsel}). 상담사 답은 그 글의 댓글(작성자 = 상담사 AI 회원)이고
 * 판정·답 만들기는 관리자 서버 실행기({@code CounselRunner})가 한다 — 여기서는 읽기 · 조언 고르기 · 주간 순위만.
 *
 * <ul>
 *   <li><b>공개 상태</b>({@link #status}): 위험 사연 {@code RISK} → 고름 {@code PICKED} → AI 답이 하나라도 {@code ANSWERED} → 그 밖(판정 전 포함) {@code WAITING}.</li>
 *   <li><b>조언 고르기</b>: 사연 쓴 사람만(403 {@code COUNSEL_PICK_FORBIDDEN}) · 판정을 마친 위험 아닌 사연만(409 {@code COUNSEL_PICK_UNAVAILABLE}) · 한 번만(409 {@code COUNSEL_ALREADY_PICKED})
 *       · 그 글의 원댓글만(없으면 404 {@code COMMENT_NOT_FOUND}, 지운·숨긴 답·답글·상담사가 아닌 AI 의 답은 409 {@code COUNSEL_PICK_UNAVAILABLE}) · 내 답은 안 됨(409 {@code COUNSEL_PICK_OWN}).
 *       고르면 같은 트랜잭션에서 주간 집계(그 주 · 게시판 · 상담사, 사람 회원은 한 줄)에 +1.</li>
 *   <li><b>순위</b>: 이번 주(한국 시각 월 00시 ~ 일 24시) 고른 수 — 사람 회원 한 줄 + 상담사마다 한 줄, 많은 순(같으면 사람 회원 → 상담사 만든 차례), 비율은 합 100(큰 나머지 반올림).</li>
 * </ul>
 */
@Service
public class CounselService {

    static final String WAITING = "WAITING";
    static final String ANSWERED = "ANSWERED";
    static final String PICKED = "PICKED";
    static final String RISK = "RISK";
    static final String KIND_HUMAN = "HUMAN";
    static final String KIND_AI = "AI";

    private final BoardCatalog catalog;
    private final PostQueryService postQueries;
    private final GroupPostGate groupGate;
    private final CounselPostRepository rows;
    private final CounselQueries queries;
    private final CounselPickStore picks;
    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries aiQueries;
    private final CommentService comments;
    private final CommentRepository commentRows;

    public CounselService(BoardCatalog catalog, PostQueryService postQueries, GroupPostGate groupGate, CounselPostRepository rows, CounselQueries queries,
                          CounselPickStore picks, AiMemberProfileRepository accounts, AiMemberQueries aiQueries, CommentService comments, CommentRepository commentRows) {
        this.catalog = catalog;
        this.postQueries = postQueries;
        this.groupGate = groupGate;
        this.rows = rows;
        this.queries = queries;
        this.picks = picks;
        this.accounts = accounts;
        this.aiQueries = aiQueries;
        this.comments = comments;
        this.commentRows = commentRows;
    }

    // ── 글 보기의 상담 칸 ──────────────────────────────────────────────────

    /** {@code GET /api/posts/{id}/counsel} — 글을 열 수 있어야 한다(글 보기와 같은 판정 — 404 · 403). 상담 글이 아니면 404 {@code COUNSEL_NOT_FOUND} */
    @Transactional(readOnly = true)
    public PostCounselView view(String postIdText, Long userId, String ip) {
        Opened o = open(postIdText, userId);
        return view(o, rows.findById(o.post().getId()).orElse(null), userId, ip);
    }

    /** {@code PUT /api/posts/{id}/counsel/pick} — 응답은 바뀐 상담 칸 */
    @Transactional
    public PostCounselView pick(String postIdText, String commentIdText, Long userId, String ip) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (commentIdText == null || commentIdText.isBlank()) {
            throw ValidationException.of("commentId", new MessageRef("counsel.commentId.required"));
        }
        Opened o = open(postIdText, userId);
        PostEntity post = o.post();
        if (!userId.equals(post.getAuthorId())) {
            throw new CounselPickForbiddenException();
        }
        CounselPostEntity row = rows.findWithLockByPostId(post.getId()).orElse(null);
        if (row == null || row.isRisk()) {
            throw new CounselPickUnavailableException(); // 위험 사연 · 아직 판정 전(1분 안 — 실행기가 판정하면 고를 수 있다)
        }
        if (row.getPickedCommentId() != null) {
            throw new CounselAlreadyPickedException();
        }
        Long commentId = PostQueryService.parseId(commentIdText);
        CommentEntity c = commentId == null ? null : commentRows.findByPostIdAndId(post.getId(), commentId).orElse(null);
        if (c == null) {
            throw new CommentNotFoundException();
        }
        Instant now = Instant.now();
        if (c.isDeleted() || c.isHiddenAt(now) || c.getParentId() != null) {
            throw new CounselPickUnavailableException(); // 지운·숨긴 답 · 답글(원댓글만 고른다)
        }
        if (userId.equals(c.getAuthorId())) {
            throw new CounselPickOwnException();
        }
        long key = CounselPostEntity.HUMAN;
        if (c.getAuthorId() != null) {
            AiMemberProfileEntity ai = accounts.findByUserId(c.getAuthorId()).orElse(null);
            if (ai != null) {
                if (!ai.isCounselor()) {
                    throw new CounselPickUnavailableException(); // 상담사가 아닌 AI 회원의 댓글
                }
                key = ai.getId();
            }
        }
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        row.pick(c.getId(), key, at);
        picks.add(row.getBoardId(), CounselRules.weekStart(at), key);
        rows.flush();
        return view(o, row, userId, ip);
    }

    /** 글을 열 수 있는 사람인가(글 보기와 같은 판정) + 상담 글인가 */
    private Opened open(String postIdText, Long userId) {
        Instant now = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, now); // 없거나 지운 글 404 POST_NOT_FOUND
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = groupGate.resolve(s, post, userId).board(); // 꺼진 게시판 · 소모임 · 19금 · 비밀글 — 글 보기와 같은 오류
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        if (post.isGroupPost() || !CounselRules.counselPrefix(b, post.getPrefixId())) {
            throw new CounselNotFoundException();
        }
        return new Opened(post, b, s);
    }

    private record Opened(PostEntity post, EffectiveBoard board, BoardCatalogSnapshot snapshot) {
    }

    private PostCounselView view(Opened o, CounselPostEntity row, Long userId, String ip) {
        PostEntity post = o.post();
        List<AiMemberProfileEntity> allCounselors = accounts.findByCounselorTrueOrderByIdAsc();
        Map<Long, AiMemberProfileEntity> byId = new LinkedHashMap<>();
        allCounselors.forEach(a -> byId.put(a.getId(), a));
        boolean mine = userId != null && userId.equals(post.getAuthorId());
        if (row != null && row.isRisk()) {
            return new PostCounselView(RISK, false, null, null, List.of());
        }
        // 답할 상담사 — 판정 뒤에는 그때 정한 상담사(지운 계정·상담사에서 빠진 계정은 답이 있을 때만), 판정 전에는 지금 켜진 상담사
        List<AiMemberProfileEntity> order = new ArrayList<>();
        if (row != null && row.getState() == CounselState.OPEN) {
            for (Long id : row.getCounselorIds()) {
                AiMemberProfileEntity a = byId.get(id);
                if (a == null) {
                    a = accounts.findById(id).orElse(null);
                }
                if (a != null) {
                    order.add(a);
                }
            }
        } else {
            allCounselors.stream().filter(AiMemberProfileEntity::isEnabled).forEach(order::add);
        }
        List<Long> userIds = order.stream().map(AiMemberProfileEntity::getUserId).toList();
        Map<Long, Long> answerByUser = new HashMap<>();
        queries.answers(List.of(post.getId()), userIds).getOrDefault(post.getId(), List.of()).forEach(a -> answerByUser.putIfAbsent(a.authorId(), a.commentId()));
        Map<Long, CommentView> views = comments.counselAnswerViews(post, o.board(), o.snapshot(), new ArrayList<>(answerByUser.values()), userId, ip);
        Set<Long> writing = writing(post.getId(), order);
        Instant now = Instant.now();
        boolean open = row == null || row.getClosedAt() == null || now.isBefore(post.getCreatedAt().plus(CounselRules.GIVE_UP));
        Map<Long, String> nicknames = userIds.isEmpty() ? Map.of() : aiQueries.nicknames(userIds);
        List<CounselAiAnswerView> answers = new ArrayList<>();
        int answered = 0;
        for (AiMemberProfileEntity a : order) {
            Long commentId = answerByUser.get(a.getUserId());
            CommentView comment = commentId == null ? null : views.get(commentId);
            if (comment == null && !(open && a.isCounselor()) && !writing.contains(a.getId())) {
                continue; // 끝내 답하지 못했다(시한이 지났거나 상담사에서 빠졌다) — 칸에서 뺀다
            }
            if (comment != null) {
                answered++;
            }
            PostAuthorView.AuthorAiView ai = comment != null && comment.author() != null && comment.author().ai() != null ? comment.author().ai()
                    : new PostAuthorView.AuthorAiView(a.getProvider().name(), a.getModel());
            answers.add(new CounselAiAnswerView(Long.toString(a.getId()), nicknames.getOrDefault(a.getUserId(), ""), a.getCounselStyle() == null ? "" : a.getCounselStyle(),
                    ai, comment));
        }
        String status = status(row, answered);
        boolean canPick = mine && row != null && row.getPickedCommentId() == null;
        return new PostCounselView(status, canPick, row == null || row.getPickedCommentId() == null ? null : Long.toString(row.getPickedCommentId()),
                post.getCreatedAt().plus(CounselRules.ANSWER_DUE), answers);
    }

    /** 대기(검수 대기함)·승인(곧 등록) 중인 상담사 답이 있는 상담사 */
    private Set<Long> writing(long postId, Collection<AiMemberProfileEntity> counselors) {
        Set<Long> out = new HashSet<>();
        if (counselors.isEmpty()) {
            return out;
        }
        for (AiDraftEntity d : queries.drafts(postId, counselors.stream().map(AiMemberProfileEntity::getId).toList())) {
            if (d.getStatus() == AiDraftStatus.PENDING || d.getStatus() == AiDraftStatus.APPROVED) {
                out.add(d.getAiMemberId());
            }
        }
        return out;
    }

    /** 공개 상태 — 위험 → 고름 → AI 답 있음 → 기다림(판정 전 · 답 없음) */
    static String status(CounselPostEntity row, int aiAnswers) {
        if (row != null && row.isRisk()) {
            return RISK;
        }
        if (row != null && row.getPickedCommentId() != null) {
            return PICKED;
        }
        return aiAnswers > 0 ? ANSWERED : WAITING;
    }

    // ── 주간 순위 ──────────────────────────────────────────────────────────

    /** {@code GET /api/boards/{slug}/counsel/ranking} — 「AI 상담소」 말머리가 없는 게시판이면 404 {@code COUNSEL_NOT_FOUND} */
    @Transactional(readOnly = true)
    public CounselRankingView ranking(String slug) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, slug);
        if (b.prefixes().stream().noneMatch(p -> p.counsel() && !p.common())) {
            throw new CounselNotFoundException();
        }
        LocalDate week = CounselRules.weekStart(Instant.now());
        Map<Long, Integer> counted = picks.week(b.id(), week);
        // 줄 — 사람 회원 한 줄 + 지금 상담사(만든 차례) + 이번 주 고른 적이 있는 옛 상담사
        List<AiMemberProfileEntity> counselors = new ArrayList<>(accounts.findByCounselorTrueOrderByIdAsc());
        Set<Long> listed = new HashSet<>();
        counselors.forEach(a -> listed.add(a.getId()));
        for (Long id : counted.keySet()) {
            if (id != CounselPostEntity.HUMAN && !listed.contains(id)) {
                accounts.findById(id).ifPresent(counselors::add);
                listed.add(id);
            }
        }
        Map<Long, String> nicknames = counselors.isEmpty() ? Map.of() : aiQueries.nicknames(counselors.stream().map(AiMemberProfileEntity::getUserId).toList());
        int[] values = new int[counselors.size() + 1];
        values[0] = counted.getOrDefault(CounselPostEntity.HUMAN, 0);
        for (int i = 0; i < counselors.size(); i++) {
            values[i + 1] = counted.getOrDefault(counselors.get(i).getId(), 0);
        }
        int[] pct = CounselRules.percents(values);
        record Line(int order, String kind, String name, PostAuthorView.AuthorAiView ai, int picks, int percent) {
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(0, KIND_HUMAN, null, null, values[0], pct[0]));
        for (int i = 0; i < counselors.size(); i++) {
            AiMemberProfileEntity a = counselors.get(i);
            lines.add(new Line(i + 1, KIND_AI, nicknames.getOrDefault(a.getUserId(), ""), new PostAuthorView.AuthorAiView(a.getProvider().name(), a.getModel()), values[i + 1],
                    pct[i + 1]));
        }
        lines.sort(Comparator.comparingInt(Line::picks).reversed().thenComparingInt(Line::order));
        List<CounselRankRowView> out = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            total += l.picks();
            out.add(new CounselRankRowView(i + 1, l.kind(), l.name(), l.ai(), l.picks(), l.percent()));
        }
        return new CounselRankingView(week.toString(), week.plusDays(6).toString(), total, out);
    }
}
