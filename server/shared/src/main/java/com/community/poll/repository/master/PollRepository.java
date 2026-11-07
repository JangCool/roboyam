package com.community.poll.repository.master;

import com.community.poll.entity.PollEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 글 투표({@code polls}) — 넣기는 {@code persist}. 질의는 {@link PollQueries} */
public interface PollRepository extends RoboyamRepository<PollEntity, Long> {
}
