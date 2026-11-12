#!/usr/bin/env bash
# ============================================================================
# DB 백업 훅 예시 — 덤프를 Cloudflare R2 로 올린다 (rclone 또는 aws cli 중 하나)
#
# 쓰는 법: 이 파일을 복사해 값을 채운 뒤 설치 때 넘긴다
#   cp backup-hook.example.sh /root/backup-hook.sh && vi /root/backup-hook.sh
#   ./irobit-db-install.sh --allow ... --backup-hook /root/backup-hook.sh
# 인자: $1 = DB 덤프(.dump), $2 = 역할 덤프(.sql, 없을 수 있다)
# R2 쪽 보관 기간은 버킷의 수명 주기 규칙(예: 30일 뒤 삭제)으로 정한다.
# R2 API 토큰은 이 버킷에 「객체 읽기·쓰기」만 준다.
# ============================================================================
set -Eeuo pipefail

BUCKET="irobit-backup"        # R2 버킷 이름
PREFIX="db"                    # 버킷 안 폴더

# ── 방법 1: rclone ───────────────────────────────────────────────────────────
# dnf install -y rclone   (EPEL — 없으면 https://rclone.org/install/)
# root 의 ~/.config/rclone/rclone.conf:
#   [r2]
#   type = s3
#   provider = Cloudflare
#   access_key_id = <R2 액세스 키>
#   secret_access_key = <R2 비밀 키>
#   endpoint = https://<계정 ID>.r2.cloudflarestorage.com
#   no_check_bucket = true
for f in "$@"; do
    rclone copy --s3-no-check-bucket "$f" "r2:${BUCKET}/${PREFIX}/"
done

# ── 방법 2: aws cli (위 rclone 부분을 지우고 쓴다) ───────────────────────────
# dnf install -y awscli2   또는 AWS 공식 설치 파일
# 자격 증명: root 의 ~/.aws/credentials 의 [r2] 프로필(R2 키), region = auto
# R2_ENDPOINT="https://<계정 ID>.r2.cloudflarestorage.com"
# for f in "$@"; do
#     aws s3 cp --only-show-errors --profile r2 --endpoint-url "$R2_ENDPOINT" "$f" "s3://${BUCKET}/${PREFIX}/"
# done
