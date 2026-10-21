package com.community.audit.service;

import com.community.audit.dto.AuditRow;
import com.community.audit.dto.AuditSearchCriteria;
import com.community.audit.dto.request.AuditSearchRequest;
import com.community.audit.dto.response.AuditLogView;
import com.community.audit.dto.response.AuditOptions;
import com.community.audit.dto.response.AuditPage;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.repository.master.AuditLogRepositoryQDSL;
import com.community.batch.catalog.BatchCatalog;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 조회(읽기 전용) — 발생 시각 최근순(같으면 ID 최근순), 조건과 쪽 나누기는 DB 에서 한다. 고치거나 지우는 API 는 없다.
 *
 * <ul>
 *   <li>조건이 틀리면 칸 이름과 함께 400 {@code VALIDATION}(틀린 값은 문구에 싣지 않는다). 쪽 크기는 1~{@value #MAX_SIZE}, 기본 {@value #DEFAULT_SIZE}. 검색어는 {@value #TERM_MAX}자까지.</li>
 *   <li>작업·대상 종류·결과·사유 이름은 요청 언어 문구({@link AuditLabels}), 행위자·대상 이름은 지금의 이름이다(기록에는 이름을 남기지 않는다).</li>
 *   <li><b>이메일은 내지 않는다</b> — 행위자는 이름으로만 보인다. 감사 기록에는 비밀번호·토큰이 처음부터 남지 않고, 로그인 실패의 이메일 대신 구분용 해시 조각만 있다.
 *       {@code changes} 는 기록의 {@code detail} 을 {@link AuditChangeFormatter} 가 바뀐 칸 목록으로 옮긴 것이다.</li>
 *   <li><b>이 조회는 감사 기록으로 남기지 않는다(가정)</b> — 쪽을 넘길 때마다 쌓이면 기록이 불어나고 읽기 때문에 기록이 계속 생기는 모순이 된다. 필요하면 첫 쪽만 {@code AUDIT_VIEW} 로 남기는 방안을 후속으로.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int TERM_MAX = 100;

    private static final Pattern ACTION = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern TARGET_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");
    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,18}");

    private final AuditLogRepositoryQDSL audits;
    private final AuditLabels labels;
    private final Messages messages;

    public AuditQueryService(AuditLogRepositoryQDSL audits, AuditLabels labels, Messages messages) {
        this.audits = audits;
        this.labels = labels;
        this.messages = messages;
    }

    public AuditOptions options() {
        return new AuditOptions(labels.actionOptions(), labels.targetTypeOptions(), labels.resultOptions());
    }

    public AuditPage search(AuditSearchRequest request) {
        AuditSearchCriteria criteria = criteria(request);
        List<AuditRow> rows = audits.search(criteria);
        return new AuditPage(views(rows), audits.count(criteria), criteria.page(), criteria.size());
    }

    private AuditSearchCriteria criteria(AuditSearchRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef invalid = new MessageRef("audit.query.invalid");

        Instant from = instant("from", request.from(), fields, invalid);
        Instant to = instant("to", request.to(), fields, invalid);
        if (from != null && to != null && from.isAfter(to)) {
            fields.put("to", new MessageRef("audit.period.reversed"));
        }
        String actor = term("actor", request.actor(), fields, invalid);
        Long actorId = null;
        if (actor != null && DIGITS.matcher(actor).matches()) {
            actorId = Long.parseLong(actor);
        }
        String action = pattern("action", request.action(), ACTION, fields, invalid);
        String targetType = pattern("targetType", request.targetType(), TARGET_TYPE, fields, invalid);
        String target = term("target", request.target(), fields, invalid);
        AuditResult result = result(request.result(), fields, invalid);
        MessageRef pageRange = new MessageRef("audit.page.range", String.valueOf(MAX_SIZE));
        int page = integer("page", request.page(), 0, 0, Integer.MAX_VALUE / MAX_SIZE, fields, pageRange);
        int size = integer("size", request.size(), DEFAULT_SIZE, 1, MAX_SIZE, fields, pageRange);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new AuditSearchCriteria(from, to, actorId, actor, action, targetType, target, target == null ? Set.of() : batchKeysNamed(target), result, page, size);
    }

    /** 이름(요청 언어)에 검색어가 들어 있는 배치의 키 — 배치 이름은 번들에 있어 DB 에서 찾을 수 없다 */
    private Set<String> batchKeysNamed(String term) {
        String lower = term.toLowerCase(messages.locale());
        Set<String> keys = new HashSet<>();
        Arrays.stream(BatchCatalog.values()).forEach(batch -> {
            if (messages.get(new MessageRef(batch.nameKey())).toLowerCase(messages.locale()).contains(lower)) {
                keys.add(batch.key());
            }
        });
        return keys;
    }

    private List<AuditLogView> views(List<AuditRow> rows) {
        Set<Long> operatorIds = new HashSet<>();
        Set<String> roleIds = new HashSet<>();
        Set<Long> memberIds = new HashSet<>();
        for (AuditRow row : rows) {
            if (row.targetId() == null || row.targetType() == null) {
                continue;
            }
            switch (row.targetType()) {
                case "OPERATOR" -> addNumber(operatorIds, row.targetId());
                case "MEMBER" -> addNumber(memberIds, row.targetId());
                case "ROLE" -> roleIds.add(row.targetId());
                default -> {
                }
            }
        }
        Map<Long, String> operatorNames = audits.operatorNames(operatorIds);
        Map<String, String> roleNames = audits.roleNames(roleIds);
        Map<Long, String> memberNames = audits.memberNicknames(memberIds);
        return rows.stream().map(row -> view(row, operatorNames, roleNames, memberNames)).toList();
    }

    private AuditLogView view(AuditRow row, Map<Long, String> operatorNames, Map<String, String> roleNames, Map<Long, String> memberNames) {
        boolean failed = !AuditResult.SUCCESS.name().equals(row.result());
        Map<String, Object> detail = AuditChangeFormatter.parse(row.detail());
        String message = failed && detail.get("reason") instanceof String reason ? labels.reasonText(reason) : null;
        return new AuditLogView(Long.toString(row.id()), row.occurredAt(), row.actorId() == null ? null : Long.toString(row.actorId()), row.actorType(), null, actorName(row),
                row.ip(), row.action(), labels.actionName(row.action()), row.targetType(), labels.targetTypeName(row.targetType()), row.targetId(),
                targetName(row, operatorNames, roleNames, memberNames), resultOf(row.result()), message, AuditChangeFormatter.json(row.action(), detail, failed));
    }

    private String actorName(AuditRow row) {
        if (AuditActorType.SYSTEM.name().equals(row.actorType())) {
            return labels.text("audit.actor.system");
        }
        if (row.actorId() == null) {
            return labels.text("audit.actor.anonymous");
        }
        return row.actorName();
    }

    /** 대상의 지금 이름. 초대는 이메일이라 이름으로 내지 않는다 */
    private String targetName(AuditRow row, Map<Long, String> operatorNames, Map<String, String> roleNames, Map<Long, String> memberNames) {
        if (row.targetType() == null || row.targetId() == null) {
            return null;
        }
        return switch (row.targetType()) {
            case "OPERATOR" -> number(row.targetId()) == null ? null : operatorNames.get(number(row.targetId()));
            case "MEMBER" -> number(row.targetId()) == null ? null : memberNames.get(number(row.targetId()));
            case "ROLE" -> roleNames.get(row.targetId());
            case "BATCH" -> {
                BatchCatalog batch = BatchCatalog.ofKey(row.targetId());
                yield batch == null ? null : messages.get(new MessageRef(batch.nameKey()));
            }
            case "SITE" -> labels.text("audit.targetName.SITE");
            case "GRADE" -> labels.text("audit.targetName.GRADE");
            default -> null;
        };
    }

    private static String resultOf(String result) {
        return switch (result) {
            case "SUCCESS" -> "OK";
            case "FAILURE" -> "FAIL";
            default -> result;
        };
    }

    private static void addNumber(Set<Long> into, String text) {
        Long value = number(text);
        if (value != null) {
            into.add(value);
        }
    }

    private static Long number(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String term(String field, String raw, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value != null && value.length() > TERM_MAX) {
            fields.put(field, invalid);
            return null;
        }
        return value;
    }

    private static String pattern(String field, String raw, Pattern pattern, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value != null && !pattern.matcher(value).matches()) {
            fields.put(field, invalid);
            return null;
        }
        return value;
    }

    private static AuditResult result(String raw, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "OK" -> AuditResult.SUCCESS;
            case "FAIL" -> AuditResult.FAILURE;
            case "DENIED" -> AuditResult.DENIED;
            default -> {
                fields.put("result", invalid);
                yield null;
            }
        };
    }

    private static Instant instant(String field, String raw, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            fields.put(field, invalid);
            return null;
        }
    }

    private static int integer(String field, String raw, int fallback, int min, int max, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value == null) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) {
                fields.put(field, invalid);
                return fallback;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            fields.put(field, invalid);
            return fallback;
        }
    }
}
