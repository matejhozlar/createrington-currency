# Createrington Currency

**Createrington Currency** is a custom Minecraft mod that introduces a robust, server-backed economy system into your game. It enables physical money, banking features, enchantments, and more — all synchronized with an external backend API.

---

## Status: Production Ready

All planned features have been implemented and the mod is stable for production use. Test for 2+ months in production with 1000+ players across multiple servers pre-CurseForge version.

---

## Features

### Backend Integration

- Persistent account balances, deposits, withdrawals, and transactions are managed through a remote server API.
- Requires a backend service with defined endpoints (see below).

### Currency Items

- Physical money items in denominations: `$1`, `$5`, `$10`, `$20`, `$50`, `$100`, `$500`, `$1000`.
- Stackable, tradable, and usable in-world or via commands.

### Player Accounts

- Secure, server-synced virtual accounts.
- Data persists across servers that use the same backend.

### Economy Commands

- `/money` – Check your current balance.
- `/baltop` – View the richest players.
- `/pay <player> <amount>` – Send money to another player.
- `/deposit` – Convert physical bills into digital balance.
- `/withdraw <amount>` – Withdraw bills from your account.
- `/daily` – Claim a once-per-day reward.
- `/lottery <amount>` – Start a new server-wide lottery.
- `/join <amount>` – Join an existing lottery.

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

### Backend API

- Mod requires a remote server with specific API endpoints:
  - `POST /currency/login`
  - `GET /currency/balance`
  - `POST /currency/pay`
  - `POST /currency/deposit`
  - `POST /currency/withdraw`
  - `GET /currency/top`
  - `GET /currency/mob-limit`
  - `POST /currency/daily`
  - `POST /currency/lottery/start`
  - `POST /currency/lottery/join`

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

## License

This project is licensed under the **MIT License**.  
Use freely, contribute back, and credit [@matejhozlar](https://github.com/matejhozlar).

---

Made with ☕ by [@matejhozlar](https://github.com/matejhozlar)  
Let the money flow. 💸
