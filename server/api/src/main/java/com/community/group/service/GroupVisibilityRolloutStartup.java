package com.community.group.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 매니저 안내 알림의 <b>안전망</b>(community api-groups.md 11-8) — 공개 범위 기능은 관리자 앱의 켜기 저장 요청 안에서 안내를 보낸다. 설정 파일·환경 변수
 * ({@code roboyam.groups.visibility-enabled})로 켜진 채(저장된 값 없음) 뜨는 서버는 그 길을 타지 않으므로, 켜져 있는 채 뜬 api 서버가 같은 선점으로 한 번 보낸다(이미 보냈으면 아무 일도 없다).
 */
@Component
public class GroupVisibilityRolloutStartup {

    private static final Logger log = LoggerFactory.getLogger(GroupVisibilityRolloutStartup.class);

    private final GroupVisibilityPolicy policy;
    private final GroupVisibilityRolloutNotifier notifier;

    public GroupVisibilityRolloutStartup(GroupVisibilityPolicy policy, GroupVisibilityRolloutNotifier notifier) {
        this.policy = policy;
        this.notifier = notifier;
    }

    /** 서버가 뜰 때 — 기능이 꺼져 있으면 아무 일도 하지 않고, 실패해도 서버 기동을 막지 않는다(선점은 롤백되어 다음 기동 때 다시 시도한다) */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            if (!policy.enabled()) {
                return;
            }
            notifier.notifyManagers(Instant.now());
        } catch (RuntimeException e) {
            log.error("공개 범위 기능 안내 알림을 보내지 못했다 — 선점은 롤백되어 다음 기동 때 다시 시도한다", e);
        }
    }
}
