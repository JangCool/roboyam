package com.community.popular.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRequestQueue;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.popular.dto.request.PopularExcludeRequest;
import com.community.popular.dto.response.PopularExcludedView;
import com.community.popular.dto.response.PopularOperatorView;
import com.community.popular.dto.response.PopularRankingView;
import com.community.popular.entity.PopularExclusionEntity;
import com.community.popular.entity.PopularSettingEntity;
import com.community.popular.exception.PopularPostNotFoundException;
import com.community.popular.repository.master.PopularExclusionRepository;
import com.community.popular.repository.master.PopularRankQueries;
import com.community.popular.repository.master.PopularSettingRepository;
import com.community.post.dto.AdminPostRow;
import com.community.post.service.AdminPostSummaries;
import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
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
 * 인기글 관리 — 기간별 순위 보기({@code popular.read}), 인기글에서 빼기·다시 넣기({@code popular.manage}). docs/features/popular-highlight-contract.md 「서버 구현 결과」
 * (community docs/api-popular.md 8절을 로보얌에 맞춘 것).
 *
 * <ul>
 *   <li><b>순위</b>: 마지막 계산의 스냅샷에서 지금 볼 수 없는 글을 걸러 번호를 이어서 붙인다(공개 인기글과 같은 차례·같은 글). 계산 뒤에 뺀 글은 {@code excluded} 로 자리에 남는다.
 *       점수는 계산 시점, 추천·댓글·조회는 지금 값.</li>
 *   <li><b>빼기</b>: 사유(INAPPROPRIATE · PROMOTION · MANIPULATION · DISPUTE · OTHER) 필수, 메모 100자까지(OTHER 면 필수). 없는 글·지운 글은 404 {@code POST_NOT_FOUND}
 *       (숨긴 글·19금 글도 뺄 수 있다). 이미 빠져 있으면 그대로(멱등 — 사유를 덮어쓰지 않는다, 200). 새로 빼면 다시 계산을 요청하고 감사 {@code POPULAR_EXCLUDE}(201).</li>
 *   <li><b>다시 넣기</b>: 뺀 줄을 지운다 — 빠져 있지 않아도(없는 글 포함) 성공(멱등). 지웠으면 다시 계산을 요청하고 감사 {@code POPULAR_RESTORE}. 점수와 조건이 받쳐 줄 때만 순위로 돌아온다.</li>
 *   <li>뺀 글은 DB 배치의 후보에서 빠지고(R__batch_24) 공개 인기글이 읽을 때도 거른다. 글쓴이에게 알리지 않는다. 글 자체(게시판·검색·최신)는 그대로다.</li>
 * </ul>
 */
@Service
public class PopularAdminService {

    private static final Logger log = LoggerFactory.getLogger(PopularAdminService.class);

    /** 빼기 결과 — 새로 뺐으면 {@code created} */
    public record ExcludeResult(PopularExcludedView view, boolean created) {
    }

