package com.community.media.service;

import com.community.board.dto.BoardActor;
import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.media.config.PostImageProperties;
import com.community.media.dto.response.ImageUploadResponse;
import com.community.media.exception.ImageDailyLimitException;
import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.permission.entity.MemberPermission;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 글 본문 이미지 올리기({@code POST /api/images}, multipart {@code file}) — 로그인한 회원만(비회원 401 은 {@code SecurityConfig}). 게시판을 모른다
 * (게시판의 이미지 허용은 글을 저장할 때 본다 — {@code 422 IMAGE_UPLOAD_NOT_ALLOWED}).
 *
 * <p>순서: 정지 {@code 403 ACCOUNT_RESTRICTED} · 글쓰기 역할 권한 없음 {@code 403 BOARD_RESTRICTED} → 파일 칸({@code 400 fields.file}) → 1분 속도({@code 429 IMAGE_RATE_LIMITED})
 * → 하루 한도({@code 429 IMAGE_DAILY_LIMIT}) → 검사·다시 만들기({@link PostImageProcessor}, 트랜잭션 밖 — 이미지 처리 동안 DB 연결을 붙잡지 않는다) → 저장소 쓰기
 * → 장부 행({@code files}, PENDING) 저장 → 실패하면 쓴 파일을 지운다. 글에 쓰이지 않은 채 {@code roboyam.media.orphan-retention-hours}(24시간)가 지나면
 * 「올리다 만 파일 정리」 배치가 지운다. 검사부터 장부 행까지는 관리자 공지 본문 이미지와 같은 몸통(shared {@link PostImageUploads})이다.
 *
 * <p>저장소 키는 {@code post-image/{UTC 년월}/{무작위 32자}.{확장자}} — 주소를 짐작할 수 없고, 한 번 쓴 파일은 고치지 않는다(1년 immutable 캐시).
 */
@Service
public class PostImageUploadService {

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostImageRateLimiter limiter;
    private final PostImageUploads uploads;
    private final PostImageProperties props;
    private final FileRepositoryQDSL fileQueries;
    private final TransactionTemplate tx;

    public PostImageUploadService(BoardCatalog catalog, BoardActorResolver actors, PostImageRateLimiter limiter, PostImageUploads uploads,
                                  PostImageProperties props, FileRepositoryQDSL fileQueries, PlatformTransactionManager txManager) {
        this.catalog = catalog;
        this.actors = actors;
        this.limiter = limiter;
        this.uploads = uploads;
        this.props = props;
        this.fileQueries = fileQueries;
        this.tx = new TransactionTemplate(txManager);
    }

    public ImageUploadResponse upload(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardActor actor = actors.resolve(userId, catalog.snapshot());
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.restricted()) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.ACCOUNT_RESTRICTED);
        }
        if (!actor.can(MemberPermission.POST_WRITE.code())) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.BOARD_RESTRICTED);
        }
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef(file == null ? "file.required" : "site.image.empty"));
        }
        limiter.acquire(userId);
        Instant now = Instant.now();
        if (tx.execute(status -> fileQueries.uploadsSince(userId, now.minus(Duration.ofDays(1)))) >= props.dailyLimit()) {
            throw new ImageDailyLimitException(props.dailyLimit());
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        PostImageUploads.Stored stored = uploads.store(userId, data);
        return new ImageUploadResponse(Long.toString(stored.id()), stored.url(), stored.width(), stored.height());
    }
}
