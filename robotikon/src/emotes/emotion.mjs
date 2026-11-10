// 감정 16종(01~16). 각 frame(t)는 t=0~1 의 한 장면이고, t=0 과 t=1 이 같은 장면이라 루프가 이어진다.
// GIF 는 반투명이 없으므로 나타나고 사라지는 효과는 opacity 대신 크기로 표현한다.
import {
  C, SCREEN, V, TAU, arm, armsBehind, blush, body, canvas, drop, easeInOut, eyes, f, heart, label,
  lineEyes, mouth, puff, say, sparkle, span, star,
} from '../parts.mjs'

const blink = (t, n) => Math.floor(t * n * 2) % 2 === 0
/** a~b 에서 0→1, c~d 에서 1→0 */
const env = (t, a, b, c, d) => easeInOut(span(t, a, b)) * (1 - easeInOut(span(t, c, d)))
/** 0→1 진행하며 끝에서 작아져 사라지는 크기 */
const life = (p, grow = 0.12, fade = 0.75) => Math.min(1, p / grow) * (p > fade ? 1 - (p - fade) / (1 - fade) : 1)

// 01 기쁨 — 웃는 눈, 안테나 통통
const joy = {
  id: '01',
  frame(t) {
    const b = Math.abs(Math.sin(TAU * t * 2))
    const lag = Math.abs(Math.sin(TAU * t * 2 - 0.7))
    const face = lineEyes('^') + mouth('grin', { w: 18 }) + blush(0.9)
    return canvas([
      body({ dy: -7 * b, sy: 1 - 0.05 * (1 - b) ** 4, by: -6 - 12 * lag, face }),
      sparkle(22, 50 - 7 * b, 9 * (blink(t, 2) ? 1 : 0.5), C.yellow),
      sparkle(180, 44 - 7 * b, 9 * (blink(t, 2) ? 0.5 : 1), C.yellow),
    ])
  },
}

// 02 슬픔 — 눈물 줄줄, 안테나 시들, "흐흐흑 ㅠㅠ"
const sad = {
  id: '02',
  frame(t) {
    const sob = Math.sin(TAU * t * 6)
    const streams = [80, 120].map((x) => `<rect x="${x - 4}" y="112" width="8" height="30" rx="4" fill="#6EC6FF"/>`).join('')
    const face = eyes({ lid: 0.2, tilt: -0.35, ly: 2 }) + streams + mouth('frown', { w: 18 })
    const tears = []
    for (const [x, dir] of [[80, -1], [120, 1]]) {
      for (let k = 0; k < 2; k++) {
        const p = (t * 2 + k / 2) % 1
        const r = 4.5 * life(p, 0.15, 0.8)
        if (r > 0.6) tears.push(drop(x + dir * 16 * p, 140 + 34 * p, r))
      }
    }
    return canvas([
      body({ dy: 4 + 1.2 * sob, bx: -12, by: 5 + sob, face, front: tears.join('') }),
      say(100, 8 + 1.5 * sob, '흐흐흑 ㅠㅠ', 28, '#6EC6FF'),
    ])
  },
}

// 03 우울 — 얼굴 화면 회색, 안테나 축 처짐, 머리 위 비구름
const gloomy = {
  id: '03',
  frame(t) {
    const sway = Math.sin(TAU * t)
    const grey = '#A3A9B6'
    const face = eyes({ color: grey, lid: 0.5, ly: 4, hl: false }) + mouth('flat', { w: 14, color: grey })
    const cy = -6 + 2 * sway
    const blobs = [[100, cy, 17], [80, cy + 6, 12], [121, cy + 5, 13], [64, cy + 11, 8], [137, cy + 11, 8]]
    const cloud = blobs.map(([x, y, r]) => `<circle cx="${f(x)}" cy="${f(y)}" r="${r + 3}" fill="${C.navy}"/>`).join('')
      + blobs.map(([x, y, r]) => `<circle cx="${f(x)}" cy="${f(y)}" r="${r}" fill="#8E97AA"/>`).join('')
      + `<rect x="62" y="${f(cy + 6)}" width="76" height="12" fill="#8E97AA"/>`
    const rain = []
    for (let k = 0; k < 5; k++) {
      const p = (t * 3 + k * 0.37) % 1
      const x = 72 + k * 14
      const y = cy + 22 + 32 * p
      rain.push(`<line x1="${f(x)}" y1="${f(y)}" x2="${f(x - 2)}" y2="${f(y + 9)}" stroke="#5BA8FF" stroke-width="4" stroke-linecap="round"/>`)
    }
    return canvas([
      body({ dy: 6 + 1.5 * sway, sy: 0.96, head: V.headDull, screen: '#465063', ball: C.grey, bx: -20, by: 12 + sway, ears: 4, face }),
      rain.join(''),
      cloud,
    ])
  },
}

