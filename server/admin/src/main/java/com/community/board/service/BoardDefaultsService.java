package com.community.board.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardNotificationValues;
import com.community.board.dto.BoardPrivacyValues;
import com.community.board.dto.BoardReportValues;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.response.BoardDefaultsResponse;
import com.community.board.dto.response.BoardPrivacyResponse;
import com.community.board.dto.response.BoardPrivacyTargetView;
import com.community.board.dto.response.BoardRefView;
import com.community.board.dto.response.BoardSettingsResponse;
import com.community.board.entity.BoardDefaultsEntity;
import com.community.board.entity.BoardEntity;
import com.community.board.entity.BoardNotificationSettingEntity;
import com.community.board.entity.BoardPrivacySettingEntity;
import com.community.board.entity.BoardReportSettingEntity;
import com.community.board.entity.BoardSecuritySettingEntity;
import com.community.board.repository.master.BoardDefaultsRepository;
import com.community.board.repository.master.BoardNotificationSettingRepository;
import com.community.board.repository.master.BoardPrivacyGateway;
import com.community.board.repository.master.BoardPrivacySettingRepository;
import com.community.board.repository.master.BoardReportSettingRepository;
import com.community.board.repository.master.BoardRepository;
import com.community.board.repository.master.BoardSecuritySettingRepository;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

/**
 * 게시판 기본 설정 — 일반 화면(전역 한 행, 게시판이 비워 둔 정책 칸이 따른다)과 하위 화면 4개(신고 정책·보안·알림·개인정보 보유기간).
 *
 * <ul>
 *   <li>일반 화면 저장은 칸 전부를 한 번에 받고 <b>바뀐 칸의 권한만</b> 본다 — 매니저·스텝 한도 칸 {@code board.staff.limit}, 나머지 {@code board.settings}.</li>
 *   <li>하위 화면 저장은 {@code board.settings}(컨트롤러). 입력 오류는 400 {@code VALIDATION} {@code fields.<칸>}. 범위는 {@link BoardFields}.</li>
 *   <li>바뀐 칸이 없으면 아무것도 쓰지 않는다(마지막 저장 시각도 그대로). 감사 기록은 바뀐 값만 — 개인정보 화면은 {@code BOARD_PRIVACY_RETENTION_UPDATE},
 *       나머지는 {@code BOARD_DEFAULTS_UPDATE}(detail 의 {@code tab} 으로 화면을 가른다).</li>
 *   <li>신고·보안·알림 설정은 이번에 저장만 한다(적용 지점은 후속 — 계약 「범위 밖」).</li>
 * </ul>
 */
@Service
public class BoardDefaultsService {

    private static final Logger log = LoggerFactory.getLogger(BoardDefaultsService.class);
    private static final Pattern CLOCK = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final int TARGET_LIMIT = 20;

    private final BoardDefaultsRepository defaults;
    private final BoardReportSettingRepository reports;
    private final BoardSecuritySettingRepository securities;
    private final BoardNotificationSettingRepository notifications;
    private final BoardPrivacySettingRepository privacies;
    private final BoardRepository boards;
    private final BoardPrivacyGateway privacyGateway;
    private final BoardViews views;
    private final GradeRepositoryQDSL grades;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;

    public BoardDefaultsService(BoardDefaultsRepository defaults, BoardReportSettingRepository reports, BoardSecuritySettingRepository securities,
                                BoardNotificationSettingRepository notifications, BoardPrivacySettingRepository privacies, BoardRepository boards,
                                BoardPrivacyGateway privacyGateway, BoardViews views, GradeRepositoryQDSL grades, AuditLogService audit,
                                AccessDeniedRecorder denied) {
        this.defaults = defaults;
        this.reports = reports;
        this.securities = securities;
        this.notifications = notifications;
        this.privacies = privacies;
        this.boards = boards;
        this.privacyGateway = privacyGateway;
        this.views = views;
        this.grades = grades;
        this.audit = audit;
        this.denied = denied;
    }

