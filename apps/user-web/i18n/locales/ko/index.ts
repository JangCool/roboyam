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
import auth from './auth';
import board from './board';
import business from './business';
import comment from './comment';
import commerce from './commerce';
import dev from './dev';
import error from './error';
import identity from './identity';
import install from './install';
import login from './login';
import mail from './mail';
import me from './me';
import menu from './menu';
import notification from './notification';
import point from './point';
import post from './post';
import profile from './profile';
import prompt from './prompt';
import provider from './provider';
import restriction from './restriction';
import sanction from './sanction';
import settings from './settings';
import shell from './shell';
import signup from './signup';
import site from './site';
import siteTerms from './siteTerms';
import social from './social';

export default {
    account,
    auth,
    board,
    business,
    comment,
    commerce,
    dev,
    error,
    identity,
    install,
    login,
    mail,
    me,
    menu,
    notification,
    point,
    post,
    profile,
    prompt,
    provider,
    restriction,
    sanction,
    settings,
    shell,
    signup,
    site,
    siteTerms,
    social,
};
