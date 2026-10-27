#!/usr/bin/env bash
# =============================================================================
# release.sh — (로컬 PC) 화면 · jar · arm64 이미지를 만들어 ECR 에 올리고, 개발 서버(EC2)에 띄운다
# =============================================================================
#
# 저장소 루트에서 Git Bash(Windows) 또는 bash 로 실행한다:
#   docker/dev/release.sh                 # 빌드 → ECR 업로드 → 서버에 파일 복사 → 서버에서 deploy.sh
#   docker/dev/release.sh --push-only     # 빌드 → ECR 업로드까지(처음 init-env.sh 전에, 또는 띄우지 않고 올리기만)
#   docker/dev/release.sh --files-only    # 서버에 compose · Caddyfile · 스크립트만 복사(빌드 · 배포 없음)
#   docker/dev/release.sh --deploy <태그> # 빌드 없이 이미 올린 태그를 띄운다(되돌리기)
#
# 설정: docker/dev/.env.release (저장소에 넣지 않는다 — .gitignore 의 .env.*). 예:
#   ROBOYAM_ECR=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com
#   ROBOYAM_EC2_HOST=ubuntu@3.35.0.10
#   ROBOYAM_EC2_KEY=~/.ssh/roboyam-dev.pem
#   AWS_PROFILE=roboyam-release            # (선택) aws configure --profile 로 만든 배포용 계정
#
# 필요한 것: pnpm(의존성 설치됨), JDK(gradlew), Docker Desktop(buildx — arm64 를 에뮬레이션으로 빌드), AWS CLI v2, ssh · scp.
# 서버(t4g = Graviton)는 arm64 라 이미지를 linux/arm64 로 만든다. 에뮬레이션이라 빌드가 몇 분 걸린다.
# ⚠ 실제 AWS 에서 아직 시험하지 않았다(문법만 확인).
# =============================================================================
set -Eeuo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CONF="$ROOT/docker/dev/.env.release"
REMOTE_BASE=/opt/roboyam

fail() { printf '오류: %s\n' "$*" >&2; exit 1; }
step() { printf '\n== %s\n' "$*"; }

MODE=all DEPLOY_TAG=""
case "${1:-}" in
    "")           ;;
    --push-only)  MODE=push ;;
    --files-only) MODE=files ;;
    --deploy)     MODE=deploy; DEPLOY_TAG=${2:-}; [ -n "$DEPLOY_TAG" ] || fail "--deploy 에 태그가 필요합니다" ;;
    -h|--help)    sed -n '/^# 저장소 루트/,/^# 필요한/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)            fail "알 수 없는 옵션: $1 (도움말: $0 --help)" ;;
esac

[ -f "$CONF" ] || fail "$CONF 이 없습니다. 머리말의 예를 보고 만드세요."
set -a
# shellcheck disable=SC1090
. <(tr -d '\r' < "$CONF")
set +a
[ -n "${ROBOYAM_ECR:-}" ] || fail ".env.release 에 ROBOYAM_ECR 이 없습니다"
[ -n "${ROBOYAM_EC2_HOST:-}" ] || fail ".env.release 에 ROBOYAM_EC2_HOST 가 없습니다"
[ -n "${ROBOYAM_EC2_KEY:-}" ] || fail ".env.release 에 ROBOYAM_EC2_KEY 가 없습니다"
KEY=${ROBOYAM_EC2_KEY/#\~/$HOME}
[ -f "$KEY" ] || fail "SSH 키 파일이 없습니다: $KEY"
SSH_OPTS=(-i "$KEY" -o StrictHostKeyChecking=accept-new)
REGION=$(printf '%s' "$ROBOYAM_ECR" | sed -n 's/^[0-9]*\.dkr\.ecr\.\([a-z0-9-]*\)\.amazonaws\.com$/\1/p')
[ -n "$REGION" ] || fail "ROBOYAM_ECR 형식이 아닙니다: $ROBOYAM_ECR"

cd "$ROOT"

build_and_push() {
    local sha dirty=""
    sha=$(git rev-parse --short HEAD)
    if [ -n "$(git status --porcelain)" ]; then
        dirty="-dirty"
        echo "⚠ 커밋하지 않은 변경이 있습니다 — 태그에 -dirty 가 붙고, 그 이미지는 커밋으로 다시 만들 수 없습니다."
    fi
    TAG="$(date +%Y%m%d-%H%M)-$sha$dirty"

    step "화면 빌드 (pnpm generate → server/*/src/main/resources/static)"
    pnpm --filter user-web generate
    pnpm --filter admin-web generate

    step "jar 빌드"
    ./gradlew :server:api:bootJar :server:admin:bootJar

    step "ECR 로그인 ($ROBOYAM_ECR)"
    aws ecr get-login-password --region "$REGION" | tr -d '\r' | docker login --username AWS --password-stdin "$ROBOYAM_ECR"

    step "arm64 이미지 빌드 · 업로드 (태그 $TAG)"
    docker buildx build --platform linux/arm64 --provenance=false --push \
        -t "$ROBOYAM_ECR/roboyam-api:$TAG" -f server/api/Dockerfile server/api
    docker buildx build --platform linux/arm64 --provenance=false --push \
        -t "$ROBOYAM_ECR/roboyam-admin:$TAG" -f server/admin/Dockerfile server/admin
}

copy_files() {
    step "서버에 파일 복사 ($ROBOYAM_EC2_HOST:$REMOTE_BASE)"
    ssh "${SSH_OPTS[@]}" "$ROBOYAM_EC2_HOST" "mkdir -p $REMOTE_BASE/dev/scripts $REMOTE_BASE/postgres"
    scp -q "${SSH_OPTS[@]}" docker/dev/compose.yml docker/dev/Caddyfile docker/dev/.env.example "$ROBOYAM_EC2_HOST:$REMOTE_BASE/dev/"
    scp -q "${SSH_OPTS[@]}" docker/dev/scripts/*.sh "$ROBOYAM_EC2_HOST:$REMOTE_BASE/dev/scripts/"
    scp -q "${SSH_OPTS[@]}" docker/postgres/Dockerfile docker/postgres/init-pg-cron.sh "$ROBOYAM_EC2_HOST:$REMOTE_BASE/postgres/"
    ssh "${SSH_OPTS[@]}" "$ROBOYAM_EC2_HOST" "chmod +x $REMOTE_BASE/dev/scripts/*.sh $REMOTE_BASE/postgres/init-pg-cron.sh"
}

remote_deploy() {
    step "서버에서 deploy.sh $1"
    ssh "${SSH_OPTS[@]}" "$ROBOYAM_EC2_HOST" "$REMOTE_BASE/dev/scripts/deploy.sh $1"
}

case "$MODE" in
    all)    build_and_push; copy_files; remote_deploy "$TAG" ;;
    push)   build_and_push; copy_files
            echo; echo "올린 태그: $TAG  (처음이면 서버에서 init-env.sh --tag $TAG ... 를 돌린다 — docker/dev/README.md)" ;;
    files)  copy_files ;;
    deploy) copy_files; remote_deploy "$DEPLOY_TAG" ;;
esac
