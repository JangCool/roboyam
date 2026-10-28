/**
 * Collects the English messages from the per-feature files (hamonica3 D-89 — split by feature instead of one file per language).
 *
 * **When you add a feature file, add a line here** (the `import` and the `export default` list below). Why not auto-collect with `import.meta.glob`:
 * this config is bundled by Nitro (server) as well as Vite (client), and `import.meta.glob` is Vite-only, so it stays untransformed on the server
 * and every request becomes a 500 (`Nuxt I18n server context has not been set up yet`).
 * The key is `fileName.what` — the file name is the first part of the key.
 *
 * ⚠ **Must be the same list as `ko/index.ts`.** A feature present in only one language shows in Korean (the fallback) in the other.
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
import groupEvent from './groupEvent';
import groupManager from './groupManager';
import groupVisibility from './groupVisibility';
import home from './home';
import install from './install';
import login from './login';
import mail from './mail';
import me from './me';
import menu from './menu';
import nicknameImage from './nicknameImage';
import notification from './notification';
import onboarding from './onboarding';
import password from './password';
import payment from './payment';
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
    groupEvent,
    groupManager,
    groupVisibility,
    home,
    install,
    login,
    mail,
    me,
    menu,
    nicknameImage,
    notification,
    onboarding,
    password,
    payment,
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
