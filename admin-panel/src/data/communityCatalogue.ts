/**
 * Verdicts for the 86 providers of `https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/plugins.json`, produced by `extensions/tools/community_catalogue.py`
 * (source: `docs/community/phisher98-catalogue.json`). Do not edit by hand - regenerate:
 *
 *   python3 extensions/tools/community_catalogue.py <raw plugins.json url> \
 *     --out-json docs/community/phisher98-catalogue.json && python3 extensions/tools/catalogue_to_panel.py
 *
 * Verdicts: READY / READY-DOWN / RESOURCES / EMBED-REFS / LOADS-PARTIAL (needs an embed extractor the
 * host lacks) / HOST-COMPAT (loads, but calls types from CloudStream's *app* module) /
 * NEEDS-ENTRY-SHIM (entry class extends the app-only `plugins.Plugin`, so it can never load here) /
 * UNKNOWN / UNREADABLE / FETCH-FAIL.
 */

export interface CommunityVerdict {
  provider: string;
  verdict: string | null;
  why: string;
  version: number | null;
  /** CloudStream's own label: 0 down, 1 ok, 2 slow, 3 beta. */
  repoStatus: number | null;
  language: string | null;
  pluginClassName: string | null;
  dex: string | null;
  extendsPlugin: boolean;
  appOnly: string[];
  embeds: string[];
  hosts: string[];
  upstream: string | null;
  /** sha256 hex of the published file as of this scan (the panel stores the same value). */
  fileHash: string | null;
}

