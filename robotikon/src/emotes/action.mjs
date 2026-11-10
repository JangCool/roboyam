// 행동 8종(17~24). 팔은 귀에서 나온다(parts.arm). 19 엄지척은 대표 디시콘이라 t=0 장면이 대표 PNG 가 된다.
import {
  C, TAU, V, arm, armsBehind, body, canvas, drop, easeInOut, eyes, heart, label,
  lineEyes, mouth, say, sparkle, span, star,
} from '../parts.mjs'

const env = (t, a, b, c, d) => easeInOut(span(t, a, b)) * (1 - easeInOut(span(t, c, d)))

// 17 인사 — 손 흔들기
const hello = {
  id: '17',
  frame(t) {
    const wave = Math.sin(TAU * t * 2)
    const face = lineEyes('^') + mouth('grin', { w: 18 })
    const arms = arm('R', { a: 164 + 12 * wave, len: 32, r: 12 }) + arm('L', { a: 18, len: 26 })
    const swish = [0, 1].map((k) => `<path d="M${186 + k * 7} ${40 + k * 4} q8 10 0 20" fill="none" stroke="${C.navy}" stroke-width="3.5" stroke-linecap="round"/>`).join('')
    return canvas([
      body({ dy: -2 * Math.abs(wave), rot: -2 * wave, face, back: arms }),
      Math.abs(wave) > 0.6 ? swish : '',
      say(58, 14, '안녕!', 30, C.yellow, -8 - 3 * wave),
    ])
  },
}

// 18 감사 — 꾸벅(가볍게), 두 손 모음, 하트 퐁
const thanks = {
  id: '18',
  frame(t) {
    const k = env(t, 0.05, 0.22, 0.55, 0.72)
    const face = lineEyes('^', { ly: 6 * k }) + mouth('smile', { w: 14, y: 128 + 3 * k })
    const hands = armsBehind([94, 158], [106, 158], 10)
    const hp = span(t, 0.6, 0.95)
    const pop = hp > 0 && hp < 1 ? heart(174, 56 - 24 * hp, 10 * Math.sin(Math.PI * hp)) : ''
    return canvas([
      body({ dy: 3 * k, sy: 1 - 0.14 * k, by: -6 + 12 * k, face, back: hands.back, front: hands.front }),
      pop,
      say(100, 6 + 3 * k, '고마워!', 28, '#FF7FA8'),
    ])
  },
}

// 19 엄지척 — "머양 최고얌!" (대표 디시콘)
const thumbsUp = {
  id: '19',
  frame(t) {
    const pump = Math.sin(TAU * t * 2)
    // 왼눈만 ^ 로 윙크(여 버전 속눈썹까지 가림)
    const wink = eyes() + `<rect x="52" y="80" width="44" height="44" fill="${C.navy}"/>` + lineEyes('^', { only: 0 })
    const face = wink + mouth('grin', { w: 18 })
    const thumb = arm('R', { a: 135 + 6 * pump, len: 20, hand: 'thumb', r: 14, thumbRot: -8 * pump })
    const tw = 0.5 + 0.5 * Math.sin(TAU * t * 4)
    return canvas([`<g transform="translate(100 112) scale(0.9) translate(-100 -112)">`
      + body({ dy: 4, face, back: thumb })
      + say(100, -10 - 3 * Math.abs(pump), [['머양 ', C.yellow], ['최고얌!', V.accent]], 28, C.yellow, -4 + 2 * pump)
      + sparkle(206, 66, 5 + 6 * tw, C.yellow)
      + sparkle(186, 46, 4 + 5 * (1 - tw), C.yellow)
      + '</g>'])
  },
}

// 20 사과 — 90도 숙임(깊게), 땀
const sorry = {
  id: '20',
  frame(t) {
    const k = env(t, 0.04, 0.22, 0.7, 0.88)
    const face = eyes({ lid: 0.35, tilt: -0.35, ly: 7 * k, hl: false }) + mouth('frown', { w: 14, y: 129 + 3 * k })
    const arms = arm('L', { a: 6, len: 30 }) + arm('R', { a: 6, len: 30 })
    const p = span(t, 0.22, 0.7)
    const sweat = k > 0.5 ? drop(158, 70 + 18 * p, 6) : ''
    return canvas([
      body({ dy: 6 * k, sy: 1 - 0.26 * k, by: -6 + 18 * k, face, back: arms, front: sweat }),
      say(100, 6 + 4 * k, '미안해..', 28, '#9FB4FF'),
    ])
  },
}

