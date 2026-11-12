// 아이로빗 아바타(apps/*/public/avatars/irobit/basic-default.svg)를 그대로 옮긴 공통 파트.
// 캐릭터는 아바타와 같은 200 단위 좌표로 그리고, 360 캔버스에 CHAR 변환으로 올린다.
// 이펙트(김·배터리·톱니 등)도 같은 200 단위 좌표에 그린다 — 캔버스 위쪽 여유는 y -43 까지다.

export const SIZE = 360
// 디시는 200px 원본을 100px 로 보여준다. 80px 에서도 읽히게 캐릭터를 캔버스에 크게 채운다
export const CHAR = { cx: 180, cy: 222, s: 1.72 }

export const C = {
  navy: '#0E1F4D',
  blue: '#2F6BFF',
  orange: '#FF9F2E',
  orangeDark: '#E5851A',
  yellow: '#FFC83D',
  white: '#FFFFFF',
  red: '#FF3B30',
  green: '#34C759',
  grey: '#8A94A6',
}

// 200px 납품에서 그림은 190/360 배로 줄어든다. 글자는 캔버스 기준 28px 이상이어야 하므로 캐릭터 좌표로는 28/s.
// 이 PC의 Noto Sans KR 은 가변 글꼴(VF)이라 resvg 에서 굵기가 안 먹는다 — 굵은 글자는 맑은 고딕 Bold 를 먼저 쓴다
export const FONT = "'Malgun Gothic', 'Noto Sans KR', sans-serif"
export const MIN_TEXT = 28 / CHAR.s

export const TAU = Math.PI * 2
export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v))
export const lerp = (a, b, k) => a + (b - a) * k
export const easeInOut = (k) => (k < 0.5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2)
/** t 구간 [a,b] 안에서의 진행률 0~1 */
export const span = (t, a, b) => clamp((t - a) / (b - a))
export const f = (n) => +n.toFixed(2)

/** 프레임마다 같은 노이즈가 나오도록 시드 고정 난수 */
export function rng(seed) {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let x = s
    x = Math.imul(x ^ (x >>> 15), x | 1)
    x ^= x + Math.imul(x ^ (x >>> 7), x | 61)
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296
  }
}

let uidSeq = 0
export const uid = (p) => `${p}${++uidSeq}`

// 위 캔버스 꼭대기(y=0)의 캐릭터 좌표 — 이펙트는 이보다 아래에 둔다
export const TOP = 100 - CHAR.cy / CHAR.s

// 실루엣 테두리 두께(캐릭터 좌표). 100px 표시에서 약 2px
export const OUTLINE = 4.5

/**
 * 남·여 버전. 아바타 basic-default(남)·basic-adult-f(여)의 차이를 그대로 옮긴다:
 * 여 = 분홍 머리, 안테나 리본(공 r9), 속눈썹, 조금 더 둥근 머리·화면.
 * 이모티콘 코드는 머리색을 V.head* 로만 쓰고, 빌드가 setVariant 로 바꿔 두 벌을 만든다.
 */
const VARIANTS = {
  m: {
    id: 'm', head: '#2F6BFF', headDull: '#4A68C8', headPale: '#5A86FF', headHot: '#3A63F0',
    hand: '#6E9BFF', accent: '#2F6BFF', headRx: 40, screenRx: 28, ballR: 12, bow: null, lashes: false,
  },
  f: {
    id: 'f', head: '#FF6B9A', headDull: '#C27A96', headPale: '#FF9DBE', headHot: '#FF4F86',
    hand: '#FFB3CC', accent: '#FF4F86', headRx: 44, screenRx: 30, ballR: 9, bow: '#FF7A8A', lashes: true,
  },
}
export const VARIANT_IDS = Object.keys(VARIANTS)
export const V = {}
export function setVariant(id) {
  if (!VARIANTS[id]) throw new Error(`없는 버전: ${id}`)
  Object.assign(V, VARIANTS[id])
}
setVariant('m')

// 아바타 원본 치수(화면 둥글기는 V.screenRx)
export const SCREEN = { x: 46, y: 76, w: 108, h: 62, cx: 100, cy: 107 }
export const EYE = { w: 20, h: 26, rx: 10, y: 92, lx: 70, rx2: 110 }