// 04 분노 — 화면 빨강, 안테나에서 김, 주먹 부들부들, "부들부들"
const angry = {
  id: '04',
  frame(t, i) {
    const shake = i % 2 ? 1 : -1
    const hot = 0.5 + 0.5 * Math.sin(TAU * 4 * t)
    const screen = `rgb(${Math.round(160 + 50 * hot)},28,38)`
    const face = eyes({ tilt: 0.45, lid: 0.05 }) + mouth('zigzag', { w: 22 })
    const puffs = [-1, 1].map((dir, k) => {
      const p = (t * 2 + k * 0.5) % 1
      // 글자가 위에 있어 김은 옆으로 뿜는다
      const r = (5 + 7 * p) * life(p)
      return r > 0.8 ? puff(100 + dir * (18 + 34 * p), 30 - 12 * p, r) : ''
    }).join('')
    const veinD = '<path d="M-4 -11 Q-4 -4 -11 -4"/><path d="M4 -11 Q4 -4 11 -4"/><path d="M-4 11 Q-4 4 -11 4"/><path d="M4 11 Q4 4 11 4"/>'
    const vein = `<g transform="translate(152 70) scale(${f(1 + 0.15 * hot)})" fill="none" stroke-linecap="round"><g stroke="#FFFFFF" stroke-width="9">${veinD}</g><g stroke="#FF3B30" stroke-width="5">${veinD}</g></g>`
    const fists = arm('L', { a: 165 + 6 * shake, len: 28, r: 11 }) + arm('R', { a: 165 - 6 * shake, len: 28, r: 11 })
    return canvas([
      body({ dx: 2 * shake, screen, ball: C.red, face, back: fists, front: vein }),
      puffs,
      say(100 + 2 * shake, 0, '부들부들', 26, '#FF4D4D'),
    ])
  },
}

// 05 짜증 — 반쯤 감은 눈, 한숨, "짜증나!"
const annoyed = {
  id: '05',
  frame(t) {
    const inhale = easeInOut(span(t, 0, 0.3))
    const exhale = easeInOut(span(t, 0.3, 0.6))
    const breath = inhale - exhale
    const face = eyes({ lid: 0.5, tilt: 0.1, lx: 5, hl: false }) + `<path d="M92 130 L110 125" stroke="${C.yellow}" stroke-width="5" stroke-linecap="round"/>`
    const p = span(t, 0.3, 0.9)
    const sigh = p > 0 && p < 1 ? puff(160 + 34 * p, 128 - 10 * p, 11 * life(p, 0.1, 0.6)) : ''
    return canvas([
      body({ dy: 3 - 6 * breath, sy: 0.98 + 0.04 * breath, by: -6 + 4 * (1 - breath), face }),
      sigh,
      say(100, 4, '짜증나!', 28, C.orange, -5),
    ])
  },
}

// 06 놀람 — 눈 동그랗게, 안테나 쭉 솟음
const surprised = {
  id: '06',
  frame(t, i) {
    const k = easeInOut(span(t, 0, 0.08)) * (1 - easeInOut(span(t, 0.75, 0.92)))
    const pop = Math.sin(Math.PI * span(t, 0, 0.16))
    const tremble = k > 0.9 ? (i % 2 ? 0.8 : -0.8) : 0
    const face = eyes({ scale: 1 + 0.3 * k, ly: -2 * k }) + mouth('o', { open: k, y: 129 })
    const bangs = k > 0.5
      ? say(44, 40, '헉!', 34 * (0.85 + 0.15 * k), C.yellow, -14) + label(168, 46, '!', 38, { rot: 18 })
      : ''
    return canvas([
      body({ dx: tremble, dy: -12 * pop, by: -6 - 22 * k, face }),
      bangs,
    ])
  },
}

// 07 무서움 — 덜덜 떨림, "무셔웡.."
const scared = {
  id: '07',
  frame(t, i) {
    const s = i % 2 ? 1 : -1
    const face = eyes({ scale: 0.72, lx: s * 1.5, hl: true }) + mouth('wave', { w: 24, y: 128 })
    // 두 손으로 입을 가림
    const hug = armsBehind([86 + s, 142], [114 + s, 142], 10)
    const p = (t * 2) % 1
    const sweat = drop(158, 76 + 14 * p, 5 * life(p, 0.1, 0.8))
    const shiver = [[2, 98], [198, 98]].map(([x, y], k) => {
      const dir = k ? 1 : -1
      return `<path d="M${x} ${y} q${dir * 6} 10 0 20 M${x + dir * 7} ${y + 4} q${dir * 4} 6 0 12" fill="none" stroke="${C.navy}" stroke-width="3.5" stroke-linecap="round"/>`
    }).join('')
    return canvas([
      body({ dx: 2.5 * s, dy: 2, sy: 0.97, head: V.headPale, face, back: hug.back, front: hug.front + sweat }),
      shiver,
      say(100 + 2 * s, 6, '무셔웡..', 28, '#B9A6FF'),
    ])
  },
}