    // ── 일반 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BoardDefaultsResponse defaults() {
        return defaultsResponse(views.defaultsRow());
    }

    @Transactional
    public BoardDefaultsResponse saveDefaults(AdminActor actor, BoardDefaultsValues request) {
        BoardDefaultsEntity row = defaults.findWithLockById(BoardDefaultsEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.INTERNAL));
        if (request == null) {
            throw ValidationException.of("dateDisplay", new MessageRef("board.field.required"));
        }
        Map<String, Object> current = row.values().toMap();
        Map<String, Object> wanted = request.toMap();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        boolean settings = false;
        boolean limit = false;
        for (Map.Entry<String, Object> entry : wanted.entrySet()) {
            Object old = current.get(entry.getKey());
            if (!Objects.equals(old, entry.getValue())) {
                before.put(entry.getKey(), old);
                after.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
                if (BoardFields.DEFAULTS.get(entry.getKey()).staffLimit()) {
                    limit = true;
                } else {
                    settings = true;
                }
            }
        }
        List<String> missing = new ArrayList<>();
        if (settings && !actor.can(Permission.BOARD_SETTINGS)) {
            missing.add(Permission.BOARD_SETTINGS.code());
        }
        if (limit && !actor.can(Permission.BOARD_STAFF_LIMIT)) {
            missing.add(Permission.BOARD_STAFF_LIMIT.code());
        }
        if (!missing.isEmpty()) {
            denied.record(actor, "MISSING_PERMISSION", "BOARD_DEFAULTS", "general", missing);
            throw new PermissionDeniedException();
        }
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        BoardRules.check(wanted, BoardFields.DEFAULTS, "", false, gradeKeys(), false, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (!after.isEmpty()) {
            row.change(request, actor.operatorId(), Instant.now());
            defaults.flush();
            record(actor, AuditActions.BOARD_DEFAULTS_UPDATE, "general", before, after);
        }
        return defaultsResponse(row);
    }

    // ── 신고 정책 · 보안 · 알림 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BoardSettingsResponse<BoardReportValues> report() {
        BoardReportSettingEntity row = reportRow(false);
        return new BoardSettingsResponse<>(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    @Transactional
    public BoardSettingsResponse<BoardReportValues> saveReport(AdminActor actor, BoardReportValues request) {
        BoardReportSettingEntity row = reportRow(true);
        BoardReportValues body = required(request);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        BoardRules.check(body.toMap(), BoardFields.TABS, "", false, gradeKeys(), false, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (changed(actor, AuditActions.BOARD_DEFAULTS_UPDATE, "report", row.values().toMap(), body.toMap())) {
            row.change(body, actor.operatorId(), Instant.now());
        }
        return new BoardSettingsResponse<>(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    @Transactional(readOnly = true)
    public BoardSettingsResponse<BoardSecurityValues> security() {
        BoardSecuritySettingEntity row = securityRow(false);
        return new BoardSettingsResponse<>(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    @Transactional
    public BoardSettingsResponse<BoardSecurityValues> saveSecurity(AdminActor actor, BoardSecurityValues request) {
        BoardSecuritySettingEntity row = securityRow(true);
        BoardSecurityValues raw = required(request);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        BoardRules.check(raw.toMap(), BoardFields.TABS, "", false, Set.of(), false, invalid);
        List<String> words = new ArrayList<>();
        if (raw.blockedWords() == null) {
            invalid.put("blockedWords", new MessageRef("board.field.required"));
        } else {
            Set<String> seen = new LinkedHashSet<>();
            for (String word : raw.blockedWords()) {
                String w = BoardRules.strip(word);
                if (!w.isEmpty() && seen.add(w.toLowerCase(Locale.ROOT))) {
                    words.add(w);
                }
            }
            if (words.size() > BoardFields.BLOCKED_WORDS_MAX || words.stream().anyMatch(w -> BoardRules.length(w) > BoardFields.BLOCKED_WORD_LENGTH_MAX)) {
                invalid.put("blockedWords", new MessageRef("board.words.limit", String.valueOf(BoardFields.BLOCKED_WORDS_MAX),
                        String.valueOf(BoardFields.BLOCKED_WORD_LENGTH_MAX)));
            }
        }
        if (raw.guestNameMin() != null && raw.guestNameMax() != null && raw.guestNameMin() > raw.guestNameMax() && !invalid.containsKey("guestNameMax")) {
            invalid.put("guestNameMax", new MessageRef("board.guestName.order"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        BoardSecurityValues body = new BoardSecurityValues(raw.viewCountDedupHours(), raw.blockedWordsEnabled(), raw.blockedWordsAction(), words,
                raw.newMemberLinkLimit(), raw.newMemberImageLimit(), raw.duplicatePostWindowMinutes(), raw.externalLinkNofollow(), raw.externalLinkWarn(),
                raw.guestCaptchaRequired(), raw.guestNameMin(), raw.guestNameMax(), raw.guestPasswordMin(), raw.guestLinkLimit(), raw.guestImageUploadAllowed());
        Map<String, Object> oldMap = row.values().toMap();
        Map<String, Object> newMap = body.toMap();
        // 금지어는 목록 전체 대신 개수와 더한·뺀 수만 감사 기록에 싣는다(최대 500개)
        List<String> oldWords = row.values().blockedWords();
        if (oldWords.equals(words)) {
            oldMap.remove("blockedWords");
            newMap.remove("blockedWords");
        } else {
            long added = words.stream().filter(w -> !oldWords.contains(w)).count();
            long removed = oldWords.stream().filter(w -> !words.contains(w)).count();
            oldMap.put("blockedWords", oldWords.size());
            newMap.put("blockedWords", words.size() + " (+" + added + " / -" + removed + ")");
        }
        if (changed(actor, AuditActions.BOARD_DEFAULTS_UPDATE, "security", oldMap, newMap)) {
            row.change(body, actor.operatorId(), Instant.now());
        }
        return new BoardSettingsResponse<>(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    @Transactional(readOnly = true)
    public BoardSettingsResponse<BoardNotificationValues> notifications() {
        BoardNotificationSettingEntity row = notificationRow(false);
        return new BoardSettingsResponse<>(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    @Transactional
    public BoardSettingsResponse<BoardNotificationValues> saveNotifications(AdminActor actor, BoardNotificationValues request) {
        BoardNotificationSettingEntity row = notificationRow(true);
        BoardNotificationValues raw = required(request);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        BoardRules.check(raw.toMap(), BoardFields.TABS, "", false, Set.of(), false, invalid);
        String start = BoardRules.strip(raw.emailQuietStart());
        String end = BoardRules.strip(raw.emailQuietEnd());
        if (!CLOCK.matcher(start).matches()) {
            invalid.put("emailQuietStart", new MessageRef("board.clock.format"));
        }
        if (!CLOCK.matcher(end).matches()) {
            invalid.put("emailQuietEnd", new MessageRef("board.clock.format"));
        }
        // 이벤트별 칸(memberDefaults — 옛 칸)은 회원 이벤트 설정(/api/admin/member-events)으로 옮겼다 — 요청에 와도 무시한다(BoardNotificationValues 가 모르는 칸)
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        BoardNotificationValues body = new BoardNotificationValues(raw.groupWindowMinutes(), raw.emailQuietHoursEnabled(), start, end,
                raw.memberNotificationRetentionDays());
        if (changed(actor, AuditActions.BOARD_DEFAULTS_UPDATE, "notifications", row.values().toMap(), body.toMap())) {
            row.change(body, actor.operatorId(), Instant.now());
        }
        return new BoardSettingsResponse<>(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    // ── 개인정보 보유기간 ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BoardPrivacyResponse privacy() {
        return privacyResponse(privacyRow(false));
    }

    @Transactional
    public BoardPrivacyResponse savePrivacy(AdminActor actor, BoardPrivacyValues request) {
        BoardPrivacySettingEntity row = privacyRow(true);
        BoardPrivacyValues body = required(request);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        BoardRules.check(body.toMap(), BoardFields.TABS, "", false, Set.of(), false, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (changed(actor, AuditActions.BOARD_PRIVACY_RETENTION_UPDATE, "privacy", row.values().toMap(), body.toMap())) {
            row.change(body, actor.operatorId(), Instant.now());
            privacies.flush(); // 대상 수(DB 함수)가 새 값을 읽게
        }
        return privacyResponse(row);
    }

    /** 축 하나의 파기 대상(저장된 설정 기준, 앞의 몇 줄) */
    @Transactional(readOnly = true)
    public List<BoardPrivacyTargetView> privacyTargets(String axis) {
        return privacyGateway.targets(axis(axis), TARGET_LIMIT);
    }

    /** 「지금 실행」 앞의 확인 — 축 이름을 검사하고 전체 스위치가 켜져 있는지 돌려준다 */
    @Transactional(readOnly = true)
    public boolean privacyEnabled() {
        return privacyRow(false).isRetentionEnabled();
    }

    /** 「지금 실행」을 마친 뒤 — 마지막 실행 시각과 감사 기록 */
    @Transactional
    public void recordPrivacyRun(AdminActor actor, String axis, long processed) {
        privacyRow(true).markRun(Instant.now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BOARD_PRIVACY_RUN, AuditResult.SUCCESS).target("BOARD_DEFAULTS", "privacy")
                .with("axis", axis).with("processed", processed));
        log.info("게시판 개인정보 보유기간을 지금 실행했다 axis={} 처리={} by operatorId={}", axis, processed, actor.operatorId());
    }

    /** 축 이름 검사 — 모르면 400 {@code VALIDATION} {@code fields.axis} */
    static String axis(String axis) {
        if (axis == null || !BoardFields.PRIVACY_AXES.contains(axis)) {
            throw ValidationException.of("axis", new MessageRef("board.privacy.axis"));
        }
        return axis;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private BoardDefaultsResponse defaultsResponse(BoardDefaultsEntity row) {
        List<BoardEntity> all = boards.findAllByOrderBySortOrderAscIdAsc();
        Map<String, List<BoardRefView>> overrides = new LinkedHashMap<>();
        int following = 0;
        for (BoardEntity board : all) {
            Map<String, Object> policy = board.policy().toMap();
            boolean any = false;
            for (String field : BoardFields.POLICY_FIELDS) {
                if (policy.get(field) != null) {
                    overrides.computeIfAbsent(field, key -> new ArrayList<>()).add(BoardViews.ref(board));
                    any = true;
                }
            }
            if (!any) {
                following++;
            }
        }
        return new BoardDefaultsResponse(row.values(), all.size(), following, overrides, row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()));
    }

    private BoardPrivacyResponse privacyResponse(BoardPrivacySettingEntity row) {
        Map<String, Long> targets = new LinkedHashMap<>();
        for (String axis : BoardFields.PRIVACY_AXES) {
            targets.put(axis, privacyGateway.count(axis));
        }
        return new BoardPrivacyResponse(row.values(), row.getUpdatedAt(), views.operatorName(row.getUpdatedBy()), targets,
                views.defaultsRow().values().attachmentRetentionDays(), row.getLastRunAt());
    }

    /** 바뀐 칸이 있으면 감사 기록(바뀐 값만)을 남기고 true */
    private boolean changed(AdminActor actor, String action, String tab, Map<String, Object> current, Map<String, Object> wanted) {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : wanted.entrySet()) {
            Object old = current.get(entry.getKey());
            if (!Objects.equals(old, entry.getValue())) {
                before.put(entry.getKey(), old == null ? "" : old);
                after.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        if (after.isEmpty()) {
            return false;
        }
        record(actor, action, tab, before, after);
        return true;
    }

    private void record(AdminActor actor, String action, String tab, Map<String, Object> before, Map<String, Object> after) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("BOARD_DEFAULTS", tab)
                .with("tab", tab).with("before", before).with("after", after));
        log.info("게시판 기본 설정을 저장했다 화면={} 칸={} by operatorId={}", tab, after.keySet(), actor.operatorId());
    }

    private Set<String> gradeKeys() {
        Set<String> keys = new HashSet<>();
        for (GradeEntity grade : grades.findAllOrdered()) {
            keys.add(grade.getGradeKey());
        }
        return keys;
    }

    private static <T> T required(T body) {
        if (body == null) {
            throw new CommonException(ErrorCode.MALFORMED_REQUEST);
        }
        return body;
    }

    private BoardReportSettingEntity reportRow(boolean lock) {
        return (lock ? reports.findWithLockById(BoardReportSettingEntity.ROW_ID) : reports.findById(BoardReportSettingEntity.ROW_ID))
                .orElseThrow(() -> new CommonException(ErrorCode.INTERNAL));
    }

    private BoardSecuritySettingEntity securityRow(boolean lock) {
        return (lock ? securities.findWithLockById(BoardSecuritySettingEntity.ROW_ID) : securities.findById(BoardSecuritySettingEntity.ROW_ID))
                .orElseThrow(() -> new CommonException(ErrorCode.INTERNAL));
    }

    private BoardNotificationSettingEntity notificationRow(boolean lock) {
        return (lock ? notifications.findWithLockById(BoardNotificationSettingEntity.ROW_ID) : notifications.findById(BoardNotificationSettingEntity.ROW_ID))
                .orElseThrow(() -> new CommonException(ErrorCode.INTERNAL));
    }

    private BoardPrivacySettingEntity privacyRow(boolean lock) {
        return (lock ? privacies.findWithLockById(BoardPrivacySettingEntity.ROW_ID) : privacies.findById(BoardPrivacySettingEntity.ROW_ID))
                .orElseThrow(() -> new CommonException(ErrorCode.INTERNAL));
    }
}
