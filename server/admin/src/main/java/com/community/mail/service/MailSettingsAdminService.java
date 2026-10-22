package com.community.mail.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mail.dto.MailInput;
import com.community.mail.dto.MailSettingChange;
import com.community.mail.dto.MailSettingHistoryRow;
import com.community.mail.dto.MailValues;
import com.community.mail.dto.request.MailSettingsSaveRequest;
import com.community.mail.dto.response.MailSettingChangeView;
import com.community.mail.dto.response.MailSettingHistoryItemView;
import com.community.mail.dto.response.MailSettingHistoryPage;
import com.community.mail.dto.response.MailSettingsView;
import com.community.mail.entity.MailSettingEntity;
import com.community.mail.entity.MailSettingHistoryEntity;
import com.community.mail.entity.MailSettingHistorySource;
import com.community.mail.repository.master.MailSettingHistoryRepositoryQDSL;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 화면의 메일 서버 설정 — 조회·저장·이력 (docs/features/mail-settings-contract.md). 입구 권한은 컨트롤러가 선언한다.
 *
 * <ul>
 *   <li><b>같은 표·같은 행</b>({@code mail_settings}, id 1)을 읽고 쓴다 — 설치 마법사가 넣은 값이 그대로 보인다. 행이 없으면(설치 때 건너뜀) 저장이 새로 만든다.</li>
 *   <li><b>검사</b>는 설치 마법사와 같다({@link MailSettingsService#normalize}) — 틀린 칸을 모아 400 {@code VALIDATION} {@code fields.<칸>}(보내는 사람 칸은 {@code fromAddress}).</li>
 *   <li><b>비밀번호</b>: 보내지 않으면 저장된 값을 그대로 둔다. 다만 <b>사용자 이름 · 서버 주소 · 보안 방식</b>을 바꾸면서 비우면 {@code fields.password}
 *       {@code mail.password.reenter} — 저장된 자격 증명이 다른 서버로 가거나 암호화 없이 나가지 않게 한다. 보내면 암호화해 바꾼다(키가 없으면 503 {@code CRYPTO_KEY_MISSING}).
 *       사용자 이름을 비우면 비밀번호도 지운다. 같은 비밀번호를 다시 넣으면(풀어서 견준다) 바뀐 것이 아니다.</li>
 *   <li><b>이력</b>: 값이 실제로 바뀐 저장만 {@code mail_setting_history} 한 줄(출처 {@code ADMIN}) + 감사 {@code MAIL_SETTINGS_UPDATE}(바뀐 칸 이름만). 같은 값 재저장은 아무것도 쓰지 않는다.</li>
 *   <li><b>반영</b>: 실제 발송({@link MailDispatcher})은 캐시 없이 보낼 때마다 이 행을 읽으므로, 커밋 직후 모든 서버(api·admin, 여러 대)의 다음 발송이 새 값을 쓴다.</li>
 * </ul>
 * 비밀번호(평문·암호문)는 응답·로그·감사·이력에 싣지 않는다.
 */
@Service
public class MailSettingsAdminService {

    private static final Logger log = LoggerFactory.getLogger(MailSettingsAdminService.class);

    static final int HISTORY_MAX_SIZE = 100;
    static final int HISTORY_DEFAULT_SIZE = 20;

    private final MailSettingRepository settings;
    private final MailSettingsService validator;
    private final MailSettingHistoryService history;
    private final MailSettingHistoryRepositoryQDSL historyQueries;
    private final OperatorRepository operators;
    private final SecretEncryptor encryptor;
    private final AuditLogService audit;
    private final Messages messages;

    public MailSettingsAdminService(MailSettingRepository settings, MailSettingsService validator, MailSettingHistoryService history,
                                    MailSettingHistoryRepositoryQDSL historyQueries, OperatorRepository operators, SecretEncryptor encryptor,
                                    AuditLogService audit, Messages messages) {
        this.settings = settings;
        this.validator = validator;
        this.history = history;
        this.historyQueries = historyQueries;
        this.operators = operators;
        this.encryptor = encryptor;
        this.audit = audit;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public MailSettingsView load() {
        return settings.findById(MailSettingEntity.ROW_ID).map(this::view).orElseGet(() -> MailSettingsView.notConfigured(encryptor.isConfigured()));
    }

    @Transactional
    public MailSettingsView save(AdminActor actor, MailSettingsSaveRequest body) {
        MailSettingsSaveRequest in = body == null ? new MailSettingsSaveRequest(null, null, null, null, null, null) : body;
        MailSettingEntity row = settings.findWithLockById(MailSettingEntity.ROW_ID).orElse(null);

        Map<String, MessageRef> raw = new LinkedHashMap<>();
        MailValues values = validator.normalize(raw, "", new MailInput(in.host(), in.port(), in.username(), in.password(), in.fromAddress(), in.security()), true);

        // 비밀번호를 비워 보냈다 — 저장된 것을 그대로 쓸 수 있는가
        boolean keepPassword = false;
        MessageRef passwordError = raw.get(MailSettingChange.PASSWORD);
        if (passwordError != null && "mail.password.required".equals(passwordError.key()) && row != null && row.getPasswordEnc() != null) {
            if (sameTarget(row, values)) {
                raw.remove(MailSettingChange.PASSWORD);
                keepPassword = true;
            } else {
                raw.put(MailSettingChange.PASSWORD, new MessageRef("mail.password.reenter"));
            }
        }
        if (!raw.isEmpty()) {
            throw new ValidationException(ordered(raw));
        }

        // 비밀번호: 지금 암호문과 바뀐 내용
        String beforeEnc = row == null ? null : row.getPasswordEnc();
        String afterEnc;
        String passwordChange = null;
        if (values.username() == null) {
            afterEnc = null;
            passwordChange = beforeEnc != null ? MailSettingChange.PASSWORD_CLEARED : null;
        } else if (keepPassword) {
            afterEnc = beforeEnc;
        } else if (beforeEnc != null && samePassword(beforeEnc, values.password())) {
            afterEnc = beforeEnc; // 같은 비밀번호를 다시 넣었다 — 다시 암호화하지 않는다
        } else {
            afterEnc = encryptor.encrypt(values.password()); // 키가 없으면 503 CRYPTO_KEY_MISSING
            passwordChange = beforeEnc == null ? MailSettingChange.PASSWORD_SET : MailSettingChange.PASSWORD_CHANGED;
        }

        List<MailSettingChange> changes = new ArrayList<>();
        diff(changes, MailSettingChange.HOST, row == null ? null : row.getHost(), values.host());
        diff(changes, MailSettingChange.PORT, row == null ? null : String.valueOf(row.getPort()), String.valueOf(values.port()));
        diff(changes, MailSettingChange.SECURITY, row == null ? null : row.getSecurity().name(), values.security().name());
        diff(changes, MailSettingChange.USERNAME, row == null ? null : row.getUsername(), values.username());
        if (passwordChange != null) {
            changes.add(MailSettingChange.password(passwordChange));
        }
        diff(changes, MailSettingChange.FROM_ADDRESS, row == null ? null : row.getFromAddress(), values.from());
        if (changes.isEmpty()) {
            return view(row);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS); // DB(timestamptz) 정밀도 — 저장 응답과 다시 읽은 값이 같게
        if (row == null) {
            row = MailSettingEntity.create(values.host(), values.port(), values.username(), afterEnc, values.from(), values.security(), actor.operatorId(), now);
            settings.persist(row);
        } else {
            row.change(values.host(), values.port(), values.username(), afterEnc, values.from(), values.security(), actor.operatorId(), now);
        }
        history.record(MailSettingHistorySource.ADMIN, actor.operatorId(), now, changes);
        List<String> fields = changes.stream().map(MailSettingChange::field).toList();
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MAIL_SETTINGS_UPDATE, AuditResult.SUCCESS).target("SITE", "mail")
                .with("fields", fields));
        log.info("메일 서버 설정을 저장했다 칸={} by operatorId={}", fields, actor.operatorId());
        return view(row);
    }

    @Transactional(readOnly = true)
    public MailSettingHistoryPage history(Integer page, Integer size) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            fields.put("page", new MessageRef("mail.history.page.invalid"));
        }
        int pageSize = size == null ? HISTORY_DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > HISTORY_MAX_SIZE) {
            fields.put("size", new MessageRef("notification.size.range", String.valueOf(HISTORY_MAX_SIZE)));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long total = historyQueries.count();
        List<MailSettingHistoryItemView> items = new ArrayList<>();
        if (total > 0 && (long) pageNumber * pageSize < total) {
            Locale locale = messages.locale();
            String deleted = messages.get(new MessageRef("mail.history.operatorDeleted"), locale);
            for (MailSettingHistoryRow row : historyQueries.page(pageNumber, pageSize)) {
                items.add(item(row, deleted, locale));
            }
        }
        return new MailSettingHistoryPage(items, total, pageNumber, pageSize);
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    /** 저장된 비밀번호를 그대로 써도 되는 대상인가 — 사용자 이름 · 서버 주소(대소문자 무시) · 보안 방식이 그대로일 때만 */
    private static boolean sameTarget(MailSettingEntity row, MailValues values) {
        return values.username() != null && values.username().equals(row.getUsername())
                && values.host() != null && values.host().equalsIgnoreCase(row.getHost())
                && values.security() == row.getSecurity();
    }

    /** 새로 넣은 비밀번호가 저장된 것과 같은가 — 풀지 못하면(키 없음·키 바뀜) 다른 것으로 본다 */
    private boolean samePassword(String encrypted, String plain) {
        if (plain == null || !encryptor.isConfigured()) {
            return false;
        }
        try {
            return plain.equals(encryptor.decrypt(encrypted));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void diff(List<MailSettingChange> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(MailSettingChange.value(field, before, after));
        }
    }

    /** 칸 오류를 화면 칸 이름·차례로 — 설치 마법사의 {@code from} 은 여기서 {@code fromAddress} 다 */
    private static Map<String, MessageRef> ordered(Map<String, MessageRef> raw) {
        Map<String, MessageRef> renamed = new LinkedHashMap<>();
        raw.forEach((field, ref) -> renamed.put("from".equals(field) ? MailSettingChange.FROM_ADDRESS : field, ref));
        Map<String, MessageRef> ordered = new LinkedHashMap<>();
        for (String field : MailSettingChange.FIELDS) {
            if (renamed.containsKey(field)) {
                ordered.put(field, renamed.get(field));
            }
        }
        renamed.forEach(ordered::putIfAbsent);
        return ordered;
    }

    private MailSettingsView view(MailSettingEntity row) {
        String by = row.getUpdatedBy() == null ? null
                : operators.findById(row.getUpdatedBy()).map(OperatorEntity::getOperatorName)
                        .orElseGet(() -> messages.get(new MessageRef("mail.history.operatorDeleted")));
        return new MailSettingsView(true, row.getHost(), row.getPort(), row.getSecurity().name(), row.getUsername(), row.getPasswordEnc() != null,
                row.getFromAddress(), encryptor.isConfigured(), row.getUpdatedAt(), by);
    }

    private MailSettingHistoryItemView item(MailSettingHistoryRow row, String deletedName, Locale locale) {
        MailSettingHistoryEntity entity = row.history();
        boolean deleted = entity.getOperatorId() == null || row.operatorName() == null;
        List<MailSettingChangeView> changes = MailSettingHistoryService.parse(entity.getChanges()).stream()
                .map(change -> new MailSettingChangeView(change.field(), fieldLabel(change.field(), locale), change.before(), change.after(), change.change()))
                .toList();
        return new MailSettingHistoryItemView(entity.getId().toString(), entity.getChangedAt(), entity.getSource().name(),
                entity.getOperatorId() == null ? null : entity.getOperatorId().toString(), deleted ? deletedName : row.operatorName(), deleted, changes);
    }

    private String fieldLabel(String field, Locale locale) {
        return field != null && MailSettingChange.FIELDS.contains(field) ? messages.get(new MessageRef("mail.field." + field), locale) : field;
    }
}