// 08 혐오 — 입 일그러짐, 몸 뒤로
const disgust = {
  id: '08',
  frame(t) {
    const k = env(t, 0, 0.25, 0.78, 0.98)
    const right = `<rect x="110" y="100" width="20" height="16" rx="8" fill="${C.yellow}"/><rect x="108" y="96" width="24" height="9" fill="${C.navy}"/>`
    const face = lineEyes('>', { only: 0 }) + right
      + `<rect x="${SCREEN.x}" y="128" width="${SCREEN.w}" height="12" fill="#2C5E3A"/>`
      + mouth('zigzag', { w: 26, y: 126, color: '#B6F07A' })
    return canvas([
      body({ dx: -6 * k, rot: -10 * k, by: -6, bx: -6 * k, face }),
      say(150, 30 - 4 * k, '으윽..', 26, '#B6F07A', 10),
    ])
  },
}

// 09 당황 — 땀방울, 화면에 물결
const flustered = {
  id: '09',
  frame(t) {
    const dart = Math.sin(TAU * t * 3) > 0 ? 5 : -5
    const waves = [0, 1, 2].map((k) => {
      const y = 84 + k * 20
      const ph = (t * 2 + k * 0.33) % 1
      const pts = []
      for (let x = SCREEN.x; x <= SCREEN.x + SCREEN.w; x += 6) pts.push(`${x} ${f(y + 3 * Math.sin((x / 16 + ph) * TAU))}`)
      return `<path d="M${pts.join(' L')}" fill="none" stroke="#3E5BB8" stroke-width="3"/>`
    }).join('')
    const face = waves + eyes({ scale: 0.8, lx: dart, hl: true }) + mouth('wave', { w: 20, y: 129 })
    const p = (t * 2) % 1
    const sweat = drop(160, 72 + 22 * p, 6.5 * life(p, 0.1, 0.85)) + drop(40, 78 + 14 * ((p + 0.5) % 1), 4.5 * life((p + 0.5) % 1, 0.1, 0.85))
    return canvas([
      body({ rot: 3 * Math.sin(TAU * t * 2), face, front: sweat }),
      say(100 + (Math.sin(TAU * t * 3) > 0 ? 2 : -2), 6, '어버버', 28, '#7FD3FF'),
    ])
  },
}

// 10 부끄러움 — 볼터치 진하게, 손으로 얼굴 가림(사이로 빼꼼)
const shy = {
  id: '10',
  frame(t) {
    const peek = env(t, 0.3, 0.45, 0.7, 0.85)
    const face = lineEyes('^') + blush(1.5, '#FF5C93') + mouth('wave', { w: 12, y: 130 })
    // 두 손으로 눈을 가렸다가 아래로 내리며 빼꼼
    const hs = armsBehind([82 - 8 * peek, 106 + 20 * peek], [118 + 8 * peek, 106 + 20 * peek], 13)
    const lines = [0, 1, 2].map((k) => `<line x1="${150 + k * 9}" y1="64" x2="${144 + k * 9}" y2="74" stroke="#E0245E" stroke-width="4.5" stroke-linecap="round"/>`).join('')
    return canvas([
      body({ rot: 4 * Math.sin(TAU * t), sy: 0.98, ball: '#FF8FB3', face, back: hs.back, front: hs.front + lines }),
      say(100, 6, '부끄부끄', 26, '#FF7FA8', -3 + 3 * Math.sin(TAU * t)),
    ])
  },
}

// 11 사랑 — 하트 눈, 하트 발사
const love = {
  id: '11',
  frame(t) {
    const beat = 1 + 0.15 * Math.abs(Math.sin(TAU * t * 2))
    const face = heart(80, 106, 11 * beat, '#FF4D7D', null) + heart(120, 106, 11 * beat, '#FF4D7D', null) + mouth('smile', { w: 14, y: 129 })
    const hearts = [[-48, 0], [50, 0.33], [-20, 0.66]].map(([dx, off]) => {
      const p = (t + off) % 1
      const s = 9 * life(p, 0.12, 0.7) * (0.8 + 0.4 * p)
      return s > 0.8 ? heart(100 + dx * (0.6 + 0.6 * p), 56 - 78 * p, s, '#FF4D7D', C.navy, dx > 0 ? 12 : -12) : ''
    }).join('')
    return canvas([
      body({ rot: 3 * Math.sin(TAU * t), ball: '#FF7FA8', face }),
      hearts,
    ])
  },
}

