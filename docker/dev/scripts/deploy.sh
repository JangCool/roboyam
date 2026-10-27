#!/usr/bin/env bash
# =============================================================================
# deploy.sh — 개발 서버에 새 이미지 태그를 띄운다 (되돌리기도 같은 명령에 예전 태그를 준다)
# =============================================================================
#
# 사용법(서버에서 — 보통은 로컬의 release.sh 가 ssh 로 부른다):
#   /opt/roboyam/dev/scripts/deploy.sh <태그>      # 그 태그로 바꿔 띄운다
#   /opt/roboyam/dev/scripts/deploy.sh             # 태그를 바꾸지 않고 다시 띄운다(compose · Caddyfile 만 바뀌었을 때)
#   /opt/roboyam/dev/scripts/deploy.sh --history   # 지금까지 띄운 태그(되돌릴 태그 찾기)
#
# 순서: 새 이미지를 먼저 받는다(실패하면 아무것도 바꾸지 않는다) → .env 의 태그를 바꾼다 → docker compose up -d →
#       두 도메인의 /actuator/health/readiness 가 응답할 때까지 기다린다.
# 기다리다 실패하면 로그 끝부분과 되돌리는 명령을 출력한다(자동으로 되돌리지는 않는다 — DB 이관이 이미 돌았을 수 있다).
# ⚠ 실제 서버에서 아직 시험하지 않았다(문법만 확인).
# =============================================================================
set -Eeuo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

HISTORY_FILE="$DEV_DIR/.deploy-history"
WAIT_SECONDS=${WAIT_SECONDS:-240}

if [ "${1:-}" = "--history" ]; then
    [ -f "$HISTORY_FILE" ] && tail -n 20 "$HISTORY_FILE" || echo "(기록 없음)"
    exit 0
fi

require_env_file
TAG=${1:-}
API_IMAGE=$(env_get ROBOYAM_IMAGE)
ADMIN_IMAGE=$(env_get ROBOYAM_ADMIN_IMAGE)
OLD_TAG=${API_IMAGE##*:}
if [ -n "$TAG" ]; then
    API_IMAGE=$(with_tag "$API_IMAGE" "$TAG")
    ADMIN_IMAGE=$(with_tag "$ADMIN_IMAGE" "$TAG")
else
    TAG=$OLD_TAG
fi

echo "[1/4] 이미지 받기 ($TAG)"
ecr_login "$(registry_of "$API_IMAGE")"
docker pull -q "$API_IMAGE" > /dev/null || fail "이미지를 받지 못했습니다: $API_IMAGE"
docker pull -q "$ADMIN_IMAGE" > /dev/null || fail "이미지를 받지 못했습니다: $ADMIN_IMAGE"

echo "[2/4] .env 태그 바꾸기 ($OLD_TAG → $TAG)"
env_set ROBOYAM_IMAGE "$API_IMAGE"
env_set ROBOYAM_ADMIN_IMAGE "$ADMIN_IMAGE"
printf '%s  %s -> %s\n' "$(date '+%Y-%m-%d %H:%M')" "$OLD_TAG" "$TAG" >> "$HISTORY_FILE"

echo "[3/4] docker compose up"
# --build: postgres 이미지(docker/postgres — pg_cron)를 만든다. 바뀐 것이 없으면 캐시로 끝나고 DB 컨테이너는 다시 만들지 않는다
dc up -d --build --remove-orphans

echo "[4/4] 기동 확인 (최대 ${WAIT_SECONDS}초 — 처음에는 Caddy 가 HTTPS 인증서를 받느라 더 걸린다)"
DOMAIN=$(env_get DEV_DOMAIN)
ADMIN_DOMAIN=$(env_get ADMIN_DOMAIN)
ok_app=0 ok_admin=0
deadline=$((SECONDS + WAIT_SECONDS))
while [ $SECONDS -lt $deadline ]; do
    [ $ok_app = 1 ] || { curl -fsS -o /dev/null --max-time 5 "https://$DOMAIN/actuator/health/readiness" 2> /dev/null && ok_app=1 && note "공개 사이트 응답: https://$DOMAIN"; }
    [ $ok_admin = 1 ] || { curl -fsS -o /dev/null --max-time 5 "https://$ADMIN_DOMAIN/actuator/health/readiness" 2> /dev/null && ok_admin=1 && note "관리자 앱 응답: https://$ADMIN_DOMAIN"; }
    [ $ok_app = 1 ] && [ $ok_admin = 1 ] && break
    sleep 5
done

if [ $ok_app = 1 ] && [ $ok_admin = 1 ]; then
    # 안 쓰는 이미지 중 7일 넘은 것만 지운다(최근 태그는 되돌리기용으로 남긴다)
    docker image prune -af --filter "until=168h" > /dev/null || true
    echo "완료: $TAG"
    exit 0
fi

echo
echo "⚠ ${WAIT_SECONDS}초 안에 응답하지 않았습니다. 최근 로그:"
dc ps
dc logs --tail 40 app admin caddy || true
echo
echo "확인할 것: DNS(A 레코드)가 이 서버의 탄력적 IP 를 가리키는지, 보안 그룹에 80 · 443 이 열려 있는지(Caddy 인증서 발급에 필요)."
[ "$OLD_TAG" != "$TAG" ] && echo "되돌리기: $0 $OLD_TAG"
exit 1
