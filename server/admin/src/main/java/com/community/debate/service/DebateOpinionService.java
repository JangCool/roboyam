package com.community.debate.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.debate.dto.request.DebatePinRejectRequest;
import com.community.debate.dto.response.DebateOpinionListView;
import com.community.debate.dto.response.DebateOpinionPageView;
import com.community.debate.dto.response.DebateOpinionView;
import com.community.debate.dto.response.DebatePinRequestListView;
import com.community.debate.dto.response.DebatePinRequestView;
import com.community.debate.entity.DebatePinRequestEntity;
import com.community.debate.entity.DebateStance;
import com.community.debate.entity.PostDebateEntity;
import com.community.debate.exception.DebateOpinionNotFoundException;
import com.community.debate.exception.DebatePostNotFoundException;
import com.community.debate.exception.PinRequestException;
import com.community.debate.repository.master.DebatePinRequestRepository;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.repository.master.PostDebateRepository;
import com.community.permission.dto.AdminActor;
import com.community.post.entity.ContentFormat;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.service.AdminPostSummaries;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 토론 의견 관리(글 번호 기준) — 목록·대기 고정 요청 {@code post.read}, 고정·숨김·고정 요청 승인·거절 {@code post.manage}(컨트롤러가 선언).
 * 예전 하이라이트 의견 관리({@code /api/admin/highlights/{id}/opinions…})를 글 번호로 옮긴 것이다 — 응답 모양 그대로(docs/features/post-debate-contract.md).
 * 의견 = 그 글의 <b>입장 배지가 붙은 원댓글</b>(지운 것 제외). 토론이 닫혀 있어도 이미 쓴 의견은 다룬다. 글 행이 없거나 번호 형식이 틀리면 404 POST_NOT_FOUND.
 *
 * <ul>
 *   <li><b>목록</b>: 입장(찬성·반대·중립)마다 고정한 의견 먼저 → 추천 많은 순 → 최근 순. 숨긴 의견도 들어 있다({@code hidden}). {@code stance} 를 주면 그쪽만 읽고 다른 쪽은 {@code total} 만.</li>
 *   <li><b>고정</b>({@code debate_pins}): 이미 고정이면 그대로. 공개 토론 의견 목록의 맨 위 차례를 정한다.</li>
 *   <li><b>숨김</b>: 신고 숨김과 같은 칸에 까닭 STAFF 로 숨긴다 — 공개 댓글 목록에는 자리(HIDDEN)만 남고 글의 보이는 댓글 수에서 빠진다. 숨김 해제는 STAFF 숨김만 풀고 수를 되돌린다
 *       (신고 숨김은 그대로). 이미 그 상태면 그대로. 글쓴이에게 알리지 않는다.</li>
 *   <li><b>고정 요청</b>: 대기 목록·승인·거절 — 게시판 정책 {@code pinRequestReviewers} 에 OPERATOR 가 없어도 운영자는 처리할 수 있다(직접 고정과 같다).
 *       규칙은 {@link DebatePinDecisions}(요청한 회원에게 알림). 운영자가 의견을 직접 고정하면 그 의견의 대기 요청은 승인으로 닫힌다.</li>
 *   <li>대상이 그 글의 의견이 아니면 {@code 404 DEBATE_OPINION_NOT_FOUND}. 바뀐 일만 감사(HIGHLIGHT_OPINION_PIN · UNPIN · HIDE · UNHIDE · HIGHLIGHT_PIN_REQUEST_APPROVE ·
 *       REJECT — 이름은 기록 호환으로 그대로, 대상 COMMENT, {@code postId})에 남는다.</li>
 * </ul>
 */
@Service
public class DebateOpinionService {

    private static final Logger log = LoggerFactory.getLogger(DebateOpinionService.class);

    static final int SIZE_DEFAULT = 30;
    static final int SIZE_MAX = 100;
    static final int PREVIEW_MAX = 200;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final AdminPostSummaries summaries;
    private final PostDebateRepository debates;
    private final DebateAdminService debateAdmin;
    private final CommentRepository comments;
    private final DebateQueries debate;
    private final PostCounterQueries counters;
    private final ProfileRepository profiles;
    private final AuditLogService audit;
    private final DebatePinRequestRepository pinRequests;
    private final DebatePinDecisions pinDecisions;

    public DebateOpinionService(AdminPostSummaries summaries, PostDebateRepository debates, DebateAdminService debateAdmin, CommentRepository comments,
                                DebateQueries debate, PostCounterQueries counters, ProfileRepository profiles, AuditLogService audit,
                                DebatePinRequestRepository pinRequests, DebatePinDecisions pinDecisions) {
        this.summaries = summaries;
        this.debates = debates;
        this.debateAdmin = debateAdmin;
        this.comments = comments;
        this.debate = debate;
        this.counters = counters;
        this.profiles = profiles;
        this.audit = audit;
        this.pinRequests = pinRequests;
        this.pinDecisions = pinDecisions;
    }

