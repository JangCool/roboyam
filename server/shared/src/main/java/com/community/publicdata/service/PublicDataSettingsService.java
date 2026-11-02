package com.community.publicdata.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.nts.NtsProperties;
import com.community.business.nts.OdcloudNtsApi;
import com.community.publicdata.dto.response.PublicDataSettingsView;
import com.community.publicdata.exception.PublicDataKeyNotFoundException;
import com.community.publicdata.repository.master.PublicDataSettingStore;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공공데이터포털 설정(관리자 설정 › 시스템 › 공공데이터포털, business-contract 「국세청 진위확인 · 공공데이터 설정」) — 국세청 상태조회·진위확인이 같이 쓰는
 * 서비스 키 하나. 포트원 설정과 같은 규칙으로 {@link SecretEncryptor}({@code ROBOYAM_CRYPTO_KEY}) 암호문을 {@code public_data_settings} 에 둔다.
 *
 * <ul>
 *   <li>키 읽는 차례({@link #ntsServiceKey}): 화면에서 저장한 값 → 환경 변수 {@code ROBOYAM_NTS_SERVICE_KEY} → 없음. 국세청 조회 때마다 읽는다
 *       (화면에서 바꾸면 재기동 없이 다음 조회부터).</li>
 *   <li>화면({@link #view})에는 값이 없다 — 출처·마지막 변경·마지막 연결 시험만. 감사({@code PUBLIC_DATA_SETTINGS_UPDATE}·{@code _DELETE})에도 칸 이름만.</li>
 *   <li>연결 시험({@link #test})은 구현이 가짜여도 실제 국세청 상태조회를 한 번 부른다(트랜잭션 밖). 고정 시험 번호는 아무 실재 번호면 된다.</li>
 * </ul>
 * 권한 검사는 컨트롤러 선언이 한다(조회·시험 {@code site.read}, 저장·지우기 {@code site.secret}).
 */
@Service
public class PublicDataSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataSettingsService.class);

    /** 표의 칸 이름 */
    public static final String NTS_SERVICE_KEY = "NTS_SERVICE_KEY";
    /** 화면·감사의 칸 이름 */
    public static final String FIELD = "serviceKey";
    /** 감사 대상 종류 · 대상 ID */
    public static final String AUDIT_TARGET = "PUBLIC_DATA_SETTINGS";
    public static final String AUDIT_TARGET_ID = "nts";
    /** 키 최대 길이 */
    static final int VALUE_MAX = 500;
    /** 연결 시험에 쓰는 등록번호(실재 번호 — 응답만 오면 된다) */
    static final String PROBE_BIZ_NO = "1248100998";

    private final PublicDataSettingStore store;
    private final SecretEncryptor encryptor;
    private final NtsProperties nts;
    private final OdcloudNtsApi odcloud;
    private final AuditLogService audit;
    private final Clock clock;

    public PublicDataSettingsService(PublicDataSettingStore store, SecretEncryptor encryptor, NtsProperties nts, OdcloudNtsApi odcloud, AuditLogService audit,
                                     ObjectProvider<Clock> clocks) {
        this.store = store;
        this.encryptor = encryptor;
        this.nts = nts;
        this.odcloud = odcloud;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 지금 쓸 서비스 키 — 화면 저장값 → 환경 변수 → 없음(null). 화면 값을 풀 수 없으면(암호화 키가 없거나 바뀜 — 로그만) 환경 변수로 넘어간다.
     * 결과를 응답·로그에 싣지 않는다.
     */
    public String ntsServiceKey() {
        String screen = screenKey().orElse(null);
        if (screen != null) {
            return screen;
        }
        return envKey();
    }

    /** 화면 — 지금 구현·암호화 키·키 출처·마지막 변경·마지막 연결 시험(값 없음) */
    public PublicDataSettingsView view() {
        PublicDataSettingStore.Row row = store.find(NTS_SERVICE_KEY).orElse(null);
        PublicDataSettingsView.Source source = row != null && row.configured() ? PublicDataSettingsView.Source.SCREEN
                : envKey() != null ? PublicDataSettingsView.Source.ENV : PublicDataSettingsView.Source.NONE;
        PublicDataSettingsView.KeyState key = new PublicDataSettingsView.KeyState(source, row == null ? null : row.updatedAt(), row == null ? null : row.updatedByName());
        return new PublicDataSettingsView(nts.provider(), encryptor.isConfigured(), key, test(row));
    }

    /**
     * 서비스 키 저장 — 앞뒤 공백을 지우고, 빈 값·가운데 공백·{@value #VALUE_MAX}자 초과는 400 {@code fields.serviceKey}. 옛 키의 연결 시험 결과는 지운다.
     *
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public PublicDataSettingsView save(String serviceKey, long operatorId, String ip) {
        String value = serviceKey == null ? "" : serviceKey.strip();
        MessageRef problem = violation(value);
        if (problem != null) {
            throw ValidationException.of(FIELD, problem);
        }
        encryptor.requireConfigured();
        store.upsert(NTS_SERVICE_KEY, encryptor.encrypt(value), operatorId, now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PUBLIC_DATA_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, AUDIT_TARGET_ID)
                .with("fields", List.of(FIELD)));
        return view();
    }

    /**
     * 화면에서 저장한 키를 지운다 — 환경 변수가 있으면 그 값으로 돌아간다.
     *
     * @throws PublicDataKeyNotFoundException 화면에서 저장한 키가 없다
     */
    @Transactional
    public void deleteServiceKey(long operatorId, String ip) {
        if (!store.clear(NTS_SERVICE_KEY, operatorId, now())) {
            throw new PublicDataKeyNotFoundException();
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PUBLIC_DATA_SETTINGS_DELETE, AuditResult.SUCCESS).target(AUDIT_TARGET, AUDIT_TARGET_ID)
                .with("field", FIELD));
    }

    /** 연결 시험 — 지금 쓸 키로 실제 국세청 상태조회 한 번(트랜잭션 밖), 결과를 마지막 시험으로 남긴다. 키가 없으면 부르지 않고 {@code NO_KEY} */
    public PublicDataSettingsView.Test test(long operatorId) {
        String key = ntsServiceKey();
        PublicDataSettingsView.Result result = key == null ? PublicDataSettingsView.Result.NO_KEY : switch (odcloud.probe(key, PROBE_BIZ_NO)) {
            case OK -> PublicDataSettingsView.Result.OK;
            case KEY_INVALID -> PublicDataSettingsView.Result.KEY_INVALID;
            case TIMEOUT -> PublicDataSettingsView.Result.TIMEOUT;
            case ERROR -> PublicDataSettingsView.Result.ERROR;
        };
        store.recordTest(NTS_SERVICE_KEY, result.name(), operatorId, now());
        PublicDataSettingsView.Test saved = test(store.find(NTS_SERVICE_KEY).orElse(null));
        return saved != null ? saved : new PublicDataSettingsView.Test(result, now(), null);
    }

    /** 키 값 검사 — 문제가 없으면 null */
    static MessageRef violation(String value) {
        if (value.isEmpty()) {
            return new MessageRef("publicData.serviceKey.required");
        }
        if (value.length() > VALUE_MAX) {
            return new MessageRef("publicData.serviceKey.max", VALUE_MAX);
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return new MessageRef("publicData.serviceKey.whitespace");
        }
        return null;
    }

    private Optional<String> screenKey() {
        Optional<String> cipher = store.cipher(NTS_SERVICE_KEY);
        if (cipher.isEmpty()) {
            return Optional.empty();
        }
        if (!encryptor.isConfigured()) {
            log.error("공공데이터포털 서비스 키를 풀 수 없다 — 암호화 키(ROBOYAM_CRYPTO_KEY)가 없다. 환경 변수 키로 넘어간다");
            return Optional.empty();
        }
        try {
            String value = encryptor.decrypt(cipher.get());
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (IllegalArgumentException e) {
            log.error("공공데이터포털 서비스 키를 풀지 못했다 — 암호화 키가 바뀌었는지 확인. 환경 변수 키로 넘어간다"); // 값·암호문은 싣지 않는다
            return Optional.empty();
        }
    }

    private String envKey() {
        String key = nts.serviceKey();
        return key == null || key.isBlank() ? null : key.strip();
    }

    private static PublicDataSettingsView.Test test(PublicDataSettingStore.Row row) {
        if (row == null || row.testedResult() == null) {
            return null;
        }
        return new PublicDataSettingsView.Test(PublicDataSettingsView.Result.valueOf(row.testedResult()), row.testedAt(), row.testedByName());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
