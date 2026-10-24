package com.community.highlight.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.debate.entity.DebateStance;
import com.community.debate.repository.master.DebateQueries;
import com.community.highlight.dto.response.HighlightOpinionListView;
import com.community.highlight.dto.response.HighlightOpinionPageView;
import com.community.highlight.dto.response.HighlightOpinionView;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.exception.HighlightNotFoundException;
import com.community.highlight.exception.HighlightOpinionNotFoundException;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.permission.dto.AdminActor;
import com.community.post.entity.ContentFormat;
import com.community.post.repository.master.PostCounterQueries;
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
 * 하이라이트 의견 관리 — 목록 {@code highlight.read}, 고정·숨김 {@code highlight.manage}(컨트롤러가 선언). community docs/api-debate.md 7절을 로보얌에 맞춘 것.
 * 의견 = 그 하이라이트 글의 <b>입장 배지가 붙은 원댓글</b>(지운 것 제외).
 *
 * <ul>
 *   <li><b>목록</b>: 입장마다 고정한 의견 먼저 → 추천 많은 순 → 최근 순. 숨긴 의견도 들어 있다({@code hidden}). {@code stance} 를 주면 그쪽만 읽고 다른 쪽은 {@code total} 만.</li>
 *   <li><b>고정</b>({@code debate_pins}): 이미 고정이면 그대로. 지금은 관리자 목록의 순서만 바꾼다(공개 홈 카드는 홈 작업 때).</li>
 *   <li><b>숨김</b>: 신고 숨김과 같은 칸에 까닭 STAFF 로 숨긴다 — 공개 댓글 목록에는 자리(HIDDEN)만 남고 글의 보이는 댓글 수에서 빠진다. 숨김 해제는 STAFF 숨김만 풀고 수를 되돌린다
 *       (신고 숨김은 그대로 — 여기서 풀지 않는다). 이미 그 상태면 그대로. 글쓴이에게 알리지 않는다.</li>
 *   <li>대상이 그 글의 의견이 아니면 {@code 404 DEBATE_OPINION_NOT_FOUND}. 바뀐 일만 감사(HIGHLIGHT_OPINION_PIN · UNPIN · HIDE · UNHIDE, 대상 COMMENT)에 남는다.</li>
 * </ul>
 */
@Service
public class HighlightOpinionService {

    private static final Logger log = LoggerFactory.getLogger(HighlightOpinionService.class);

    static final int SIZE_DEFAULT = 30;
    static final int SIZE_MAX = 100;
    static final int PREVIEW_MAX = 200;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final HighlightRepository highlights;
    private final CommentRepository comments;
    private final DebateQueries debate;
    private final PostCounterQueries counters;
    private final ProfileRepository profiles;
    private final AuditLogService audit;

