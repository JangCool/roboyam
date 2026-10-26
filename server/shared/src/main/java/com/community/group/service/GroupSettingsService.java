package com.community.group.service;

import com.community.group.config.GroupProperties;
import com.community.group.dto.GroupRuleValues;
import com.community.group.entity.GroupCreationMode;
import com.community.group.entity.GroupSettingsEntity;
import com.community.group.repository.master.GroupSettingsRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 설정 네 구획을 읽는 <b>한 곳</b> — 지금 적용되는 값 = 저장된 칸 ?? 설정 파일 값(범위로 보정). 캐시하지 않는다(요청마다 기본 키 한 번 읽기 — 저장하면 다음 요청부터
 * 모든 서버에 적용). 공개 사이트(개설·가입 판정)와 관리자 앱(설정 화면)이 같이 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class GroupSettingsService {

    /** 각 칸의 범위 — DB CHECK(V89 ck_group_settings_*)·화면 limits 와 같다 */
    public static final int MAX_PER_OWNER_MIN = 1, MAX_PER_OWNER_MAX = 10;
    public static final int MANAGER_LIMIT_MIN = 1, MANAGER_LIMIT_MAX = 10;
    public static final int COOLDOWN_MIN = 0, COOLDOWN_MAX = 365;
    public static final int RETRY_MIN = 0, RETRY_MAX = 720;
    public static final int REJECT_LIMIT_MIN = 1, REJECT_LIMIT_MAX = 3;
    public static final int DAILY_LIMIT_MIN = 1, DAILY_LIMIT_MAX = 100;
    public static final int TEXT_MAX_MIN = 10, TEXT_MAX_MAX = 100;
    public static final int REAPPLY_MIN = 0, REAPPLY_MAX = 90;
    public static final int CAPACITY_MIN = 5, CAPACITY_MAX = 200;
    public static final int TRENDING_SIZE_MIN = 1, TRENDING_SIZE_MAX = 20;
    public static final int TRENDING_DAYS_MIN = 1, TRENDING_DAYS_MAX = 90;
    public static final int EVENT_UPCOMING_MIN = 1, EVENT_UPCOMING_MAX = 100;
    public static final int EVENT_AHEAD_MIN = 1, EVENT_AHEAD_MAX = 730;
    public static final int EVENT_LENGTH_MIN = 1, EVENT_LENGTH_MAX = 30;

    private final GroupSettingsRepository settings;
    private final GroupProperties props;

    public GroupSettingsService(GroupSettingsRepository settings, GroupProperties props) {
        this.settings = settings;
        this.props = props;
    }

    /** 설정 행(마이그레이션이 넣어 둔다 — 없으면 빈 값으로 본다) */
    public Optional<GroupSettingsEntity> row() {
        return settings.findById(GroupSettingsEntity.ROW_ID);
    }

    // ── 기본값(설정 파일, 범위로 보정) ─────────────────────────────────────────

    public GroupRuleValues.Creation creationDefaults() {
        return new GroupRuleValues.Creation(props.creationMode() == null ? GroupCreationMode.APPROVAL : props.creationMode(), props.minGrade(),
                clamp(props.maxPerOwner(), MAX_PER_OWNER_MIN, MAX_PER_OWNER_MAX));
    }

    public GroupRuleValues.Manager managerDefaults() {
        int max = clamp(props.managerLimitMax(), MANAGER_LIMIT_MIN, MANAGER_LIMIT_MAX);
        int def = Math.min(clamp(props.managerLimitDefault(), MANAGER_LIMIT_MIN, MANAGER_LIMIT_MAX), max);
        return new GroupRuleValues.Manager(props.managersEnabled(), def, max);
    }

    public GroupRuleValues.Visibility visibilityDefaults() {
        return new GroupRuleValues.Visibility(props.visibilityEnabled(), clamp(props.visibilityCooldownDays(), COOLDOWN_MIN, COOLDOWN_MAX),
                clamp(props.visibilityRetryHours(), RETRY_MIN, RETRY_MAX), clamp(props.visibilityRejectLimit(), REJECT_LIMIT_MIN, REJECT_LIMIT_MAX),
                clamp(props.visibilityDailyLimit(), DAILY_LIMIT_MIN, DAILY_LIMIT_MAX), clamp(props.visibilityTextMax(), TEXT_MAX_MIN, TEXT_MAX_MAX));
    }

    public GroupRuleValues.Policy policyDefaults() {
        int min = clamp(props.capacityMin(), CAPACITY_MIN, CAPACITY_MAX);
        int max = Math.max(min, clamp(props.capacityMax(), CAPACITY_MIN, CAPACITY_MAX));
        return new GroupRuleValues.Policy(clamp(props.reapplyDays(), REAPPLY_MIN, REAPPLY_MAX), min, max,
                clamp(props.trendingSize(), TRENDING_SIZE_MIN, TRENDING_SIZE_MAX), clamp(props.trendingActiveDays(), TRENDING_DAYS_MIN, TRENDING_DAYS_MAX),
                clamp(props.eventMaxUpcoming(), EVENT_UPCOMING_MIN, EVENT_UPCOMING_MAX), clamp(props.eventMaxDaysAhead(), EVENT_AHEAD_MIN, EVENT_AHEAD_MAX),
                clamp(props.eventMaxLengthDays(), EVENT_LENGTH_MIN, EVENT_LENGTH_MAX));
    }

    // ── 지금 적용되는 값(저장된 칸 ?? 기본값) ───────────────────────────────────

    public GroupRuleValues.Creation creation() {
        return creation(row().orElse(null));
    }

    public GroupRuleValues.Creation creation(GroupSettingsEntity row) {
        GroupRuleValues.Creation d = creationDefaults();
        if (row == null) {
            return d;
        }
        return new GroupRuleValues.Creation(row.getCreationMode() == null ? d.creationMode() : row.getCreationMode(),
                row.getMinGrade() == null ? d.minGrade() : row.getMinGrade(), or(row.getMaxPerOwner(), d.maxPerOwner()));
    }

    public GroupRuleValues.Manager manager() {
        return manager(row().orElse(null));
    }

    public GroupRuleValues.Manager manager(GroupSettingsEntity row) {
        GroupRuleValues.Manager d = managerDefaults();
        if (row == null) {
            return d;
        }
        int max = or(row.getManagerLimitMax(), d.limitMax());
        return new GroupRuleValues.Manager(row.getManagersEnabled() == null ? d.managersEnabled() : row.getManagersEnabled(),
                Math.min(or(row.getManagerLimitDefault(), d.limitDefault()), max), max);
    }

    public GroupRuleValues.Visibility visibility() {
        return visibility(row().orElse(null));
    }

    public GroupRuleValues.Visibility visibility(GroupSettingsEntity row) {
        GroupRuleValues.Visibility d = visibilityDefaults();
        if (row == null) {
            return d;
        }
        return new GroupRuleValues.Visibility(row.getVisibilityEnabled() == null ? d.visibilityEnabled() : row.getVisibilityEnabled(),
                or(row.getVisibilityCooldownDays(), d.cooldownDays()), or(row.getVisibilityRetryHours(), d.retryHours()),
                or(row.getVisibilityRejectLimit(), d.rejectLimit()), or(row.getVisibilityDailyLimit(), d.dailyLimit()), or(row.getVisibilityTextMax(), d.textMax()));
    }

    public GroupRuleValues.Policy policy() {
        return policy(row().orElse(null));
    }

    public GroupRuleValues.Policy policy(GroupSettingsEntity row) {
        GroupRuleValues.Policy d = policyDefaults();
        if (row == null) {
            return d;
        }
        int min = or(row.getCapacityMin(), d.capacityMin());
        int max = Math.max(min, or(row.getCapacityMax(), d.capacityMax()));
        return new GroupRuleValues.Policy(or(row.getReapplyDays(), d.reapplyDays()), min, max, or(row.getTrendingSize(), d.trendingSize()),
                or(row.getTrendingActiveDays(), d.trendingActiveDays()), or(row.getEventMaxUpcoming(), d.eventMaxUpcoming()),
                or(row.getEventMaxDaysAhead(), d.eventMaxDaysAhead()), or(row.getEventMaxLengthDays(), d.eventMaxLengthDays()));
    }

    /** 설정 화면에 없는 한도 */
    public GroupProperties properties() {
        return props;
    }

    private static int or(Short value, int fallback) {
        return value == null ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