/**
 * 아이로빗 몸 한 벌.
 * @param {object} o
 *  dx,dy: 이동 / sy: 바닥(y=164) 기준 세로 눌림 / rot: 바닥 중심 회전(도)
 *  head, screen, limb: 색 / ears: 귀 세로 이동
 *  ball, ballGlow(0~1), bx, by: 안테나 공 색·후광·위치(꼭짓점 100,42 기준, 기본 0,-6)
 *  face: 화면 안 내용(화면 모양으로 잘림). 생략하면 기본 눈
 *  outline: 실루엣 바깥 테두리 색 — 다크 모드에서 남색 다리·귀·안테나가 묻히지 않게 한다(null 이면 없음)
 *  back, front: 몸과 함께 움직이는 덧그림(팔·눈물 등). back 은 머리 뒤, front 는 머리 앞
 */
export function body(o = {}) {
  const {
    dx = 0, dy = 0, sy = 1, rot = 0,
    head = V.head, screen = C.navy, limb = C.navy, ears = 0,
    ball = C.orange, ballGlow = 0, bx = 0, by = -6,
    face = eyes(), outline = C.white, back = '', front = '',
  } = o
  const clip = uid('scr')
  const s = { ...SCREEN, rx: V.screenRx }
  const hr = V.headRx
  const br = V.ballR
  const ballX = 100 + bx
  const ballY = 42 + by
  // 여: 안테나 공 양옆 리본
  const bowPath = `M${f(ballX)} ${f(ballY)} L${f(ballX - 24)} ${f(ballY - 14)} L${f(ballX - 24)} ${f(ballY + 14)} Z M${f(ballX)} ${f(ballY)} L${f(ballX + 24)} ${f(ballY - 14)} L${f(ballX + 24)} ${f(ballY + 14)} Z`
  const bow = V.bow ? `<path d="${bowPath}" fill="${V.bow}" stroke="${V.bow}" stroke-width="3" stroke-linejoin="round"/>` : ''
  const stalk = Math.hypot(bx, by + 6) > 1
    ? `<line x1="100" y1="44" x2="${f(ballX)}" y2="${f(ballY)}" stroke="${limb}" stroke-width="8" stroke-linecap="round"/>`
    : ''
  // GIF 는 반투명이 없어 후광 대신 짧은 빛살로 빛남을 표현한다
  const glow = ballGlow > 0
    ? [0, 1, 2, 3, 4, 5].map((k) => {
      const a = (k / 6) * TAU - Math.PI / 2
      const r1 = 17 + 2 * ballGlow
      const r2 = r1 + 4 + 4 * ballGlow
      return `<line x1="${f(ballX + Math.cos(a) * r1)}" y1="${f(ballY + Math.sin(a) * r1)}" x2="${f(ballX + Math.cos(a) * r2)}" y2="${f(ballY + Math.sin(a) * r2)}" stroke="${ball}" stroke-width="4" stroke-linecap="round"/>`
    }).join('')
    : ''
  const O = OUTLINE
  const edge = outline
    ? `<g fill="${outline}" stroke="${outline}" stroke-linecap="round">
<line x1="100" y1="42" x2="46" y2="164" stroke-width="${16 + O * 2}"/>
<line x1="100" y1="42" x2="154" y2="164" stroke-width="${16 + O * 2}"/>
${stalk ? `<line x1="100" y1="44" x2="${f(ballX)}" y2="${f(ballY)}" stroke-width="${8 + O * 2}"/>` : ''}
<circle cx="${f(ballX)}" cy="${f(ballY)}" r="${br + O}" stroke="none"/>
${V.bow ? `<path d="${bowPath}" stroke-width="${3 + O * 2}" stroke-linejoin="round"/>` : ''}
<rect x="16" y="${92 + ears}" width="18" height="34" rx="9" stroke-width="${O * 2}"/>
<rect x="166" y="${92 + ears}" width="18" height="34" rx="9" stroke-width="${O * 2}"/>
<rect x="28" y="62" width="144" height="90" rx="${hr}" stroke-width="${O * 2}"/>
</g>`
    : ''
  return `<g transform="translate(${f(dx)} ${f(dy)}) rotate(${f(rot)} 100 164) translate(0 164) scale(1 ${f(sy)}) translate(0 -164)">
<defs><clipPath id="${clip}"><rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" rx="${s.rx}"/></clipPath></defs>
${back}${edge}
<line x1="100" y1="42" x2="46" y2="164" stroke="${limb}" stroke-width="16" stroke-linecap="round"/>
<line x1="100" y1="42" x2="154" y2="164" stroke="${limb}" stroke-width="16" stroke-linecap="round"/>
${stalk}${glow}${bow}<circle cx="${f(ballX)}" cy="${f(ballY)}" r="${br}" fill="${ball}"/>
<rect x="16" y="${92 + ears}" width="18" height="34" rx="9" fill="${limb}"/>
<rect x="166" y="${92 + ears}" width="18" height="34" rx="9" fill="${limb}"/>
<rect x="28" y="62" width="144" height="90" rx="${hr}" fill="${head}"/>
<rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" rx="${s.rx}" fill="${screen}"/>
<g clip-path="url(#${clip})">${face}</g>
${front}
</g>`
}

