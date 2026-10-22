package com.community.team.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 팀 — V41__teams_notification_channels.sql 의 {@code teams}. 알림 이벤트의 「추가 수신 팀」으로 쓰는 운영자 묶음이다(조직 구성).
 * 이름은 대소문자를 무시하고 유일하다(유일 인덱스 {@code lower(team_name)}). 정렬 순서는 화면에 보이는 차례이고 서버가 1부터 다시 매긴다.
 * 팀원이 있는 팀은 지울 수 없고(대신 사용 안 함) 멈춘 팀({@code enabled=false})은 알림을 받지 않는다.
 */
@Entity
@Table(name = "teams")
@DynamicUpdate
public class TeamEntity {

    @Id
    private Long id;

    private String teamName;
    private String description;
    private boolean enabled;
    private int sortOrder;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected TeamEntity() {
    }

    public static TeamEntity create(long id, String teamName, String description, boolean enabled, int sortOrder, long operatorId, Instant now) {
        TeamEntity team = new TeamEntity();
        team.id = id;
        team.teamName = teamName;
        team.description = description;
        team.enabled = enabled;
        team.sortOrder = sortOrder;
        team.createdAt = now;
        team.createdBy = operatorId;
        team.updatedAt = now;
        team.updatedBy = operatorId;
        return team;
    }

    /** 이름·설명·사용 여부를 고친다 */
    public void edit(String teamName, String description, boolean enabled, long operatorId, Instant now) {
        this.teamName = teamName;
        this.description = description;
        this.enabled = enabled;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 정렬 순서를 다시 매긴다 */
    public void reorder(int sortOrder, long operatorId, Instant now) {
        this.sortOrder = sortOrder;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 팀원이 바뀌었다 — 수정 시각만 갱신한다 */
    public void touch(long operatorId, Instant now) {
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Long getId() {
        return id;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
