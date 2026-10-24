# PrivKeyboard

Privacy-focused Android keyboard (encrypted cloud sync planned).

**Status:** Phase 5 — effects, sounds, haptics, theme collection (on top of Phase 2–4).

- QWERTY + symbols layers, shift / caps-lock, auto-capitalisation, backspace repeat, haptics
- Long-press popups: digits, accents, symbols (long-press space = switch keyboard)
- Emoji panel (categories + recents)
- English / Bangla (Avro-style phonetic) — globe key switches language
  - `ami` → আমি, `bangla` → বাংলা, `sundor` → সুন্দর, `bhalObasi` → ভালোবাসি
  - capitals are different letters: T ট, D ড, N ণ, S শ, Sh ষ, R ড়, O ো, I ঈ, U ঊ; `Ng` ঙ, `NG` ঞ, `ng` ং
- Password / e-mail / URL / number fields always use the English layout
- **Clipboard history** (unlimited): saved while PrivKeyboard is your active keyboard
  - text is AES-256-GCM encrypted with a key held in the Android Keystore
  - clipboard icon above the keys → insert, pin, delete; "Manage" opens the full screen (search, tags/folders, pin, delete, clear)
  - never recorded: password fields, incognito fields, clips flagged sensitive by password managers
  - can be switched off in the clipboard screen

## Get the APK
1. Push this repo to GitHub (branch `main`).
2. Open the **Actions** tab → latest **Build APK** run.
3. Download the artifact **PrivKeyboard-debug-apk** (zip containing `app-debug.apk`).
4. Install it on your phone, open the app, and follow the 3 steps.

The workflow uses the Gradle version installed by CI (no wrapper needed).

## Renaming the app/package
Change `namespace` and `applicationId` in `app/build.gradle.kts`, move the
`app/src/main/java/com/example/privkeyboard` folder accordingly, update the
`package` lines in the `.kt` files and `android:settingsActivity` in
`app/src/main/res/xml/method.xml`.

## Phase 4 — themes / Liquid Glass
- Whole app and keyboard are drawn over a themed backdrop; glass surfaces (keys, toolbar, bottom nav,
  cards, dialogs, menus, FAB) sample a blurred copy of it, with translucent tint, top reflection,
  highlight border, soft glow and an animated illuminated-pill selected state
- Theme tab: gradient presets or a photo from the gallery (cover / contain / center), background blur,
  brightness overlay, glass opacity & blur, accent glow, key height, light / dark / system, reset
- Text colour adapts to the actual brightness of the background (adaptive contrast)
- Changes apply live to the app and the keyboard (no restart); settings are stored locally
- Menu (⋮) on the Theme tab: reset background, copy / paste theme as JSON

## Phase 5 — effects
- Effects tab: key animation (ripple, glow burst, bounce, pop), glowing swipe trail (visual only),
  7 synthesised sound packs + volume, 5 haptic patterns + strength
- Theme tab → Theme collection: 12 one-tap looks (background + glass + accent + effect + sound + haptic)
- Settings are included in the theme JSON (copy / paste from the Theme tab menu)