    public HighlightOpinionService(HighlightRepository highlights, CommentRepository comments, DebateQueries debate, PostCounterQueries counters,
                                   ProfileRepository profiles, AuditLogService audit) {
        this.highlights = highlights;
        this.comments = comments;
        this.debate = debate;
        this.counters = counters;
        this.profiles = profiles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public HighlightOpinionListView list(String idText, String stanceRaw, String pageRaw, String sizeRaw) {
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
        HighlightEntity h = requireHighlight(idText);
        Instant now = Instant.now();
        List<CommentEntity> all = comments.findByPostIdOrderByIdAsc(h.getPostId());
        Set<Long> pinned = debate.pinnedOf(h.getPostId());
        Map<Long, Integer> replies = new HashMap<>();
        all.stream().filter(c -> c.getParentId() != null && !c.isDeleted() && !c.isHiddenAt(now)).forEach(c -> replies.merge(c.getParentId(), 1, Integer::sum));
        List<CommentEntity> opinions = all.stream().filter(c -> isOpinion(c)).toList();
        Map<Long, ProfileEntity> members = members(opinions);
        HighlightOpinionPageView pro = page(opinions, DebateStance.PRO, only, page, size, pinned, replies, members, now);
        HighlightOpinionPageView con = page(opinions, DebateStance.CON, only, page, size, pinned, replies, members, now);
        return new HighlightOpinionListView(Long.toString(h.getId()), Long.toString(h.getPostId()), h.isDebate(), h.isDebateOpenAt(now), pro, con);
    }

    @Transactional
    public HighlightOpinionView pin(AdminActor actor, String idText, String commentIdText, boolean on) {
        HighlightEntity h = requireHighlight(idText);
        CommentEntity c = requireOpinion(h, commentIdText, false);
        Instant now = Instant.now();
        boolean was = debate.pinnedOf(h.getPostId()).contains(c.getId());
        if (was != on) {
            if (on) {
                debate.pin(c.getId(), h.getPostId(), actor.operatorId(), now);
            } else {
                debate.unpin(c.getId());
            }
            record(actor, on ? AuditActions.HIGHLIGHT_OPINION_PIN : AuditActions.HIGHLIGHT_OPINION_UNPIN, h, c);
        }
        return viewOne(h, c, now);
    }

    @Transactional
    public HighlightOpinionView hide(AdminActor actor, String idText, String commentIdText, boolean on) {
        HighlightEntity h = requireHighlight(idText);
        CommentEntity c = requireOpinion(h, commentIdText, true);
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
            record(actor, on ? AuditActions.HIGHLIGHT_OPINION_HIDE : AuditActions.HIGHLIGHT_OPINION_UNHIDE, h, c);
            log.info("하이라이트 의견 {} highlightId={} commentId={} by operatorId={}", on ? "숨김" : "숨김 해제", h.getId(), c.getId(), actor.operatorId());
        }
        return viewOne(h, c, now);
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private static boolean isOpinion(CommentEntity c) {
        return c.getParentId() == null && c.getStance() != null && !c.isDeleted();
    }

    private HighlightEntity requireHighlight(String idText) {
        Long id = HighlightAdminService.parseId(idText);
        if (id == null) {
            throw new HighlightNotFoundException();
        }
        return highlights.findById(id).orElseThrow(HighlightNotFoundException::new);
    }

    /** 그 하이라이트 글의 의견 — 아니면 404 DEBATE_OPINION_NOT_FOUND. {@code lock} 이면 행을 쓰기 잠금으로 읽는다 */
    private CommentEntity requireOpinion(HighlightEntity h, String commentIdText, boolean lock) {
        Long commentId = HighlightAdminService.parseId(commentIdText);
        if (commentId == null) {
            throw new HighlightOpinionNotFoundException();
        }
        return (lock ? comments.findWithLockByPostIdAndId(h.getPostId(), commentId) : comments.findByPostIdAndId(h.getPostId(), commentId))
                .filter(HighlightOpinionService::isOpinion).orElseThrow(HighlightOpinionNotFoundException::new);
    }

    private void record(AdminActor actor, String action, HighlightEntity h, CommentEntity c) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("COMMENT", Long.toString(c.getId()))
                .with("highlightId", Long.toString(h.getId())).with("postId", Long.toString(h.getPostId())).with("stance", c.getStance()));
    }

    private HighlightOpinionPageView page(List<CommentEntity> opinions, DebateStance stance, DebateStance only, int page, int size, Set<Long> pinned,
                                          Map<Long, Integer> replies, Map<Long, ProfileEntity> members, Instant now) {
        List<CommentEntity> side = opinions.stream().filter(c -> stance.name().equals(c.getStance()))
                .sorted(Comparator.<CommentEntity>comparingInt(c -> pinned.contains(c.getId()) ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(CommentEntity::getLikeCount).reversed())
                        .thenComparing(CommentEntity::getId, Comparator.reverseOrder()))
                .toList();
        if (only != null && only != stance) {
            return new HighlightOpinionPageView(side.size(), page, size, false, List.of());
        }
        long from = (long) (page - 1) * size;
        List<HighlightOpinionView> items = side.stream().skip(from).limit(size).map(c -> view(c, pinned, replies, members, now)).toList();
        return new HighlightOpinionPageView(side.size(), page, size, from + items.size() < side.size(), items);
    }

    private HighlightOpinionView viewOne(HighlightEntity h, CommentEntity c, Instant now) {
        Set<Long> pinned = debate.pinnedOf(h.getPostId());
        Map<Long, Integer> replies = new HashMap<>();
        replies.put(c.getId(), (int) comments.findByPostIdOrderByIdAsc(h.getPostId()).stream()
                .filter(r -> c.getId().equals(r.getParentId()) && !r.isDeleted() && !r.isHiddenAt(now)).count());
        return view(c, pinned, replies, members(List.of(c)), now);
    }

    private static HighlightOpinionView view(CommentEntity c, Set<Long> pinned, Map<Long, Integer> replies, Map<Long, ProfileEntity> members, Instant now) {
        ProfileEntity profile = c.getAuthorId() == null ? null : members.get(c.getAuthorId());
        String name = c.getAuthorId() == null ? (c.getGuestName() == null ? "" : c.getGuestName()) : (profile == null ? "" : profile.getNickname());
        boolean hidden = c.isHiddenAt(now);
        return new HighlightOpinionView(Long.toString(c.getId()), c.getStance(), c.getAuthorId() == null ? "GUEST" : "MEMBER",
                c.getAuthorId() == null ? null : Long.toString(c.getAuthorId()), name, preview(c.getContentFormat(), c.getContent()), c.getLikeCount(),
                replies.getOrDefault(c.getId(), 0), c.getCreatedAt(), pinned.contains(c.getId()), hidden, hidden ? c.getHiddenReason() : null);
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
        text = SPACES.matcher(text.replace(' ', ' ')).replaceAll(" ").strip();
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
