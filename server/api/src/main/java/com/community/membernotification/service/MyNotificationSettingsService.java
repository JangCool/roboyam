package com.community.membernotification.service;

import com.community.membernotification.dto.request.MyNotificationSettingInput;
import com.community.membernotification.dto.request.MyNotificationSettingsRequest;
import com.community.membernotification.dto.response.MyNotificationSettingView;
import com.community.membernotification.dto.response.MyNotificationSettingsView;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventSettingEntity;
import com.community.membernotification.exception.NotificationSettingLockedException;
import com.community.membernotification.repository.master.MemberEventSettingRepository;
import com.community.membernotification.repository.master.MemberNotificationPreferenceQueries;
import com.community.membernotification.repository.master.MemberNotificationPreferenceQueries.Choice;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 설정 — {@code GET}·{@code PUT /api/me/notification-settings}. docs/features/member-mypage-contract.md 「알림 설정」·「결정」.
 *
 * <ul>
 *   <li><b>이벤트 목록</b>: 회원 이벤트 카탈로그({@link MemberEvent}) 중 준비된 것({@code featureReady}), 카탈로그 차례. 운영자 값은 {@code member_event_settings}(줄이 없으면 코드의 처음 값 —
 *       발행기 {@code MemberNotificationPublisher} 와 같은 셈).</li>
 *   <li><b>잠금</b>: 코드 잠금({@link MemberEvent#lockReason()}) 또는 운영자 「회원이 끌 수 있음」 꺼짐 → {@code memberCanTurnOff=false}, 받기는 늘 true.
 *       운영자가 보내지 않는 채널도 늘 true 로 보인다.</li>
 *   <li><b>저장</b>: 보낸 줄만 바꾼다. 검사 순서 — {@code 400 VALIDATION fields.events|events[i].code|inApp|email}(모르는 코드·준비 중·같은 코드 두 번·빠진 칸) →
 *       잠긴 이벤트를 끄려 함 {@code 409 NOTIFICATION_SETTING_LOCKED}(잠긴 이벤트를 켬으로 보내면 무시). 운영자가 보내지 않는 채널의 값은 바꾸지 않는다(전에 고른 값을 둔다).
 *       응답은 저장 뒤 {@code GET} 과 같다.</li>
 *   <li><b>이메일 받기</b>: 이메일이 없으면 {@code NO_EMAIL}, 인증 전이면 {@code NOT_VERIFIED} — 회원 알림 메일 발송기의 수신 조건과 같다.</li>
 * </ul>
 */
@Service
public class MyNotificationSettingsService {

    static final String NO_EMAIL = "NO_EMAIL";
    static final String NOT_VERIFIED = "NOT_VERIFIED";

    private final UserRepository users;
    private final MemberEventSettingRepository eventSettings;
    private final MemberNotificationPreferenceQueries preferences;
    private final Messages messages;

    public MyNotificationSettingsService(UserRepository users, MemberEventSettingRepository eventSettings, MemberNotificationPreferenceQueries preferences,
                                         Messages messages) {
        this.users = users;
        this.eventSettings = eventSettings;
        this.preferences = preferences;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public MyNotificationSettingsView get(long userId) {
        return view(requireUser(userId));
    }

    @Transactional
    public MyNotificationSettingsView update(long userId, MyNotificationSettingsRequest body) {
        UserEntity user = requireUser(userId);
        List<MyNotificationSettingInput> inputs = body == null ? null : body.events();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (inputs == null) {
            invalid.put("events", new MessageRef("member.notification.settings.events.required"));
            throw new ValidationException(invalid);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            MyNotificationSettingInput input = inputs.get(i);
            String path = "events[" + i + "]";
            if (input == null) {
                invalid.put(path + ".code", new MessageRef("member.notification.settings.code.invalid"));
                continue;
            }
            Optional<MemberEvent> event = MemberEvent.ofCode(input.code()).filter(MemberEvent::featureReady);
            if (event.isEmpty() || !seen.add(event.get().code())) {
                invalid.put(path + ".code", new MessageRef("member.notification.settings.code.invalid"));
            }
            if (input.inApp() == null) {
                invalid.put(path + ".inApp", new MessageRef("member.notification.settings.choice.required"));
            }
            if (input.email() == null) {
                invalid.put(path + ".email", new MessageRef("member.notification.settings.choice.required"));
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        Map<String, MemberEventSettingEntity> settings = operatorSettings();
        Map<String, Choice> current = preferences.choicesOf(userId);
        Instant now = Instant.now();
        List<Runnable> writes = new ArrayList<>();
        for (MyNotificationSettingInput input : inputs) {
            MemberEvent event = MemberEvent.ofCode(input.code()).orElseThrow();
            Operator op = Operator.of(event, settings.get(event.code()));
            if (!op.memberCanTurnOff()) {
                if (!input.inApp() || !input.email()) {
                    throw new NotificationSettingLockedException();
                }
                continue; // 잠긴 이벤트를 켬으로 보냈다 — 저장할 것이 없다
            }
            Choice before = current.get(event.code());
            boolean inApp = op.inAppOffered() ? input.inApp() : before == null || before.inApp();
            boolean email = op.emailOffered() ? input.email() : before == null || before.email();
            writes.add(() -> preferences.upsert(userId, event.code(), inApp, email, now));
        }
        writes.forEach(Runnable::run);
        return view(user);
    }

    private MyNotificationSettingsView view(UserEntity user) {
        Map<String, MemberEventSettingEntity> settings = operatorSettings();
        Map<String, Choice> choices = preferences.choicesOf(user.getId());
        Locale locale = messages.locale();
        List<MyNotificationSettingView> events = Arrays.stream(MemberEvent.values()).filter(MemberEvent::featureReady).map(event -> {
            Operator op = Operator.of(event, settings.get(event.code()));
            Choice choice = choices.get(event.code());
            boolean inApp = !op.memberCanTurnOff() || !op.inAppOffered() || choice == null || choice.inApp();
            boolean email = !op.memberCanTurnOff() || !op.emailOffered() || choice == null || choice.email();
            return new MyNotificationSettingView(event.code(), event.area().id(), messages.get(new MessageRef(event.nameKey()), locale),
                    messages.get(new MessageRef(event.descriptionKey()), locale), op.inAppOffered(), op.emailOffered(), op.memberCanTurnOff(),
                    event.lockReason() == null ? null : event.lockReason().name(), inApp, email);
        }).toList();

        String blocked = user.getEmail() == null || user.getEmail().isBlank() ? NO_EMAIL : !user.isEmailVerified() ? NOT_VERIFIED : null;
        return new MyNotificationSettingsView(events, blocked == null, blocked);
    }

    private Map<String, MemberEventSettingEntity> operatorSettings() {
        return eventSettings.findAll().stream().collect(Collectors.toMap(MemberEventSettingEntity::getCode, Function.identity()));
    }

    private UserEntity requireUser(long userId) {
        return users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }

    /** 운영자 값을 이 회원 화면의 뜻으로 — 줄이 없으면 코드의 처음 값 */
    private record Operator(boolean inAppOffered, boolean emailOffered, boolean memberCanTurnOff) {

        static Operator of(MemberEvent event, MemberEventSettingEntity row) {
            boolean enabled = row == null ? event.defaultEnabled() : row.isEnabled();
            boolean inApp = row == null ? event.defaultInApp() : row.isInApp();
            boolean email = row == null ? event.defaultEmail() : row.isEmail();
            boolean canTurnOff = row == null ? event.defaultMemberCanTurnOff() : row.isMemberCanTurnOff();
            return new Operator(enabled && inApp, enabled && email, !event.locked() && canTurnOff);
        }
    }
}