    @Transactional(readOnly = true)
    public DebateOpinionListView list(String postIdText, String stanceRaw, String pageRaw, String sizeRaw) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        DebateStance only = null;
        try {
            only = DebateStance.parse(stanceRaw);
        } catch (IllegalArgumentException e) {
            fields.put("stance", new MessageRef("debate.stance.invalid"));
        }
        Integer page = intParam(pageRaw, 1, 1, Integer.MAX_VALUE);
        if (page == null) {
            fields.put("page", new MessageRef("highlight.opinions.page"));
        }
        Integer size = intParam(sizeRaw, SIZE_DEFAULT, 1, SIZE_MAX);
        if (size == null) {
            fields.put("size", new MessageRef("highlight.opinions.size", "1", String.valueOf(SIZE_MAX)));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long postId = requirePost(postIdText);
        Instant now = Instant.now();
        List<CommentEntity> all = comments.findByPostIdOrderByIdAsc(postId);
        Set<Long> pinned = debate.pinnedOf(postId);
        Map<Long, Integer> replies = new HashMap<>();
        all.stream().filter(c -> c.getParentId() != null && !c.isDeleted() && !c.isHiddenAt(now)).forEach(c -> replies.merge(c.getParentId(), 1, Integer::sum));
        List<CommentEntity> opinions = all.stream().filter(DebateOpinionService::isOpinion).toList();
        Map<Long, ProfileEntity> members = members(opinions);
        Map<Long, Long> pending = pendingByComment(postId);
        DebateOpinionPageView pro = page(opinions, DebateStance.PRO, only, page, size, pinned, replies, members, pending, now);
        DebateOpinionPageView con = page(opinions, DebateStance.CON, only, page, size, pinned, replies, members, pending, now);
        DebateOpinionPageView neutral = page(opinions, DebateStance.NEUTRAL, only, page, size, pinned, replies, members, pending, now);
        PostDebateEntity d = debates.findById(postId).orElse(null);
        return new DebateOpinionListView(highlightIdText(postId), Long.toString(postId), d != null, d != null, d == null || d.isNeutralEnabled(), pro, con,
                neutral);
    }

    @Transactional
    public DebateOpinionView pin(AdminActor actor, String postIdText, String commentIdText, boolean on) {
        long postId = requirePost(postIdText);
        CommentEntity c = requireOpinion(postId, commentIdText, false);
        Instant now = Instant.now();
        boolean was = debate.pinnedOf(postId).contains(c.getId());
        if (was != on) {
            if (on) {
                debate.pin(c.getId(), postId, actor.operatorId(), now);
                pinDecisions.closeOnDirectPin(c.getId(), actor.operatorId(), now); // 대기 요청이 있으면 승인으로 닫는다
            } else {
                debate.unpin(c.getId());
            }
            record(actor, on ? AuditActions.HIGHLIGHT_OPINION_PIN : AuditActions.HIGHLIGHT_OPINION_UNPIN, postId, c);
        }
        return viewOne(postId, c, now);
    }

    @Transactional
    public DebateOpinionView hide(AdminActor actor, String postIdText, String commentIdText, boolean on) {
        long postId = requirePost(postIdText);
        CommentEntity c = requireOpinion(postId, commentIdText, true);
        Instant now = Instant.now();
        boolean changed;
        if (on) {
            boolean wasVisible = !c.isHiddenAt(now);
            changed = c.hideByStaff(now);
            if (changed && wasVisible) {
                counters.addCommentCount(c.getPostId(), -1);
            }
        } else {
            changed = c.unhideByStaff(now);
            if (changed) {
                counters.addCommentCount(c.getPostId(), 1);
            }
        }
        if (changed) {
            comments.flush();
            record(actor, on ? AuditActions.HIGHLIGHT_OPINION_HIDE : AuditActions.HIGHLIGHT_OPINION_UNHIDE, postId, c);
            log.info("토론 의견 {} postId={} commentId={} by operatorId={}", on ? "숨김" : "숨김 해제", postId, c.getId(), actor.operatorId());
        }
        return viewOne(postId, c, now);
    }

