# shellcheck shell=bash
# ============================================================================
# installer-native 공통 함수 — DB·앱·배포 스크립트가 source 한다(직접 실행하지 않는다).
# ============================================================================

STAMP="$(date '+%Y%m%d_%H%M%S')"

log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '[%s] [경고] %s\n' "$(date '+%H:%M:%S')" "$*" >&2; }
fail() { printf '\n[오류] %s\n' "$*" >&2; exit 1; }
step() { printf '\n── %s ──\n' "$*"; }

# 스크립트 맨 위 주석(3번째 줄부터 첫 빈 주석 구분선 전까지)을 도움말로 보여 준다
usage_from_header() {   # <스크립트 경로>
    awk 'NR >= 3 { if ($0 !~ /^#/) exit; if ($0 ~ /^# =+$/) exit; sub(/^# ?/, ""); print }' "$1"
}

on_error() {
    local code=$? line="${BASH_LINENO[0]:-?}" fn="${FUNCNAME[1]:-main}"
    printf '\n[오류] %s() %s번째 줄에서 실패 (종료 코드 %s)\n' "$fn" "$line" "$code" >&2
    [ -n "${LOG_FILE:-}" ] && [ -f "${LOG_FILE}" ] && printf '       로그: %s\n' "$LOG_FILE" >&2
    exit "$code"
}

require_root() {
    [ "${EUID}" -eq 0 ] || fail "root 권한으로 실행해야 합니다. sudo 를 사용하세요."
}

# Rocky Linux 9 (EL9) 만 받는다. aarch64(t4g) 가 기본 대상이고 x86_64 는 경고만 한다.
check_os() {
    [ -f /etc/os-release ] || fail "/etc/os-release 가 없어 OS 를 확인할 수 없습니다."
    # shellcheck disable=SC1091
    . /etc/os-release
    case "${ID:-}" in
        rocky|almalinux|rhel) ;;
        *) fail "지원하지 않는 OS 입니다: ${ID:-unknown} (Rocky Linux 9 계열만 지원)" ;;
    esac
    [ "${VERSION_ID%%.*}" = "9" ] || fail "EL9 계열만 지원합니다. 현재: ${PRETTY_NAME:-unknown}"
    ARCH="$(uname -m)"
    case "$ARCH" in
        aarch64) ;;
        x86_64) warn "x86_64 입니다. 대상은 t4g(aarch64)이지만 저장소 주소를 아키텍처에 맞춰 쓰므로 그대로 진행합니다." ;;
        *) fail "지원하지 않는 아키텍처입니다: ${ARCH}" ;;
    esac
    log "운영체제 : ${PRETTY_NAME} (${ARCH})"
}

# 장비 메모리(MB). 커널 예약분을 뺀 값이라 표기 용량보다 조금 작다(2GB → 약 1.8GB)
mem_total_mb() { awk '/^MemTotal:/ { print int($2 / 1024) }' /proc/meminfo; }

# 표기 용량(GB) — MemTotal 을 반올림한다. 2GB 장비는 1.8GB 로 잡히므로 2 가 된다
mem_nominal_gb() {
    local mb; mb="$(mem_total_mb)"
    local gb=$(( (mb + 512) / 1024 ))
    [ "$gb" -lt 1 ] && gb=1
    echo "$gb"
}

# 이 서버의 사설 IPv4 — 기본 경로가 쓰는 원본 주소
detect_private_ip() {
    ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i < NF; i++) if ($i == "src") { print $(i + 1); exit } }'
}

is_ipv4() { [[ "$1" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; }
is_cidr4() { [[ "$1" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}/([0-9]|[12][0-9]|3[0-2])$ ]]; }
is_cidr6() { [[ "$1" =~ ^[0-9A-Fa-f:]+/[0-9]{1,3}$ ]]; }

read_password_twice() {   # <표시 이름> <담을 변수명>
    local label="$1" target="$2" p1="" p2=""
    while true; do
        read -r -s -p "   ${label} 입력: " p1; echo
        [ -n "$p1" ] || { echo "   비워 둘 수 없습니다."; continue; }
        read -r -s -p "   ${label} 확인: " p2; echo
        [ "$p1" = "$p2" ] || { echo "   일치하지 않습니다. 다시 입력하세요."; continue; }
        printf -v "$target" '%s' "$p1"
        unset p1 p2
        break
    done
}

# 영숫자 무작위 문자열 — 헷갈리는 글자(0 O 1 l I)는 뺀다
rand_alnum() {   # <길이>
    local out=""
    while [ "${#out}" -lt "$1" ]; do
        out+="$(openssl rand -base64 48 | tr -dc 'A-HJ-NP-Za-km-z2-9')"
    done
    printf '%s' "${out:0:$1}"
}

# KEY=VALUE 환경 파일에서 값 하나 읽기(셸로 source 하지 않는다 — ENC(...) 의 괄호 때문)
env_get() {   # <파일> <키>
    [ -f "$1" ] || return 0
    sed -n "s/^$2=//p" "$1" | tail -n 1
}

# KEY=VALUE 를 바꾸거나 없으면 덧붙인다
env_set() {   # <파일> <키> <값>
    local file="$1" key="$2" value="$3" tmp
    tmp="$(mktemp "${file}.XXXXXX")"
    if grep -q "^${key}=" "$file" 2>/dev/null; then
        awk -v k="$key" -v v="$value" 'BEGIN { FS = OFS = "=" } $1 == k { print k "=" v; next } { print }' "$file" > "$tmp"
    else
        { cat "$file" 2>/dev/null; printf '%s=%s\n' "$key" "$value"; } > "$tmp"
    fi
    chmod --reference="$file" "$tmp" 2>/dev/null || true
    chown --reference="$file" "$tmp" 2>/dev/null || true
    mv -f "$tmp" "$file"
}
