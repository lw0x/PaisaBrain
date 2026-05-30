<p align="center">
  <img src="playstore-assets/app_icon_1024x1024.png" width="120" alt="Paisa Brain Logo" />
</p>

<h1 align="center">🧠 Paisa Brain</h1>

<p align="center">
  <strong>Remembers everything. Tells no one.</strong>
</p>

<p align="center">
  AI-powered money tracker + memory vault + privacy guard.<br/>
  100% offline. Zero data collection. Free forever.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-6.0%2B-teal?style=flat-square" alt="Android 6.0+" />
  <img src="https://img.shields.io/badge/Internet-NONE-brightgreen?style=flat-square" alt="No Internet" />
  <img src="https://img.shields.io/badge/Price-Free%20Forever-gold?style=flat-square" alt="Free Forever" />
  <img src="https://img.shields.io/badge/Data%20Collected-Zero-blue?style=flat-square" alt="Zero Data" />
  <img src="https://img.shields.io/badge/Languages-32-purple?style=flat-square" alt="32 Languages" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="MIT License" />
</p>

---

## 🤔 Why Paisa Brain?

Every finance app wants your data. We made one that **physically cannot have it**.

```
This app has NO INTERNET PERMISSION.
Your data cannot leave your phone. Not even theoretically.
Verify: Settings → Apps → Paisa Brain → Permissions → No network listed.
```

---

## ✨ Features

### 💰 Money Brain — Auto-Track Spending
- Reads bank SMS (50+ banks, all UPI apps) → auto-categorizes
- Monthly budget with real-time progress & predictions
- Cash expense tracking with quick templates
- Ghost subscription finder
- Salary detection & payday countdown
- Group expense splitting (flatmates, trips, office)
- Net worth tracker & investment portfolio
- Debt payoff planner (snowball & avalanche)
- Tax saver (80C / 80D / HRA / New vs Old regime)
- Cash flow forecast (30/60/90 days ahead)
- Financial Health Score (0–900)

### 🔍 Vault Brain — Remember Everything
- Snap photos → AI extracts text (offline OCR)
- Voice memos → transcribed on-device
- Quick text notes
- Natural language search ("wifi password", "doctor medicine")
- Document organizer & warranty tracker
- Important date detection from your notes

### 🛡️ Privacy Guard — Spy on the Spies
- See which apps accessed your camera, mic, location
- Privacy Score (0–100)
- Background access alerts ("Social app used mic at 3am!")
- Self-transparency report (what WE accessed: nothing)

### 🆘 Emergency — Life Happens
- Lock-screen emergency info card (blood group, contacts)
- Survival Mode for job loss / income disruption
- Dispute helper for wrong charges (step-by-step + templates)
- "In Case of Emergency" vault for trusted family access
- "Can I Afford This?" instant calculator
- Polite "Say No" scripts for lending pressure

### 🎮 Gamification — Make Saving Fun
- Savings streaks with streak freeze
- 30+ achievement badges (Common → Legendary)
- Level 1–20 progression system
- Daily money-saving challenges
- Weekly spending "roasts" (witty & shareable)
- Money Personality quiz (7 types)

### 😌 Emotional Intelligence
- Financial anxiety mode (calming + factual reassurance)
- Milestone celebrations with confetti
- Money journal (mood + spending correlation)
- Weekly Win (positive-only Sunday summary)
- Financial Weather metaphor (☀️🌧️⛈️)
- Impulse purchase 24-hour cooler
- Letters to your future self

### ♿ Accessibility
- **Blind users**: Full TalkBack support, charts read as text
- **Deaf users**: Vibration patterns + visual alerts for every sound
- **Motor impaired**: 48dp+ touch targets, no swipe-only actions
- **Elderly**: Simple Mode with huge text, 3-tab navigation, voice readout
- **Low vision**: High contrast mode, WCAG 2.1 AA compliant
- **32 languages**: Auto-detects phone language, or switch manually

### 📱 Works Everywhere
- Android 6.0 → 17 (API 23–37)
- 18 OEM skins tested (all major manufacturers)
- Auto-detects device capabilities, adapts features accordingly
- Works on 2GB RAM phones (graceful degradation)
- Home screen widget (3 sizes)

---

## 🏗️ Architecture

