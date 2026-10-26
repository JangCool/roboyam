/**
 * 한국어 문구를 업무별 파일에서 모은다 (hamonica3 D-89 — 언어별 한 파일에 모두 넣지 않고 업무별로 나눈다).
 *
 * **업무 파일을 늘리면 여기에 한 줄 더한다**(`import` 와 아래 `export default` 목록). `import.meta.glob` 으로 자동 수집하지 않는 이유:
 * 이 설정 파일은 Vite(클라이언트)뿐 아니라 Nitro(서버)에서도 번들되는데, `import.meta.glob` 은 Vite 전용이라 서버에서는 변환되지 않고 그대로 남아
 * 모든 요청이 500(`Nuxt I18n server context has not been set up yet`)이 된다.
 * 키는 `파일이름.무엇` 이다 — 파일 이름이 키의 첫 마디.
 *
 * ⚠ **`en/index.ts` 와 같은 목록이어야 한다.** 한쪽에만 있는 업무는 그 언어에서 한국어(폴백)로 보인다.
 */
import account from './account';
import adult from './adult';
import auth from './auth';
import avatar from './avatar';
import block from './block';
import board from './board';
import business from './business';
import comment from './comment';
import commerce from './commerce';
import dev from './dev';
import dm from './dm';
import error from './error';
import identity from './identity';
import feed from './feed';
import grade from './grade';
import group from './group';
import install from './install';
import login from './login';
import mail from './mail';
import me from './me';
import menu from './menu';
import nicknameImage from './nicknameImage';
import notification from './notification';
import onboarding from './onboarding';
import password from './password';
import point from './point';
import pointShop from './pointShop';
import poll from './poll';
import post from './post';
import profile from './profile';
import prompt from './prompt';
import provider from './provider';
import restriction from './restriction';
import sanction from './sanction';
import search from './search';
import settings from './settings';
import shell from './shell';
import signup from './signup';
import site from './site';
import siteTerms from './siteTerms';
import social from './social';
import verify from './verify';
import view from './view';
import terms from './terms';

export default {
    account,
    adult,
    auth,
    avatar,
    block,
    board,
    business,
    comment,
    commerce,
    dev,
    dm,
    error,
    identity,
    feed,
    grade,
    group,
    install,
    login,
    mail,
    me,
    menu,
    nicknameImage,
    notification,
    onboarding,
    password,
    point,
    pointShop,
    poll,
    post,
    profile,
    prompt,
    provider,
    restriction,
    sanction,
    search,
    settings,
    shell,
    signup,
    site,
    siteTerms,
    social,
    verify,
    view,
    terms,
};
