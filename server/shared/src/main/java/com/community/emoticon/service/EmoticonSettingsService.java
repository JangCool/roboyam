package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.dto.request.EmoticonSettingsSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.repository.master.EmoticonSettingsStore;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsRow;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsValues;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로보티콘 설정(계약 가정 1·4·6·9 — 받기 보상 · 작가 하루 상한 · 팩당 개수 · 심사 대기 상한 · 유료 판매 켜기 · 수수료율 · 가격 단계 · 정산일 · 최소 지급액 · 셀프 환불 기간).
 * 공개 규칙({@code GET /api/emoticons/rules})과 관리자 설정 화면이 같은 값을 본다. 설정 행은 받기·신청·승인이 그때그때 읽는다(메모리 사본 없음 — 바로 반영).
 *
 * <p>저장 범위는 admin-web {@code UtilEmoticon.SETTING_RANGES}(목업 {@code mockEmoticonSaveSettings})와 같고 DB CHECK 가 한 번 더 막는다(V142·V143).
 * 가격 단계 = 1~10개 · 100원 단위 100~100,000원 · 겹침 없음(저장은 오름차순) · <b>상점에 나온 팩(판매 중·판매 중단)이 쓰는 단계는 뺄 수 없다</b>(목업은 판매 중만 — 서버는 판매 중단도 센다:
 * 다시 판매하면 그 가격으로 나오므로). 수수료·정산일·최소 지급액·환불 기간은 S2 에서 저장만 하고 S3·S4 가 쓴다.
 */
@Service
public class EmoticonSettingsService {

    public static final String TARGET = "EMOTICON_SETTINGS";

    /** 계약의 기본값(V142·V143 의 DEFAULT 와 같다) */
    public static final int DEFAULT_REWARD = 10;
    public static final int DEFAULT_DAILY_CAP = 500;
    public static final int DEFAULT_ITEM_MIN = 8;
    public static final int DEFAULT_ITEM_MAX = 40;
    public static final int DEFAULT_PENDING_LIMIT = 3;
    public static final List<Integer> DEFAULT_PRICE_TIERS = List.of(1500, 2500, 3500);
    public static final int DEFAULT_FEE_PERCENT = 30;
    public static final int DEFAULT_SETTLEMENT_DAY = 10;
    public static final int DEFAULT_PAYOUT_MINIMUM = 10_000;
    public static final int DEFAULT_REFUND_DAYS = 7;
    public static final double VAT_RATE = 0.1;
    public static final double WITHHOLDING_RATE = 0.033;
    /** 유료 판매는 처음에 꺼져 있다(목업 기본은 켜짐 — 실결제(S3)가 열리기 전에 유료 승인이 생기지 않게 운영자가 켠다) */
    public static final boolean DEFAULT_PAID_SALES_ENABLED = false;
    /** 가격 단계 범위 */
    public static final int TIER_MIN = 100;
    public static final int TIER_MAX = 100_000;
    public static final int TIER_UNIT = 100;
    public static final int TIERS_MAX = 10;

    private final EmoticonSettingsStore store;
    private final EmoticonContractService contracts;
    private final AuditLogService audit;
    private final Clock clock;

