package com.community.hotdeal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.NotAuthorException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.group.service.GroupPostGate;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.entity.DealView;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.exception.DealEndRequestAuthorLimitException;
import com.community.hotdeal.exception.DealEndRequestDailyLimitException;
import com.community.hotdeal.exception.DealEndRequestExistsException;
import com.community.hotdeal.exception.DealEndRequestNotAllowedException;
import com.community.hotdeal.exception.DealEndRequestOwnException;
import com.community.hotdeal.exception.DealEndedException;
import com.community.hotdeal.exception.DealKeepLimitException;
import com.community.hotdeal.exception.DealModerateForbiddenException;
import com.community.hotdeal.exception.DealNotEndedException;
import com.community.hotdeal.exception.DealNotFlaggedException;
import com.community.hotdeal.exception.DealNotFoundException;
import com.community.hotdeal.exception.DealReopenExpiredException;
import com.community.hotdeal.exception.DealReopenUsedException;
import com.community.hotdeal.repository.master.DealEndRequestStore;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 핫딜 상태 API — 작성자 종료·되돌리기(D2)·「아직 진행 중」(keep)·종료 요청, 가격 허위 신고의 종료 요청 접수(D4). docs/features/hotdeal-contract.md 「권한·정책」·확정 가정
 * 「종료 요청」·서버 단계 고정 규칙 4·5·9·11, 메모 1-4·2. 응답은 모두 새 {@code PostDeal}.
 *
 * <ul>
 *   <li><b>대상</b>: 보이는 글(없음·지움 404 {@code POST_NOT_FOUND}, 숨김 403 {@code POST_HIDDEN}, 소모임 비공개는 소모임 판정) · 핫딜 게시판 글이고 딜 줄이 있어야 한다(아니면 404
 *       {@code DEAL_NOT_FOUND}). 작성자 동작은 쓴 회원만({@code 403 FORBIDDEN}).</li>
 *   <li><b>잠금</b>: 딜 행을 잠가 다시 읽고 바꾼다({@link DealEndService#lock}) — 종료 요청 문턱 경계·종료·keep·자동 종료가 줄을 선다. 종료 요청은 그 전에 요청자 단위 잠금
 *       ({@code deal-end-request:<회원>})을 잡아 하루·작성자 한도를 동시 요청이 넘지 못하게 한다(잠그는 차례: 요청자 → 딜 행).</li>
 *   <li><b>종료 요청</b>(규칙 4): 로그인(401) → 사유(400 {@code fields.reason}) → 대상 → 자기 글 {@code 403 DEAL_END_REQUEST_OWN} → 자격(그 게시판 쓰기 판정 — {@code writeMin}·
 *       이메일 인증·정지 아님·사업자 아님·역할 권한 — 과 첫 등급(새싹) 아님, 매니저·스텝 면제) {@code 403 DEAL_END_REQUEST_NOT_ALLOWED} → 잠금 → 끝난 딜(종료·기간 종료)
 *       {@code 409 DEAL_ENDED} → 이미 보냄 {@code 409 DEAL_END_REQUEST_EXISTS}(한도를 쓰지 않는다) → 하루(한국 0시) {@code 429 DEAL_END_REQUEST_DAILY_LIMIT} →
 *       같은 작성자 기간 {@code 429 DEAL_END_REQUEST_AUTHOR_LIMIT {limit, days}}(0 = 없음) → 저장. 의심인 동안의 요청은 의심 구간({@code flagged_period}).
 *       지금 회차(마지막 keep 뒤)의 요청이 문턱({@code endRequestThreshold})에 닿으면 의심(FLAGGED) + 그 회차 줄을 의심 구간으로 + 작성자 알림 {@code DEAL_FLAGGED},
 *       keep 을 다 쓴 딜이면 관리자 알림 {@code HOTDEAL_KEEP_LIMIT}.</li>
 *   <li><b>keep</b>: 의심(FLAGGED — 기간 종료·종료가 아님)만({@code 409 DEAL_NOT_FLAGGED}), {@code keepMaxCount}(0 = 없음)까지({@code 409 DEAL_KEEP_LIMIT}). 의심을 풀고 그 글의
 *       의심 구간 표시를 끈다(작성자가 반박한 회차 — 이 회차 요청자는 보상받지 않는다).</li>
 *   <li><b>작성자 종료</b>: 진행·의심만(종료·기간 종료 {@code 409 DEAL_ENDED}) → ENDED AUTHOR + 의심 구간 요청자 보상(규칙 5).</li>
 *   <li><b>되돌리기</b>(D2·규칙 11): 끝나지 않았으면 {@code 409 DEAL_NOT_ENDED} · 이미 썼으면 {@code 409 DEAL_REOPEN_USED} · 작성자 종료가 아니거나 24시간이 지났으면
 *       {@code 409 DEAL_REOPEN_EXPIRED}(자동·운영진 종료는 운영진만 재개). 재고가 품절이었으면 「모름」, 기간이 지났으면 작성자에게 {@code DEAL_REOPENED_FIX_ENDS}. 의심 표시는 그대로 둔다.</li>
 *   <li><b>게시판 매니저·스텝 종료·재개</b>(사용자 결정 2026-11-01 — 메모 4절): 로그인(401) → 사유(종료 1~200자 필수 · 재개 0~200자, 앞뒤 공백 뗌 — {@code 400 fields.reason}) → 대상
 *       → 담당(그 글 게시판의 매니저·스텝이고 활성 회원 — 다른 게시판 매니저·일반 회원·정지 {@code 403 DEAL_MODERATE_FORBIDDEN}, 요청마다 다시 판정) → 잠금 → 상태(종료: 이미 종료
 *       {@code 409 DEAL_ENDED} · 재개: 종료가 아님 {@code 409 DEAL_NOT_ENDED}). 길은 운영진 종료·재개와 같다(shared {@link DealModeration} — ENDED MANAGER + 의심 구간 보상 + 작성자 알림
 *       {@code DEAL_MANAGER_ENDED}(사유) · 재개는 의심·keep·요청 리셋 + 기간이 지났으면 {@code DEAL_REOPENED_FIX_ENDS}). 감사 {@code HOTDEAL_END}·{@code HOTDEAL_REOPEN}(행위자 회원,
 *       대상 POST, detail {@code boardId}·{@code role}·{@code reason}).</li>
 * </ul>
 */
@Service
public class DealStateService {

    public static final List<String> REASONS = List.of("SOLD_OUT", "PRICE_CHANGED", "LINK_DEAD");
    public static final String PRICE_CHANGED = "PRICE_CHANGED";
    /** 매니저·스텝 종료·재개 사유 최대 길이 */
    public static final int MODERATE_REASON_MAX = 200;
    public static final String AUDIT_TARGET_POST = "POST";

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostQueryService postQueries;
    private final GroupPostGate groupGate;
    private final PostDealRepository deals;
    private final DealEndService ends;
    private final DealEndRequestStore requests;
    private final DealSettingsReader settings;
    private final DealNotices notices;
    private final DealReader reader;
    private final PostCounterQueries counters;
    private final SnowflakeIdGenerator ids;
    private final DealModeration moderation;
    private final AuditLogService audit;

    public DealStateService(BoardCatalog catalog, BoardActorResolver actors, PostQueryService postQueries, GroupPostGate groupGate, PostDealRepository deals,
                            DealEndService ends, DealEndRequestStore requests, DealSettingsReader settings, DealNotices notices, DealReader reader,
                            PostCounterQueries counters, SnowflakeIdGenerator ids, DealModeration moderation, AuditLogService audit) {
        this.moderation = moderation;
        this.audit = audit;
        this.catalog = catalog;
        this.actors = actors;
        this.postQueries = postQueries;
        this.groupGate = groupGate;
        this.deals = deals;
        this.ends = ends;
        this.requests = requests;
        this.settings = settings;
        this.notices = notices;
        this.reader = reader;
        this.counters = counters;
        this.ids = ids;
    }

    /** 종료 요청 접수 결과 */
    public enum Submit {
        CREATED, NOT_FOUND, ENDED, EXISTS, DAILY_LIMIT, AUTHOR_LIMIT
    }

    private record Target(PostEntity post, EffectiveBoard board) {
    }

    // ── 작성자 ──────────────────────────────────────────────────────────────

    /** 작성자 종료 */
    @Transactional
    public PostDealView end(String postId, Long userId) {
        Instant now = ends.now();
        Target t = authorTarget(postId, userId, now);
        PostDealEntity d = ends.lock(t.post().getId()).orElseThrow(DealNotFoundException::new);
        if (!d.view(settings.current().autoExpireDays(), now).live()) {
            throw DealEndedException.action();
        }
        ends.endByAuthor(d, userId, now);
        return view(t, userId, now);
    }

    /** 작성자 되돌리기(D2) */
    @Transactional
    public PostDealView reopen(String postId, Long userId) {
        Instant now = ends.now();
        Target t = authorTarget(postId, userId, now);
        PostDealEntity d = ends.lock(t.post().getId()).orElseThrow(DealNotFoundException::new);
        if (!d.isEnded()) {
            throw DealNotEndedException.reopen();
        }
        if (d.isReopenUsed()) {
            throw new DealReopenUsedException();
        }
        if (!d.canReopenByAuthor(now)) {
            throw new DealReopenExpiredException();
        }
        d.reopenByAuthor(now);
        ends.notifyIfExpired(d, now);
        return view(t, userId, now);
    }

    /** 「아직 진행 중」 */
    @Transactional
    public PostDealView keep(String postId, Long userId) {
        Instant now = ends.now();
        Target t = authorTarget(postId, userId, now);
        PostDealEntity d = ends.lock(t.post().getId()).orElseThrow(DealNotFoundException::new);
        DealSettings s = settings.current();
        if (d.view(s.autoExpireDays(), now) != DealView.FLAGGED) {
            throw new DealNotFlaggedException();
        }
        if (s.keepMaxCount() > 0 && d.getKeepCount() >= s.keepMaxCount()) {
            throw new DealKeepLimitException();
        }
        d.keep(now);
        requests.clearFlagged(d.getPostId());
        return view(t, userId, now);
    }

    // ── 게시판 매니저·스텝 ────────────────────────────────────────────────────

    /**
     * 게시판 매니저·스텝 종료 — 운영진 종료와 같은 길(ENDED MANAGER + 의심 구간 보상 + 작성자 알림 {@code DEAL_MANAGER_ENDED} 사유)
     *
     * @param ip 감사 기록에 남길 요청자 IP
     */
    @Transactional
    public PostDealView moderateEnd(String postId, Long userId, String rawReason, String ip) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        String reason = moderateReason(rawReason, true);
        Instant now = ends.now();
        Target t = target(postId, userId, catalog.snapshot(), now);
        long id = t.post().getId();
        String role = requireBoardRole(t, userId);
        PostDealEntity d = ends.lock(id).orElseThrow(DealNotFoundException::new);
        String before = d.view(settings.current().autoExpireDays(), now).name();
        switch (moderation.end(id, DealModeration.Actor.boardMember(userId), reason)) {
            case NOT_FOUND -> throw new DealNotFoundException();
            case FORBIDDEN -> throw new DealModerateForbiddenException();
            case STATE_CHANGED -> throw DealEndedException.action();
            case CHANGED -> {
            }
        }
        audit.record(moderateEvent(AuditActions.HOTDEAL_END, userId, ip, t, role, reason).with("before", before));
        return view(t, userId, now);
    }

    /** 게시판 매니저·스텝 재개 — 운영진 재개와 같은 리셋(의심·keep·종료 요청 줄) + 기간이 지났으면 작성자에게 「마감을 고쳐 주세요」 */
    @Transactional
    public PostDealView moderateReopen(String postId, Long userId, String rawReason, String ip) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        String reason = moderateReason(rawReason, false);
        Instant now = ends.now();
        Target t = target(postId, userId, catalog.snapshot(), now);
        long id = t.post().getId();
        String role = requireBoardRole(t, userId);
        PostDealEntity d = ends.lock(id).orElseThrow(DealNotFoundException::new);
        String endedBy = d.getEndedBy();
        switch (moderation.reopen(id, DealModeration.Actor.boardMember(userId))) {
            case NOT_FOUND -> throw new DealNotFoundException();
            case FORBIDDEN -> throw new DealModerateForbiddenException();
            case STATE_CHANGED -> throw DealNotEndedException.reopen();
            case CHANGED -> {
            }
        }
        AuditEvent event = moderateEvent(AuditActions.HOTDEAL_REOPEN, userId, ip, t, role, reason);
        audit.record(endedBy == null ? event : event.with("endedBy", endedBy));
        return view(t, userId, now);
    }

    /** 그 글 게시판에서 맡은 자리(MANAGER · STAFF) — 매니저·스텝이 아니거나 활성 회원이 아니면(정지·영구 정지) 403 */
    private String requireBoardRole(Target t, long userId) {
        String role = moderation.boardRole(t.board().id(), userId);
        if (role == null) {
            throw new DealModerateForbiddenException();
        }
        return role;
    }

    /** 사유 — NUL 을 빼고 앞뒤 공백을 뗀 뒤 {@value #MODERATE_REASON_MAX}자까지. 종료는 필수, 재개는 비면 null */
    static String moderateReason(String raw, boolean required) {
        String text = raw == null ? "" : raw.replace("\u0000", "").strip();
        if (text.isEmpty()) {
            if (required) {
                throw ValidationException.of("reason", new MessageRef("hotdeal.moderate.reason.required"));
            }
            return null;
        }
        if (text.codePointCount(0, text.length()) > MODERATE_REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("hotdeal.admin.reason.tooLong", String.valueOf(MODERATE_REASON_MAX)));
        }
        return text;
    }

    /** 감사 — 행위자 회원(매니저·스텝), 대상 글, detail {@code boardId}·{@code role}(MANAGER · STAFF)·{@code reason}(있으면) */
    private static AuditEvent moderateEvent(String action, long userId, String ip, Target t, String role, String reason) {
        AuditEvent event = AuditEvent.byMember(userId, ip, action, AuditResult.SUCCESS).target(AUDIT_TARGET_POST, Long.toString(t.post().getId()))
                .with("boardId", Long.toString(t.board().id())).with("role", role);
        return reason == null ? event : event.with("reason", reason);
    }

    // ── 종료 요청 ────────────────────────────────────────────────────────────

    /** 종료 요청 */
    @Transactional
    public PostDealView request(String postId, Long userId, String reason) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (reason == null || !REASONS.contains(reason)) {
            throw ValidationException.of("reason", new MessageRef("hotdeal.endRequest.reason"));
        }
        Instant now = ends.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        Target t = target(postId, userId, s, now);
        if (userId.equals(t.post().getAuthorId())) {
            throw new DealEndRequestOwnException();
        }
        BoardActor actor = actors.resolve(userId, s);
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (!eligible(t.board(), actor, s)) {
            throw new DealEndRequestNotAllowedException();
        }
        DealSettings settingsNow = settings.current();
        switch (submit(t.post().getId(), userId, reason, false, now)) {
            case NOT_FOUND -> throw new DealNotFoundException();
            case ENDED -> throw DealEndedException.action();
            case EXISTS -> throw new DealEndRequestExistsException();
            case DAILY_LIMIT -> throw new DealEndRequestDailyLimitException(settingsNow.endRequestDailyLimit());
            case AUTHOR_LIMIT -> throw new DealEndRequestAuthorLimitException(settingsNow.endRequestPerAuthorLimit(), settingsNow.endRequestPerAuthorDays());
            case CREATED -> {
            }
        }
        return view(t, userId, now);
    }

    /**
     * 가격 허위 신고(D4 · 규칙 11) — 신고 접수 트랜잭션 안에서 부른다(신고 API 응답은 그대로). 신고한 회원이 종료 요청 자격이 있으면 {@code PRICE_CHANGED} 요청 1건({@code from_report})으로
     * 접수한다. 이미 요청했거나 끝난 딜·한도·자격 미달이면 요청 없이 신고만 받는다(조용히 — 한도를 쓰지 않는다).
     *
     * @return 요청 줄을 만들었다
     */
    @Transactional
    public boolean fromReport(PostEntity post, EffectiveBoard board, BoardActor actor, BoardCatalogSnapshot s) {
        if (actor.guest() || !DealWriter.dealBoard(board) || actor.userId().equals(post.getAuthorId()) || !eligible(board, actor, s)) {
            return false;
        }
        return submit(post.getId(), actor.userId(), PRICE_CHANGED, true, ends.now()) == Submit.CREATED;
    }

    /** 종료 요청 자격(규칙 4) — 그 게시판 글쓰기 판정 + 첫 등급(새싹) 아님(매니저·스텝 면제) — 핫딜 쓰기 자격({@link DealWriter#requireWriter})과 같다 */
    static boolean eligible(EffectiveBoard b, BoardActor actor, BoardCatalogSnapshot s) {
        if (actor.guest() || BoardAccessPolicy.writeBlocked(b, actor, s) != null) {
            return false;
        }
        return actor.staffOf(b.id()) || actor.gradeKey() == null || !actor.gradeKey().equals(s.firstGradeKey());
    }

    /** 잠금 → 끝난 딜 → 이미 보냄 → 하루 한도 → 작성자 기간 한도 → 저장 → 문턱이면 의심 */
    private Submit submit(long postId, long memberId, String reason, boolean fromReport, Instant now) {
        counters.lockWriter("deal-end-request:" + memberId);
        PostDealEntity d = ends.lock(postId).orElse(null);
        if (d == null) {
            return Submit.NOT_FOUND;
        }
        DealSettings s = settings.current();
        if (!d.view(s.autoExpireDays(), now).live()) {
            return Submit.ENDED;
        }
        if (requests.exists(postId, memberId)) {
            return Submit.EXISTS;
        }
        Instant dayStart = ServiceTime.dateOf(now).atStartOfDay(ServiceTime.ZONE).toInstant();
        if (requests.countByMemberSince(memberId, dayStart) >= s.endRequestDailyLimit()) {
            return Submit.DAILY_LIMIT;
        }
        if (s.endRequestPerAuthorLimit() > 0 && d.getAuthorId() != null
                && requests.countByMemberAndAuthorSince(memberId, d.getAuthorId(), now.minus(Duration.ofDays(s.endRequestPerAuthorDays())))
                >= s.endRequestPerAuthorLimit()) {
            return Submit.AUTHOR_LIMIT;
        }
        boolean flagged = d.getFlaggedAt() != null;
        requests.insert(ids.nextId(), postId, memberId, d.getAuthorId(), reason, flagged, fromReport, now);
        if (!flagged && requests.countRound(postId, d.getKeptAt()) >= s.endRequestThreshold()) {
            d.flag(now);
            requests.markRoundFlagged(postId, d.getKeptAt());
            notices.flagged(d);
            if (s.keepMaxCount() > 0 && d.getKeepCount() >= s.keepMaxCount()) {
                notices.keepLimit(d); // 작성자는 더 「아직 진행 중」을 누를 수 없다 — 「운영자 확인 중」
            }
        }
        return Submit.CREATED;
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    private Target authorTarget(String postId, Long userId, Instant now) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Target t = target(postId, userId, catalog.snapshot(), now);
        if (!userId.equals(t.post().getAuthorId())) {
            throw new NotAuthorException();
        }
        return t;
    }

    private Target target(String postId, Long userId, BoardCatalogSnapshot s, Instant now) {
        PostEntity post = postQueries.requireVisible(postId, now);
        EffectiveBoard b = groupGate.resolve(s, post, userId).board();
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        if (!DealWriter.dealBoard(b)) {
            throw new DealNotFoundException();
        }
        return new Target(post, b);
    }

    /** 바꾼 뒤의 딜 — 딜 행 변경을 먼저 내보내고(같은 딜 수·집계는 SQL 로 센다) 지금 상태로 읽는다 */
    private PostDealView view(Target t, Long viewerId, Instant now) {
        deals.flush();
        PostDealView v = reader.forPost(t.post(), t.board(), viewerId, now);
        if (v == null) {
            throw new DealNotFoundException();
        }
        return v;
    }
}