/**
 * 아바타의 노란 알약 눈.
 * open: 0~1 (1=다 뜸). lid: 위에서 덮는 눈꺼풀 비율(졸린 눈). tilt: 눈꺼풀 기울기(+ 안쪽이 내려감=화남, - 바깥이 내려감=슬픔)
 * lx/ly: 시선 이동, scale: 눈 크기
 */
export function eyes({ open = 1, lid = 0, tilt = 0, lx = 0, ly = 0, color = C.yellow, hl = true, lidColor = C.navy, scale = 1 } = {}) {
  const out = []
  ;[EYE.lx, EYE.rx2].forEach((x0, k) => {
    const w = EYE.w * scale
    const x = x0 + EYE.w / 2 - w / 2 + lx
    const h = Math.max(3, EYE.h * scale * open)
    const y = EYE.y + ly + (EYE.h - h) / 2
    out.push(`<rect x="${f(x)}" y="${f(y)}" width="${f(w)}" height="${f(h)}" rx="${f(Math.min(w / 2, h / 2))}" fill="${color}"/>`)
    if (hl && open > 0.6 && lid + Math.abs(tilt) < 0.35) out.push(`<circle cx="${f(x + w * 0.4)}" cy="${f(y + 6 * open * scale)}" r="${f(3.5 * scale)}" fill="${C.white}"/>`)
    if (lid > 0 || tilt !== 0) {
      const inner = clamp(lid + tilt)
      const outer = clamp(lid - tilt)
      const [l, r] = k === 0 ? [outer, inner] : [inner, outer]
      out.push(`<path d="M${f(x - 2)} ${f(y - 2)} L${f(x + w + 2)} ${f(y - 2)} L${f(x + w + 2)} ${f(y + h * r)} L${f(x - 2)} ${f(y + h * l)} Z" fill="${lidColor}"/>`)
    }
    // 여: 바깥 눈꼬리 속눈썹(아바타 basic-adult-f)
    if (V.lashes && open > 0.5 && lid < 0.4) {
      const ox = k === 0 ? x - 4 : x + w + 4
      const dir = k === 0 ? -1 : 1
      const top = y + h * clamp(lid - (k === 0 ? tilt : tilt))
      out.push(lash(ox, top + 2, dir, -1, color))
    }
  })
  return out.join('')
}

/** 속눈썹 한 가닥. up=-1 이면 위로, 1 이면 아래로 */
function lash(x, y, dir, up, color) {
  return `<line x1="${f(x)}" y1="${f(y)}" x2="${f(x + dir * 8)}" y2="${f(y + up * 8)}" stroke="${color}" stroke-width="4" stroke-linecap="round"/>`
}

/** X 눈 */
export function xEyes({ color = C.red, size = 10, width = 7, lx = 0 } = {}) {
  return [80, 120].map((cx) => {
    const x = cx + lx
    const y = 105
    return `<path d="M${x - size} ${y - size} L${x + size} ${y + size} M${x + size} ${y - size} L${x - size} ${y + size}" stroke="${color}" stroke-width="${width}" stroke-linecap="round"/>`
  }).join('')
}

