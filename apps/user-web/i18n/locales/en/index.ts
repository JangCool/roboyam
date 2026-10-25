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
