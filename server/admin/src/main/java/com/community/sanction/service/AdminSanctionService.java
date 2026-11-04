package com.community.sanction.service;

import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.report.exception.ReportCaseNotFoundException;
import com.community.report.repository.master.ReportCaseRepository;
import com.community.sanction.dto.SanctionIssue;
import com.community.sanction.dto.SanctionOperator;
import com.community.sanction.dto.request.SanctionCreateRequest;
import com.community.sanction.dto.request.SanctionLiftRequest;
import com.community.sanction.dto.response.SanctionCountsView;
import com.community.sanction.dto.response.SanctionLineView;
import com.community.sanction.dto.response.SanctionListView;
import com.community.sanction.dto.response.SanctionPreviewView;
import com.community.sanction.dto.response.SanctionTargetView;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.exception.SanctionNotFoundException;
import com.community.sanction.exception.SanctionTargetNotFoundException;
import com.community.sanction.repository.master.AdminSanctionStore;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 제재(docs/features/member-sanction-contract.md · admin-web {@code useSanctionApi.ts}) — 목록 · 하나 · 회원 찾기 · 현황 · 부여 · 해제. 규칙은 shared {@link MemberSanctionService}.
 *
 * <ul>
 *   <li><b>목록</b>: {@code scope} ALL(기본) · ACTIVE(진행 중 정지·영구 정지) · CLOSED(끝남·해제·무효) · {@code kind} · {@code searchField} NICKNAME(기본 — 2자 이상 부분 일치, 아니면
 *       400 {@code fields.q}) · SANCTION_NO(숫자 정확히, 아니면 400 {@code fields.q}) · {@code from}·{@code to}(부여 시각 UTC ISO, 양 끝 포함 — 형식 오류는 그 칸, 시작 &gt; 끝은
 *       {@code fields.from}) · {@code userId} · {@code sort} newest(기본)·oldest · {@code page} 0부터 · {@code size} 기본 20 최대 50. 모르는 값은 기본값.</li>
 *   <li><b>권한</b>: 입구는 컨트롤러 선언, 부여·해제의 단계별 권한(경고·기간 정지 {@code sanction.manage} · 영구 정지·건너뛰기·영구 정지 해제 {@code sanction.ban})은
 *       shared 서비스가 보고 모자라면 거부 감사 + 403 {@code PERMISSION_DENIED}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminSanctionService {

    static final int SIZE_DEFAULT = 20;
    static final int SIZE_MAX = 50;
    static final int QUERY_MIN = 2;
    static final int QUERY_MAX = 50;
    static final int TARGETS_MAX = 10;

    private final MemberSanctionService sanctions;
    private final AdminSanctionStore store;
    private final SanctionViews views;
    private final ReportCaseRepository reportCases;
    private final AccessDeniedRecorder denied;

    public AdminSanctionService(MemberSanctionService sanctions, AdminSanctionStore store, SanctionViews views, ReportCaseRepository reportCases,
                                AccessDeniedRecorder denied) {
        this.sanctions = sanctions;
        this.store = store;
        this.views = views;
        this.reportCases = reportCases;
        this.denied = denied;
    }

    public SanctionListView list(String scopeRaw, String kindRaw, String searchField, String qRaw, String fromRaw, String toRaw, String userIdRaw, String sort,
                                 String pageRaw, String sizeRaw) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String scope = scopeRaw == null ? "ALL" : scopeRaw.strip().toUpperCase(Locale.ROOT);
        SanctionKind kind = SanctionKind.of(kindRaw).orElse(null);
        String q = trim(qRaw);
        String nicknameLike = null;
        Long sanctionNo = null;
        if (q != null) {
            if ("SANCTION_NO".equalsIgnoreCase(trim(searchField))) {
                String digits = q.startsWith("#") ? q.substring(1) : q;
                sanctionNo = parseId(digits);
                if (sanctionNo == null) {
                    invalid.put("q", new MessageRef("sanction.query.number"));
                }
            } else if (q.codePointCount(0, q.length()) < QUERY_MIN) {
                invalid.put("q", new MessageRef("sanction.query.tooShort", QUERY_MIN));
            } else {
                nicknameLike = "%" + likeEscape(cut(q, QUERY_MAX).toLowerCase(Locale.ROOT)) + "%";
            }
        }
        Instant from = instant(fromRaw, "from", invalid);
        Instant to = instant(toRaw, "to", invalid);
        if (from != null && to != null && from.isAfter(to)) {
            invalid.put("from", new MessageRef("sanction.range.invalid"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        Long userId = trim(userIdRaw) == null ? null : parseIdOr(userIdRaw, -1L);
        int page = Math.max(0, number(pageRaw, 0));
        int size = Math.min(SIZE_MAX, Math.max(1, number(sizeRaw, SIZE_DEFAULT)));
        AdminSanctionStore.Filter filter = new AdminSanctionStore.Filter(scope, kind, nicknameLike, sanctionNo, from, to, userId, "oldest".equalsIgnoreCase(trim(sort)));
        Instant now = sanctions.now();
        long[] counts = store.counts(now);
        List<SanctionLineView> items = store.page(filter, now, page, size).stream().map(r -> SanctionViews.line(r, now)).toList();
        return new SanctionListView(new SanctionCountsView(counts[0], counts[1], counts[2]), store.total(filter, now), page, size, items);
    }

    public SanctionLineView get(String idText) {
        Long id = parseId(idText);
        return views.line((id == null ? java.util.Optional.<SanctionRow>empty() : sanctions.find(id)).orElseThrow(SanctionNotFoundException::new));
    }

    /** 부여 대화창의 회원 찾기 — 닉네임 2자 이상(아니면 400 {@code fields.q}), 최대 10명, 탈퇴·탈퇴 진행 중 제외 */
    public List<SanctionTargetView> targets(String qRaw) {
        String q = trim(qRaw);
        if (q == null || q.codePointCount(0, q.length()) < QUERY_MIN) {
            throw ValidationException.of("q", new MessageRef("sanction.query.tooShort", QUERY_MIN));
        }
        String lower = cut(q, QUERY_MAX).toLowerCase(Locale.ROOT);
        return store.targets("%" + likeEscape(lower) + "%", lower, TARGETS_MAX).stream()
                .map(t -> new SanctionTargetView(Long.toString(t.id()), t.nickname(), t.gradeKey(), t.status(), Math.min(SanctionLadder.WARNING_LIMIT, t.warningCount())))
                .toList();
    }

    public SanctionPreviewView preview(String userIdText) {
        Long userId = parseId(userIdText);
        if (userId == null) {
            throw new SanctionTargetNotFoundException();
        }
        return views.preview(userId);
    }

    @Transactional
    public SanctionLineView create(AdminActor actor, SanctionCreateRequest body) {
        Long userId = body == null ? null : parseId(body.userId());
        if (userId == null) {
            throw new SanctionTargetNotFoundException();
        }
        Long reportCaseId = null;
        if (trim(body.reportCaseId()) != null) {
            reportCaseId = parseId(body.reportCaseId());
            if (reportCaseId == null || !reportCases.existsById(reportCaseId)) {
                throw new ReportCaseNotFoundException();
            }
        }
        SanctionRow row = sanctions.issue(new SanctionIssue(userId, body.kind(), body.reason(), Boolean.TRUE.equals(body.skipStep()), body.confirmNickname(), reportCaseId, ""),
                operator(actor, "MEMBER", Long.toString(userId)));
        return views.line(row);
    }

    @Transactional
    public SanctionLineView lift(AdminActor actor, String idText, SanctionLiftRequest body) {
        Long id = parseId(idText);
        if (id == null) {
            throw new SanctionNotFoundException();
        }
        return views.line(sanctions.lift(id, body == null ? null : body.reason(), operator(actor, "SANCTION", Long.toString(id))));
    }

    /** 이 운영자의 제재 권한 — 모자라면 거부 감사(따로 커밋) 뒤 403 */
    public SanctionOperator operator(AdminActor actor, String targetType, String targetId) {
        return new SanctionOperator(actor.operatorId(), actor.ip(), actor.can(Permission.SANCTION_MANAGE), actor.can(Permission.SANCTION_BAN), code -> {
            denied.record(actor, "MISSING_PERMISSION", targetType, targetId, List.of(code));
            throw new PermissionDeniedException();
        });
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

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

    private static Long parseIdOr(String raw, Long fallback) {
        Long id = parseId(raw);
        return id == null ? fallback : id;
    }

    static int number(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static Instant instant(String raw, String field, Map<String, MessageRef> invalid) {
        String text = trim(raw);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            invalid.put(field, new MessageRef("sanction.time.invalid"));
            return null;
        }
    }

    static String trim(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        return s.isEmpty() ? null : s;
    }

    static String cut(String text, int max) {
        return text.codePointCount(0, text.length()) <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }

    /** LIKE 의 특수 글자를 글자 그대로(ESCAPE '\') */
    static String likeEscape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
