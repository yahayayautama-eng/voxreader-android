# Store listing text

DRAFT. Character limits are AppGallery's; the Chinese stores (Xiaomi, OPPO, vivo, Honor) use similar ones. Draft screenshots are in `docs/store-screenshots/` (`en`, `zh-CN`, `zh-TW`): library, reader, voice sheet, import. They come from a 2.5 GB emulator, so the voice sheet lists only the English voices and the Chinese readers show no highlight (Kokoro is too slow to speak under emulation). Retake all of them on a real phone with 3 GB or more, during playback, before submitting.

## English

**App name** (30): Vox Reader – Offline Read Aloud

**Short description** (80): Neural voices that read your books aloud, fully offline. No account, no ads.

**Description:**

Vox Reader reads your books and documents aloud with natural-sounding neural voices that run entirely on your phone.

- Works in airplane mode. The voices are built in, and this version has no internet permission at all.
- Follows along: the sentence being read is highlighted, and the page scrolls with the voice.
- Opens EPUB, PDF, DOCX, MOBI, FB2 and TXT, and reads printed pages you scan with the camera.
- Reads English and Mandarin Chinese, switching voice sentence by sentence in mixed books.
- No account, no tracking, nothing collected. Your books never leave your phone.
- Free to use, with 1 hour of reading aloud each day. Vox Pro, a one-time purchase, removes the limit. Reading on screen is never limited.

**Keywords:** read aloud, text to speech, TTS, audiobook, offline, EPUB, PDF reader, English learning

## 简体中文

**应用名称**：Vox 朗读 – 离线听书

**一句话简介**（80 字以内）：内置神经语音，离线朗读电子书和文档。无需账号，无广告。

**应用介绍：**

Vox Reader 用自然流畅的神经语音为您朗读书籍和文档，全部在手机本地完成。

- 断网也能用：语音内置于应用中，本版本完全不申请网络权限。
- 跟读高亮：正在朗读的句子会高亮显示，页面随语音自动滚动。
- 支持 EPUB、PDF、DOCX、MOBI、FB2、TXT，还能用相机扫描纸质书页朗读。
- 支持英语和普通话，中英混排的书会逐句切换合适的语音。
- 无需账号，不追踪，不收集任何信息。您的书永远留在您的手机上。
- 免费使用，每天可朗读 1 小时；一次性购买 Vox Pro 即可无限收听。屏幕阅读不限时。

特别适合学英语：用地道的英语语音听原版书，逐句高亮，中文界面。

**关键词**：听书, 朗读, 文字转语音, TTS, 有声书, 离线, 电子书, 英语学习, PDF 阅读器

## 繁體中文

**應用名稱**：Vox 朗讀 – 離線聽書

**一句話簡介**：內建神經語音，離線朗讀電子書和文件。無需帳號，無廣告。

**應用介紹：**

Vox Reader 用自然流暢的神經語音為您朗讀書籍和文件，全部在手機本機完成。

- 離線也能用：語音內建於應用程式中，本版本完全不要求網路權限。
- 跟讀標示：正在朗讀的句子會標示出來，頁面隨語音自動捲動。
- 支援 EPUB、PDF、DOCX、MOBI、FB2、TXT，還能用相機掃描紙本書頁朗讀。
- 支援英語和普通話，中英混排的書會逐句切換合適的語音。
- 無需帳號，不追蹤，不收集任何資訊。您的書永遠留在您的手機上。
- 免費使用，每天可朗讀 1 小時；一次性購買 Vox Pro 即可無限收聽。螢幕閱讀不限時。

## Before submitting

- The Chinese app name must match the name on the APP filing (APP备案) and the software copyright certificate (软著) exactly.
- The home-screen name is "Vox 朗读" (简体) and "Vox 朗讀" (繁體), set by `app_name` in `values-zh-rCN` and `values-zh-rTW`. The 备案 and 软著 names must match the store name above.
- The free tier stops reading aloud after 1 hour per day (`DAILY_LISTEN_LIMIT_SECONDS` in `app/build.gradle.kts`). Vox Pro is the HMS IAP non-consumable product `vox_pro_unlock`: create it in AppGallery Connect before release, and build with `-PhmsAppId=<your AppGallery app ID>`. Test buying and restoring on a real Huawei phone, including in airplane mode after purchase.
- In the release form, declare the app as standalone (单机): the app itself has no network permission, and the only network use is IAP inside HMS Core. Confirm with Huawei that this qualifies.
