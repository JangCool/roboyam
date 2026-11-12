package com.community.group.service;

import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupRepository;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 소모임 대표 이미지(V154 {@code groups.image_file_id} — files 용도 {@code GROUP_IMAGE}) 붙이기·떼기·주소의 한 곳. 부르는 쪽 트랜잭션 안에서 돈다.
 *
 * <ul>
 *   <li><b>붙일 수 있는 파일</b>({@link #claimable}): 용도 GROUP_IMAGE 이고, 내가 올린 PENDING 파일이거나 <b>내 반려된 개설 신청</b>이 쥐고 있는 파일(반려된 신청을 고쳐
 *       다시 낼 때 화면이 그 이미지를 되살려 보낸다 — 새 신청으로 옮기고 반려 행에서는 뗀다). 파일 행을 쓰기 잠금으로 읽어 「올리다 만 파일 정리」 배치와 줄을 선다.</li>
 *   <li><b>붙이기</b>({@link #attach}): 파일을 IN_USE 로. 한 파일은 한 소모임 행만 쓴다(V154 고유 색인).</li>
 *   <li><b>떼기</b>({@link #detach}): 바꾸기·지우기·신청 취소·해산 때. 파일은 {@link FileEntity#release} 로 PENDING 으로 돌아가고, 올린 지
 *       {@code irobit.media.orphan-retention-hours} 가 지났으면 다음 「올리다 만 파일 정리」(file-orphan-cleanup)가 저장소·장부에서 지운다(글 이미지와 같은 방식).
 *       반려된 신청·비활성 소모임은 떼지 않는다(다시 내기·다시 열기 때 그대로 쓴다).</li>
 * </ul>
 */
@Component
public class GroupImages {

    /** 칸 이름 · 문구 키 — {@code 400 VALIDATION fields.imageFileId} */
    public static final String FIELD = "imageFileId";
    public static final String INVALID = "group.imageFileId.invalid";

    private final FileRepository files;
    private final GroupRepository groups;
    private final ImageStorage storage;

    public GroupImages(FileRepository files, GroupRepository groups, ImageStorage storage) {
        this.files = files;
        this.groups = groups;
        this.storage = storage;
    }

    /** 이 회원이 붙일 수 있는 대표 이미지 파일 — 쓰기 잠금으로 읽는다. 아니면 null */
    public FileEntity claimable(long userId, long fileId) {
        FileEntity file = files.findWithLockById(fileId).filter(f -> f.getPurpose() == FilePurpose.GROUP_IMAGE).orElse(null);
        if (file == null) {
            return null;
        }
        if (file.getStatus() == FileStatus.PENDING) {
            return Objects.equals(file.getUploadedByUser(), userId) ? file : null;
        }
        GroupEntity holder = groups.findByImageFileId(fileId).orElse(null);
        return holder != null && holder.getStatus() == GroupStatus.REJECTED && Objects.equals(holder.getCreatorId(), userId) ? file : null;
    }

    /**
     * 소모임에 붙인다({@link #claimable} 로 받은 파일). 반려된 신청이 쥐고 있던 파일이면 그 행에서 먼저 떼고 내보낸다(고유 색인이 새 행의 INSERT 와 겹치지 않게).
     * 이 소모임이 쥐고 있던 다른 파일은 부르는 쪽이 먼저 {@link #detach} 한다.
     */
    public void attach(GroupEntity group, FileEntity file, Instant now) {
        groups.findByImageFileId(file.getId()).filter(holder -> !holder.getId().equals(group.getId())).ifPresent(holder -> {
            holder.changeImage(null);
            groups.flush();
        });
        file.markUsed(now);
        group.changeImage(file.getId());
    }

    /** 소모임에서 뗀다 — 파일은 PENDING 으로 돌아가 정리 배치가 지운다. 이미지가 없으면 아무 일도 없다 */
    public void detach(GroupEntity group) {
        Long fileId = group.getImageFileId();
        if (fileId == null) {
            return;
        }
        group.changeImage(null);
        files.findWithLockById(fileId).ifPresent(FileEntity::release);
    }

    /** 그 파일의 공개 주소 */
    public String url(FileEntity file) {
        return storage.publicUrl(file.getStorageKey());
    }

    /** 소모임 ID → 대표 이미지 공개 주소(이미지가 있는 소모임만) — 파일 장부를 한 번에 읽는다 */
    public Map<Long, String> urls(Collection<GroupEntity> rows) {
        Map<Long, Long> fileByGroup = new HashMap<>();
        for (GroupEntity g : rows) {
            if (g != null && g.getImageFileId() != null) {
                fileByGroup.put(g.getId(), g.getImageFileId());
            }
        }
        if (fileByGroup.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> keyByFile = new HashMap<>();
        files.findAllById(new HashSet<>(fileByGroup.values())).forEach(f -> keyByFile.put(f.getId(), f.getStorageKey()));
        Map<Long, String> urls = new HashMap<>();
        fileByGroup.forEach((groupId, fileId) -> {
            String key = keyByFile.get(fileId);
            if (key != null) {
                urls.put(groupId, storage.publicUrl(key));
            }
        });
        return urls;
    }
}