    /** 대기 중인 고정 요청(오래된 것부터) — 지우거나 숨긴 의견의 요청은 뺀다 */
    @Transactional(readOnly = true)
    public DebatePinRequestListView pinRequests(String postIdText) {
        long postId = requirePost(postIdText);
        Instant now = Instant.now();
        Map<Long, CommentEntity> byId = new HashMap<>();
        List<CommentEntity> all = comments.findByPostIdOrderByIdAsc(postId);
        all.forEach(c -> byId.put(c.getId(), c));
        Set<Long> pinned = debate.pinnedOf(postId);
        Map<Long, Integer> replies = new HashMap<>();
        all.stream().filter(c -> c.getParentId() != null && !c.isDeleted() && !c.isHiddenAt(now)).forEach(c -> replies.merge(c.getParentId(), 1, Integer::sum));
        List<DebatePinRequestEntity> rows = pinRequests.findByPostIdAndStatusOrderByIdAsc(postId, DebatePinRequestEntity.PENDING).stream()
                .filter(r -> {
                    CommentEntity c = byId.get(r.getCommentId());
                    return c != null && !c.isDeleted() && !c.isHiddenAt(now);
                }).toList();
        Map<Long, ProfileEntity> members = members(rows.stream().map(r -> byId.get(r.getCommentId())).toList());
        Map<Long, Long> pending = pendingByComment(postId);
        List<DebatePinRequestView> items = rows.stream()
                .map(r -> pinRequestView(r, view(byId.get(r.getCommentId()), pinned, replies, members, pending, now))).toList();
        return new DebatePinRequestListView(highlightIdText(postId), Long.toString(postId), items);
    }

