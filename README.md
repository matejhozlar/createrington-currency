# Createrington Currency – Minecraft Economy Mod

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-5E7C16?logo=minecraft&logoColor=white)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.172-orange)
![Backend API](https://img.shields.io/badge/Backend-Required-critical)

**Createrington Currency** is a fully‑fledged economy mod for Minecraft built on the NeoForge mod loader. It introduces physical currency items, player bank accounts, an ATM block and a suite of commands that all tie into a remote backend API. Unlike simple economy add‑ons, balances and transactions live on your own server so you can connect multiple game servers to the same economy.

---

## Status: Production Ready

All planned features have been implemented and the mod is stable for production use. Tested for 2+ months in production with 1000+ players across multiple servers pre-CurseForge version. If you run into any issues, feel free to contact me.

--- 

## Downloads & Related Projects

- [**Download on CurseForge**](https://www.curseforge.com/minecraft/mc-mods/create-rington-currency)
- [**Play with the full modpack**](https://www.curseforge.com/minecraft/modpacks/create-rington)
- [**Visit the live server**](https://create-rington.com)
- [**Discord Integration**](https://github.com/matejhozlar/mc-page)

## Backend Templates

- [**JavaScript**](https://github.com/matejhozlar/createrington-javascript-backend) (**Most Stable**)
- [**TypeScript**](https://github.com/matejhozlar/createrington-typescript-backend) (*Unfinished*)
- [**Python**](https://github.com/matejhozlar/createrington-python-backend) (*Unfinished*)

---

## Features

### Backend Integration

- **Persistent accounts & transactions:** All player balances, deposits, withdrawals and transfers are performed via HTTP requests to a remote server. Commands call endpoints such as `/currency/balance`, `/currency/pay`, `/currency/deposit`, `/currency/withdraw` and `/currency/top`. The mod caches JWT tokens and refreshes them periodically to authenticate requests 
- **Requires a backend API:** Without an API server the mod will not function. See the configuration section for setting API URLs.

### Currency Items

- **Eight denominations:** `$1`, `$5`, `$10`, `$20`, `$50`, `$100`, `$500`, `$1000`.
- **Stackable and tradable:** Bills behave like regular items. You can deposit them to your account or withdraw them using commands or the ATM.
- **Drop behaviour:** Certain hostile mobs can drop small bills when killed. Drop chances are configurable and scale with the `Capitalist Greed` enchantment (see below).

### Player Accounts

- **Server‑synced balances:** Each player has a unique account identified by their UUID. Balances are fetched from the backend using `/currency/balance`. Accounts persist across servers that point to the same API.
- **Secure transactions:** The mod obtains a JWT token from the backend via the `/currency/login` endpoint and includes it in all authenticated requests.

### Economy Commands

| Command         | Description                          |
|-----------------|--------------------------------------|
| `/money`        | Check your current balance           |
| `/baltop`       | See the richest players              |
| `/pay`          | Send money to another player         |
| `/deposit`      | Convert bills into balance           |
| `/withdraw`     | Withdraw bills from balance          |
| `/daily`        | Daily money reward                   |
| `/lottery`      | Start a server-wide lottery          |
| `/join`         | Join a lottery in progress           |

All commands enforce a global cooldown, configurable via `commandCooldownMs`. If a player executes a command too quickly, they will see a cooldown message.

Every command can be switched off individually in the config. A disabled command is not registered at all, so it won't show up in tab completion:

| Config key                | Disables                 |
|---------------------------|--------------------------|
| `disableMoneyCommand`     | `/money`                 |
| `disablePayCommand`       | `/pay`                   |
| `disableCashCommands`     | `/deposit`, `/withdraw`  |
| `disableBaltopCommand`    | `/baltop`                |
| `disableDailyCommand`     | `/daily`                 |
| `disableLotteryCommands`  | `/lottery`, `/join`      |
| `disableVoteCommand`      | `/vote`                  |

### ATM Block & GUI
- **Interactive ATM:** Eight ATM variants can be crafted or given by operators. When right‑clicked it opens a custom GUI where players can deposit or withdraw money without typing commands.
- **PIN & authentication:** The GUI guides players through a simple login flow; once authenticated it displays their balance, deposit buttons and withdraw options. The screen class organises the UI into views for deposit, withdraw total, withdraw single bills and withdraw bundles.
- **Bundled withdrawals:** Players can specify denominations and counts for withdrawal, or enter a lump sum to automatically get the best combination of bills.
- **Feedback:** After each deposit or withdrawal the server sends a success or error payload so the screen can display a coloured status message.

### Mob Drops

- Zombies, Skeletons, Spiders, and Creepers can drop $1 or $5 bills.
- Drop chances increase with the **Capitalist Greed** enchantment.
- Daily mob drop earnings capped to maintain balance.

### Enchantment: Capitalist Greed

- Levels I–III.
- Increases the chance of mobs dropping money when killed.
- Must be applied to weapons.

### Lottery System

- Players can start and join lotteries with in-game currency.
- The winner receives the entire pot.
- Cooldown enforced between lottery rounds.

---

## ⚙ Requirements

### Minecraft

- Minecraft version **1.21.1**
- Requires **NeoForge** mod loader
- Requires **[CRNet](https://www.curseforge.com/minecraft/mc-mods/crnet)** `3.0.0` or newer - the shared library that handles all backend HTTP calls and JWT authentication

### Backend API

- Mod requires a remote server with specific API endpoints:
```http
POST   /currency/login
GET    /currency/balance
POST   /currency/pay
POST   /currency/deposit
POST   /currency/withdraw
GET    /currency/top
GET    /currency/mob-limit
POST   /currency/daily
POST   /currency/lottery/start
POST   /currency/lottery/join
```

> ⚠️ Without the backend API, this mod **will not function**.

---

## Configuration

Upon first launch, the mod generates a config file at:
`/config/createringtoncurrency-common.toml`

Inside, you can set:

- API base URL (`http://127.0.0.1:5000/` by default)
- Mob drop rates
- Daily mob earnings cap
- Cooldowns for commands and lotteries
- Per-command `disable*Command` toggles to turn off any command

---

## Development

- Built with **NeoForge** for Minecraft 1.21.1.
- Developed using Java & Gradle.
- Token-based authentication used to secure all player transactions.

### Build Instructions

1. Clone the repo.
2. Open in an IDE (e.g., IntelliJ).
3. Use JDK 21+.
4. Run `gradlew build` to compile.

---

Made with ☕ by [@matejhozlar](https://github.com/matejhozlar)  
Let the money flow. 💸
