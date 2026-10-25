package com.community.member.service;

import com.community.media.service.ImageStorage;
import com.community.member.dto.response.MemberConsentRecord;
import com.community.member.dto.response.MemberInboxView;
import com.community.member.dto.response.MemberNicknameChangeUsage;
import com.community.member.dto.response.MemberNicknameImageView;
import com.community.member.dto.response.MemberNotificationSettingView;
import com.community.member.dto.response.MemberSettingsView;
import com.community.member.dto.response.MemberViewSettingsView;
import com.community.member.repository.master.MemberSettingsQueries;
import com.community.member.repository.master.MemberSettingsQueries.Choice;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventSettingEntity;
import com.community.membernotification.repository.master.MemberEventSettingRepository;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.nicknameimage.repository.master.NicknameImageRequestRepository;
import com.community.site.dto.response.DisplayValuesView;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.terms.entity.TermsKind;
import com.community.user.entity.MemberDisplayPreferenceEntity;
import com.community.user.entity.MemberViewSettingEntity;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.MemberDisplayPreferenceRepository;
import com.community.user.repository.master.MemberViewSettingRepository;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 관리자 회원 상세의 개인 설정 · 쪽지 수신 · 닉네임 변경 횟수 · 닉네임 이미지 · 약관 동의 버전 나누기 — {@link MemberService#detail} 이 탈퇴하지 않은 회원에게만 부른다.
 * <b>트랜잭션은 부르는 서비스가 연다</b>(읽기만 한다).
 *
 * <ul>
 *   <li><b>표시 형식</b>: 회원 행(V52)이 있으면 그 값({@code displayOwn=true}), 없으면 사이트 표시 기본값(설정 행이 없으면 처음 값) — 회원 화면 {@code MemberDisplayService} 와 같은 층.</li>
 *   <li><b>보기 설정</b>: 회원 행(V73)이 있으면 그 값({@code own=true}), 없으면 기본값 {@code AUTO}·{@code 100}·{@code OLDEST}.</li>
 *   <li><b>알림 설정</b>: 회원 이벤트 카탈로그 전부(준비 중 포함, 카탈로그 차례). 잠금 = 코드 잠금 또는 운영자 「회원이 끌 수 있음」 꺼짐(표에 줄이 없으면 코드의 처음 값) —
 *       잠긴 이벤트는 받기가 늘 true, 아니면 회원이 고른 값(줄이 없으면 true). 회원 화면 {@code MyNotificationSettingsService} 와 같은 잠금 셈이지만,
 *       운영자가 보내지 않는 채널도 회원이 고른 값 그대로 보인다(운영자 끔은 이벤트 설정 화면이 보여 준다).</li>
 *   <li><b>닉네임 이미지</b>: 회원 화면 {@code NicknameImageService#view} 와 같은 기준 — 적용 중(APPROVED·파일 있음), 최근 신청이 대기·반려일 때만 신청.</li>
 * </ul>
 */
@Component
public class MemberSettingsAssembler {

    /** 동의 버전 문자열 조각({@code T3}·{@code P2}) */
    private static final Pattern AGREEMENT_PART = Pattern.compile("^[A-Z]\\d+$");

    private final MemberSettingsQueries queries;
    private final MemberDisplayPreferenceRepository displayPreferences;
    private final MemberViewSettingRepository viewSettings;
    private final SiteSettingRepository siteSettings;
    private final MemberEventSettingRepository eventSettings;
    private final NicknameImageRequestRepository nicknameImages;
    private final ImageStorage storage;
    private final Messages messages;

    public MemberSettingsAssembler(MemberSettingsQueries queries, MemberDisplayPreferenceRepository displayPreferences, MemberViewSettingRepository viewSettings,
                                   SiteSettingRepository siteSettings, MemberEventSettingRepository eventSettings, NicknameImageRequestRepository nicknameImages,
                                   ImageStorage storage, Messages messages) {
        this.queries = queries;
        this.displayPreferences = displayPreferences;
        this.viewSettings = viewSettings;
        this.siteSettings = siteSettings;
        this.eventSettings = eventSettings;
        this.nicknameImages = nicknameImages;
        this.storage = storage;
        this.messages = messages;
    }

    /** 개인 설정 구획과 쪽지 수신 — 건수·쪽지 수신은 한 번의 질의 */
    public Result assemble(long userId) {
        MemberSettingsQueries.Counts counts = queries.countsOf(userId);

        MemberDisplayPreferenceEntity own = displayPreferences.findById(userId).orElse(null);
        DisplayValuesView display = own != null
                ? new DisplayValuesView(own.getLanguage(), own.getZoneId(), own.getTimeFormat(), own.getDateFormat())
                : siteDefaults();

        MemberViewSettingEntity viewRow = viewSettings.findById(userId).orElse(null);
        MemberViewSettingsView view = viewRow != null
                ? new MemberViewSettingsView(viewRow.getTheme(), viewRow.getTextScale(), viewRow.getCommentOrder(), true)
                : new MemberViewSettingsView(MemberViewSettingEntity.DEFAULT_THEME, MemberViewSettingEntity.DEFAULT_TEXT_SCALE,
                        MemberViewSettingEntity.DEFAULT_COMMENT_ORDER, false);

        MemberSettingsView settings = new MemberSettingsView(display, own != null, view, notifications(userId), null, counts.blockedUsers(),
                counts.blockedWords(), counts.memos(), new MemberInboxView(counts.notifications(), counts.unreadNotifications()));
        return new Result(settings, counts.acceptDm());
    }

    /** 닉네임 변경 횟수 */
    public MemberNicknameChangeUsage nicknameChange(ProfileEntity profile) {
        return new MemberNicknameChangeUsage(profile.getNicknameChangeCount(), ProfileEntity.NICKNAME_FREE_LIMIT);
    }

    /** 닉네임 이미지 — 적용 중인 그림과 최근 신청(대기·반려) */
    public MemberNicknameImageView nicknameImage(long userId) {
        MemberNicknameImageView.Current current = nicknameImages.findFirstByUserIdAndStatus(userId, NicknameImageStatus.APPROVED)
                .filter(r -> r.getImageKey() != null)
                .map(r -> new MemberNicknameImageView.Current(storage.publicUrl(r.getImageKey()), r.getReviewedAt()))
                .orElse(null);
        MemberNicknameImageView.Request request = nicknameImages.findFirstByUserIdOrderByIdDesc(userId)
                .filter(r -> r.getStatus() == NicknameImageStatus.PENDING || r.getStatus() == NicknameImageStatus.REJECTED)
                .map(r -> new MemberNicknameImageView.Request(Long.toString(r.getId()), r.getStatus().name(),
                        r.getImageKey() == null ? null : storage.publicUrl(r.getImageKey()), r.getCreatedAt(),
                        r.getStatus() == NicknameImageStatus.REJECTED ? r.getRejectReason() : null))
                .orElse(null);
        return new MemberNicknameImageView(current, request);
    }

    /**
     * 동의 버전 {@code T{n}.P{m}} 을 이용약관·개인정보처리방침으로 나눈다(동의 시각은 같다 — 가입 때 함께 받는다). 이용약관 조각이 없는 옛 값({@code install}·{@code v1} 등)은
     * 이용약관 칸에 통째로 두고 개인정보는 null. 개인정보 조각이 없으면 개인정보 null.
     *
     * @return [이용약관, 개인정보] — 버전이 없으면 둘 다 null
     */
    public static MemberConsentRecord[] splitConsent(Instant agreedAt, String version) {
        if (version == null || version.isBlank()) {
            return new MemberConsentRecord[] {new MemberConsentRecord(agreedAt, version), null};
        }
        String terms = null;
        String privacy = null;
        boolean structured = true;
        for (String part : version.split("\\.")) {
            if (!AGREEMENT_PART.matcher(part).matches()) {
                structured = false;
                break;
            }
            if (part.startsWith(TermsKind.TERMS.agreementPrefix())) {
                terms = part;
            } else if (part.startsWith(TermsKind.PRIVACY.agreementPrefix())) {
                privacy = part;
            }
        }
        if (!structured || terms == null) {
            return new MemberConsentRecord[] {new MemberConsentRecord(agreedAt, version), null};
        }
        return new MemberConsentRecord[] {new MemberConsentRecord(agreedAt, terms), privacy == null ? null : new MemberConsentRecord(agreedAt, privacy)};
    }

    private List<MemberNotificationSettingView> notifications(long userId) {
        Map<String, MemberEventSettingEntity> operator = eventSettings.findAll().stream()
                .collect(Collectors.toMap(MemberEventSettingEntity::getCode, Function.identity()));
        Map<String, Choice> choices = queries.choicesOf(userId);
        return Arrays.stream(MemberEvent.values()).map(event -> {
            MemberEventSettingEntity row = operator.get(event.code());
            boolean canTurnOff = !event.locked() && (row == null ? event.defaultMemberCanTurnOff() : row.isMemberCanTurnOff());
            Choice choice = choices.get(event.code());
            boolean inApp = !canTurnOff || choice == null || choice.inApp();
            boolean email = !canTurnOff || choice == null || choice.email();
            return new MemberNotificationSettingView(event.code(), messages.get(new MessageRef(event.nameKey())), inApp, email, !canTurnOff);
        }).toList();
    }

    private DisplayValuesView siteDefaults() {
        return siteSettings.findById(SiteSettingEntity.ROW_ID)
                .map(r -> new DisplayValuesView(r.getDisplayLanguage(), r.getDisplayZoneId(), r.getDisplayTimeFormat(), r.getDisplayDateFormat()))
                .orElseGet(() -> new DisplayValuesView(SiteSettingEntity.DEFAULT_LANGUAGE, SiteSettingEntity.DEFAULT_ZONE_ID, SiteSettingEntity.DEFAULT_TIME_FORMAT,
                        SiteSettingEntity.DEFAULT_DATE_FORMAT));
    }

    /** 개인 설정 구획과 쪽지 받기 */
    public record Result(MemberSettingsView settings, boolean acceptDm) {
    }
}