/** 선 눈. kind: '^' 웃는 눈, 'u' 감고 미소, '-' 감은 눈, '>' 질끈(왼눈 >, 오른눈 <) */
export function lineEyes(kind, { color = C.yellow, width = 7, ly = 0, lx = 0, only = null } = {}) {
  return [[80, 0], [120, 1]].filter(([, k]) => only === null || only === k).map(([cx0, k]) => {
    const cx = cx0 + lx
    const y = 106 + ly
    let d
    if (kind === '^') d = `M${cx - 10} ${y + 5} Q${cx} ${y - 13} ${cx + 10} ${y + 5}`
    else if (kind === 'u') d = `M${cx - 10} ${y - 3} Q${cx} ${y + 12} ${cx + 10} ${y - 3}`
    else if (kind === '-') d = `M${cx - 10} ${y} L${cx + 10} ${y}`
    else d = k === 0 ? `M${cx - 9} ${y - 10} L${cx + 9} ${y} L${cx - 9} ${y + 10}` : `M${cx + 9} ${y - 10} L${cx - 9} ${y} L${cx + 9} ${y + 10}`
    const dir = k === 0 ? -1 : 1
    // 여: 눈꼬리 속눈썹 — 웃는 눈은 위로, 감은 눈은 아래로
    const lashes = !V.lashes || kind === '>' ? ''
      : kind === '^' ? lash(cx + dir * 11, y + 3, dir, -1, color)
        : lash(cx + dir * 11, kind === 'u' ? y - 2 : y + 1, dir, 1, color)
    return `<path d="${d}" fill="none" stroke="${color}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round"/>${lashes}`
  }).join('')
}

/** 입(화면 아래쪽). kind: smile, grin, frown, flat, o, wave, zigzag, yawn */
export function mouth(kind, { x = 100, y = 127, w = 16, open = 1, color = C.yellow } = {}) {
  const line = (w = 5) => `fill="none" stroke="${color}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"`
  const st = line()
  const h = w / 2
  if (kind === 'smile') return `<path d="M${x - h} ${y - 3} Q${x} ${y + 7} ${x + h} ${y - 3}" ${st}/>`
  if (kind === 'grin') return `<path d="M${x - h - 2} ${y - 5} L${x + h + 2} ${y - 5} Q${x + h} ${y + 8} ${x} ${y + 8} Q${x - h} ${y + 8} ${x - h - 2} ${y - 5} Z" fill="${color}"/>`
  if (kind === 'frown') return `<path d="M${x - h} ${y + 4} Q${x} ${y - 6} ${x + h} ${y + 4}" ${st}/>`
  if (kind === 'flat') return `<path d="M${x - h} ${y} L${x + h} ${y}" ${st}/>`
  if (kind === 'o') return `<ellipse cx="${x}" cy="${y}" rx="${f(4 + 2 * open)}" ry="${f(4 + 3 * open)}" fill="${color}"/>`
  if (kind === 'yawn') return `<ellipse cx="${x}" cy="${f(y - 2)}" rx="${f(6 + 5 * open)}" ry="${f(3 + 8 * open)}" fill="${color}"/>`
  if (kind === 'wave') return `<path d="M${x - h - 2} ${y} q${f(w / 6)} -5 ${f(w / 3)} 0 t${f(w / 3)} 0 t${f(w / 3)} 0 t${f(w / 3)} 0" ${line(4)}/>`
  return `<path d="M${x - h - 2} ${y + 2} l${f(w / 4)} -6 l${f(w / 4)} 6 l${f(w / 4)} -6 l${f(w / 4)} 6 l${f(w / 4)} -6" ${line(4)}/>`
}

/** 볼터치(화면 안) */
export function blush(k = 1, color = '#FF7FA8') {
  return [62, 138].map((x) => `<ellipse cx="${x}" cy="124" rx="${f(9 * k)}" ry="${f(5 * k)}" fill="${color}"/>`).join('')
}

