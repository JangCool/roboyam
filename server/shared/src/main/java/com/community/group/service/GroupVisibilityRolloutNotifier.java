package com.community.group.service;

import com.community.group.entity.GroupManagerPermission;
import com.community.group.repository.master.GroupPublishRequestQueries;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 범위 기능을 켤 때 <b>한 번</b> 보내는 매니저 안내(community api-groups.md 11-8 「UNPUBLISH 매니저 안내」) — 「글 공개 처리·되돌리기」를 맡은 지금 매니저에게
 * 기존 종류 {@code GROUP_MANAGER_PERMISSIONS_CHANGED}(맡은 일 전체)를 한 건씩 보낸다. 설정 행의 {@code visibility_notice_sent_at} 을 비어 있을 때만 채우는 선점에 성공한 쪽만
 * 보낸다 — 관리자 앱의 켜기 저장 요청 또는 api 서버 기동 안전망 중 먼저 선점한 하나, 껐다 다시 켜도 다시 보내지 않는다.
 */
@Component
public class GroupVisibilityRolloutNotifier {

    private static final Logger log = LoggerFactory.getLogger(GroupVisibilityRolloutNotifier.class);

    private final GroupPublishRequestQueries queries;
    private final GroupNotices notices;

    public GroupVisibilityRolloutNotifier(GroupPublishRequestQueries queries, GroupNotices notices) {
        this.queries = queries;
        this.notices = notices;
    }

    /**
     * 선점 결과 — {@code claimed} 이 이번 호출이 선점했나(그 시각이 {@code now} 로 적혔다), {@code sent} 가 보낸 알림 수(대상 수 — 대상이 없어도 선점은 끝난다).
     */
    public record Result(boolean claimed, int sent) {
    }

    /** 선점에 성공하면 보내고 결과를, 이미 보냈으면 {@code (false, 0)} — 부르는 쪽의 트랜잭션에 참여한다(없으면 연다) */
    @Transactional
    public Result notifyManagers(Instant now) {
        if (!queries.claimManagerNotice(now)) {
            return new Result(false, 0);
        }
        int sent = 0;
        for (GroupPublishRequestQueries.ManagerRow row : queries.activeManagersWith(GroupManagerPermission.UNPUBLISH)) {
            notices.rolloutManagerNotice(row.userId(), row.groupId(), row.groupName(), row.leaderId(), GroupManagerPermission.keys(row.perms()));
            sent++;
        }
        log.info("공개 범위 기능 안내 — 「글 공개 처리·되돌리기」 매니저 {}명에게 알림을 보냈다", sent);
        return new Result(true, sent);
    }
}
