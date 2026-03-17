# Createrington Currency — Feature Ideas

## Create Mod Integrations

### 1. Train Fare System
Charge players a fare to ride Create trains. Place a "Ticket Machine" block at stations, players tap their bank card, fare gets deducted. Could use Create's train schedule/station name system to set per-route pricing. Already tracking train crashes, so the hook points exist.

### 2. Mechanical Minting / Money Printer
A multiblock using Create's kinetic system. Feed paper + dye into a pressing/mixing setup to physically "print" bills. Require SU (stress units) scaling with denomination — printing $1000 bills needs serious rotational force. Great money sink if you require ink/materials.

### 3. Conveyor Belt Shop Fronts
Let players set up automated shops using Create deployers/belts. A "Price Tag" block or item frame variant that, when items pass through, automatically charges/pays the shop owner via the API. Vending machine vibes.

### 4. Create Schematic Cost Estimation
Hook into Create's schematicannon. Before printing a schematic, estimate material cost in currency and optionally charge the player.

## New Blocks & Items

### 5. Card Reader Block
Lang key for `card_reader` already exists in en_us.json. A redstone-emitting block: swipe a bank card, it emits a redstone signal if the player has sufficient balance (configurable threshold). Opens up redstone-gated doors, vaults, VIP areas.

### 6. Cash Register Block
Player-to-player trading block. One player places items, the other sets a price, both confirm. Escrow-style with the API handling the transaction. Safer than `/pay` + trust.

### 7. Safe / Vault Block
Physical currency storage with combination lock. Unlike ender chests, these are breakable/raidable — adds risk/reward to hoarding physical bills vs. keeping money in the bank.

### 8. Money Detector (Hopper variant)
A hopper that sorts bills by denomination. Feed a mixed stack of bills in, they get sorted into chests. Useful for mob farm collection systems.

## Economy Systems

### 9. Player Shops / Auction House
`/sell <item> <price>` to list, `/buy` or a Shop GUI block to browse. The backend API already handles auth and transactions, so this is mostly a new set of endpoints + GUI.

### 10. Interest / Savings Accounts
Daily interest on banked funds (compound or simple, configurable). Incentivizes keeping money in the bank vs. physical bills. Could tie into the `/daily` system.

### 11. Taxes & Fees
Configurable transaction tax on `/pay`, withdrawal fees at ATMs, death tax (lose % of carried cash on death). Creates money sinks to fight inflation.

### 12. Bounty System
`/bounty <player> <amount>` — place a bounty on someone. Killer collects. PvP economy integration.

## Enchantment & Progression

### 13. Expand Capitalist Greed
Levels 4-5 currently do nothing. Could add effects like: L4 = mobs drop higher denominations ($10/$20), L5 = rare chance of $100 bill from boss mobs (Warden, Elder Guardian).

### 14. Midas Touch Enchantment
Pickaxe enchantment: small chance to convert mined ores into currency instead of drops. Iron ore -> $5, diamond ore -> $50, etc.

### 15. More Mob Drops
Enderman, Piglin, Guardian, Wither, Ender Dragon as drop sources. Boss mobs could drop $500/$1000 bills at low rates.

## Social / Fun

### 16. Gambling Table Block
Blackjack or dice game GUI, similar in style to the ATM. Play against the house (server) with configurable odds. Money sink.

### 17. Leaderboard Scoreboard Integration
Push `/baltop` data to the vanilla scoreboard sidebar so wealth rankings are always visible.

### 18. Pay-to-Vote Upgrades
Expand the vote system: pay currency to start special votes (e.g., vote to double mob drops for 10 minutes, vote for keep-inventory for 30 minutes).