/** 하트. (x,y)=중심, s=반폭 */
export function heart(x, y, s, fill = '#FF4D7D', stroke = C.navy, rot = 0) {
  const d = `M0 ${f(0.95 * s)} C${f(-1.5 * s)} ${f(-0.05 * s)} ${f(-0.75 * s)} ${f(-1.05 * s)} 0 ${f(-0.4 * s)} C${f(0.75 * s)} ${f(-1.05 * s)} ${f(1.5 * s)} ${f(-0.05 * s)} 0 ${f(0.95 * s)} Z`
  return `<path transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" d="${d}" fill="${fill}"${stroke ? ` stroke="${stroke}" stroke-width="3.5" stroke-linejoin="round"` : ''}/>`
}

/** 별(5각) */
export function star(x, y, r, fill = C.yellow, rot = 0, stroke = C.navy) {
  const pts = []
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * TAU - Math.PI / 2
    const rr = i % 2 ? r * 0.46 : r
    pts.push(`${f(Math.cos(a) * rr)} ${f(Math.sin(a) * rr)}`)
  }
  return `<path transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" d="M${pts.join(' L')} Z" fill="${fill}" stroke="${stroke}" stroke-width="3" stroke-linejoin="round"/>`
}

/** 물방울(눈물·땀). (x,y)=둥근 아래쪽 중심 */
export function drop(x, y, r, fill = '#6EC6FF', stroke = C.navy) {
  return `<path transform="translate(${f(x)} ${f(y)})" d="M0 ${f(-2.2 * r)} Q${f(r)} ${f(-0.6 * r)} ${f(r)} 0 A${f(r)} ${f(r)} 0 0 1 ${f(-r)} 0 Q${f(-r)} ${f(-0.6 * r)} 0 ${f(-2.2 * r)} Z" fill="${fill}" stroke="${stroke}" stroke-width="3" stroke-linejoin="round"/>`
}

/** 굵은 글자(외곽선 포함). 크기는 캐릭터 좌표 */
export function label(x, y, text, size, { fill = C.yellow, stroke = C.navy, width = 4, anchor = 'middle', rot = 0 } = {}) {
  return `<text transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" text-anchor="${anchor}" font-family="${FONT}" font-weight="700" font-size="${f(size)}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" stroke-linejoin="round" paint-order="stroke">${text}</text>`
}

/**
 * 팔(귀에서 나옴). side: 'L'|'R'
 * a: 각도(0=아래, 90=옆으로, 180=위로), len: 길이, to: [x,y] 로 손 위치를 직접 지정
 * hand: 'mitt' 둥근 손, 'thumb' 엄지척(thumbRot 으로 엄지 방향)
 */
export function arm(side, { a = 20, len = 30, to = null, hand = 'mitt', r = 10, thumbRot = 0, color = C.navy, handColor = V.hand, outline = C.white } = {}) {
  const sx = side === 'L' ? 25 : 175
  const sy = 116
  const out = side === 'L' ? -1 : 1
  const rad = (a * Math.PI) / 180
  const [hx, hy] = to ?? [sx + out * Math.sin(rad) * len, sy + Math.cos(rad) * len]
  const tw = r * 0.85
  const th = r * 1.7
  const thumb = (fill, grow) => `<rect x="${f(hx - tw / 2 - grow)}" y="${f(hy - r * 0.6 - th - grow)}" width="${f(tw + grow * 2)}" height="${f(th + r * 0.6 + grow * 2)}" rx="${f(tw / 2 + grow)}" fill="${fill}" transform="rotate(${f(thumbRot)} ${f(hx)} ${f(hy)})"/>`
  const shapes = (fill, grow, palm = fill) => `<line x1="${sx}" y1="${sy}" x2="${f(hx)}" y2="${f(hy)}" stroke="${fill}" stroke-width="${10 + grow * 2}" stroke-linecap="round"/>`
    + `<circle cx="${f(hx)}" cy="${f(hy)}" r="${f(r + grow)}" fill="${palm}"/>`
    + (hand === 'thumb' ? thumb(palm, grow) : '')
  return (outline ? shapes(outline, OUTLINE) : '') + shapes(color, 0, handColor)
}