    /** 고정 요청 승인·거절 — 그 글의 요청이 아니면 404 PIN_REQUEST_NOT_FOUND. 거절 사유 100자 초과 400 {@code fields.reason} */
    @Transactional
    public DebatePinRequestView decidePinRequest(AdminActor actor, String postIdText, String requestIdText, boolean approve, DebatePinRejectRequest body) {
        String reason = approve || body == null ? null : body.reason();
        if (reason != null && reason.strip().codePointCount(0, reason.strip().length()) > DebatePinRequestEntity.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("debate.pinRequest.reason.tooLong", String.valueOf(DebatePinRequestEntity.REASON_MAX)));
        }
        long postId = requirePost(postIdText);
        Long requestId = DebateAdminService.parseId(requestIdText);
        DebatePinRequestEntity row = requestId == null ? null : pinRequests.findWithLockById(requestId).filter(r -> r.getPostId() == postId).orElse(null);
        if (row == null) {
            throw PinRequestException.notFound();
        }
        Instant now = Instant.now();
        pinDecisions.decide(row, approve, reason, DebatePinRequestEntity.BY_OPERATOR, actor.operatorId(), actor.operatorId(), now);
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(),
                        approve ? AuditActions.HIGHLIGHT_PIN_REQUEST_APPROVE : AuditActions.HIGHLIGHT_PIN_REQUEST_REJECT, AuditResult.SUCCESS)
                .target("COMMENT", Long.toString(row.getCommentId())).with("postId", Long.toString(postId)).with("requestId", Long.toString(row.getId()))
                .with("requesterId", Long.toString(row.getRequesterId()));
        // detail 맵은 null 값을 받지 않는다(Map.copyOf) — 승인이거나 사유 없이 거절하면 사유 칸을 뺀다
        audit.record(row.getRejectReason() == null ? event : event.with("reason", row.getRejectReason()));
        log.info("의견 고정 요청 {} requestId={} by operatorId={}", approve ? "승인" : "거절", row.getId(), actor.operatorId());
        CommentEntity c = comments.findByPostIdAndId(postId, row.getCommentId()).filter(x -> !x.isDeleted()).orElse(null);
        return pinRequestView(row, c == null ? null : viewOne(postId, c, now));
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 글 번호 — 형식이 틀리거나 글 행이 없으면 404 POST_NOT_FOUND(지운 글도 행이 있으면 다룬다) */
    private long requirePost(String postIdText) {
        Long postId = DebateAdminService.parseId(postIdText);
        if (postId == null || !summaries.rows(List.of(postId)).containsKey(postId)) {
            throw new DebatePostNotFoundException();
        }
        return postId;
    }

    private String highlightIdText(long postId) {
        Long id = debateAdmin.highlightIdOf(postId);
        return id == null ? null : Long.toString(id);
    }

    private static DebatePinRequestView pinRequestView(DebatePinRequestEntity r, DebateOpinionView opinion) {
        return new DebatePinRequestView(Long.toString(r.getId()), Long.toString(r.getCommentId()), r.getStatus(), opinion, Long.toString(r.getRequesterId()),
                r.getRejectReason(), r.getCreatedAt(), r.getDecidedAt());
    }

    /** 댓글 ID → 대기 요청 ID */
    private Map<Long, Long> pendingByComment(long postId) {
        Map<Long, Long> out = new HashMap<>();
        pinRequests.findByPostIdAndStatusOrderByIdAsc(postId, DebatePinRequestEntity.PENDING).forEach(r -> out.put(r.getCommentId(), r.getId()));
        return out;
    }

    private static boolean isOpinion(CommentEntity c) {
        return c.getParentId() == null && c.getStance() != null && !c.isDeleted();
    }

    /** 그 글의 의견 — 아니면 404 DEBATE_OPINION_NOT_FOUND. {@code lock} 이면 행을 쓰기 잠금으로 읽는다 */
    private CommentEntity requireOpinion(long postId, String commentIdText, boolean lock) {
        Long commentId = DebateAdminService.parseId(commentIdText);
        if (commentId == null) {
            throw new DebateOpinionNotFoundException();
        }
        return (lock ? comments.findWithLockByPostIdAndId(postId, commentId) : comments.findByPostIdAndId(postId, commentId))
                .filter(DebateOpinionService::isOpinion).orElseThrow(DebateOpinionNotFoundException::new);
    }

    private void record(AdminActor actor, String action, long postId, CommentEntity c) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("COMMENT", Long.toString(c.getId()))
                .with("postId", Long.toString(postId)).with("stance", c.getStance()));
    }

    private DebateOpinionPageView page(List<CommentEntity> opinions, DebateStance stance, DebateStance only, int page, int size, Set<Long> pinned,
                                       Map<Long, Integer> replies, Map<Long, ProfileEntity> members, Map<Long, Long> pending, Instant now) {
        List<CommentEntity> side = opinions.stream().filter(c -> stance.name().equals(c.getStance()))
                .sorted(Comparator.<CommentEntity>comparingInt(c -> pinned.contains(c.getId()) ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(CommentEntity::getLikeCount).reversed())
                        .thenComparing(CommentEntity::getId, Comparator.reverseOrder()))
                .toList();
        if (only != null && only != stance) {
            return new DebateOpinionPageView(side.size(), page, size, false, List.of());
        }
        long from = (long) (page - 1) * size;
        List<DebateOpinionView> items = side.stream().skip(from).limit(size).map(c -> view(c, pinned, replies, members, pending, now)).toList();
        return new DebateOpinionPageView(side.size(), page, size, from + items.size() < side.size(), items);
    }

    private DebateOpinionView viewOne(long postId, CommentEntity c, Instant now) {
        Set<Long> pinned = debate.pinnedOf(postId);
        Map<Long, Integer> replies = new HashMap<>();
        replies.put(c.getId(), (int) comments.findByPostIdOrderByIdAsc(postId).stream()
                .filter(r -> c.getId().equals(r.getParentId()) && !r.isDeleted() && !r.isHiddenAt(now)).count());
        return view(c, pinned, replies, members(List.of(c)), pendingByComment(postId), now);
    }

    private static DebateOpinionView view(CommentEntity c, Set<Long> pinned, Map<Long, Integer> replies, Map<Long, ProfileEntity> members,
                                          Map<Long, Long> pending, Instant now) {
        ProfileEntity profile = c.getAuthorId() == null ? null : members.get(c.getAuthorId());
        String name = c.getAuthorId() == null ? (c.getGuestName() == null ? "" : c.getGuestName()) : (profile == null ? "" : profile.getNickname());
        boolean hidden = c.isHiddenAt(now);
        return new DebateOpinionView(Long.toString(c.getId()), c.getStance(), c.getAuthorId() == null ? "GUEST" : "MEMBER",
                c.getAuthorId() == null ? null : Long.toString(c.getAuthorId()), name, preview(c.getContentFormat(), c.getContent()), c.getLikeCount(),
                replies.getOrDefault(c.getId(), 0), c.getCreatedAt(), pinned.contains(c.getId()), hidden, hidden ? c.getHiddenReason() : null, c.getDislikeCount(),
                pending.containsKey(c.getId()) ? Long.toString(pending.get(c.getId())) : null);
    }

    private Map<Long, ProfileEntity> members(List<CommentEntity> list) {
        List<Long> ids = list.stream().map(CommentEntity::getAuthorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProfileEntity> out = new HashMap<>();
        if (!ids.isEmpty()) {
            profiles.findAllById(ids).forEach(p -> out.put(p.getUserId(), p));
        }
        return out;
    }

    /** 본문 앞 {@value #PREVIEW_MAX}자 — HTML 은 태그를 떼고 엔티티를 풀고, 줄바꿈·이어진 공백은 공백 하나로. 넘으면 끝에 … */
    static String preview(ContentFormat format, String content) {
        if (content == null) {
            return "";
        }
        String text = format == ContentFormat.HTML ? HtmlUtils.htmlUnescape(TAG.matcher(content.replaceAll("(?i)<br\\s*/?>|</p>", " ")).replaceAll("")) : content;
        text = SPACES.matcher(text.replace('\u00A0', ' ')).replaceAll(" ").strip();
        if (text.codePointCount(0, text.length()) <= PREVIEW_MAX) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, PREVIEW_MAX)) + "…";
    }

    private static Integer intParam(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip().toLowerCase(Locale.ROOT));
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