    private final PopularSettingRepository settings;
    private final PopularExclusionRepository exclusions;
    private final PopularRankQueries ranks;
    private final PopularPreview preview;
    private final PopularRecompute recompute;
    private final BatchRequestQueue requests;
    private final AdminPostSummaries summaries;
    private final BoardCatalog catalog;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public PopularAdminService(PopularSettingRepository settings, PopularExclusionRepository exclusions, PopularRankQueries ranks, PopularPreview preview,
                               PopularRecompute recompute, BatchRequestQueue requests, AdminPostSummaries summaries, BoardCatalog catalog,
                               OperatorRepository operators, AuditLogService audit) {
        this.settings = settings;
        this.exclusions = exclusions;
        this.ranks = ranks;
        this.preview = preview;
        this.recompute = recompute;
        this.requests = requests;
        this.summaries = summaries;
        this.catalog = catalog;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PopularRankingView ranking(String periodRaw) {
        String period = PopularRules.period(periodRaw);
        if (period == null) {
            throw ValidationException.of("period", new MessageRef("popular.period.invalid"));
        }
        PopularSettingEntity setting = settings.findById(PopularSettingEntity.ROW_ID).orElse(null);
        boolean excludeStaff = setting == null || setting.isExcludeStaffBoards();
        PopularRankQueries.Range range = ranks.range(period);
        PopularPreview.Ranking ranking = preview.ranking(period, excludeStaff);
        List<PopularExclusionEntity> rows = exclusions.findTop200ByOrderByExcludedAtDescPostIdDesc();
        return new PopularRankingView(period, range == null ? null : range.from(), range == null ? null : range.to(),
                setting == null ? PopularRules.MONDAY : setting.getWeekStart(), ranking.computedAt(), requests.hasOpen(BatchCatalog.POPULAR_RANK.key()),
                ranking.items(), excludedViews(rows), PopularRules.EXCLUSION_REASONS);
    }

    @Transactional
    public ExcludeResult exclude(AdminActor actor, PopularExcludeRequest body) {
        PopularExcludeRequest b = body == null ? new PopularExcludeRequest(null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Long postId = parseId(b.postId());
        if (postId == null) {
            fields.put("postId", new MessageRef("popular.exclude.postId.invalid"));
        }
        String reason = b.reason() == null ? null : b.reason().strip();
        if (reason == null || !PopularRules.EXCLUSION_REASONS.contains(reason)) {
            fields.put("reason", new MessageRef("popular.exclude.reason.invalid"));
        }
        String memo = b.memo() == null || b.memo().isBlank() ? null : b.memo().strip();
        if (memo != null && memo.codePointCount(0, memo.length()) > PopularRules.EXCLUSION_MEMO_MAX) {
            fields.put("memo", new MessageRef("popular.exclude.memo.tooLong", String.valueOf(PopularRules.EXCLUSION_MEMO_MAX)));
        } else if (memo == null && PopularRules.REASON_OTHER.equals(reason)) {
            fields.put("memo", new MessageRef("popular.exclude.memo.required"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = Instant.now();
        AdminPostRow post = summaries.rows(List.of(postId)).get(postId);
        if (post == null || post.goneAt(now)) {
            throw new PopularPostNotFoundException();
        }
        Optional<PopularExclusionEntity> existing = exclusions.findById(postId);
        if (existing.isPresent()) {
            return new ExcludeResult(excludedViews(List.of(existing.get())).get(0), false);
        }
        PopularExclusionEntity row = PopularExclusionEntity.create(postId, reason, memo, actor.operatorId(), now);
        exclusions.persist(row);
        exclusions.flush();
        boolean requested = recompute.request(actor.operatorId());
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POPULAR_EXCLUDE, AuditResult.SUCCESS)
                .target("POST", Long.toString(postId)).with("reason", reason).with("recomputeRequested", requested);
        audit.record(memo == null ? event : event.with("memo", memo)); // detail 맵은 null 값을 받지 않는다
        log.info("인기글에서 뺐다 postId={} reason={} by operatorId={}", postId, reason, actor.operatorId());
        return new ExcludeResult(excludedViews(List.of(row)).get(0), true);
    }

    @Transactional
    public void restore(AdminActor actor, String postIdText) {
        Long postId = parseId(postIdText);
        if (postId == null) {
            throw ValidationException.of("postId", new MessageRef("popular.exclude.postId.invalid"));
        }
        Optional<PopularExclusionEntity> existing = exclusions.findById(postId);
        if (existing.isEmpty()) {
            return;
        }
        String reason = existing.get().getReason();
        exclusions.delete(existing.get());
        exclusions.flush();
        boolean requested = recompute.request(actor.operatorId());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POPULAR_RESTORE, AuditResult.SUCCESS)
                .target("POST", Long.toString(postId)).with("reason", reason).with("recomputeRequested", requested));
        log.info("인기글에 다시 넣었다 postId={} by operatorId={}", postId, actor.operatorId());
    }

    /** 뺀 글 줄 — 글·작성자·운영자를 한 번에 읽는다 */
    private List<PopularExcludedView> excludedViews(List<PopularExclusionEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        Map<Long, AdminPostRow> posts = summaries.rows(rows.stream().map(PopularExclusionEntity::getPostId).toList());
        Map<Long, ProfileEntity> members = summaries.authors(posts.values());
        Map<Long, String> operatorNames = new HashMap<>();
        List<Long> operatorIds = rows.stream().map(PopularExclusionEntity::getExcludedBy).filter(Objects::nonNull).distinct().toList();
        if (!operatorIds.isEmpty()) {
            operators.findAllById(operatorIds).forEach(o -> operatorNames.put(o.getId(), o.getOperatorName()));
        }
        return rows.stream().map(r -> {
            AdminPostRow post = posts.get(r.getPostId());
            boolean available = post != null && AdminPostRow.PUBLIC.equals(post.stateAt(now,
                    post.boardId() != null && s.board(post.boardId()).map(b -> b.active()).orElse(false)));
            PopularOperatorView by = r.getExcludedBy() == null ? null : new PopularOperatorView(Long.toString(r.getExcludedBy()), operatorNames.get(r.getExcludedBy()));
            return new PopularExcludedView(Long.toString(r.getPostId()), available, post == null ? null : summaries.view(post, s, members, now), r.getReason(),
                    r.getMemo(), by, r.getExcludedAt());
        }).toList();
    }

    /** 문자열 글 ID → long. 형식이 틀리면 null */
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