/** 손만(얼굴 앞). 팔은 arm() 으로 머리 뒤(back)에 그리고 손은 이걸로 앞(front)에 그린다 */
export function hands(points, r = 11, color = V.hand, outline = C.white) {
  return points.map(([x, y]) => `<circle cx="${f(x)}" cy="${f(y)}" r="${f(r + OUTLINE)}" fill="${outline}"/><circle cx="${f(x)}" cy="${f(y)}" r="${f(r)}" fill="${color}"/>`).join('')
}

/** 팔은 머리 뒤, 손은 얼굴 앞 */
export function armsBehind(left, right, r = 11) {
  return {
    back: arm('L', { to: left, r }) + arm('R', { to: right, r }),
    front: hands([left, right], r),
  }
}

/**
 * 대사 글자(말풍선 없이). 흰 바깥 테두리 + 남색 안쪽 테두리라 밝은·어두운 배경 모두에서 읽힌다.
 * parts: 문자열 하나 또는 [[글자, 색], ...](한 줄 안에서 색을 나눔). (x,y)=가운데 아래(글자 기준선)
 */
export function say(x, y, parts, size, fill = C.yellow, rot = 0) {
  const spans = (typeof parts === 'string' ? [[parts, fill]] : parts)
  const body = (color) => spans.map(([s, c]) => `<tspan fill="${color ?? c}">${s}</tspan>`).join('')
  const base = `transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" text-anchor="middle" font-family="${FONT}" font-weight="700" font-size="${f(size)}" stroke-linejoin="round"`
  return `<text ${base} stroke="${C.white}" stroke-width="${f(size * 0.38)}">${body(C.white)}</text>`
    + `<text ${base} stroke="${C.navy}" stroke-width="${f(size * 0.17)}" paint-order="stroke">${body()}</text>`
}

/** 김(증기) 한 덩이. 흰 배경에서도 보이게 남색 외곽선을 덩어리 바깥에만 두른다. GIF 는 반투명이 없어 opacity 대신 크기로 사라지게 한다 */
export function puff(x, y, r, opacity = 1) {
  const blobs = [[x, y, r], [x - r * 0.7, y + r * 0.35, r * 0.68], [x + r * 0.72, y + r * 0.3, r * 0.62]]
  const ring = blobs.map(([bx, by, br]) => `<circle cx="${f(bx)}" cy="${f(by)}" r="${f(br + 2)}" fill="${C.navy}"/>`).join('')
  const fill = blobs.map(([bx, by, br], k) => `<circle cx="${f(bx)}" cy="${f(by)}" r="${f(br)}" fill="${k ? '#DCE4F2' : '#F4F7FC'}"/>`).join('')
  return `<g opacity="${f(opacity)}">${ring}${fill}</g>`
}

/** 배터리 아이콘. level: 켜진 칸 수(0~4), barColor: 칸 색 */
export function battery(x, y, { w = 56, h = 30, level = 4, barColor = C.green, bolt = false } = {}) {
  const bars = []
  const pad = 5
  const gap = 3
  const bw = (w - pad * 2 - gap * 3) / 4
  for (let i = 0; i < level; i++) {
    bars.push(`<rect x="${f(x + pad + i * (bw + gap))}" y="${y + pad}" width="${f(bw)}" height="${h - pad * 2}" rx="2" fill="${barColor}"/>`)
  }
  const b = bolt ? lightning(x + w / 2, y + h / 2, 0.55, C.white, C.navy) : ''
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="7" fill="${C.white}" stroke="${C.navy}" stroke-width="5"/>`
    + `<rect x="${x + w + 1}" y="${y + h / 2 - 7}" width="7" height="14" rx="3" fill="${C.navy}"/>`
    + bars.join('') + b
}

/** 번개. (x,y)=중심 */
export function lightning(x, y, scale = 1, fill = C.yellow, stroke = C.navy) {
  return `<path transform="translate(${f(x)} ${f(y)}) scale(${f(scale)})" d="M4 -22 L-12 4 L-1 4 L-6 22 L12 -6 L1 -6 Z" fill="${fill}" stroke="${stroke}" stroke-width="${f(3.5 / scale)}" stroke-linejoin="round"/>`
}

/** 반짝이(4각 별) */
export function sparkle(x, y, r, fill = C.white, rot = 0) {
  const k = r * 0.28
  return `<path transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" d="M0 ${-r} Q${k} ${-k} ${r} 0 Q${k} ${k} 0 ${r} Q${-k} ${k} ${-r} 0 Q${-k} ${-k} 0 ${-r} Z" fill="${fill}"/>`
}