    public EmoticonSettingsService(EmoticonSettingsStore store, EmoticonContractService contracts, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.contracts = contracts;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 지금 설정 — 부르는 서비스의 트랜잭션에서 읽는다 */
    public SettingsRow current() {
        return store.read();
    }

    /** 가격을 정하기 전에(승인·가격 바꾸기) — 설정 행을 나눔 잠금으로 잡고 읽는다(설정 저장과 줄을 선다) */
    public SettingsRow currentForPricing() {
        return store.readShared();
    }

    /** 공개 규칙 */
    @Transactional(readOnly = true)
    public EmoticonPublicView.Rules rules() {
        SettingsRow s = store.read();
        return new EmoticonPublicView.Rules(EmoticonSpec.IMAGE_SIZE, EmoticonSpec.IMAGE_PADDING, EmoticonSpec.MAX_BYTES, EmoticonSpec.RECOMMENDED_BYTES,
                EmoticonSpec.MAX_FRAMES, s.itemMin(), s.itemMax(), EmoticonSpec.COVER_MAX_BYTES, EmoticonSpec.NAME_MIN, EmoticonSpec.NAME_MAX, EmoticonSpec.LABEL_MIN,
                EmoticonSpec.LABEL_MAX, EmoticonSpec.MAX_TAGS, EmoticonSpec.TAG_MAX, EmoticonSpec.DESCRIPTION_MAX, EmoticonSpec.DISPLAY_SIZE,
                EmoticonSpec.DISPLAY_SIZE_MOBILE, EmoticonSpec.PICKER_SIZE, s.rewardPerAcquire(), s.rewardDailyCap(), s.pendingLimit(), s.priceTiers(), VAT_RATE,
                s.feeRatePercent() / 100.0, s.refundDays(), s.settlementDay(), s.payoutMinimum(), WITHHOLDING_RATE, s.paidSalesEnabled());
    }

    /** 관리자 설정 화면 */
    @Transactional(readOnly = true)
    public EmoticonAdminView.SettingsView adminView() {
        return view(store.read());
    }

    /** 심사 기준(심사 서랍) */
    public EmoticonAdminView.PackRules packRules(SettingsRow s) {
        return new EmoticonAdminView.PackRules(s.itemMin(), s.itemMax(), s.paidSalesEnabled(), s.priceTiers());
    }

    /**
     * 저장 — 칸마다 정수·범위를 모아 {@code 400 fields.<칸>}, 하나라도 틀리면 저장 없음. 바뀐 칸이 없으면 쓰지 않는다.
     * 감사 {@code EMOTICON_SETTINGS_UPDATE}(바뀐 칸만 before/after — 돈 관련). 권한 {@code emoticon.settings} 는 컨트롤러가 본다.
     */
    @Transactional
    public EmoticonAdminView.SettingsView save(long operatorId, String ip, EmoticonSettingsSaveRequest body) {
        EmoticonSettingsSaveRequest b = body == null ? new EmoticonSettingsSaveRequest(null, null, null, null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        range(fields, "rewardPerAcquire", b.rewardPerAcquire(), 0, 1000);
        if (range(fields, "rewardDailyCap", b.rewardDailyCap(), 1, 100_000) && b.rewardPerAcquire() != null && b.rewardDailyCap() < b.rewardPerAcquire()) {
            fields.put("rewardDailyCap", new MessageRef("emoticon.settings.capBelowReward"));
        }
        boolean minOk = range(fields, "itemMin", b.itemMin(), 1, 40);
        if (range(fields, "itemMax", b.itemMax(), 8, 60) && minOk && b.itemMax() < b.itemMin()) {
            fields.put("itemMax", new MessageRef("emoticon.settings.maxBelowMin"));
        }
        range(fields, "pendingLimit", b.pendingLimit(), 1, 10);
        range(fields, "feeRatePercent", b.feeRatePercent(), 0, 90);
        range(fields, "settlementDay", b.settlementDay(), 1, 28);
        range(fields, "payoutMinimum", b.payoutMinimum(), 0, 1_000_000);
        range(fields, "refundDays", b.refundDays(), 1, 30);
        if (b.paidSalesEnabled() == null) {
            fields.put("paidSalesEnabled", new MessageRef("emoticon.settings.paidSalesEnabled.required"));
        }
        List<Integer> tiers = b.priceTiers() == null ? List.of() : b.priceTiers();
        boolean tiersOk = false;
        if (tiers.isEmpty() || tiers.size() > TIERS_MAX) {
            fields.put("priceTiers", new MessageRef("emoticon.settings.priceTiers.count", TIERS_MAX));
        } else if (tiers.stream().anyMatch(v -> v == null || v < TIER_MIN || v > TIER_MAX || v % TIER_UNIT != 0)) {
            fields.put("priceTiers", new MessageRef("emoticon.settings.priceTiers.range", String.format("%,d", TIER_UNIT), String.format("%,d", TIER_MIN),
                    String.format("%,d", TIER_MAX)));
        } else if (new HashSet<>(tiers).size() != tiers.size()) {
            fields.put("priceTiers", new MessageRef("emoticon.settings.priceTiers.duplicate"));
        } else {
            tiersOk = true;
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        SettingsRow before = store.readForUpdate();
        List<Integer> sorted = tiers.stream().sorted().toList();
        if (tiersOk) {
            List<Integer> missing = store.tiersInUse().stream().filter(v -> !sorted.contains(v)).toList();
            if (!missing.isEmpty()) {
                throw ValidationException.of("priceTiers", new MessageRef("emoticon.settings.priceTiers.inUse",
                        missing.stream().map(v -> String.format("%,d", v)).collect(Collectors.joining(", "))));
            }
        }
        Map<String, Object> was = new LinkedHashMap<>();
        Map<String, Object> now = new LinkedHashMap<>();
        diff(was, now, "rewardPerAcquire", before.rewardPerAcquire(), b.rewardPerAcquire());
        diff(was, now, "rewardDailyCap", before.rewardDailyCap(), b.rewardDailyCap());
        diff(was, now, "itemMin", before.itemMin(), b.itemMin());
        diff(was, now, "itemMax", before.itemMax(), b.itemMax());
        diff(was, now, "pendingLimit", before.pendingLimit(), b.pendingLimit());
        diff(was, now, "paidSalesEnabled", before.paidSalesEnabled(), b.paidSalesEnabled());
        diff(was, now, "feeRatePercent", before.feeRatePercent(), b.feeRatePercent());
        diff(was, now, "settlementDay", before.settlementDay(), b.settlementDay());
        diff(was, now, "payoutMinimum", before.payoutMinimum(), b.payoutMinimum());
        diff(was, now, "refundDays", before.refundDays(), b.refundDays());
        diff(was, now, "priceTiers", before.priceTiers(), sorted);
        if (!now.isEmpty()) {
            Instant at = clock.instant();
            store.save(new SettingsValues(b.rewardPerAcquire(), b.rewardDailyCap(), b.itemMin(), b.itemMax(), b.pendingLimit(), b.paidSalesEnabled(), b.feeRatePercent(),
                    b.settlementDay(), b.payoutMinimum(), b.refundDays(), sorted), operatorId, at);
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_SETTINGS_UPDATE, AuditResult.SUCCESS).target(TARGET, "1").with("before", was)
                    .with("after", now));
        }
        return view(store.read());
    }

    private EmoticonAdminView.SettingsView view(SettingsRow s) {
        EmoticonAdminView.SettingDefaults defaults = new EmoticonAdminView.SettingDefaults(DEFAULT_REWARD, DEFAULT_DAILY_CAP, DEFAULT_ITEM_MIN, DEFAULT_ITEM_MAX,
                DEFAULT_PENDING_LIMIT, DEFAULT_FEE_PERCENT, DEFAULT_SETTLEMENT_DAY, DEFAULT_PAYOUT_MINIMUM, DEFAULT_REFUND_DAYS, DEFAULT_PAID_SALES_ENABLED,
                DEFAULT_PRICE_TIERS);
        return new EmoticonAdminView.SettingsView(s.rewardPerAcquire(), s.rewardDailyCap(), s.itemMin(), s.itemMax(), s.pendingLimit(), s.feeRatePercent(),
                s.settlementDay(), s.payoutMinimum(), s.refundDays(), s.paidSalesEnabled(), s.priceTiers(), store.tiersInUse(), contracts.summary(), defaults, s.updatedAt(),
                s.updatedByName());
    }

    /** 정수·범위 — 맞으면 참 */
    private static boolean range(Map<String, MessageRef> fields, String field, Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            fields.put(field, new MessageRef("emoticon.settings.range", String.format("%,d", min), String.format("%,d", max)));
            return false;
        }
        return true;
    }

    private static void diff(Map<String, Object> was, Map<String, Object> now, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            was.put(field, before instanceof List<?> l ? new ArrayList<>(l) : before);
            now.put(field, after instanceof List<?> l ? new ArrayList<>(l) : after);
        }
    }
}
