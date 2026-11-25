package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.request.SiteSearchVerificationRequest;
import com.community.site.dto.response.SiteSearchVerificationResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색 엔진 소유 확인 코드(관리자 설정 > 일반 「검색 엔진 등록」) 조회·저장 — 사이트 설정 행의 {@code search_verify_*} 칸(V271).
 *
 * <p>칸마다 앞뒤 공백을 떼고 비면 null(그 태그를 넣지 않는다), 아니면 영문·숫자·{@code -}·{@code _} 1~{@value #MAX}자. 어긋난 칸을 모아 한꺼번에 {@code 400 VALIDATION}
 * ({@code fields.naver}·{@code fields.google}·{@code fields.bing}).
 * 바뀐 때만 감사({@code SITE_SEARCH_VERIFICATION_UPDATE}, 바뀐 칸의 {@code before}/{@code after})와 관리자 알림(사이트 설정 변경, 변형 {@code siteSearchVerification})을 남긴다.
 * 감사 기록이 공개 사이트 설정 사본({@code site.info})의 세대 번호를 올리므로({@code CacheAutoRefresh}) 모든 api 서버가 몇 초 안에 새 값으로 {@code index.html} 머리를 만든다.
 */
@Service
public class SiteSearchVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SiteSearchVerificationService.class);

    /** 칸 하나의 최대 글자 수 — V271 의 {@code varchar(100)} 와 같다 */
    static final int MAX = 100;
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1," + MAX + "}$");

    private final SiteSettingRepository settings;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public SiteSearchVerificationService(SiteSettingRepository settings, AuditLogService audit, AdminNotices notices) {
        this.settings = settings;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public SiteSearchVerificationResponse load() {
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        return view(row);
    }

    @Transactional
    public SiteSearchVerificationResponse save(AdminActor actor, SiteSearchVerificationRequest request) {
        String naver = normalize(request == null ? null : request.naver());
        String google = normalize(request == null ? null : request.google());
        String bing = normalize(request == null ? null : request.bing());

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        check(errors, "naver", naver);
        check(errors, "google", google);
        check(errors, "bing", bing);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        Map<String, Object> before = flat(row.getSearchVerifyNaver(), row.getSearchVerifyGoogle(), row.getSearchVerifyBing());
        Map<String, Object> after = flat(naver, google, bing);
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        before.forEach((key, value) -> {
            if (!Objects.equals(value, after.get(key))) {
                from.put(key, value);
                to.put(key, after.get(key));
            }
        });
        if (!to.isEmpty()) {
            row.changeSearchVerification(naver, google, bing, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_SEARCH_VERIFICATION_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("before", from).with("after", to));
            notices.siteSettingsChanged(actor, "siteSearchVerification");
            log.info("검색 엔진 소유 확인 코드를 바꿨다 operatorId={} fields={}", actor.operatorId(), to.keySet());
        }
        return view(row);
    }

    private static SiteSearchVerificationResponse view(SiteSettingEntity row) {
        return new SiteSearchVerificationResponse(row.getSearchVerifyNaver(), row.getSearchVerifyGoogle(), row.getSearchVerifyBing());
    }

    /** 앞뒤 공백을 떼고 비면 null */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static void check(Map<String, MessageRef> errors, String field, String value) {
        if (value != null && !CODE.matcher(value).matches()) {
            errors.put(field, new MessageRef("siteSearchVerification.code", String.valueOf(MAX)));
        }
    }

    /** 감사 「바뀐 내용」의 칸 — 없는 값은 빈 글자로(맵에 null 을 넣지 않는다) */
    private static Map<String, Object> flat(String naver, String google, String bing) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("naver", naver == null ? "" : naver);
        out.put("google", google == null ? "" : google);
        out.put("bing", bing == null ? "" : bing);
        return out;
    }
}
