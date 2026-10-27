# 개발 서버 스크립트(init-env · deploy · backup · restore)가 같이 쓰는 함수. 직접 실행하지 않고 source 한다.
# shellcheck shell=bash

DEV_DIR=${DEV_DIR:-/opt/roboyam/dev}
ENV_FILE="$DEV_DIR/.env"

fail() { printf '오류: %s\n' "$*" >&2; exit 1; }
note() { printf '  %s\n' "$*"; }

# .env 의 값 하나를 읽는다(따옴표 없는 KEY=VALUE 한 줄 형식만 — init-env.sh 가 그렇게 쓴다)
env_get() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1; }

# .env 의 값 하나를 바꾼다(없으면 끝에 더한다). 값에 | · & 가 들어가면 안 된다(sed 치환) — 이미지 주소 · 태그만 바꾼다
env_set() {
    if grep -q "^$1=" "$ENV_FILE"; then
        sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
    else
        printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"
    fi
}

require_env_file() {
    [ -f "$ENV_FILE" ] || fail "$ENV_FILE 이 없습니다. 먼저 init-env.sh 로 만드세요."
}

# ECR 에 docker 로그인한다. 인자: 레지스트리 호스트(<계정>.dkr.ecr.<리전>.amazonaws.com)
# 서버의 IAM 역할(인스턴스 프로파일)로 인증하므로 서버에 AWS 키를 두지 않는다
ecr_login() {
    local registry=$1 region
    region=$(printf '%s' "$registry" | sed -n 's/^[0-9]*\.dkr\.ecr\.\([a-z0-9-]*\)\.amazonaws\.com$/\1/p')
    [ -n "$region" ] || fail "ECR 레지스트리 주소 형식이 아닙니다: $registry"
    aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry" > /dev/null \
        || fail "ECR 로그인 실패 — 서버에 IAM 역할(roboyam-dev-ec2)이 붙어 있는지, 정책에 ECR 읽기 권한이 있는지 확인하세요."
}

# 이미지 주소에서 레지스트리 호스트만 뗀다: 123.dkr.ecr.ap-northeast-2.amazonaws.com/roboyam-api:tag → 123.dkr.ecr...com
registry_of() { printf '%s' "${1%%/*}"; }

# 이미지 주소의 태그를 바꾼다: .../roboyam-api:old → .../roboyam-api:new
with_tag() { printf '%s:%s' "${1%:*}" "$2"; }

dc() { docker compose --project-directory "$DEV_DIR" -f "$DEV_DIR/compose.yml" --env-file "$ENV_FILE" "$@"; }
