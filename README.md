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
- **Change-making recipes:** bills craft into the next size up and split back down (5x$1 <-> $5, 2x$5 <-> $10, 2x$10 <-> $20, 5x$10 <-> $50, 2x$50 <-> $100, 5x$100 <-> $500, 2x$500 <-> $1000, plus 2x$20+$10 -> $50 and 5x$20 -> $100), so exact change never needs an ATM.

### Bank Card

- **Right-click:** shows your balance on the action bar. **Sneak + right-click:** lists your last five transactions in chat. Both share the `commandCooldownMs` cooldown (never faster than once a second) and can be switched off with `disableBankCardUse`.
- **Offhand:** with a Create shopping list in the main hand, the card in the offhand lets the Stock Ticker integration withdraw the bills the list is missing.

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

Any command can be turned off in the config, see [Disabling commands](#disabling-commands).

### Vote Command

| Command                  | Description                                                                                                                                              |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/vote <type> [days]`    | Start a 30-second server-wide vote to change the time or weather. Types: `day`, `night`, `clear`, `rain`, `thunder`. `days` (1-7) only applies to weather |
| `/vote yes` / `/vote no` | Cast your vote while one is running (typing `1` or `2` in chat also works)                                                                               |

A vote passes once more than half of the eligible players have voted yes — not simply when yes outnumbers no — so a vote everybody ignores now fails. Spectators cannot start or cast a vote and are never counted; if the [AFKStatus](https://www.curseforge.com/minecraft/mc-mods/afkstatus) mod is installed AFK players are not counted either, unless they vote anyway. The vote ends the moment the result is certain instead of always waiting out the 30 seconds, and if you are the only eligible player it is applied straight away and announced to the server.

The share required is `voteApprovalPercent` (50 by default) and the AFK exclusion can be turned off with `voteIgnoreAfk`.

### Admin Commands

| Command                             | Description                                                                                                                                                                                                                                                                                                               |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/createringtoncurrency admin-mode` | Operators only. Toggles admin mode for yourself; `on` / `off` set it explicitly. While it is on, right-clicking any depositor terminal opens its owner menu (set price, take bills) and chat tells you whose terminal you opened; while it is off you pay like any other customer. It switches off again when you log out |
| `/createringtoncurrency audit`      | Operators only. Counts every bill on the server and reports where it is — see [Cash audit](#cash-audit)                                                                                                                                                                                                                    |

### Cash audit

`/createringtoncurrency audit` takes a census of the physical cash on the server: how much exists, who is holding it, and where it is sitting.

| Command                                   | Description                                                                                       |
|-------------------------------------------|---------------------------------------------------------------------------------------------------|
| `/createringtoncurrency audit`            | Full scan: online and offline players, every container and every entity in every dimension        |
| `/createringtoncurrency audit players`    | Players only. Instant, and does not touch the world files                                          |
| `/createringtoncurrency audit goto <n>`   | Teleport to location `n` from your last audit                                                      |

The chat summary shows the total, a per-source split, the change since the last audit, and the richest locations. Each location comes with a `[go]` button that teleports you there and a `[copy]` button that puts an `/execute in <dimension> run tp @s <x> <y> <z>` command on your clipboard. The number of rows is `audit.auditChatSites`; the full list — every location, with its bill breakdown and teleport command — is written to `createringtoncurrency-audits/audit-<timestamp>.txt` next to the server jar.

A full scan saves the world first so that what is on disk is current, then reads the region files of every dimension on background threads. It is read-only and never writes to world data. Chunks are only parsed in full when their raw bytes contain a bill id, so most of a large world is skipped cheaply; progress is reported in chat.

The scan walks container NBT generically rather than looking for known tags, so it finds bills in modded inventories — depositor terminals, Create vaults, anything built on an item handler — as well as vanilla ones, and it follows them into shulker boxes, bundles and container items. It also covers dropped items, chest minecarts, chest boats, pack animals and the crafting grid a player has open. Villager trade offers are skipped, since a bill listed in a trade does not exist yet.

**Known gaps:** bills riding inside a moving Create contraption or in transit on a belt; a pack animal someone is riding at the moment of the scan, which is saved to neither the entity files nor the rider's player data. Players moving bills around during a long scan can also be counted twice, so run it when the server is quiet. Every report states its own coverage rather than implying a total it cannot back up.

Because the mod's backend is the ledger and bills are ordinary items, comparing successive audits is the cheapest way to notice a duplication bug: a jump in the total with no withdrawals to explain it is the signal to look for. Only a full scan updates that baseline and only a full scan is compared against it, so using `audit players` never disturbs the comparison. Bills the backend has already debited but not yet handed to an offline player are reported separately, since they are a liability with no physical counterpart yet.

### ATM Block & GUI
- **Interactive ATM:** Eight ATM variants can be crafted or given by operators. When right‑clicked it opens a custom GUI where players can deposit or withdraw money without typing commands.
- **PIN & authentication:** The GUI guides players through a simple login flow; once authenticated it displays their balance, deposit buttons and withdraw options. The screen class organises the UI into views for deposit, withdraw total, withdraw single bills and withdraw bundles.
- **Bundled withdrawals:** Players can specify denominations and counts for withdrawal, or enter a lump sum to automatically get the best combination of bills.
- **Feedback:** After each deposit or withdrawal the server sends a success or error payload so the screen can display a coloured status message.

### Mob Drops

- Every drop is one line in the `mobDrops.drops` config list: `<entity or #tag>=<denomination>:<chance>`, for example `minecraft:zombie=1:2.0`, `#minecraft:skeletons=5:2.0` or `minecraft:warden=100:25.0`. The namespace defaults to `minecraft`.
- Each matching line is rolled on its own, so one kill can drop several bills; what drops is handed out as the largest bills that fit the amount.
- By default zombies, spiders, creepers, skeletons, wither skeletons and blazes roll for a $1 bill, and skeletons, wither skeletons and blazes also roll for a $5.
- Drop chances increase with the **Capitalist Greed** enchantment (`capitalistGreedBonus`, percentage points per level).
- Daily mob drop earnings are capped (`mobDailyLimit`) to maintain balance.

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
- Requires **[CRNet](https://www.curseforge.com/minecraft/mc-mods/crnet)** `3.0.5` or newer - the shared library that handles all backend HTTP calls and JWT authentication

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
- Mob drop table (`mobDrops.drops`) and the Capitalist Greed bonus per level
- Daily mob earnings cap
- Cooldowns for commands and lotteries
- Vote approval share (`vote.voteApprovalPercent`) and whether AFK players count (`vote.voteIgnoreAfk`)
- How many audit locations are listed in chat (`audit.auditChatSites`)
- Per-command `disable*Command` toggles (see below)

### Disabling commands

Every chat command can be switched off individually. A disabled command is not registered at all, so it won't show up in tab completion:

| Config key                | Disables                            |
|---------------------------|-------------------------------------|
| `disableMoneyCommand`     | `/money`                            |
| `disablePayCommand`       | `/pay`                              |
| `disableCashCommands`     | `/deposit`, `/withdraw`             |
| `disableBaltopCommand`    | `/baltop`                           |
| `disableDailyCommand`     | `/daily`                            |
| `disableLotteryCommands`  | `/lottery`, `/join`                 |
| `disableVoteCommand`      | `/vote`                             |
| `disableAdminModeCommand` | `/createringtoncurrency admin-mode` |

These toggles only affect the chat commands. The ATM block and the Create Stock Ticker integration have their own deposit/withdraw paths and keep working regardless, so for example `disableCashCommands = true` makes the ATM the only way to move bills in and out of an account.

`disableBankCardUse` turns off the Bank Card right-click balance and history in the same way; the Stock Ticker payment path is unaffected.

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
