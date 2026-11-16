package com.community.pointgame.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.pointgame.dto.response.PointGameSettingsView;
import com.community.pointgame.entity.PointGameSettingEntity;
import com.community.pointgame.repository.master.PointGameSettingStore;
import com.community.pointgame.repository.master.PointGameSettingStore.Current;
import com.community.pointgame.repository.master.PointGameSettingStore.SettingsRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 게임 설정(point-games-contract 「당첨 확인 링크」) — 관리자 조회·저장(권한 {@code pointshop.read}·{@code pointshop.manage} 는 관리자 컨트롤러 선언)과 회원 화면의 링크 읽기.
 *
 * <ul>
 *   <li><b>검사</b>: 사업자 이름 — 앞뒤 공백을 뺀 1~{@value PointGameSettingEntity#PROVIDER_NAME_MAX}자, 제어 문자 없음({@code fields.lotteryProviderName}).
 *       링크 — 앞뒤 공백을 뺀 {@value PointGameSettingEntity#CHECK_URL_MAX}자 이하의 {@code https://} 절대 주소, 호스트에 점이 있어야 하고 공백·제어 문자·사용자 정보({@code a@b}) 없음
 *       ({@code fields.lotteryCheckUrl}). 두 칸 모두 보고 한 번에 400.</li>
 *   <li><b>저장</b>: 설정 행을 잠근 뒤 바뀐 칸만 감사({@code POINT_GAME_SETTINGS_UPDATE}, 전→후 — 일반 운영). 같은 값이면 감사 없이 그대로.</li>
 *   <li><b>회원 링크</b>({@link #checkLink}): 저장 값이 위 규칙에 맞을 때만(아니면 빈 값 — 화면은 링크를 그리지 않는다).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PointGameSettingsService {

    /** 감사 대상 종류 */
    public static final String TARGET_SETTINGS = "POINT_GAME_SETTINGS";

    /** 회원 화면에 보일 당첨 확인 링크 */
    public record CheckLink(String providerName, String url) {
    }

    private final PointGameSettingStore store;
    private final AuditLogService audit;
    private final Clock clock;

    public PointGameSettingsService(PointGameSettingStore store, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    public PointGameSettingsView settings() {
        SettingsRow row = store.settings();
        return new PointGameSettingsView(row.providerName(), row.checkUrl(), PointGameSettingEntity.DEFAULT_PROVIDER_NAME, PointGameSettingEntity.DEFAULT_CHECK_URL,
                row.updatedAt(), row.updatedByName());
    }

    /** 회원 화면의 당첨 확인 링크 — 저장 값이 규칙에 맞지 않으면 빈 값 */
    public Optional<CheckLink> checkLink() {
        SettingsRow row = store.settings();
        if (providerNameProblem(row.providerName()) != null || checkUrlProblem(row.checkUrl()) != null) {
            return Optional.empty();
        }
        return Optional.of(new CheckLink(row.providerName().strip(), row.checkUrl().strip()));
    }

    /**
     * 두 칸을 한 번에 저장한다.
     *
     * @throws ValidationException {@code fields.lotteryProviderName}·{@code fields.lotteryCheckUrl}
     */
    @Transactional
    public PointGameSettingsView update(String providerName, String checkUrl, long operatorId, String ip) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        MessageRef nameProblem = providerNameProblem(providerName);
        if (nameProblem != null) {
            bad.put("lotteryProviderName", nameProblem);
        }
        MessageRef urlProblem = checkUrlProblem(checkUrl);
        if (urlProblem != null) {
            bad.put("lotteryCheckUrl", urlProblem);
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        String name = providerName.strip();
        String url = checkUrl.strip();
        Current before = store.lock();
        if (!name.equals(before.providerName()) || !url.equals(before.checkUrl())) {
            store.update(name, url, operatorId, now());
            Map<String, Object> from = new LinkedHashMap<>();
            Map<String, Object> to = new LinkedHashMap<>();
            if (!name.equals(before.providerName())) {
                from.put("lotteryProviderName", before.providerName());
                to.put("lotteryProviderName", name);
            }
            if (!url.equals(before.checkUrl())) {
                from.put("lotteryCheckUrl", before.checkUrl());
                to.put("lotteryCheckUrl", url);
            }
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_GAME_SETTINGS_UPDATE, AuditResult.SUCCESS).target(TARGET_SETTINGS, "1")
                    .with("before", from).with("after", to));
        }
        return settings();
    }

    /** 사업자 이름 문제(없으면 null) — 앞뒤 공백을 뺀 1~30자, 제어 문자 없음 */
    static MessageRef providerNameProblem(String raw) {
        String value = raw == null ? "" : raw.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > PointGameSettingEntity.PROVIDER_NAME_MAX || value.chars().anyMatch(Character::isISOControl)) {
            return new MessageRef("pointGame.settings.providerName.length", PointGameSettingEntity.PROVIDER_NAME_MAX);
        }
        return null;
    }

    /** 링크 문제(없으면 null) — {@code https://} 절대 주소(호스트에 점), 300자 이하, 공백·제어 문자·사용자 정보 없음 */
    static MessageRef checkUrlProblem(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > PointGameSettingEntity.CHECK_URL_MAX
                || value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isISOControl(cp) || Character.isSpaceChar(cp))) {
            return invalidUrl();
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return invalidUrl();
        }
        String host = uri.getHost();
        if (uri.getScheme() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT)) || host == null || !host.contains(".") || host.startsWith(".")
                || host.endsWith(".") || uri.getRawUserInfo() != null) {
            return invalidUrl();
        }
        return null;
    }

    private static MessageRef invalidUrl() {
        return new MessageRef("pointGame.settings.checkUrl.invalid", PointGameSettingEntity.CHECK_URL_MAX);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
