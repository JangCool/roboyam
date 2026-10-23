package com.community.board.entity;

import com.community.board.dto.BoardNotificationValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 게시판 기본 설정 「알림」(행 하나) — V42__boards.sql 의 {@code board_notification_settings}.
 *
 * <p>옛 칸 {@code member_defaults}(종류별 사이트 알림·이메일·회원이 끌 수 있음)는 V58 이 회원 이벤트 설정({@code member_event_settings})으로 옮겼다. 칸은 이미 배포된 앱과의 호환 때문에
 * 표에 남지만 이 앱은 <b>읽지도 쓰지도 않는다</b>(매핑하지 않는다 — 변경 감지가 그 칸을 건드리지 않게 {@link DynamicUpdate}).
 */
@Entity
@Table(name = "board_notification_settings")
@DynamicUpdate
public class BoardNotificationSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private int groupWindowMinutes;
    private boolean emailQuietHoursEnabled;
    private String emailQuietStart;
    private String emailQuietEnd;
    private int memberNotificationRetentionDays;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardNotificationSettingEntity() {
    }

    public BoardNotificationValues values() {
        return new BoardNotificationValues(groupWindowMinutes, emailQuietHoursEnabled, emailQuietStart, emailQuietEnd, memberNotificationRetentionDays);
    }

    public void change(BoardNotificationValues v, long operatorId, Instant now) {
        this.groupWindowMinutes = v.groupWindowMinutes();
        this.emailQuietHoursEnabled = v.emailQuietHoursEnabled();
        this.emailQuietStart = v.emailQuietStart();
        this.emailQuietEnd = v.emailQuietEnd();
        this.memberNotificationRetentionDays = v.memberNotificationRetentionDays();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
