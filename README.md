# दृष्टि — Dṛṣṭi

*"See beyond the obvious; discover different perspectives through hidden clues and deeper signals."*

A private, offline-first Vedic decision companion for Android. Chat is the
home screen; Kundali, Panchang, and Settings are one tap away.

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
- **Decision Journal** — Full lifecycle management for comparing choices with
  astrological support scores, recording selections, and retrospective
  outcome calibration. See `ui/screen/decisions/DecisionScreen.kt`.
- **Profile & Places** — fully offline person/place database, seeded with a
  dozen major Indian cities, editable lat/long/timezone.
- **Settings** — MOCK/LIVE/GEMINI AI modes, model selection, hardware-encrypted
  API key storage (`androidx.security-crypto`).
- **Guided BYOK Setup** — A non-technical, guided flow for users to connect
  their own Gemini API keys from Google AI Studio. Includes smart
  auto-detection of keys from the clipboard.
- **MOCK AI mode by default** — the whole app is usable with zero API key
  and zero network calls. LIVE/GEMINI modes (optional) send only interpretive
  text to your own configured endpoint; positions are still always computed
  locally.
- Unit tests for the astronomical engine, sign/nakshatra mapping, hashing,
  and score validation (`app/src/test`).

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
  Dasha come from `AstroCalc`; the interpretive layer is deterministic
  rule-based text.
- **LIVE** (OpenAI): set your own OpenAI-compatible API key.
- **GEMINI** (Google): Bring your own Gemini API key via the guided
  setup flow. Uses Google's native Interactions API for intelligent
  astrological reasoning while maintaining a private, local-only conversation
  history.

## AI-Powered Capabilities

When **LIVE** or **GEMINI** mode is enabled, the following screens utilize AI for specialized reasoning (while all calculations remain local):

- **Chat Companion**: General conversational reasoning grounded in your chart data. It can automatically detect when you are describing a choice and trigger a structured decision analysis.
- **Decision Journal**: Specialized comparison of choices. AI evaluates your Dasha and transits to provide "Astrological Support %" and detailed explanations for each path.
- **Outcome Calibration**: Retrospective analysis of your recorded decisions. AI compares the original prediction against what actually happened to identify alignment or miscalibration.
- **Transit Analysis**: Interpretive summary of today's planetary movements. AI identifies favorable themes and caution areas specifically for your natal positions.

## Privacy

Birth data, chat history, and everything else lives in a local Room
database on-device. The API keys live in hardware-backed `EncryptedSharedPreferences`.
The app uses a "Privacy by Design" approach for AI:
- **Bring Your Own Key (BYOK)**: Users provide their own keys from their
  own accounts.
- **Minimal Context**: Only the specific data points needed for the
  interpretation (e.g., current Dasha lord, Nakshatra) are sent to the AI.
- **No PII in AI Logs**: Database logs store SHA-256 hashes of inputs, never
  plain text.
- **Smart Clipboard Detection**: The app only reads the clipboard to detect
  an `AIza` pattern key; this processing is strictly local and never logged.

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