/** 톱니바퀴 */
export function gear(cx, cy, { r = 18, depth = 5, teeth = 8, rot = 0, fill = C.yellow, stroke = C.navy, hole = 6 } = {}) {
  const pts = []
  const step = TAU / teeth
  for (let i = 0; i < teeth; i++) {
    const a = i * step
    const seq = [[a - step * 0.25, r - depth], [a - step * 0.14, r], [a + step * 0.14, r], [a + step * 0.25, r - depth]]
    for (const [ang, rad] of seq) pts.push(`${f(Math.cos(ang) * rad)} ${f(Math.sin(ang) * rad)}`)
  }
  return `<g transform="translate(${f(cx)} ${f(cy)}) rotate(${f(rot)})">`
    + `<path d="M${pts.join(' L')} Z" fill="${fill}" stroke="${stroke}" stroke-width="3.5" stroke-linejoin="round"/>`
    + `<circle r="${hole}" fill="${stroke}"/></g>`
}

/** 픽셀 그림. rows: '#'=칠함 */
export function pixels(rows, x, y, cell, fill) {
  const out = []
  rows.forEach((row, j) => {
    for (let i = 0; i < row.length; i++) {
      if (row[i] === '#') out.push(`<rect x="${f(x + i * cell)}" y="${f(y + j * cell)}" width="${f(cell + 0.3)}" height="${f(cell + 0.3)}" fill="${fill}"/>`)
    }
  })
  return out.join('')
}

/** 경고 삼각형 */
export function warning(x, y, size = 34, fill = C.yellow) {
  const h = size * 0.88
  return `<g transform="translate(${f(x)} ${f(y)})">`
    + `<path d="M0 ${f(-h / 2)} L${f(size / 2)} ${f(h / 2)} L${f(-size / 2)} ${f(h / 2)} Z" fill="${fill}" stroke="${C.navy}" stroke-width="5" stroke-linejoin="round"/>`
    + `<rect x="-2.6" y="${f(-h / 2 + 10)}" width="5.2" height="${f(h * 0.42)}" rx="2.6" fill="${C.navy}"/>`
    + `<circle cx="0" cy="${f(h / 2 - 6)}" r="3" fill="${C.navy}"/></g>`
}

/**
 * 가로 띠로 잘라 어긋나게 그리는 글리치.
 * bands: [{ y, h, dx }] — 캐릭터 좌표. 띠 밖은 원본 그대로 그린다
 */
export function sliced(svg, bands) {
  if (!bands.length) return svg
  const parts = []
  const rest = uid('rest')
  // 띠를 뺀 나머지
  const mask = bands.map((b) => `<rect x="-60" y="${f(b.y)}" width="320" height="${f(b.h)}" fill="black"/>`).join('')
  parts.push(`<defs><mask id="${rest}" maskUnits="userSpaceOnUse" x="-60" y="-60" width="320" height="320"><rect x="-60" y="-60" width="320" height="320" fill="white"/>${mask}</mask></defs><g mask="url(#${rest})">${svg}</g>`)
  for (const b of bands) {
    const id = uid('band')
    parts.push(`<defs><clipPath id="${id}"><rect x="-60" y="${f(b.y)}" width="320" height="${f(b.h)}"/></clipPath></defs><g clip-path="url(#${id})"><g transform="translate(${f(b.dx)} 0)">${svg}</g></g>`)
  }
  return parts.join('')
}

/** 360 캔버스 한 장. layers 는 캐릭터 좌표(200 단위) */
export function canvas(layers) {
  const { cx, cy, s } = CHAR
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}" viewBox="0 0 ${SIZE} ${SIZE}">
<g transform="translate(${cx} ${cy}) scale(${s}) translate(-100 -100)">
${layers.join('\n')}
</g>
</svg>`
}

export function resetIds() {
  uidSeq = 0
}