// 21 응원 — 주먹 쥐고 흔들기(파이팅)
const cheer = {
  id: '21',
  frame(t) {
    const pump = Math.abs(Math.sin(TAU * t * 2))
    const face = eyes({ tilt: 0.3, lid: 0.05 }) + mouth('grin', { w: 20 })
    const arms = arm('R', { a: 155 + 20 * pump, len: 30, r: 11 }) + arm('L', { a: 15, len: 24, r: 11 })
    const burst = pump > 0.8
      ? [-40, 0, 40].map((d) => {
        const a = ((-90 + d) * Math.PI) / 180
        return `<line x1="${(178 + Math.cos(a) * 18).toFixed(1)}" y1="${(84 + Math.sin(a) * 18).toFixed(1)}" x2="${(178 + Math.cos(a) * 28).toFixed(1)}" y2="${(84 + Math.sin(a) * 28).toFixed(1)}" stroke="${C.yellow}" stroke-width="5" stroke-linecap="round"/>`
      }).join('')
      : ''
    return canvas([
      body({ dy: -4 * pump, ball: '#FFE14D', face, back: arms }),
      burst,
      star(26, 40, 8 + 4 * pump, C.yellow, 40 * t),
      say(100, -1 - 2 * pump, '파이팅!', 28, '#FF4D4D', -4),
    ])
  },
}

// 22 박수 — 짝짝
const clap = {
  id: '22',
  frame(t) {
    const gap = 16 * Math.abs(Math.cos(TAU * t * 2))
    const face = lineEyes('^') + mouth('grin', { w: 20, y: 125 })
    const hands = armsBehind([88 - gap, 146], [112 + gap, 146], 11)
    const hit = gap < 4
      ? [-1, 1].map((d) => `<path d="M${100 + d * 24} 160 l${d * 10} 6 M${100 + d * 20} 172 l${d * 9} 10 M${100 + d * 10} 176 l${d * 3} 11" stroke="${C.yellow}" stroke-width="4.5" stroke-linecap="round"/>`).join('')
      : ''
    return canvas([
      body({ dy: -3 * (1 - gap / 16), face, back: hands.back, front: hands.front + hit }),
      say(100, 6 - 2 * (1 - gap / 16), '짝짝짝!', 28, C.yellow),
    ])
  },
}

// 23 거절 — 팔로 X, 도리도리, "싫어!"
const refuse = {
  id: '23',
  frame(t) {
    const shake = Math.sin(TAU * t * 3)
    const face = eyes({ lid: 0.35, tilt: 0.25, lx: 4 * shake, hl: false }) + mouth('frown', { w: 14, y: 126 })
    const x = arm('L', { to: [132, 140], r: 12 }) + arm('R', { to: [68, 140], r: 12 })
    return canvas([
      body({ dx: 5 * shake, rot: 3 * shake, face, front: x }),
      say(100 + 7 * shake, 3, '싫어!', 32, '#FF4D4D', -4 * shake),
    ])
  },
}

// 24 질문 — 물음표 띄우고 고개 갸웃
const question = {
  id: '24',
  frame(t) {
    const tilt = env(t, 0, 0.2, 0.8, 1)
    const bob = Math.sin(TAU * t * 2)
    const face = eyes({ lx: -3, ly: -3 * tilt }) + mouth('o', { open: 0.3, x: 104, y: 129 })
    const scratch = arm('L', { to: [44 + 4 * Math.sin(TAU * t * 4), 62], r: 10 })
    return canvas([
      body({ rot: -9 * tilt, face, front: scratch }),
      label(158, 52 + 4 * bob, '?', 50, { rot: 12, width: 5 }),
      say(58, 8 + 2 * bob, '뭐지?', 26, C.yellow, -8),
    ])
  },
}

export default [hello, thanks, thumbsUp, sorry, cheer, clap, refuse, question]