export const COMMUNITY_VERDICTS: Record<string, CommunityVerdict> = {
 "allmovielandprovider": {
  "provider": "AllMovieLandProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 25,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.phisher98.AllMovieLandProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "mapi.elochkaigolochla.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "76967d2992879b09511d35b0e1e44ec46610ee494516fb851d22dadab7775d15"
 },
 "allwish": {
  "provider": "AllWish",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 18,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.allwish.AllWishPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "all-wish.me",
   "megaplay.buzz",
   "omg10.com",
   "player.sgsgsgsr.site"
  ],
  "upstream": null,
  "fileHash": "aad6c98914cba66fc6fd45788b5d6efbbfa76c22b14b91e35c8a806785cf967e"
 },
 "anichi": {
  "provider": "Anichi",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 27,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.Anichi.AnichiPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidStack"
  ],
  "hosts": [
   "api.allanime.day",
   "ok.ru",
   "allanime.day",
   "watchanime.uns.bio"
  ],
  "upstream": null,
  "fileHash": "9ff65e6380ad99687e0d3c1185466958d0249ec9d678e5edbf5ae9b19664661b"
 },
 "anidb": {
  "provider": "AniDb",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 15,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.anidb.AniDbPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "anidb.app",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "9d7bc29db53f13b735d66b81d49b87f10f0aa532a19ad78bf3f14a76d53a2a18"
 },
 "anikage": {
  "provider": "Anikage",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 8,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.anikage.AnikagePlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "anikage.cc",
   "og.bakayaro.live",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "f0c88a4316bfd05e82026dfb63d965434ee632dd218283e85513d8e2bc1fd6f0"
 },
 "anikoto": {
  "provider": "AniKoto",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 4,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.anikoto.AnikotoPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "anikototv.to",
   "megaplay.buzz",
   "mewcdn.online",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "61d31bde3dc3c084ecf527fbb7f1543e0810bc81c703fec4a9e30ed124cbd04f"
 },
 "anilight": {
  "provider": "Anilight",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 3,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.anilight.AnilightPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "api.anilight.live",
   "anilight.live",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "4a615a623d68307e829db9a09e17d3ce1e9e1a36de1c3e2bbef538a855e2bc11"
 },
 "animeav1": {
  "provider": "Animeav1",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 9,
  "repoStatus": 1,
  "language": "mx",
  "pluginClassName": "com.Animeav1.Animeav1Provider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.VidStack"
  ],
  "hosts": [
   "animeav1.com",
   "animeav1.uns.bio",
   "cdn.animeav1.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "13f6735390ce0f54fcd0c5964f6564e62ddc5c4711165bdb2807183d9a3d2f5d"
 },
 "animecloud": {
  "provider": "AnimeCloud",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 11,
  "repoStatus": 1,
  "language": "de",
  "pluginClassName": "com.animecloud.AnimecloudProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "fireani.me",
   "luluvdo.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "d5163aef20264d413577ad972017c1c078bfb0b222b8275cad65348e90582828"
 },
 "animedekhoprovider": {
  "provider": "AnimeDekhoProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 70,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.phisher98.AnimeDekhoPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.FileMoon",
   "com.lagradost.cloudstream3.extractors.FilemoonV2",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.Krakenfiles",
   "com.lagradost.cloudstream3.extractors.StreamTape",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor",
   "com.lagradost.cloudstream3.extractors.Vidmoly",
   "com.lagradost.cloudstream3.extractors.Voe"
  ],
  "hosts": [
   "onepace.me",
   "playhydrax.com",
   "abyssplayer.com",
   "animedekho.app"
  ],
  "upstream": null,
  "fileHash": "75f365b027ea358a04683fa4d7b0588a21b118cb80424f404ce3be0597d29e24"
 },
 "animedubhindi": {
  "provider": "Animedubhindi",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 8,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.animedubhindi.AnimedubhindiProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.FileMoon"
  ],
  "hosts": [
   "api.gofile.io",
   "gofile.io",
   "hubcloud.foo",
   "new.gdflix"
  ],
  "upstream": null,
  "fileHash": "edf3c7c32ad325460c88677c12f3c4f53e729e593c90fb4807aafe94c6750b91"
 },
 "animekhor": {
  "provider": "Animekhor",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 13,
  "repoStatus": 1,
  "language": "zh",
  "pluginClassName": "com.Animekhor.AnimenosubProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Dailymotion",
   "com.lagradost.cloudstream3.extractors.EmturbovidExtractor",
   "com.lagradost.cloudstream3.extractors.Mp4Upload",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor"
  ],
  "hosts": [
   "animekhor.org",
   "animekhor.p2pstream.vip",
   "donghuaworld.com",
   "embedwish.com"
  ],
  "upstream": null,
  "fileHash": "5781e4e1a8eed09fc680f73372889410f7fa53de25e7c603a6cdbb23eaf1b1d6"
 },
 "animenosub": {
  "provider": "Animenosub",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 11,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.Animenosub.AnimenosubProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.StreamSB",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor"
  ],
  "hosts": [
   "animenosub.to",
   "animenosub.upn.one",
   "filemoon.sx",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "5d6ba70cecf6e4796cbf6b991836fc526b156defdf240169c9b16de3704b84af"
 },
 "animepahe": {
  "provider": "AnimePahe",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 39,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.AnimePaheProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "kwik.cx",
   "animepahe.com",
   "animepahe.org",
   "animepahe.pw"
  ],
  "upstream": null,
  "fileHash": "f39e2fc245042e933ec91da01e74b8f105e7ba80e6e4b6706c8f6aaaa754f592"
 },
 "animesalt": {
  "provider": "Animesalt",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 15,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.phisher98.AnimesaltProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Filesim"
  ],
  "hosts": [
   "playhydrax.com",
   "abyssplayer.com",
   "animesalt.cx",
   "as-cdn26.top"
  ],
  "upstream": null,
  "fileHash": "7c7823d355513b7cb32d37bfc7d7a0784b2c8dabf18c9daf59cdb22cc844e560"
 },
 "animexin": {
  "provider": "Animexin",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 15,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.Animexin.AnimexinPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Dailymotion",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.StreamSB",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor"
  ],
  "hosts": [
   "animexin.dev",
   "filemoon.sx",
   "omg10.com",
   "vtbe.to"
  ],
  "upstream": null,
  "fileHash": "b49173260c50f75cbb82258fc706958fe615a7ad723c2a76fa536cb9c69a363f"
 },
 "anineko": {
  "provider": "Anineko",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 6,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.anineko.AninekoPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.DoodLaExtractor",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor"
  ],
  "hosts": [
   "anineko.to",
   "bibiemb.xyz",
   "kitsu.io",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "ac5ca447945cff89abfbcfc082b4ec05676026f8a626df8e652d02c47aaa0be0"
 },
 "anisnatch": {
  "provider": "AniSnatch",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 1,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.AniSnatch.AniSnatchPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "megaplay.buzz",
   "anisnatch.to",
   "kitsu.io",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "aa9795db9c58e436c5b658ef5dbdf93817008618c37ca4ea162996ecb2142e17"
 },
 "anivortex": {
  "provider": "AniVortex",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 10,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.AniVortex.AniVortexPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "api.anivortex.in",
   "omg10.com",
   "www.youtube.com"
  ],
  "upstream": null,
  "fileHash": "e89294c3781885009afcb680f12e8c0d030b585562f4cef019915e9b26f29f9e"
 },
 "aniworld": {
  "provider": "Aniworld",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 14,
  "repoStatus": 1,
  "language": "de",
  "pluginClassName": "com.Aniworld.AniworldPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.DoodLaExtractor",
   "com.lagradost.cloudstream3.extractors.Vidmoly"
  ],
  "hosts": [
   "serienstream.to",
   "aniworld.to",
   "byse.sx",
   "filemoon.to"
  ],
  "upstream": null,
  "fileHash": "296db4936d945bb373e590b2e3bfc881afb7aefde353ebed7f9a49481f65f6e9"
 },
 "anizone": {
  "provider": "Anizone",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 8,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.ycngmn.AnizonePlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "anizone.to",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "f2dd8168d874a5f26167e0cab86b1295b35c359a88c65a0e88fba144f830983b"
 },
 "banglaplex": {
  "provider": "BanglaPlex",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 8,
  "repoStatus": 1,
  "language": "bn",
  "pluginClassName": "com.BanglaPlex.BanglaPlexProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.StreamTape",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.Vtbe"
  ],
  "hosts": [
   "banglaplex.click",
   "bpx.rpmvid.site",
   "hglink.to",
   "iplayerhls.com"
  ],
  "upstream": null,
  "fileHash": "f087d0fa0a61b847854e4155fc70575d7cce7b7ee95a596f57ed80088379fe7d"
 },
 "chikianimation": {
  "provider": "Chikianimation",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 2,
  "repoStatus": 1,
  "language": "zh",
  "pluginClassName": "com.Chikianimation.ChikianimationPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Dailymotion",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.StreamTape"
  ],
  "hosts": [
   "chikianimation.com",
   "galaxydonghua.xyz",
   "ghbrisk.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "d20d52b6a4ef93739ca6430048773a08080329e854d5be1a2c792cb3351bdc51"
 },
 "cinefreak": {
  "provider": "Cinefreak",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 14,
  "repoStatus": 1,
  "language": "bn",
  "pluginClassName": "com.cinefreak.CinefreakPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.PixelDrain",
   "com.lagradost.cloudstream3.extractors.StreamTape"
  ],
  "hosts": [
   "cinefreak.nl",
   "hubcloud.foo",
   "hubcloud.one",
   "hubdrive.space"
  ],
  "upstream": null,
  "fileHash": "65bacc61c1da86e3eb1345f670e65f887eb51feee7a5f06e873efa2f99c7378e"
 },
 "cinemacity": {
  "provider": "Cinemacity",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 26,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.Cinemacity.CinemacityPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "cinemacity.cc",
   "v3-cinemeta.strem.io",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "7a5399da11d43768a8e5b632b9e2c712a5cc35207296a15d2a0e85bd283a3f9d"
 },
 "coflix": {
  "provider": "Coflix",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 19,
  "repoStatus": 1,
  "language": "fr",
  "pluginClassName": "com.Coflix.CoflixProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.LuluStream",
   "com.lagradost.cloudstream3.extractors.StreamSB",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor",
   "com.lagradost.cloudstream3.extractors.Vidmoly",
   "com.lagradost.cloudstream3.extractors.Voe"
  ],
  "hosts": [
   "coflix.esq",
   "coflix.upn.one",
   "darkibox.com",
   "filemoon.sx"
  ],
  "upstream": null,
  "fileHash": "01029e13ba1c8b76b0469039f792eba7c54990e1d389e72f0a0e33fb63a376a9"
 },
 "comix": {
  "provider": "Comix",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 2,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.comix.ComixPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "comix.to",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "0fb13f8fbba6c2e4b2eb3e12d1a096535e80c06ad299109cc8592ffa7b4061ea"
 },
 "desicinemas": {
  "provider": "Desicinemas",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 16,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Desicinemas.DesicinemasPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "desicinemas.to",
   "flow.tvlogy.to",
   "tellygossips.net",
   "desicinemas.phisherdesicinema.workers.dev"
  ],
  "upstream": null,
  "fileHash": "2a06c43432556c2bd2afaea32c162da8c165926e6926a765eec1b56810b108d5"
 },
 "donghuastream": {
  "provider": "Donghuastream",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 22,
  "repoStatus": 1,
  "language": "zh",
  "pluginClassName": "com.Donghuastream.DonghuastreamProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Dailymotion",
   "com.lagradost.cloudstream3.extractors.Geodailymotion",
   "com.lagradost.cloudstream3.extractors.OkRuHTTP",
   "com.lagradost.cloudstream3.extractors.OkRuSSL",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.helper.AesHelper"
  ],
  "hosts": [
   "donghuastream.org",
   "filemoon.sx",
   "omg10.com",
   "play.streamplay.co.in"
  ],
  "upstream": null,
  "fileHash": "1c499c5501de45596af37a9b2a8675b29b3c70d89057e674c4f2321b3ea8e369"
 },
 "dorabash": {
  "provider": "DoraBash",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 13,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.DoraBash.DoraBashProvider",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.ByseVepoin",
   "com.lagradost.cloudstream3.extractors.FileMoon",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.StreamSB",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor"
  ],
  "hosts": [
   "dorabash.in",
   "playhydrax.com",
   "abyssplayer.com",
   "bysevepoin.com"
  ],
  "upstream": null,
  "fileHash": "bc96643f38535822fcc6a64504ab2dbad6a3d6e158e7e9ace3d9439c392d8416"
 },
 "dudefilms": {
  "provider": "DudeFilms",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 12,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.dudefilms.DudefilmsPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.PixelDrain",
   "com.lagradost.cloudstream3.extractors.VidStack"
  ],
  "hosts": [
   "api.gofile.io",
   "dudefilms.sarl",
   "gofile.io",
   "hubcloud.foo"
  ],
  "upstream": null,
  "fileHash": "0da5a3e44c19fc865677b6ef472b1100f0cfe16050eba4f5fce5d5e2b6be9871"
 },
 "fibwatch": {
  "provider": "Fibwatch",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 11,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Fibwatch.FibwatchPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "fibdrama.top",
   "fibtoon.top",
   "fibwatch.top",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "8d329f2e8a8211b5e4037c6896a8df0c193081e1cf58fe8a879ff097393ca4c9"
 },
 "fivemovierulz": {
  "provider": "Fivemovierulz",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 8,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.darkdemon.FivemovierulzPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor"
  ],
  "hosts": [
   "5movierulz.gripe",
   "hglink.to",
   "mivalyo.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "e4ed2c944147da0380fbee2cea9881e8db4c9760e56537c84c21c05ceb8099fd"
 },
 "fourkhdhub": {
  "provider": "FourKHDHub",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 39,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.fourKHDHub.FourKHDHubProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.PixelDrain",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack"
  ],
  "hosts": [
   "api.simkl.com",
   "4khdhub.dad",
   "hubcloud.foo",
   "hubdrive.space"
  ],
  "upstream": null,
  "fileHash": "63f1823a30e0f23cb267a630d351d9b9f597cbd762acafc9ca7892d1d93191bb"
 },
 "goldenaudiobooks": {
  "provider": "GoldenAudiobooks",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 1,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.goldenaudiobooks.GoldenAudiobooksPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "goldenaudiobooks.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "78655d3f601840b916a7b165db87278cf3a74f0c58dcbe14cedf548a3a1dd738"
 },
 "goojara": {
  "provider": "Goojara",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.Goojara.GoojaraProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.VidhideExtractor"
  ],
  "hosts": [
   "omg10.com",
   "stre4mpay.one",
   "streamplay.to",
   "thumbs.dreamstime.com"
  ],
  "upstream": null,
  "fileHash": "b04d1fa521daa69ed78dab50b39b11449b5d8204a213261f23376fa45b0d1a7c"
 },
 "hdhub4u": {
  "provider": "HDhub4u",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 55,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.hdhub4u.HDhub4uPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.PixelDrain",
   "com.lagradost.cloudstream3.extractors.StreamTape",
   "com.lagradost.cloudstream3.extractors.VidHidePro"
  ],
  "hosts": [
   "hdhub4u.glass",
   "hdstream4u.com",
   "hubcloud.foo",
   "hubdrive.space"
  ],
  "upstream": null,
  "fileHash": "a8e13d203258cef18070700428dbf43058392b6be1c5de477ac2031d71c4256e"
 },
 "hdmovie2": {
  "provider": "Hdmovie2",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Hdmovie2.Hdmovie2Plugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "molop.art",
   "playhydrax.com",
   "enc-dec.app",
   "exxample.com"
  ],
  "upstream": null,
  "fileHash": "f45ae1826a3ce6f5bdd315072e5ed1ef7e789655b721983d745dd83237418e76"
 },
 "hianime": {
  "provider": "HiAnime",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 1,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.HiAnime.HiAnimeProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "hianime.at",
   "megaplay.buzz",
   "omg10.com",
   "vidtube.site"
  ],
  "upstream": null,
  "fileHash": "1609c760b708dc134a4af28731061986d718c12e0e52bf563b112e9a728cfa38"
 },
 "hindmoviez": {
  "provider": "Hindmoviez",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 17,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.hindmoviez.HindmoviezPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "gdshine.org",
   "hindmoviez.icu",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "5e587d4af35ec2b393ddc4925e4695b84215248aaecce288df18fdaef78b64c3"
 },
 "idlixprovider": {
  "provider": "IdlixProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 16,
  "repoStatus": 1,
  "language": "id",
  "pluginClassName": "com.idlix.IdlixProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "jeniusplay.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "f0fdab0ac98f91bb3645cddadf13422a64ad541c5923503f3b043d1e27fff5db"
 },
 "iptvplayer": {
  "provider": "IPTVPlayer",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 9,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.phisher98.IPTVPlayerPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "8f7b910c80a4dee386059054e5897409550a61284d28d69f27ff2dc1c0f9dadc"
 },
 "istreamflare": {
  "provider": "IStreamFlare",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 6,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.IStreamFlare.IStreamFlareProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "v3-cinemeta.strem.io",
   "exposeworld.art",
   "guten.hippitunes.pro",
   "iasbase.net"
  ],
  "upstream": null,
  "fileHash": "3374e3f5d4b813010ee215c7b064d25f15cdb93d2e4ee497f50928d25dde8f72"
 },
 "jellyfin": {
  "provider": "Jellyfin",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 6,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.JellyfinPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "cf5bdf1a6879492c0c23efc2ba1bd5921929b662bc261adf28aba4ff87dc0346"
 },
 "kartoons": {
  "provider": "Kartoons",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 4,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Kartoons.KartoonsPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "kartoons.me",
   "api.kartoons.me",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "4099269afb0c81c9fee1930432e8f8eb8bb7a517c392ddf4479f13a266c84f91"
 },
 "kickassanime": {
  "provider": "Kickassanime",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 27,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.kickassanime.KickassanimePlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "kaa.lt",
   "omg10.com",
   "plyr.link"
  ],
  "upstream": null,
  "fileHash": "21d8f4eb2e28c631c399b43813b4802f066c735f696950c825434e6566fbd184"
 },
 "kisskhprovider": {
  "provider": "KisskhProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 22,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.KisskhProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "kisskh.is",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "02d2b739ba68f6ab8b61e56d191d3f01c824d8aff1a71157878682fd17ee1289"
 },
 "latanime": {
  "provider": "Latanime",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 5,
  "repoStatus": 1,
  "language": "mx",
  "pluginClassName": "com.latanime.LatanimeProvider",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.VidStack"
  ],
  "hosts": [
   "animeav1.uns.bio",
   "latanime.org",
   "omg10.com",
   "player.zilla-networks.com"
  ],
  "upstream": null,
  "fileHash": "00df7acefd83abf4275bc0be53ece8c59a7029d581b2a9e5d85763b0c369ff27"
 },
 "layarkacaprovider": {
  "provider": "LayarKacaProvider",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 10,
  "repoStatus": 1,
  "language": "id",
  "pluginClassName": "com.layarKacaProvider.LayarKacaProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.DoodLaExtractor",
   "com.lagradost.cloudstream3.extractors.EmturbovidExtractor",
   "com.lagradost.cloudstream3.extractors.FileMoon",
   "com.lagradost.cloudstream3.extractors.FilemoonV2",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.Mp4Upload",
   "com.lagradost.cloudstream3.extractors.StreamTape",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro6",
   "com.lagradost.cloudstream3.extractors.Voe"
  ],
  "hosts": [
   "playcdn.de",
   "playhydrax.com",
   "tv12.lk21official.cc",
   "abyssplayer.com"
  ],
  "upstream": null,
  "fileHash": "ab7071b282396eee837fd0021d0b4ea4777c066f2fb76a157050c296d25f91da"
 },
 "masstamilanprovider": {
  "provider": "MassTamilanProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 9,
  "repoStatus": 1,
  "language": "ta",
  "pluginClassName": "com.likdev256.MovieHUBProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "goodproxy.goodproxy.workers.dev",
   "masstamilan.dev",
   "miro.medium.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "2f55f5b5c6b9844c4d1d091394d90fccfe7d8018b1c8282763810c664d7763b8"
 },
 "megakino": {
  "provider": "Megakino",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 6,
  "repoStatus": 1,
  "language": "de",
  "pluginClassName": "com.Megakino.MegakinoProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Voe"
  ],
  "hosts": [
   "megakino5.org",
   "omg10.com",
   "watch.gxplayer.xyz"
  ],
  "upstream": null,
  "fileHash": "0c0911352a1c0255d173600d0c529414d3dcc1d2322f61b7291046a791c335c3"
 },
 "microtv": {
  "provider": "Microtv",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 3,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Microtv.MicrotvProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "api.gofile.io",
   "dldokan.online",
   "gofile.io",
   "new.microtv.st"
  ],
  "upstream": null,
  "fileHash": "5da4fe091844c7a82e4049bc43a31f915764d17227541c966b9b300c1d3750c7"
 },
 "movieboxprovider": {
  "provider": "MovieBoxProvider",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 31,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.MovieBox.MovieBoxProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "api3.aoneroom.com",
   "api4.aoneroom.com",
   "api4sg.aoneroom.com",
   "api5.aoneroom.com"
  ],
  "upstream": null,
  "fileHash": "7661180124d821b3bb24487769e08760d6e72cc3abff09551832b1bcba2157c0"
 },
 "movies4u": {
  "provider": "Movies4u",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 15,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.movies4u.Movies4uProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "api.gofile.io",
   "gofile.io",
   "hubcloud.foo",
   "m4ulinks.com"
  ],
  "upstream": null,
  "fileHash": "16376a2a4c555b4bb6e7aac98c272d48686de5684ed7feb240bc58c1e55f72ea"
 },
 "mplayerprovider": {
  "provider": "MPlayerProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 9,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.MPlayer.MPlayerPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "api.mxplayer.in",
   "d3sgzbosmwirao.cloudfront.net",
   "omg10.com",
   "qqcdnpictest.mxplay.com"
  ],
  "upstream": null,
  "fileHash": "ff35e8c4b9ccccbaae11f93aec4666ea6c2617834d45a587a8ab24fad6b46781"
 },
 "multimoviesprovider": {
  "provider": "MultiMoviesProvider",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 54,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.phisher98.MultiMoviesProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor"
  ],
  "hosts": [
   "db.speedracelight.com",
   "gofile.io",
   "multimovies.casa",
   "nxsha.space"
  ],
  "upstream": null,
  "fileHash": "3a7d8fddcd4b1fd8713e6f731218a216a81b50b291c9451369b483c010aa6161"
 },
 "netcinez": {
  "provider": "Netcinez",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "pt-br",
  "pluginClassName": "com.Netcinez.NetcinezProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "netcinez.si",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "97050334f64256322cbce641016c8efd327321f728406212a536e491cfeb1821"
 },
 "obejrzyjto": {
  "provider": "ObejrzyjTo",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 3,
  "repoStatus": 1,
  "language": "pl",
  "pluginClassName": "com.phisher98.ObejrzyjToPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "ultrastream.online",
   "vidneo.cc",
   "vidara.to",
   "bysekoze.com"
  ],
  "upstream": null,
  "fileHash": "a32283416da35e2ce8120eac3a1d8707615460eae718055b0b3eb3f94e7cf18c"
 },
 "ohli24": {
  "provider": "OHLI24",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 7,
  "repoStatus": 1,
  "language": "ko",
  "pluginClassName": "com.ohli24.OHLI24Plugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "ani.ohli24.com",
   "cdndania.com",
   "michealcdn.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "964fea6171c26d7ce60b4928acd7e6d5370bceeb592128dea76006c937929f33"
 },
 "onepace": {
  "provider": "OnePace",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 23,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.OnePacePlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.Vidmoly"
  ],
  "hosts": [
   "playhydrax.com",
   "abyssplayer.com",
   "animedekho.app",
   "animedekho.co"
  ],
  "upstream": null,
  "fileHash": "b70e90f391b3d72bf4f081cf34cf186fb23ec1f92cdf2d8d9931c1dba3fdca2c"
 },
 "onetouchtv": {
  "provider": "OneTouchTV",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.OneTouchTV.OneTouchTVPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "2f6c1a8523d6600aa476e7e03246bfab0460d2348f19cd032630f808d4aeaa3b"
 },
 "pencurimovie": {
  "provider": "Pencurimovie",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 8,
  "repoStatus": 1,
  "language": "id",
  "pluginClassName": "com.Pencurimovie.PencurimovieProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com",
   "ww21.pencurimovie.sbs"
  ],
  "upstream": null,
  "fileHash": "5ec9779acb4307d8ae2cb9bb97ded76caf8b2900dd0a3b4136311712e206ccef"
 },
 "pinoymoviepedia": {
  "provider": "Pinoymoviepedia",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "fil",
  "pluginClassName": "com.Pinoymoviepedia.PinoymoviepediaProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.MixDrop",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.Upstream",
   "com.lagradost.cloudstream3.extractors.VidHidePro3",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor",
   "com.lagradost.cloudstream3.extractors.Voe"
  ],
  "hosts": [
   "bluray7.com",
   "dood.wf",
   "ds2play.com",
   "luluvdo.store"
  ],
  "upstream": null,
  "fileHash": "397de2df0f619e97b1d11103fa8457fc954f4acfdf073cfffde932116f3a3303"
 },
 "piratexplay": {
  "provider": "Piratexplay",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.piratexplay.PiratexplayProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack"
  ],
  "hosts": [
   "playhydrax.com",
   "abyssplayer.com",
   "as-cdn21.top",
   "buzzheavier.com"
  ],
  "upstream": null,
  "fileHash": "457bb95745c7fcd033f5eac8800692198115632d9b7e927a32020e58dc65793c"
 },
 "pmsm": {
  "provider": "Pmsm",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 7,
  "repoStatus": 1,
  "language": "id",
  "pluginClassName": "com.pmsm.PmsmPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor"
  ],
  "hosts": [
   "dhtpre.com",
   "larhu.website",
   "netu.msmbot.club",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "17560eb374da85f4fb07016da46993dca5d46cb16c3ce9501e7f1a2e62e56b73"
 },
 "publicsportsiptv": {
  "provider": "PublicSportsIPTV",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.PublicSportsIPTV.PublicSportsIPTVProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com",
   "www.fancode.com"
  ],
  "upstream": null,
  "fileHash": "839f2757594b3e5e4e544134c105eef347b2b49497bc9b34a4917b92a064e293"
 },
 "quickiptv": {
  "provider": "QuickIPTV",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 8,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.QuickIPTVPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "embedme.top",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "2289a6c1659d0ac2bc88afd15eb464e137ab942c3c7665d764e08ac9883181ba"
 },
 "reanime": {
  "provider": "Reanime",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 2,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.ReanimePlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "enc-dec.app",
   "flixcloud.cc",
   "reanime.to",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "6e765dcfa260b0fcb828f2fc2a5af2d96d9ff4582348fd41f66f4d38866d50c7"
 },
 "ringz": {
  "provider": "RingZ",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 12,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.RingZ.RingZProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "10bbbdbec6cd5ff25ad136b197ebcda97e9daa3753d45d5827ee0f8d089e7b15"
 },
 "showbox": {
  "provider": "ShowBox",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 8,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.SuperStreamPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com",
   "opensubtitles-v3.strem.io",
   "v3-cinemeta.strem.io",
   "watchsomuch.tv"
  ],
  "upstream": null,
  "fileHash": "e2b7a88db996bed9f380cabc9d28867dde7ae4b7ad6c8d47732cfcbe2f5b7164"
 },
 "streamplay": {
  "provider": "StreamPlay",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 670,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.StreamPlayPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "MainActivity",
   "Plugin",
   "AccountManager",
   "AccountManager$Companion"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.DoodLaExtractor",
   "com.lagradost.cloudstream3.extractors.DoodYtExtractor",
   "com.lagradost.cloudstream3.extractors.FileMoon",
   "com.lagradost.cloudstream3.extractors.FilemoonV2",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.Jeniusplay",
   "com.lagradost.cloudstream3.extractors.MixDrop",
   "com.lagradost.cloudstream3.extractors.Mp4Upload",
   "com.lagradost.cloudstream3.extractors.OkRuHTTP",
   "com.lagradost.cloudstream3.extractors.OkRuSSL",
   "com.lagradost.cloudstream3.extractors.PixelDrain",
   "com.lagradost.cloudstream3.extractors.StreamSB",
   "com.lagradost.cloudstream3.extractors.StreamSB8",
   "com.lagradost.cloudstream3.extractors.StreamTape",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.Streamlare",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidHidePro6",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor",
   "com.lagradost.cloudstream3.extractors.Vidmolyme",
   "com.lagradost.cloudstream3.extractors.Voe",
   "com.lagradost.cloudstream3.extractors.helper.AesHelper"
  ],
  "hosts": [
   "enc-dec.app",
   "api3.aoneroom.com",
   "kisskh.nl",
   "anime-kitsu.strem.fun"
  ],
  "upstream": null,
  "fileHash": "17c3ef4017dc364aace411586dd0b1d2db68ddee4a996bd04f8d68f9cdddc6d4"
 },
 "stremioaddon": {
  "provider": "StremioAddon",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 15,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.StremioAddonProvider",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "files.catbox.moe",
   "watchsomuch.tv",
   "opensubtitles-v3.strem.io",
   "example.com"
  ],
  "upstream": null,
  "fileHash": "f5501e7bc185cdb87005eb033d608ef560ed12e02c6f3b035fc5f667d1b1685d"
 },
 "stremiox": {
  "provider": "StremioX",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 26,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.StremioXPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "MainActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "torrentio.strem.fun",
   "watchsomuch.tv",
   "aiometadata.elfhosted.com",
   "opensubtitles-v3.strem.io"
  ],
  "upstream": null,
  "fileHash": "df1ad3d3e5d2736e50ad384c78991e21cda5f4d466ac2786999004484f74937f"
 },
 "superstream": {
  "provider": "SuperStream",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 36,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.SuperStreamPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "www.febbox.com",
   "aiometadata.elfhosted.com",
   "febapi.nuvioapp.space",
   "opensubtitles-v3.strem.io"
  ],
  "upstream": null,
  "fileHash": "481f699935a5c8fc86c244945aac3626671b4ee7c4ec4274793ea035b1fe0c6d"
 },
 "tamilblasters": {
  "provider": "Tamilblasters",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 10,
  "repoStatus": 1,
  "language": "ta",
  "pluginClassName": "com.tamilblasters.TamilblastersPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.VidHidePro"
  ],
  "hosts": [
   "cavanhabg.com",
   "hgcloud.to",
   "omg10.com",
   "tryzendm.com"
  ],
  "upstream": null,
  "fileHash": "99ef5b0c1470e834bf54ba46277d8d3ce3ce77994f09718cb36c5d23110e4103"
 },
 "toonhub": {
  "provider": "ToonHub",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 12,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.toonhub4u.Toonhub4uPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.MixDrop",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor"
  ],
  "hosts": [
   "allinonedownloader.fun",
   "animezia.cloud",
   "asnwish.com",
   "cdnwish.com"
  ],
  "upstream": null,
  "fileHash": "75bee3d04ad2aaebd4cb7c2437d146a763f340ae17fdb773c4cdd2ab69515ac5"
 },
 "toonstream": {
  "provider": "Toonstream",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 10,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Toonstream.ToonstreamProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.DoodLaExtractor",
   "com.lagradost.cloudstream3.extractors.Filesim",
   "com.lagradost.cloudstream3.extractors.StreamSB",
   "com.lagradost.cloudstream3.extractors.StreamWishExtractor",
   "com.lagradost.cloudstream3.extractors.VidHidePro",
   "com.lagradost.cloudstream3.extractors.VidStack",
   "com.lagradost.cloudstream3.extractors.VidhideExtractor",
   "com.lagradost.cloudstream3.extractors.Vidmolyme"
  ],
  "hosts": [
   "playhydrax.com",
   "abyssplayer.com",
   "as-cdn26.top",
   "blakiteapi.xyz"
  ],
  "upstream": null,
  "fileHash": "49206f4477330da75e159cda91fca00ec461013e36c92d9c99ec9aaf0575270d"
 },
 "toontales": {
  "provider": "ToonTales",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.ToonTales.ToonTalesProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com",
   "www.toontales.net"
  ],
  "upstream": null,
  "fileHash": "e6ad8f7af1e450f1ddd66e40779d7b22303c3620e7a13b26de232004d1c3c035"
 },
 "topcartoons": {
  "provider": "Topcartoons",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 5,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.Topcartoons.TopcartoonsProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com",
   "www.topcartoons.tv"
  ],
  "upstream": null,
  "fileHash": "66ff9467cd91580ee4adbaa102aadb1f56c0123b2c16f16f5da67f81bce32360"
 },
 "topstreamfilm": {
  "provider": "Topstreamfilm",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 9,
  "repoStatus": 1,
  "language": "de",
  "pluginClassName": "com.Topstreamfilm.TopstreamfilmPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.MixDrop"
  ],
  "hosts": [
   "dropload.io",
   "omg10.com",
   "supervideo.tv",
   "www.topstreamfilm.live"
  ],
  "upstream": null,
  "fileHash": "1640017866afc55622e8670bd4702e061cef7cff4dc3d659bb2c13486fa4deb3"
 },
 "torrastream": {
  "provider": "TorraStream",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 96,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.TorraStreamProvider",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin",
   "AccountManager",
   "AccountManager$Companion"
  ],
  "embeds": [],
  "hosts": [
   "anime-kitsu.strem.fun",
   "opensubtitles-v3.strem.io",
   "aiometadata.elfhosted.com",
   "feed.animetosho.xyz"
  ],
  "upstream": null,
  "fileHash": "3cfd2e387a00151717a393e9271e626323b5204072fc74b34f251b70c568c524"
 },
 "uhdmoviesprovider": {
  "provider": "UHDmoviesProvider",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 40,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.UHDmoviesProviderPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "video-seed.xyz",
   "api.simkl.com",
   "driveleech.net",
   "driveleech.org"
  ],
  "upstream": null,
  "fileHash": "5be4aa3e1418d3ca09758a2e33dc96563ad4be96a74760cb700035faf9c4e2ed"
 },
 "ultima": {
  "provider": "Ultima",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 63,
  "repoStatus": 2,
  "language": "en",
  "pluginClassName": "com.phisher98.UltimaPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "MainActivity",
   "Plugin",
   "HomeViewModel",
   "HomeViewModel$Companion",
   "RepositoryData"
  ],
  "embeds": [],
  "hosts": [
   "cdn.jsdelivr.net",
   "cloudstream-ultima-sync-default-rtdb.firebaseio.com",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "bffb4ca63b4b1ee3c96d172938b4e675f527ca08af5bcbfd14c08c8bdaacd60a"
 },
 "xdmovies": {
  "provider": "XDMovies",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 15,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.phisher98.XDMoviesProvider",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [],
  "hosts": [
   "cinemeta-live.strem.io",
   "top.xdmovies.wtf",
   "challenges.cloudflare.com",
   "hubcloud.foo"
  ],
  "upstream": null,
  "fileHash": "c1fd26cbb7c2885d80bb2500a0e5623e0727d0c391a1ff72c3b823287cb3ff47"
 },
 "yts": {
  "provider": "YTS",
  "verdict": "HOST-COMPAT",
  "why": "loads, but its code calls app-only types (CloudStreamApp, CommonActivity) - those paths throw unless the host provides a matching type",
  "version": 11,
  "repoStatus": 1,
  "language": "en",
  "pluginClassName": "com.YTS.YTSProvider",
  "dex": "DEFLATED",
  "extendsPlugin": false,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity"
  ],
  "embeds": [],
  "hosts": [
   "omg10.com",
   "www12.yts-official.to",
   "yts.bz"
  ],
  "upstream": null,
  "fileHash": "092870265e17ffe895c39fca65d4970488f877d3b6450dac97a3b779289d0975"
 },
 "zinkmovies": {
  "provider": "Zinkmovies",
  "verdict": "NEEDS-ENTRY-SHIM",
  "why": "the entry class itself extends com.lagradost.cloudstream3.plugins.Plugin (app module), so loadClass fails with \"Didn't find class\" before anything runs",
  "version": 10,
  "repoStatus": 1,
  "language": "hi",
  "pluginClassName": "com.zinkmovies.ZinkmoviesPlugin",
  "dex": "DEFLATED",
  "extendsPlugin": true,
  "appOnly": [
   "CloudStreamApp",
   "CommonActivity",
   "Plugin"
  ],
  "embeds": [
   "com.lagradost.cloudstream3.extractors.PixelDrain",
   "com.lagradost.cloudstream3.extractors.StreamTape"
  ],
  "hosts": [
   "hubcloud.foo",
   "hubcloud.one",
   "hubdrive.space",
   "omg10.com"
  ],
  "upstream": null,
  "fileHash": "66184b0510b83ea25b389920453d3614d7d24efb7b876f898159a7e73fe2c1d1"
 }
}

/** Case- and punctuation-insensitive lookup: `SFlix`, `sflix`, `sflixprovider` all match one row. */
export function communityNote(name?: string | null, internalName?: string | null): CommunityVerdict | null {
  for (const key of [internalName, name]) {
    if (!key) continue;
    const norm = key.toLowerCase().replace(/[^a-z0-9]/g, '');
    const hit = COMMUNITY_VERDICTS[norm] ?? COMMUNITY_VERDICTS[norm + 'provider'];
    if (hit) return hit;
  }
  return null;
}

/** One sentence for a table cell: verdict + the reason, trimmed. */
export function communityNoteLabel(row: CommunityVerdict): string {
  const status = row.repoStatus === 0 ? ' (repo says down)' : '';
  return `${row.verdict ?? 'UNKNOWN'}${status} — ${row.why || 'no note'}`;
}
