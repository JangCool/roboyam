package com.community.group.config;

import com.community.group.entity.GroupCreationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.groups.* — 소모임 설정의 <b>기본값</b>(관리자 앱 소모임 설정에 저장된 값이 없을 때 쓰는 값, 화면의 「기본값으로」 기준)과 설정 화면에 없는 한도.
 * 범위 밖 값은 읽을 때 가까운 값으로 보정한다({@code GroupSettingsService}). 저장된 값이 있으면 그 값이 이긴다(DB &gt; yml).
 *
 * @param creationMode           개설 방식(승인제)
 * @param minGrade               개설 최소 등급 키(등급 설정에 없는 키면 누구나 — 첫 등급처럼 본다)
 * @param maxPerOwner            한 사람이 모임장일 수 있는 소모임 수(신청 중·열림·비활성 합, 1~10)
 * @param managersEnabled        매니저 기능(3단계 — 기본 꺼짐)
 * @param managerLimitDefault    새 소모임의 매니저 기본 인원(1~10)
 * @param managerLimitMax        모임장이 정할 수 있는 매니저 최대 인원(1~10)
 * @param visibilityEnabled      공개 범위 기능(5단계 — 기본 꺼짐)
 * @param visibilityCooldownDays 비공개→공개 쿨다운(0~365일)
 * @param visibilityRetryHours   공개 요청 거절 뒤 재요청(0~720시간)
 * @param visibilityRejectLimit  같은 글 거절 한도(1~3회)
 * @param visibilityDailyLimit   하루 공개 요청 수(1~100건)
 * @param visibilityTextMax      요청 한마디·거절 사유 글자 수(10~100자)
 * @param reapplyDays            가입 거절 뒤 재신청(0~90일)
 * @param capacityMin            정원 최소(5~200)
 * @param capacityMax            정원 최대(5~200)
 * @param trendingSize           지금 뜨는 소모임 개수(1~20 — 6단계)
 * @param trendingActiveDays     지금 뜨는 활동 기준(1~90일 — 6단계)
 * @param eventMaxUpcoming       앞으로 열려 있는 일정 수(1~100 — 4단계)
 * @param eventMaxDaysAhead      일정 시작 최대(1~730일 — 4단계)
 * @param eventMaxLengthDays     일정 길이 최대(1~30일 — 4단계)
 * @param createDailyLimit       한 회원의 하루(24시간) 개설 신청 수(화면 설정 없음)
 * @param joinDailyLimit         한 회원의 하루(24시간) 가입·신청 수 합(화면 설정 없음)
 * @param newcomerDays           상세 「최근 N일 가입」의 N
 */
@ConfigurationProperties(prefix = "roboyam.groups")
public record GroupProperties(@DefaultValue("APPROVAL") GroupCreationMode creationMode, @DefaultValue("REGULAR") String minGrade,
                              @DefaultValue("3") int maxPerOwner,
                              @DefaultValue("false") boolean managersEnabled, @DefaultValue("5") int managerLimitDefault, @DefaultValue("10") int managerLimitMax,
                              @DefaultValue("false") boolean visibilityEnabled, @DefaultValue("7") int visibilityCooldownDays,
                              @DefaultValue("24") int visibilityRetryHours, @DefaultValue("3") int visibilityRejectLimit, @DefaultValue("10") int visibilityDailyLimit,
                              @DefaultValue("100") int visibilityTextMax,
                              @DefaultValue("7") int reapplyDays, @DefaultValue("5") int capacityMin, @DefaultValue("200") int capacityMax,
                              @DefaultValue("5") int trendingSize, @DefaultValue("14") int trendingActiveDays, @DefaultValue("30") int eventMaxUpcoming,
                              @DefaultValue("366") int eventMaxDaysAhead, @DefaultValue("7") int eventMaxLengthDays,
                              @DefaultValue("1") int createDailyLimit, @DefaultValue("50") int joinDailyLimit, @DefaultValue("7") int newcomerDays) {
}
