# Acca Live

Native Android app (Kotlin + Jetpack Compose) for tracking NFL accumulators live, using ESPN's free public API.

## Install

**[Download the latest APK](https://github.com/stester101/AccaLive/releases/latest/download/AccaLive.apk)** on your phone and open it. Android will ask you to allow installs from your browser the first time.

Every push to `main` builds a new signed APK and publishes it as the latest release. Builds share one signing key, so each new version installs straight over the old one and keeps your accas.

## Features

- **My Accas** — every leg with a live state (winning / losing / level / won / lost / void), score and clock, leg win %, odds, and the acca's live chance, potential returns and fair value
- **Games** — the week's slate with live scores, possession, down & distance, red zone, win-probability bar and lines (handicap, total, match odds); week picker
- **Game detail** — win-probability chart, full odds with open → current and live fair prices, your legs on that game, top performers
- **Build acca** — pick games and markets: match result, handicap, total points, and player props (anytime TD, passing/rushing/receiving yards, receptions, rush+rec). Odds pre-filled from ESPN, editable as fractional, decimal or American
- **Results** — auto-settled accas with profit/loss
- **Live tracking** — background updates with an ongoing summary notification, plus alerts for lead changes, legs settling and accas landing
- **Cover screen** — compact swipeable acca view when folded (launch on the Flip's cover screen with Good Lock → MultiStar)
- Fractional / decimal / American odds

## Data

ESPN endpoints (unofficial, may change):

- `site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard`
- `.../summary?event={id}` — box score, win probability, predictor, odds
- `.../teams/{id}/roster`

Leg and acca chances are estimates built on ESPN's live win probability (handicaps and totals use a normal model of the remaining margin/points calibrated to it; player props use pace against the line). Fair value is chance × returns, not a bookmaker cash-out.

Please gamble responsibly — begambleaware.org