```
app/src/main/java/com/paisabrain/app/
├── MainActivity.kt              # Entry point
├── SplashActivity.kt            # Fast branded splash (1.2s)
├── PaisaBrainApp.kt             # Application class
├── LanguageManager.kt           # 32-language support
├── DeviceCompat.kt              # Device capability detection
│
├── parser/                      # SMS parsing engine
│   ├── SmsParser.kt             # Main parser
│   ├── SmsBankPatterns.kt       # 50+ bank regex patterns
│   ├── SmsReceiver.kt           # Real-time SMS capture
│   ├── SmsHistoryScanner.kt     # Bulk initial scan
│   └── CategoryClassifier.kt   # Merchant → category AI
│
├── ai/                          # Intelligence engines
│   ├── InsightEngine.kt         # Spending insights
│   ├── PersonalityClassifier.kt # Money personality
│   ├── RoastGenerator.kt        # Weekly roasts
│   ├── PredictionEngine.kt      # Month-end predictions
│   ├── FinancialTwin.kt         # Personal pattern AI
│   ├── SmartHelper.kt           # Tips, challenges, goals
│   ├── PremiumFeatures.kt       # Health score, heatmap, roundup
│   ├── NetWorthTracker.kt       # Assets vs liabilities
│   ├── CashFlowForecast.kt     # Future balance prediction
│   ├── DebtPayoffPlanner.kt    # Snowball & avalanche
│   ├── TaxSaverTracker.kt      # 80C/80D/HRA/regime comparison
│   ├── BillCalendar.kt         # Auto-detected bill dates
│   ├── InvestmentTracker.kt    # Portfolio & SIP tracking
│   ├── SmartCategoryEngine.kt  # Self-learning categories
│   ├── SmartNotificationEngine.kt # 10 contextual notification types
│   ├── SubscriptionCancelHelper.kt # Cancel guides
│   ├── FamilyBudgetMode.kt     # Multi-person budgets
│   ├── PurchaseDecisionHelper.kt # "Can I afford this?"
│   ├── WealthAge.kt            # Financial age calculator
│   ├── MoneyStoryGenerator.kt  # Monthly narrative
│   ├── ReportGenerator.kt      # PDF/CSV reports
│   └── VaultSearchEngine.kt    # Semantic-like search
│
├── db/                          # Encrypted database (SQLCipher)
├── ui/                          # Jetpack Compose UI (Material 3)
├── vault/                       # OCR, voice, auto-tagger
├── privacy/                     # Privacy Guard engine
├── security/                    # Anti-tampering (10 layers)
├── gamification/                # Streaks, achievements, levels
├── emotional/                   # Anxiety mode, journal, milestones
├── emergency/                   # Emergency card, ICE vault, survival
├── followup/                    # Call/money follow-up tracking
├── social/                      # "Say No" helper, relationship tracker
├── backup/                      # Encrypted backup/restore
├── cash/                        # Cash wallet tracker
├── groups/                      # Group expense splitting
├── search/                      # Transaction search & filters
├── proof/                       # Proof document generator
├── widget/                      # Home screen widget
├── tools/                       # Calculator, documents, commute, warranty
├── india/                       # Festival planner, gold, parent care
├── compat/                      # OEM compatibility (18 skins)
└── accessibility/               # Elderly mode, TalkBack, WCAG 2.1 AA
```

---

## 🔒 Privacy & Security

| Layer | Protection |
|:---:|---|
| 1 | No internet permission (manifest-blocked) |
| 2 | AES-256-GCM encrypted database (SQLCipher) |
| 3 | AES-256 encrypted backups (user's password) |
| 4 | Biometric / PIN lock |
| 5 | ProGuard R8 (5-pass code obfuscation) |
| 6 | Custom obfuscation dictionary |
| 7 | Root detection |
| 8 | Debugger detection |
| 9 | Emulator detection |
| 10 | APK signature & integrity verification |

---

## 🌍 Supported Languages

Auto-detects phone language. Manual switch in Settings.

| Language | Code | | Language | Code |
|---|---|---|---|---|
| English | en | | Punjabi | pa |
| Hindi | hi | | Odia | or |
| Tamil | ta | | Assamese | as |
| Telugu | te | | Urdu | ur |
| Kannada | kn | | Spanish | es |
| Malayalam | ml | | French | fr |
| Marathi | mr | | Arabic | ar |
| Bengali | bn | | + 18 more | ... |
| Gujarati | gu | | | |

---

## 📱 Device Compatibility

- **Minimum**: Android 6.0 (API 23)
- **Target**: Android 15 (API 35)
- **Tested on**: All major manufacturers
- **RAM**: 2GB+ (graceful degradation for low-end)
- **Storage**: ~65MB app + user data
- **OEM skins**: 18 custom OS variants handled

---

## 📊 App Stats

| Metric | Value |
|---|---|
| Source files | 110+ |
| Lines of Kotlin | ~73,000 |
| Features | 86+ |
| AI engines | 15+ (all on-device) |
| Paid apps replaced | 15+ |
| Cost | ₹0 forever |
| Data collected | Physically impossible |

---

## 📜 Privacy Policy

Paisa Brain collects, stores, transmits, and processes **ZERO** user data.

- No servers. No databases. No analytics. No tracking.
- No internet permission. No account required.
- All processing happens exclusively on-device.
- All data stored in encrypted local database.
- Uninstalling permanently deletes all data.

This is not a policy decision — it is a **technical impossibility**. The app cannot transmit data because it does not have the Android INTERNET permission.

---

## 🤝 Contributing

Contributions welcome! Areas where help is appreciated:

- 🏦 **New bank SMS patterns** — add regex for banks we don't support yet
- 🌍 **Translations** — add new languages or improve existing ones
- 🎭 **Roast templates** — write more witty spending roasts
- 🐛 **Bug fixes** — test on your device, report issues
- 🎨 **UI polish** — animations, micro-interactions
- ♿ **Accessibility** — test with screen readers, suggest improvements

### Guidelines
1. No brand/company names in user-facing code (use generic terms)
2. All features must work offline (no internet calls)
3. Follow Material 3 design guidelines
4. Maintain WCAG 2.1 AA accessibility
5. Test on Android 6.0+ before submitting

---

## 📄 License

## 📄 License

```
Copyright (c) lwc.network. All Rights Reserved.

Permission is hereby granted post approval from creator, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use and not sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.

UNAUTHORIZED USE, REPRODUCTION, OR DISTRIBUTION OF THIS SOFTWARE WITHOUT
PRIOR WRITTEN APPROVAL FROM THE CREATOR IS STRICTLY PROHIBITED.

For permission requests, contact: lwc.network
```

---

## ⭐ Star This Repo

If you believe personal finance should be **private by default**, give us a star. It helps others find this project.

---

<p align="center">
  Built with ❤️ for people who deserve financial privacy.<br/>
  Free forever. Private forever. Yours forever.
</p>
