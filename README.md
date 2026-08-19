# दृष्टि — Dṛṣṭi

*"Give me a clearer view of the path; let me make the choice."*

A private, offline-first Vedic decision companion for Android. Chat is the
home screen; Kundali, Panchang, and Settings are one tap away.

---

## ⚠️ Before you open this in Android Studio

This project was generated in a sandboxed environment **without Android SDK
or internet access**, so it has never been compiled or run. It has been
written carefully and cross-checked (package names, imports, method
signatures, entity/DAO wiring, and enum serialization all verified by
static inspection), but Android Studio's own Gradle sync is the first real
compiler pass it will go through. Budget a little time for the normal
first-open friction of any generated project — a missing semicolon or an
AGP/Kotlin version mismatch is far more likely than a structural problem.

**Gradle wrapper jar:** `gradle/wrapper/gradle-wrapper.jar` (a binary file)
is *not* included — it can't be produced without network access. When you
open the project, Android Studio will either regenerate it automatically or
prompt you to. If not, run this once you have a JDK on your PATH:
```
gradle wrapper --gradle-version 8.11.1
```

**compileSdk 37 / AGP:** the build file uses the exact `compileSdk { version
= release(37) { minorApiLevel = 1 } }` block you specified, which needs a
recent AGP (8.9+) and Android Studio (Ladybug/Meerkat or newer) with SDK 37
installed via the SDK Manager. If your installed Android Studio is older,
either update it or simplify that block to `compileSdk = 37` and drop the
`minorApiLevel` if sync complains.

---

## What actually works right now

- **Chat home screen** — local, paginated conversation history (Room),
  intent recognition, and replies grounded in your real chart data.
- **Real astronomical calculations** — Julian Day, Lahiri ayanamsha, Sun and
  Moon position (Meeus low-precision series), Mercury–Saturn via JPL
  approximate Keplerian elements, mean lunar node (Rahu/Ketu), and the
  Ascendant — all computed **locally in Kotlin**, not invented by an AI.
  See `ai/provider/AstroCalc.kt`.
- **Kundali** — persisted natal chart with a hand-drawn North Indian diamond
  chart (Jetpack Compose `Canvas`), whole-sign houses, nakshatra/pada.
- **Vimshottari Dasha** — full Mahadasha sequence + current Antardasha,
  computed from the Moon's natal nakshatra.
- **Panchang** — Vara, Tithi, Nakshatra, Yoga, Karana, sunrise/sunset,
  Rahu Kalam, Yamaganda, Gulika Kalam, Abhijit Muhurta — cached per
  date+location so it's never recalculated needlessly.
- **Profile & Places** — fully offline person/place database, seeded with a
  dozen major Indian cities, editable lat/long/timezone.
- **Settings** — MOCK/LIVE AI toggle, model name, encrypted API key storage
  (`androidx.security-crypto`), cache/diagnostics controls.
- **MOCK AI mode by default** — the whole app is usable with zero API key
  and zero network calls. LIVE mode (optional) sends only interpretive text
  to a configured OpenAI-compatible endpoint; positions are still always
  computed locally.
- Unit tests for the astronomical engine, sign/nakshatra mapping, hashing,
  and score validation (`app/src/test`).

## What's scaffolded but not wired into a screen yet

The full data model, Room entities/DAOs, and `AiAstrologyService` methods
for **Transit analysis**, **Decision analysis** (option comparison with
astrological/timing support scores), **Decision Journal** (immutable
pre-decision snapshots), and **Outcome recording/calibration** are all
present and functional in code — `analyzeTransits`, `analyzeDecision`,
`analyzeOutcome` on `AiAstrologyService`, and the `DecisionEntity` /
`DecisionAnalysisEntity` / `DecisionOutcomeEntity` / `OutcomeAnalysisEntity`
tables. There just isn't a dedicated screen for them yet in this pass, since
they weren't part of the requested folder structure. Adding a
`ui/screen/decisions/DecisionScreen.kt` + `DecisionViewModel.kt` following
the exact same pattern as `KundaliViewModel`/`PanchangViewModel` is the
natural next step — ask and I'll add it.

## Architecture

```
Compose UI  →  ViewModel  →  Repository / AiAstrologyService  →  Room / AstroCalc
```

- `ai/provider/AstroCalc.kt` — real astronomy (Julian Day, ayanamsha, Sun,
  Moon, planets, node, ascendant, sunrise/sunset).
- `ai/provider/AiProvider.kt` — `AiAstrologyService` interface,
  `PanchangCalculator`, `DashaCalculator`, `MockAiProvider` (default),
  `OpenAiProvider` (LIVE mode), `IntentRecognizer` (local, no API call for
  obvious intents), `AiProviderFactory`.
- `database/` — Room entities, DAOs, `DrishtiDatabase`.
- `data/repository/` — thin repositories over Room.
- `di/ServiceLocator.kt` — manual DI (no Hilt), single instance owned by
  `DrishtiApplication`.
- `model/CoreModels.kt` — all shared DTOs (`kotlinx.serialization`), kept
  separate from Room `@Entity` classes so raw astronomical DATA never gets
  confused with AI-generated interpretation.
- `ui/` — theme, screens, viewmodels, and the nav graph (`ui/app/DrishtiApp.kt`
  → `ui/screen/home/HomeScreen.kt` for the bottom-nav tabs).

## AI modes

- **MOCK** (default): zero cost, fully offline. Positions/Kundali/Panchang/
  Dasha come from `AstroCalc`; the interpretive layer (decision comparisons,
  transit notes, chat replies) is deterministic rule-based text, clearly
  generated from your real chart/Dasha/Panchang data rather than random or
  templated filler.
- **LIVE**: set your OpenAI-compatible API key in Settings. Only the
  interpretive capabilities call the network; astronomical positions are
  never sent *to be calculated* by the model, only supplied *to* it as
  already-computed input.

## Astrology system (fixed for V1)

Sidereal zodiac · Lahiri ayanamsha · Whole Sign houses · Vimshottari Dasha.

## Privacy

Birth data, chat history, and everything else lives in a local Room
database on-device. The API key lives in `EncryptedSharedPreferences`. LIVE
mode only ever sends the minimum context needed for the specific request
(no whole-database dumps) — see how each ViewModel builds its own
`AiRequestContext`.

## Running it

1. Open the `Drsti` folder in Android Studio (Ladybug/2024.2 or newer
   recommended for compileSdk 37 support).
2. Let Gradle sync (needs internet, on your machine, once).
3. Run on an emulator or a physical phone over USB debugging.
4. First launch → bottom tab **Settings → Manage birth profiles** → add your
   birth details → Kundali/Panchang/Dasha populate automatically.

## Known limitations / honesty notes

- Planetary longitudes use low-precision (arc-minute-level) public-domain
  algorithms, not a full ephemeris (e.g. JPL DE) — fine for whole-sign
  Vedic astrology, not for research-grade astronomy.
- Sunrise/sunset uses a standard approximate formula; near the poles or on
  specific edge-case dates it may return "not computable" rather than a
  wrong time (the app says so rather than guessing).
- LIVE mode's OpenAI integration is written but has not been exercised
  against a real API key in this environment (no network access here) —
  double-check the request/response shape against OpenAI's current API
  docs before relying on it.
- No export/import, Transit screen, or Decision Journal screen yet (see
  above) — the data layer is ready for them.
