package com.community.viewsetting.service;

import com.community.user.entity.MemberViewSettingEntity;
import com.community.user.repository.master.MemberViewSettingRepository;
import com.community.user.repository.master.UserRepository;
import com.community.viewsetting.dto.request.ViewSettingsRequest;
import com.community.viewsetting.dto.response.ViewSettingsResponse;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 보기 설정({@code member_view_settings}) — 야간 모드·글자 크기·댓글 기본 정렬. 규약은 {@code docs/features/member-social-contract.md} 「보기 설정」
 * (원본 community {@code docs/api-profile.md} {@code GET·PUT /api/me/settings}, 칸 셋).
 *
 * <ul>
 *   <li>읽기는 행을 만들지 않는다 — 행이 없으면 기본값({@code AUTO}·{@code 100}·{@code OLDEST}).</li>
 *   <li>저장은 세 칸을 통째로 덮는다. 틀린 칸은 한꺼번에 {@code 400 VALIDATION}(아무것도 저장하지 않는다). 기본값과 같은 값을 저장해도 행이 생긴다.</li>
 *   <li>서버는 값만 저장하고 적용은 화면 몫이다. 읽기 취향이라 이메일 인증 전·정지 중에도 쓸 수 있다(제한 검사 없음).</li>
 *   <li>저장은 회원 행을 잠근 뒤 만들거나 고친다(같은 회원의 동시 저장을 줄 세운다). 탈퇴한 계정이면 {@code 401}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MemberViewSettingsService {

    private final MemberViewSettingRepository settings;
    private final UserRepository users;

    public MemberViewSettingsService(MemberViewSettingRepository settings, UserRepository users) {
        this.settings = settings;
        this.users = users;
    }

    /** 내 보기 설정 — 저장한 적이 없으면 기본값 */
    public ViewSettingsResponse mine(long userId) {
        return settings.findById(userId).map(MemberViewSettingsService::view).orElseGet(MemberViewSettingsService::defaults);
    }

    /** 세 칸을 통째로 저장한다 → 저장된 값 */
    @Transactional
    public ViewSettingsResponse save(long userId, ViewSettingsRequest request) {
        ViewSettingsPolicy.Checked checked = ViewSettingsPolicy.check(request);

        users.findWithLockById(userId).filter(user -> !user.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = Instant.now();
        MemberViewSettingEntity setting = settings.findById(userId).orElse(null);
        if (setting == null) {
            setting = MemberViewSettingEntity.create(userId, checked.theme(), checked.textScale(), checked.commentOrder(), now);
            settings.persist(setting);
        } else {
            setting.change(checked.theme(), checked.textScale(), checked.commentOrder(), now);
        }
        return view(setting);
    }

    private static ViewSettingsResponse defaults() {
        return new ViewSettingsResponse(ViewSettingsPolicy.DEFAULT_THEME, ViewSettingsPolicy.DEFAULT_TEXT_SCALE, ViewSettingsPolicy.DEFAULT_COMMENT_ORDER);
    }

    private static ViewSettingsResponse view(MemberViewSettingEntity s) {
        return new ViewSettingsResponse(s.getTheme(), s.getTextScale(), s.getCommentOrder());
    }
}
