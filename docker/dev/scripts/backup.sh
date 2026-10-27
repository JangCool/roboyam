#!/usr/bin/env bash
# =============================================================================
# backup.sh — DB(pg_dump)와 올린 이미지(media)를 S3 에 백업한다
# =============================================================================
#
# server-setup.sh 가 매일 04:00 에 돌게 cron 을 걸어 둔다(/etc/cron.d/roboyam-backup, 로그 /opt/roboyam/backups/backup.log).
# 손으로도 돌릴 수 있다 — 배포 전 · 위험한 작업 전에:
#   /opt/roboyam/dev/scripts/backup.sh
#
# 올리는 곳: .env 의 BACKUP_S3_URI 아래
#   <BACKUP_S3_URI>/db/roboyam-<날짜시각>.dump       (pg_dump -Fc — restore.sh 가 되살린다)
#   <BACKUP_S3_URI>/media/media-<날짜시각>.tar.gz
# S3 쪽 보관 기간은 버킷 수명 주기 규칙이 정한다(docker/dev/aws/s3-backup-lifecycle.json — 30일).
# 서버에는 최근 3개만 남긴다.
#
# EBS 스냅샷(디스크 통째, DLM 정책)과는 따로다 — 스냅샷은 서버를 통째로 되살릴 때, 이 백업은 DB 만 되살리거나 다른 서버(RDS)로 옮길 때 쓴다.
# ⚠ 실제 서버에서 아직 시험하지 않았다(문법만 확인).
# =============================================================================
set -Eeuo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

BACKUP_DIR=${BACKUP_DIR:-/opt/roboyam/backups}
KEEP_LOCAL=3

require_env_file
S3_URI=$(env_get BACKUP_S3_URI)
[ -n "$S3_URI" ] || fail ".env 에 BACKUP_S3_URI 가 없습니다."
DB_USER=$(env_get DB_USERNAME)
STAMP=$(date '+%Y%m%d-%H%M')
umask 077
mkdir -p "$BACKUP_DIR"

echo "$(date '+%F %T') 백업 시작"

dump="$BACKUP_DIR/roboyam-$STAMP.dump"
# -Fc: 압축된 사용자 지정 형식(pg_restore 로 되살린다). 실패한 반쪽 파일이 남지 않게 임시 이름으로 쓰고 끝나면 바꾼다
dc exec -T postgres pg_dump -U "$DB_USER" -d roboyam -Fc > "$dump.part" || { rm -f "$dump.part"; fail "pg_dump 실패"; }
mv "$dump.part" "$dump"
aws s3 cp --only-show-errors "$dump" "$S3_URI/db/" || fail "S3 업로드 실패(db) — IAM 역할의 S3 권한 · BACKUP_S3_URI 확인"
note "DB: $S3_URI/db/$(basename "$dump") ($(du -h "$dump" | cut -f1))"

media="$BACKUP_DIR/media-$STAMP.tar.gz"
# 올린 이미지 볼륨은 admin 컨테이너의 /var/lib/roboyam/media 에 붙어 있다(compose.yml)
if dc exec -T admin tar -C /var/lib/roboyam/media -czf - . > "$media.part"; then
    mv "$media.part" "$media"
    aws s3 cp --only-show-errors "$media" "$S3_URI/media/" || fail "S3 업로드 실패(media)"
    note "media: $S3_URI/media/$(basename "$media") ($(du -h "$media" | cut -f1))"
else
    rm -f "$media.part"
    note "⚠ media 백업 실패(admin 컨테이너가 떠 있는지 확인) — DB 백업은 끝났다"
fi

# 서버에는 최근 것만 남긴다
ls -1t "$BACKUP_DIR"/roboyam-*.dump 2> /dev/null | tail -n +$((KEEP_LOCAL + 1)) | xargs -r rm -f
ls -1t "$BACKUP_DIR"/media-*.tar.gz 2> /dev/null | tail -n +$((KEEP_LOCAL + 1)) | xargs -r rm -f

echo "$(date '+%F %T') 백업 끝"
