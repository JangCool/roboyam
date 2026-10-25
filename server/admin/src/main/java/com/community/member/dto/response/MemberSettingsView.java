package com.community.member.dto.response;

import com.community.site.dto.response.DisplayValuesView;
import java.util.List;

/**
 * 회원 상세의 개인 설정·알림함 구획 — admin-web {@code MemberSettings} 모양. 회원이 직접 정한 값을 운영자가 읽기만 한다. 차단·메모·알림함은 건수만.
 * 탈퇴한 회원은 구획 전체가 null 이다.
 *
 * @param display          이 회원에게 적용되는 표시 형식(V52 {@code member_display_preferences} — 행이 없으면 사이트 표시 기본값)
 * @param displayOwn       회원이 직접 고른 표시 형식이 있는가(행 있음)
 * @param view             보기 설정(V73 {@code member_view_settings} — 행이 없으면 기본값 · {@code own=false})
 * @param notifications    회원 알림 설정 — 회원 이벤트 카탈로그({@code MemberEvent}) 전부, 카탈로그 차례(V67 회원 선택 반영)
 * @param pushSubscribed   웹 푸시 구독 — 푸시 기능이 없어 null
 * @param blockedUserCount 차단한 회원 수(탈퇴한 상대 제외 — 회원 화면과 같은 셈, V71)
 * @param blockedWordCount 차단 단어 수(V71)
 * @param memoCount        남긴 이용자 메모 수(탈퇴한 상대 제외, V71)
 * @param inbox            알림함 건수(V50 {@code member_notifications})
 */
public record MemberSettingsView(DisplayValuesView display, boolean displayOwn, MemberViewSettingsView view, List<MemberNotificationSettingView> notifications,
                                 Boolean pushSubscribed, Long blockedUserCount, Long blockedWordCount, Long memoCount, MemberInboxView inbox) {
}
