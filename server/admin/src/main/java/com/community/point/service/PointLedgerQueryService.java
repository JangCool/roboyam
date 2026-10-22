package com.community.point.service;

import com.community.member.service.MemberMasking;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.point.dto.PointLedgerFilter;
import com.community.point.dto.PointLedgerRow;
import com.community.point.dto.response.PointLedgerItemView;
import com.community.point.dto.response.PointLedgerPageView;
import com.community.point.dto.response.PointLedgerSummaryView;
import com.community.point.dto.response.PointMemberCandidateView;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerRepositoryQDSL;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 포인트 원장 조회와 조정 대상 후보 — 읽기 전용(감사 기록 없음). 입구 권한은 컨트롤러가 선언한다(원장 {@code point.read}, 후보 {@code point.adjust}).
 *
 * <ul>
 *   <li><b>원장</b>: 최신이 위, 쪽 번호({@code page} 0부터 · {@code size} 1~{@value #MAX_SIZE}). {@code summary} 는 같은 조건의 <b>전체</b> 줄 기준. 틀린 조건은 칸을 모아
 *       400 {@code VALIDATION} {@code fields.<조건>}(시각 형식 · 시작이 끝보다 늦음 {@code fields.from} · 모르는 종류·사유 · 쪽).</li>
 *   <li><b>후보</b>: 탈퇴하지 않은 회원(정상·정지) 최대 {@value #CANDIDATE_LIMIT} 명, 지금 잔액 포함. 검색어가 비면 최근 가입 순.</li>
 *   <li>이메일은 {@code member.pii} 가 없으면 가리고, 이메일 검색은 그때 전체 주소가 같을 때만 맞는다(회원 목록과 같은 규칙).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PointLedgerQueryService {

    static final int MAX_SIZE = 100;
    static final int DEFAULT_SIZE = 20;
    static final int CANDIDATE_LIMIT = 20;
    private static final Set<String> KINDS = Set.of("EARN", "USE", "REVOKE");

    private final PointLedgerRepositoryQDSL ledger;
    private final Messages messages;

    public PointLedgerQueryService(PointLedgerRepositoryQDSL ledger, Messages messages) {
        this.ledger = ledger;
        this.messages = messages;
    }

    public PointLedgerPageView ledger(AdminActor actor, String from, String to, String kind, List<String> reasons, String q, Integer page, Integer size) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Instant fromAt = instant("from", from, fields);
        Instant toAt = instant("to", to, fields);
        if (fromAt != null && toAt != null && fromAt.isAfter(toAt)) {
            fields.putIfAbsent("from", new MessageRef("point.ledger.period.reversed"));
        }
        String kindValue = kind == null || kind.isBlank() ? null : kind.strip();
        if (kindValue != null && !KINDS.contains(kindValue)) {
            fields.put("kind", new MessageRef("point.ledger.kind.invalid"));
        }
        List<String> reasonCodes = reasons(reasons, fields);
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            fields.put("page", new MessageRef("point.ledger.page.invalid"));
        }
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            fields.put("size", new MessageRef("notification.size.range", String.valueOf(MAX_SIZE)));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        String keyword = q == null || q.isBlank() ? null : q.strip().toLowerCase(Locale.ROOT);
        PointLedgerFilter filter = new PointLedgerFilter(fromAt, toAt, kindValue, reasonCodes, keyword);
        boolean canPii = actor.can(Permission.MEMBER_PII);

        long[] summary = ledger.summary(filter, canPii);
        long total = summary[0];
        List<PointLedgerItemView> items = new ArrayList<>();
        if (total > 0 && (long) pageNumber * pageSize < total) {
            Locale locale = messages.locale();
            for (PointLedgerRow row : ledger.page(filter, canPii, pageNumber, pageSize)) {
                items.add(item(row, locale));
            }
        }
        return new PointLedgerPageView(items, total, new PointLedgerSummaryView(summary[1], summary[2], summary[3]));
    }

    public List<PointMemberCandidateView> candidates(AdminActor actor, String q) {
        boolean canPii = actor.can(Permission.MEMBER_PII);
        return ledger.candidates(q, canPii, CANDIDATE_LIMIT).stream()
                .map(row -> new PointMemberCandidateView(Long.toString(row.id()), row.nickname(), canPii ? row.email() : MemberMasking.email(row.email()),
                        row.balance() == null ? 0 : row.balance()))
                .toList();
    }

    private PointLedgerItemView item(PointLedgerRow row, Locale locale) {
        return new PointLedgerItemView(Long.toString(row.id()), Long.toString(row.userId()), row.memberNickname(), row.kind(), row.reason(),
                reasonLabel(messages, row.reason(), locale), row.note(), row.delta(), row.balanceAfter(), row.actorName(), row.createdAt());
    }

    /** 사유 코드 → 요청 언어의 이름(모르는 코드면 코드 그대로) */
    public static String reasonLabel(Messages messages, String code, Locale locale) {
        return PointReason.of(code).map(reason -> messages.get(new MessageRef(reason.labelKey()), locale)).orElse(code);
    }

    private static Instant instant(String field, String text, Map<String, MessageRef> fields) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.strip());
        } catch (DateTimeParseException e) {
            fields.put(field, new MessageRef("point.ledger.time.invalid"));
            return null;
        }
    }

    /** 사유 코드들 — 쉼표로 이은 값도 받는다. 모르는 코드가 있으면 {@code fields.reasons} */
    private static List<String> reasons(List<String> raw, Map<String, MessageRef> fields) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            for (String code : item.split(",")) {
                String c = code.strip();
                if (c.isEmpty()) {
                    continue;
                }
                if (PointReason.of(c).isEmpty()) {
                    fields.put("reasons", new MessageRef("point.ledger.reason.invalid"));
                    return null;
                }
                out.add(c);
            }
        }
        return out.isEmpty() ? null : List.copyOf(out);
    }
}
