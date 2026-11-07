package com.community.poll.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 투표 선택지 한 줄 — V81 {@code poll_options}({@code position} 0부터 차례, 투표 안에서 유일). 고치기(첫 표 전에만)는 모두 지우고 다시 넣는다.
 */
@Entity
@Table(name = "poll_options")
public class PollOptionEntity {

    @Id
    private Long id;

    private Long pollId;
    private short position;
    private String label;

    protected PollOptionEntity() {
    }

    public static PollOptionEntity create(long id, long pollId, int position, String label) {
        PollOptionEntity option = new PollOptionEntity();
        option.id = id;
        option.pollId = pollId;
        option.position = (short) position;
        option.label = label;
        return option;
    }

    public Long getId() {
        return id;
    }

    public Long getPollId() {
        return pollId;
    }

    public int getPosition() {
        return position;
    }

    public String getLabel() {
        return label;
    }
}
