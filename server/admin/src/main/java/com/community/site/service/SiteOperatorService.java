package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.commerce.sales.CommerceSalesService;
import com.community.member.service.MemberMasking;
import com.community.notification.service.AdminNotices;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.SiteFooterNotice;
import com.community.site.dto.SiteOperatorValues;
import com.community.site.dto.request.SiteOperatorRequest;
import com.community.site.dto.response.SiteOperatorResponse;
import com.community.site.entity.SiteFooterNoticeScope;
import com.community.site.entity.SiteOperatorEntity;
import com.community.site.entity.SiteOperatorHistoryEntity;
import com.community.site.entity.SiteOperatorStage;
import com.community.site.repository.master.SiteOperatorHistoryRepository;
import com.community.site.repository.master.SiteOperatorRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 주체 설정(설정 > 운영·약관 > 운영 주체 `/settings/operator`, docs/features/terms-operator-contract.md) 조회·저장 — {@code site_operator} 한 행(V158). 사업자 정보의 정본이다(커머스 사업자 정보의 겹치는 칸이
 * 여기서 읽힌다).
 *
 * <ul>
 *   <li><b>검사</b>(칸마다 모아 한꺼번에 {@code 400 VALIDATION}, 사업자 칸은 {@code business.xxx}): 단계 · 운영자 이름 1~30 · 연락 이메일(254, 모양) · 전화(20, 숫자·{@code + - ( )}·띄어쓰기 —
 *       사업자 단계에서 필수) · 보호책임자(「같음」이 아니면 이름 30·이메일 필수) · 호스팅 50 · 사업자 단계의 상호 50·대표자 30·사업자등록번호 {@code ###-##-#####}·주소 200(모두 필수),
 *       통신판매업 신고번호·신고 기관 50, 법인등록번호 20(법인만) · 푸터 안내 문구(V177) 최대 3줄·줄마다 120자, 보이기 {@code INDIVIDUAL_ONLY}·{@code ALWAYS}.
 *       화면 {@code UtilSiteOperator.validate} 와 같다.</li>
 *   <li><b>푸터 안내 문구</b>: 줄마다 앞뒤 공백을 떼고 빈 줄은 버린다(다 비면 안내 숨김). 요청에 칸이 없으면(null — 옛 화면) 지금 값을 둔다.</li>
 *   <li><b>개인 단계로 저장하면 사업자 칸을 비운다.</b> 「같음」이면 보호책임자 칸, 법인이 아니면 법인등록번호도 비운다.</li>
 *   <li><b>판매 중 막기</b>(커머스 규칙): 판매가 켜져 있는 동안 개인 단계로 바꾸거나 통신판매업 신고번호를 비우면 409 {@code COMMERCE_SALES_OPEN}, 로보티콘 유료 판매가
 *       켜져 있으면 409 {@code EMOTICON_PAID_SALES_OPEN}({@link CommerceSalesService#assertOperatorChangeAllowed}).</li>
 *   <li><b>감사</b>: 단계가 바뀌면 {@code SITE_OPERATOR_STAGE_CHANGE} + 전환 이력 한 줄, 칸만 바뀌면 {@code SITE_OPERATOR_UPDATE}, 같은 값이면 아무것도 남기지 않는다(행도 그대로).
 *       바뀐 칸만 {@code before}/{@code after}(이메일은 가린 값). 바뀐 때 관리자 알림(사이트 설정 변경, 변형 {@code siteOperator}).</li>
 * </ul>
 * 이미 게시된 약관은 바뀌지 않는다 — 새 값은 다음 게시 때 자리표시에 들어간다.
 */
@Service
public class SiteOperatorService {

    private static final Logger log = LoggerFactory.getLogger(SiteOperatorService.class);

    static final int NAME_MAX = 30;
    static final int EMAIL_MAX = 254;
    static final int PHONE_MAX = 20;
    static final int SHORT_MAX = 50;
    static final int ADDRESS_MAX = 200;
    static final int CORPORATE_NUMBER_MAX = 20;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-() ]+$");
    private static final Pattern REGISTRATION_NUMBER = Pattern.compile("^\\d{3}-\\d{2}-\\d{5}$");
    private static final Set<String> EMAIL_FIELDS = Set.of("contactEmail", "privacyOfficerEmail");

    private final SiteOperatorRepository operators;
    private final SiteOperatorHistoryRepository history;
    private final OperatorRepository staff;
    private final CommerceSalesService sales;
    private final AuditLogService audit;
    private final AdminNotices notices;
    private final SnowflakeIdGenerator ids;

    public SiteOperatorService(SiteOperatorRepository operators, SiteOperatorHistoryRepository history, OperatorRepository staff, CommerceSalesService sales,
                               AuditLogService audit, AdminNotices notices, SnowflakeIdGenerator ids) {
        this.operators = operators;
        this.history = history;
        this.staff = staff;
        this.sales = sales;
        this.audit = audit;
        this.notices = notices;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public SiteOperatorResponse load() {
        return view(row(false));
    }

    @Transactional
    public SiteOperatorResponse save(AdminActor actor, SiteOperatorRequest request) {
        Parsed parsed = validate(request);
        SiteOperatorValues next = parsed.values();
        // 판매 스위치 행 → 운영 주체 행 차례로 잠근다(판매 켜기와 같은 차례 — 켜기가 읽는 운영 주체 값이 그 사이에 바뀌지 않는다)
        sales.assertOperatorChangeAllowed(next.stage(), next.mailOrderNumber());
        SiteOperatorEntity row = row(true);
        SiteOperatorValues before = row.values();
        SiteFooterNotice noticeBefore = row.footerNotice();
        // 칸이 없으면(옛 화면) 지금 값을 둔다
        SiteFooterNotice noticeNext = new SiteFooterNotice(parsed.noticeLines() != null ? parsed.noticeLines() : noticeBefore.lines(),
                parsed.noticeScope() != null ? parsed.noticeScope() : noticeBefore.scope());
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        Map<String, Object> after = flat(next, noticeNext);
        flat(before, noticeBefore).forEach((key, value) -> {
            if (!Objects.equals(value, after.get(key))) {
                from.put(key, masked(key, value));
                to.put(key, masked(key, after.get(key)));
            }
        });
        if (to.isEmpty()) {
            return view(row);
        }
        Instant now = Instant.now();
        row.change(next, noticeNext, actor.operatorId(), now);
        boolean stageChanged = before.stage() != next.stage();
        if (stageChanged) {
            history.persist(SiteOperatorHistoryEntity.create(ids.nextId(), before.stage(), next.stage(), actor.operatorId(), now));
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), stageChanged ? AuditActions.SITE_OPERATOR_STAGE_CHANGE : AuditActions.SITE_OPERATOR_UPDATE,
                AuditResult.SUCCESS).target("SITE", "1").with("before", from).with("after", to));
        notices.siteSettingsChanged(actor, "siteOperator");
        log.info("운영 주체를 저장했다 operatorId={} stage={}→{} fields={}", actor.operatorId(), before.stage(), next.stage(), to.keySet());
        return view(row);
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    /**
     * 검사를 마친 요청.
     *
     * @param noticeLines 푸터 안내 줄(빈 목록 = 숨김) — 요청에 칸이 없으면 null(지금 값을 둔다)
     * @param noticeScope 푸터 안내 보이기 — 요청에 칸이 없으면 null(지금 값을 둔다)
     */
    private record Parsed(SiteOperatorValues values, List<String> noticeLines, SiteFooterNoticeScope noticeScope) {
    }

    /** 요청 → 저장할 값(앞뒤 공백 뗌, 빈 선택 칸 null). 위반은 칸마다 모아 400 */
    private static Parsed validate(SiteOperatorRequest in) {
        SiteOperatorRequest r = in == null ? new SiteOperatorRequest(null, null, null, null, null, null, null, null, null, null, null, null) : in;
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        SiteOperatorStage stage = SiteOperatorStage.of(r.stage() == null ? null : r.stage().strip()).orElse(null);
        if (stage == null) {
            errors.put("stage", new MessageRef("siteOperator.stage.invalid"));
        }
        boolean business = stage != null && stage.business();
        String operatorName = text(errors, "operatorName", r.operatorName(), NAME_MAX, true);
        String contactEmail = email(errors, "contactEmail", r.contactEmail(), true);
        String phone = text(errors, "phone", r.phone(), PHONE_MAX, business);
        if (phone != null && !errors.containsKey("phone") && !PHONE.matcher(phone).matches()) {
            errors.put("phone", new MessageRef("siteOperator.field.phone"));
        }
        boolean same = !Boolean.FALSE.equals(r.privacyOfficerSameAsOperator());
        String officerName = same ? null : text(errors, "privacyOfficerName", r.privacyOfficerName(), NAME_MAX, true);
        String officerEmail = same ? null : email(errors, "privacyOfficerEmail", r.privacyOfficerEmail(), true);
        String hosting = text(errors, "hostingProvider", r.hostingProvider(), SHORT_MAX, false);
        String mail = text(errors, "mailProvider", r.mailProvider(), SHORT_MAX, false);

        SiteOperatorValues.Business biz = null;
        if (business) {
            SiteOperatorRequest.Business b = r.business() == null ? new SiteOperatorRequest.Business(null, null, null, null, null, null, null) : r.business();
            String businessName = text(errors, "business.businessName", b.businessName(), SHORT_MAX, true);
            String representative = text(errors, "business.representative", b.representative(), NAME_MAX, true);
            String registration = text(errors, "business.registrationNumber", b.registrationNumber(), 12, true);
            if (registration != null && !errors.containsKey("business.registrationNumber") && !REGISTRATION_NUMBER.matcher(registration).matches()) {
                errors.put("business.registrationNumber", new MessageRef("siteOperator.field.registrationNumber"));
            }
            String address = text(errors, "business.address", b.address(), ADDRESS_MAX, true);
            String mailOrderNumber = text(errors, "business.mailOrderNumber", b.mailOrderNumber(), SHORT_MAX, false);
            String mailOrderAuthority = text(errors, "business.mailOrderAuthority", b.mailOrderAuthority(), SHORT_MAX, false);
            String corporateNumber = stage == SiteOperatorStage.CORPORATION
                    ? text(errors, "business.corporateNumber", b.corporateNumber(), CORPORATE_NUMBER_MAX, false) : null;
            biz = new SiteOperatorValues.Business(businessName, representative, registration, address, mailOrderNumber, mailOrderAuthority, corporateNumber);
        }
        List<String> noticeLines = r.footerNotice() == null ? null : noticeLines(errors, r.footerNotice());
        SiteFooterNoticeScope noticeScope = null;
        if (r.footerNoticeScope() != null) {
            noticeScope = SiteFooterNoticeScope.of(r.footerNoticeScope().strip()).orElse(null);
            if (noticeScope == null) {
                errors.put("footerNoticeScope", new MessageRef("siteOperator.footerNoticeScope.invalid"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Parsed(new SiteOperatorValues(stage, operatorName, contactEmail, phone, same, officerName, officerEmail, hosting, mail, biz), noticeLines,
                noticeScope);
    }

    /** 푸터 안내 문구 — 줄마다 앞뒤 공백을 떼고 빈 줄은 버린다. 3줄 넘게 · 120자 넘는 줄이 있으면 {@code errors.footerNotice} */
    private static List<String> noticeLines(Map<String, MessageRef> errors, String raw) {
        List<String> lines = SiteFooterNotice.split(raw);
        if (lines.size() > SiteFooterNotice.LINES_MAX) {
            errors.put("footerNotice", new MessageRef("siteOperator.footerNotice.lines", SiteFooterNotice.LINES_MAX));
        } else if (lines.stream().anyMatch(line -> line.codePointCount(0, line.length()) > SiteFooterNotice.LINE_MAX)) {
            errors.put("footerNotice", new MessageRef("siteOperator.footerNotice.lineLength", SiteFooterNotice.LINE_MAX));
        }
        return lines;
    }

    /** 글자 칸 — 앞뒤 공백을 떼고 빈 글자는 null. 필수인데 비었거나 길면 {@code errors} 에 넣는다 */
    private static String text(Map<String, MessageRef> errors, String field, String raw, int max, boolean required) {
        String value = raw == null ? null : raw.strip();
        if (value == null || value.isEmpty()) {
            if (required) {
                errors.put(field, new MessageRef("siteOperator.field.required"));
            }
            return null;
        }
        if (value.codePointCount(0, value.length()) > max) {
            errors.put(field, new MessageRef("siteOperator.field.length", max));
        }
        return value;
    }

    private static String email(Map<String, MessageRef> errors, String field, String raw, boolean required) {
        String value = text(errors, field, raw, EMAIL_MAX, required);
        if (value != null && !errors.containsKey(field) && !EMAIL.matcher(value).matches()) {
            errors.put(field, new MessageRef("siteOperator.field.email"));
        }
        return value;
    }

    // ── 보기·도우미 ─────────────────────────────────────────────────────────

    private SiteOperatorEntity row(boolean lock) {
        return (lock ? operators.findWithLockById(SiteOperatorEntity.ROW_ID) : operators.findById(SiteOperatorEntity.ROW_ID))
                .orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
    }

    private SiteOperatorResponse view(SiteOperatorEntity row) {
        SiteOperatorValues v = row.values();
        SiteFooterNotice notice = row.footerNotice();
        List<SiteOperatorHistoryEntity> changes = history.findAllByOrderByChangedAtDescIdDesc();
        Set<Long> who = changes.stream().map(SiteOperatorHistoryEntity::getChangedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!who.isEmpty()) {
            for (OperatorEntity operator : staff.findAllById(who)) {
                names.put(operator.getId(), operator.getOperatorName());
            }
        }
        SiteOperatorValues.Business b = v.business();
        return new SiteOperatorResponse(v.stage().name(), v.operatorName(), v.contactEmail(), v.phone(), v.privacyOfficerSameAsOperator(), v.privacyOfficerName(),
                v.privacyOfficerEmail(), v.hostingProvider(), v.mailProvider(),
                b == null ? null : new SiteOperatorResponse.Business(b.businessName(), b.representative(), b.registrationNumber(), b.address(), b.mailOrderNumber(),
                        b.mailOrderAuthority(), b.corporateNumber()),
                notice.stored(), notice.scope().name(), row.getUpdatedAt(),
                changes.stream().map(c -> new SiteOperatorResponse.StageChange(c.getChangedAt(), c.getFromStage().name(), c.getToStage().name(),
                        c.getChangedBy() == null ? null : names.get(c.getChangedBy()))).toList());
    }

    /** 감사 「바뀐 내용」의 칸 — 없는 값은 빈 글자(맵에 null 을 넣지 않는다), 사업자 칸은 {@code business.} 이 붙는다. 푸터 안내 문구는 줄을 이은 글 */
    private static Map<String, Object> flat(SiteOperatorValues v, SiteFooterNotice notice) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", v.stage().name());
        out.put("operatorName", nz(v.operatorName()));
        out.put("contactEmail", nz(v.contactEmail()));
        out.put("phone", nz(v.phone()));
        out.put("privacyOfficerSameAsOperator", v.privacyOfficerSameAsOperator());
        out.put("privacyOfficerName", nz(v.privacyOfficerName()));
        out.put("privacyOfficerEmail", nz(v.privacyOfficerEmail()));
        out.put("hostingProvider", nz(v.hostingProvider()));
        out.put("mailProvider", nz(v.mailProvider()));
        SiteOperatorValues.Business b = v.business();
        out.put("business.businessName", b == null ? "" : nz(b.businessName()));
        out.put("business.representative", b == null ? "" : nz(b.representative()));
        out.put("business.registrationNumber", b == null ? "" : nz(b.registrationNumber()));
        out.put("business.address", b == null ? "" : nz(b.address()));
        out.put("business.mailOrderNumber", b == null ? "" : nz(b.mailOrderNumber()));
        out.put("business.mailOrderAuthority", b == null ? "" : nz(b.mailOrderAuthority()));
        out.put("business.corporateNumber", b == null ? "" : nz(b.corporateNumber()));
        out.put("footerNotice", nz(notice.stored()));
        out.put("footerNoticeScope", notice.scope().name());
        return out;
    }

    /** 감사 기록에는 이메일 원문을 남기지 않는다(가린 값 {@code ab***@example.com}) */
    private static Object masked(String key, Object value) {
        if (EMAIL_FIELDS.contains(key) && value instanceof String s && !s.isEmpty()) {
            return MemberMasking.email(s);
        }
        return value;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