// 12 설렘 — 하트 박동(두근두근), 안테나 깜빡
const flutter = {
  id: '12',
  frame(t) {
    const local = t % 0.5
    const pulse = Math.max(Math.exp(-((local - 0.02) ** 2) / 0.0012), 0.7 * Math.exp(-((local - 0.15) ** 2) / 0.0012))
    const face = eyes({ scale: 1.08, hl: false }) + sparkle(78, 100, 6, C.white) + sparkle(118, 100, 6, C.white) + blush(1.1) + mouth('smile', { w: 12, y: 130 })
    const hs = 17 * (1 + 0.3 * pulse)
    const beatLines = pulse > 0.4
      ? [[-1, -1], [1, -1], [1.2, 0.3]].map(([x, y]) => `<line x1="${f(160 + x * 26)}" y1="${f(26 + y * 22)}" x2="${f(160 + x * 34)}" y2="${f(26 + y * 30)}" stroke="#FF4D7D" stroke-width="4" stroke-linecap="round"/>`).join('')
      : ''
    return canvas([
      body({ dy: -3 * pulse, ball: blink(t, 4) ? '#FF7FA8' : C.orange, face }),
      heart(160, 28, hs),
      say(62, 10 - 2 * pulse, '두근두근', 24, '#FF4D7D', -6),
      beatLines,
    ])
  },
}

// 13 신남 — 점프, 별 이펙트
const excited = {
  id: '13',
  frame(t) {
    const hop = Math.abs(Math.sin(TAU * t))
    const land = 1 - hop
    const face = lineEyes('^') + mouth('grin', { w: 22 }) + blush(0.8)
    const arms = arm('L', { a: 158 + 14 * hop, len: 30 }) + arm('R', { a: 158 + 14 * hop, len: 30 })
    const stars = [[18, 34, 0], [182, 30, 0.25], [190, 128, 0.5], [10, 126, 0.75]].map(([x, y, off]) => {
      const tw = 0.5 + 0.5 * Math.sin(TAU * (t * 2 + off))
      return star(x, y, 6 + 6 * tw, C.yellow, 72 * t)
    }).join('')
    return canvas([
      body({ dy: -20 * hop, sy: 1 - 0.08 * land ** 6, by: -6 - 6 * hop, ball: '#FFE14D', ballGlow: hop, face, back: arms }),
      stars,
    ])
  },
}

// 14 평온 — 눈 감고 미소, 느린 호흡
const calm = {
  id: '14',
  frame(t) {
    const br = Math.sin(TAU * t)
    const face = lineEyes('u') + mouth('smile', { w: 14, y: 128 }) + blush(0.7, '#FF9DBF')
    return canvas([
      body({ dy: -2 * br, sy: 1 + 0.025 * br, bx: 4 * Math.sin(TAU * t + 1), by: -6, face }),
      sparkle(28, 52, 4 + 3 * (0.5 + 0.5 * Math.sin(TAU * t)), C.yellow),
      sparkle(176, 40, 4 + 3 * (0.5 - 0.5 * Math.sin(TAU * t)), C.yellow),
    ])
  },
}

// 15 지루함 — 눈 반쯤, 하품
const bored = {
  id: '15',
  frame(t) {
    const y = env(t, 0.32, 0.48, 0.66, 0.8)
    let face
    if (y < 0.15) face = eyes({ lid: 0.5, lx: -5, hl: false }) + mouth('flat', { w: 12, x: 96 })
    else face = lineEyes('>', { width: 6 }) + mouth('yawn', { open: y })
    const tear = y > 0.6 ? drop(140, 104, 4) : ''
    return canvas([
      body({ dy: 2 - 4 * y, sy: 1 + 0.04 * y, by: -6 - 6 * y, face, front: tear }),
      say(100, 4, '심심해..', 26, '#A3A9B6'),
    ])
  },
}

// 16 졸림 — 눈 깜빡(스르르 감김), Zzz
const sleepy = {
  id: '16',
  frame(t) {
    const e = t < 0.68 ? easeInOut(span(t, 0, 0.68)) : 1 - easeInOut(span(t, 0.68, 0.76))
    const lid = 0.25 + 0.7 * e
    const face = lid > 0.85 ? lineEyes('u', { ly: 4 }) : eyes({ lid, hl: false, ly: 2 })
    const zs = [0, 1, 2].map((k) => {
      const p = (t + k / 3) % 1
      const s = (16 + 12 * p) * life(p, 0.12, 0.8)
      return s > 3 ? label(146 + 34 * p, 54 - 70 * p, 'Z', s, { fill: C.white, rot: -10 }) : ''
    }).join('')
    return canvas([
      body({ rot: 5 * e, dy: 4 * e, ball: '#C98A3E', by: -6 + 4 * e, bx: 6 * e, face }),
      zs,
    ])
  },
}

export default [joy, sad, gloomy, angry, annoyed, surprised, scared, disgust, flustered, shy, love, flutter, excited, calm, bored, sleepy]
