import express from "express";
import jwt from "jsonwebtoken";

const app = express();
const PORT = 5000;
const JWT_SECRET = "CHANGE-ME-must-be-at-least-32-chars"; // Must match mod config
const MOD_AUDIENCE = "createrington.mod";

app.use(express.json());

// ---- In-memory state -------------------------------------------------------

const STARTING_BALANCE = 1000;
const DAILY_AMOUNT = 250;
const DAILY_COOLDOWN_MS = 24 * 60 * 60 * 1000;
const MAX_HISTORY_LIMIT = 20;

// uuid -> { name, balance, lastDaily, transactions }
const players = new Map();
let txIdCounter = 1;

// null | { entryAmount, endsAt, pot, participants: Set<uuid> }
let lottery = null;

function getOrCreatePlayer(uuid, name) {
  if (!players.has(uuid)) {
    players.set(uuid, {
      name: name || uuid,
      balance: STARTING_BALANCE,
      lastDaily: null,
      transactions: [],
    });
  } else if (name) {
    players.get(uuid).name = name;
  }
  return players.get(uuid);
}

function addTx(player, amount, balanceBefore, balanceAfter, type, description) {
  player.transactions.unshift({
    id: txIdCounter++,
    amount: String(amount),
    balanceBefore: String(balanceBefore),
    balanceAfter: String(balanceAfter),
    transactionType: type,
    description: description || null,
    createdAt: new Date().toISOString(),
  });
}

function fmt(n) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(n);
}

// ---- JWT middleware ---------------------------------------------------------

function verifyJWT(req, res, next) {
  const authHeader = req.headers["authorization"];
  if (!authHeader) {
    return res.status(401).json({ success: false, message: "Missing Authorization header" });
  }
  const token = authHeader.split(" ")[1];
  if (!token) {
    return res.status(401).json({ success: false, message: "Invalid Authorization format" });
  }
  try {
    req.user = jwt.verify(token, JWT_SECRET, { audience: MOD_AUDIENCE });
    next();
  } catch (err) {
    return res.status(403).json({ success: false, message: "Invalid or expired token", error: { message: err.message } });
  }
}

// ---- Currency endpoints ----------------------------------------------------

// GET /api/currency/balance
app.get("/api/currency/balance", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const player = getOrCreatePlayer(uuid, name);
  console.log(`[balance] ${player.name} (${uuid}) → ${player.balance}`);
  res.json({
    success: true,
    message: "Balance retrieved",
    playerMessage: null,
    data: { balance: player.balance },
  });
});

// POST /api/currency/pay
// Body: { toUuid: string, amount: number }
app.post("/api/currency/pay", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const { toUuid, amount } = req.body;
  const sender = getOrCreatePlayer(uuid, name);

  if (uuid === toUuid) {
    return res.status(400).json({ success: false, message: "Cannot transfer to self" });
  }
  if (!amount || amount <= 0) {
    return res.status(400).json({ success: false, message: "Invalid amount" });
  }
  if (sender.balance < amount) {
    return res.status(400).json({
      success: false,
      message: "Insufficient balance",
      playerMessage: `You only have ${fmt(sender.balance)}.`,
    });
  }

  const recipient = getOrCreatePlayer(toUuid);
  const senderBefore = sender.balance;
  const recipientBefore = recipient.balance;

  sender.balance -= amount;
  recipient.balance += amount;

  addTx(sender, -amount, senderBefore, sender.balance, "transfer_out", `Paid ${fmt(amount)} to ${recipient.name}`);
  addTx(recipient, amount, recipientBefore, recipient.balance, "transfer_in", `Received ${fmt(amount)} from ${sender.name}`);

  console.log(`[pay] ${sender.name} → ${recipient.name}: ${amount} (sender now: ${sender.balance})`);
  res.json({
    success: true,
    message: "Payment successful",
    playerMessage: `Paid ${fmt(amount)} to ${recipient.name}.`,
    data: { new_sender_balance: sender.balance },
  });
});

// POST /api/currency/deposit
// Body: { amount: number, reason?: string }
app.post("/api/currency/deposit", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const { amount, reason } = req.body;
  const player = getOrCreatePlayer(uuid, name);

  if (!amount || amount <= 0) {
    return res.status(400).json({ success: false, message: "Invalid amount" });
  }

  const before = player.balance;
  player.balance += amount;
  addTx(player, amount, before, player.balance, "deposit", reason || "Deposit");

  console.log(`[deposit] ${player.name}: +${amount} (now: ${player.balance})`);
  res.json({
    success: true,
    message: "Deposit successful",
    playerMessage: `Deposited ${fmt(amount)}.`,
    data: { new_balance: player.balance },
  });
});

// POST /api/currency/withdraw
// Body: { denomination: number, count: number }
app.post("/api/currency/withdraw", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const { denomination, count } = req.body;
  const player = getOrCreatePlayer(uuid, name);

  if (!denomination || denomination <= 0 || !count || count <= 0 || !Number.isInteger(count)) {
    return res.status(400).json({ success: false, message: "Invalid denomination or count" });
  }
  const total = denomination * count;
  if (player.balance < total) {
    return res.status(400).json({
      success: false,
      message: "Insufficient balance",
      playerMessage: `Need ${fmt(total)} but you only have ${fmt(player.balance)}.`,
    });
  }

  const before = player.balance;
  player.balance -= total;
  addTx(player, -total, before, player.balance, "withdrawal", `Withdrew ${count}x ${fmt(denomination)}`);

  console.log(`[withdraw] ${player.name}: ${count}x${denomination} = ${total} (now: ${player.balance})`);
  res.json({
    success: true,
    message: "Withdrawal successful",
    playerMessage: `Withdrew ${count}x ${fmt(denomination)}.`,
    data: { withdrawn: total, new_balance: player.balance, denomination, count },
  });
});

// GET /api/currency/top
app.get("/api/currency/top", verifyJWT, (req, res) => {
  const top10 = [...players.entries()]
    .sort((a, b) => b[1].balance - a[1].balance)
    .slice(0, 10)
    .map(([, p]) => ({ name: p.name, balance: p.balance }));
  console.log(`[top] ${top10.length} entries`);
  res.json({ success: true, message: "Top balances retrieved", data: top10 });
});

// GET /api/currency/history?page=1&limit=10
app.get("/api/currency/history", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const page = Math.max(1, parseInt(req.query.page) || 1);
  const limit = Math.min(MAX_HISTORY_LIMIT, Math.max(1, parseInt(req.query.limit) || 10));
  const player = getOrCreatePlayer(uuid, name);

  const start = (page - 1) * limit;
  const transactions = player.transactions.slice(start, start + limit);
  const hasMore = player.transactions.length > start + limit;

  console.log(`[history] ${player.name}: page ${page} limit ${limit} → ${transactions.length} txs`);
  res.json({ success: true, data: { transactions, page, hasMore } });
});

// POST /api/currency/daily
app.post("/api/currency/daily", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const player = getOrCreatePlayer(uuid, name);

  const now = Date.now();
  if (player.lastDaily && now - player.lastDaily < DAILY_COOLDOWN_MS) {
    const remaining = DAILY_COOLDOWN_MS - (now - player.lastDaily);
    const hours = Math.floor(remaining / 3_600_000);
    const minutes = Math.floor((remaining % 3_600_000) / 60_000);
    return res.status(400).json({
      success: false,
      message: "Daily already claimed",
      playerMessage: `Come back in ${hours}h ${minutes}m.`,
      data: { amount: null },
    });
  }

  const before = player.balance;
  player.balance += DAILY_AMOUNT;
  player.lastDaily = now;
  addTx(player, DAILY_AMOUNT, before, player.balance, "daily", "Daily reward");

  console.log(`[daily] ${player.name}: +${DAILY_AMOUNT} (now: ${player.balance})`);
  res.json({
    success: true,
    message: "Daily reward claimed",
    playerMessage: `You received ${fmt(DAILY_AMOUNT)}!`,
    data: { amount: DAILY_AMOUNT },
  });
});

// POST /api/currency/lottery/start
// Body: { amount: number }
app.post("/api/currency/lottery/start", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const { amount } = req.body;
  const player = getOrCreatePlayer(uuid, name);

  if (lottery && lottery.endsAt > new Date()) {
    return res.status(409).json({ success: false, message: "A lottery is already running", playerMessage: "A lottery is already in progress." });
  }
  if (!amount || amount <= 0) {
    return res.status(400).json({ success: false, message: "Invalid entry amount" });
  }
  if (player.balance < amount) {
    return res.status(400).json({ success: false, message: "Insufficient balance", playerMessage: `You need ${fmt(amount)} to start a lottery.` });
  }

  const endsAt = new Date(Date.now() + 5 * 60 * 1000);
  const before = player.balance;
  player.balance -= amount;
  addTx(player, -amount, before, player.balance, "lottery_entry", "Started lottery");

  lottery = { entryAmount: amount, endsAt, pot: amount, participants: new Set([uuid]) };

  console.log(`[lottery/start] ${player.name}: entry ${amount}, ends ${endsAt.toISOString()}`);
  res.json({
    success: true,
    message: "Lottery started",
    playerMessage: `Started a lottery! Entry: ${fmt(amount)}.`,
    data: { entryAmount: amount, endsAt: endsAt.toISOString() },
  });
});

// POST /api/currency/lottery/join
// Body: { amount: number }
app.post("/api/currency/lottery/join", verifyJWT, (req, res) => {
  const { uuid, name } = req.user;
  const { amount } = req.body;
  const player = getOrCreatePlayer(uuid, name);

  if (!lottery || lottery.endsAt <= new Date()) {
    return res.status(404).json({ success: false, message: "No active lottery", playerMessage: "There's no active lottery to join." });
  }
  if (lottery.participants.has(uuid)) {
    return res.status(409).json({ success: false, message: "Already joined", playerMessage: "You've already joined the lottery." });
  }
  if (amount !== lottery.entryAmount) {
    return res.status(400).json({ success: false, message: `Entry amount must be ${lottery.entryAmount}`, playerMessage: `Entry amount is ${fmt(lottery.entryAmount)}.` });
  }
  if (player.balance < amount) {
    return res.status(400).json({ success: false, message: "Insufficient balance", playerMessage: `You need ${fmt(amount)} to join.` });
  }

  const before = player.balance;
  player.balance -= amount;
  addTx(player, -amount, before, player.balance, "lottery_entry", "Joined lottery");

  lottery.pot += amount;
  lottery.participants.add(uuid);

  console.log(`[lottery/join] ${player.name}: joined, pot ${lottery.pot}, participants: ${lottery.participants.size}`);
  res.json({
    success: true,
    message: "Joined lottery",
    playerMessage: `Joined! Pot: ${fmt(lottery.pot)}.`,
    data: { entryAmount: lottery.entryAmount, totalPot: lottery.pot, participantCount: lottery.participants.size },
  });
});

// ---- Trains endpoint -------------------------------------------------------

// POST /api/trains/crash
app.post("/api/trains/crash", verifyJWT, (req, res) => {
  const { trainId, location, speed, reason } = req.body || {};
  console.log("\n=== Train Crash Report ===");
  console.log(`Train ID: ${trainId ?? "unknown"}`);
  console.log(`Location: ${location ? JSON.stringify(location) : "unknown"}`);
  console.log(`Speed: ${speed ?? "unknown"}`);
  console.log(`Reason: ${reason ?? "unknown"}`);
  console.log("==========================\n");
  res.json({ success: true, message: "Crash reported" });
});

// ---- Health check ----------------------------------------------------------

app.get("/health", (req, res) => {
  res.json({ status: "ok", timestamp: new Date().toISOString() });
});

// ---- Error handler ---------------------------------------------------------

app.use((err, req, res, next) => {
  console.error("Error:", err);
  res.status(500).json({ success: false, message: "Internal server error", error: { message: err.message } });
});

// ---- Startup ---------------------------------------------------------------

const testToken = jwt.sign(
  { uuid: "00000000-0000-0000-0000-000000000001", name: "TestPlayer" },
  JWT_SECRET,
  { audience: MOD_AUDIENCE },
);

app.listen(PORT, () => {
  console.log(`Currency API Test Server running on http://localhost:${PORT}`);
  console.log(`
Endpoints:
  GET  /api/currency/balance
  POST /api/currency/pay          { toUuid, amount }
  POST /api/currency/deposit      { amount, reason? }
  POST /api/currency/withdraw     { denomination, count }
  GET  /api/currency/top
  GET  /api/currency/history      ?page=1&limit=10
  POST /api/currency/daily
  POST /api/currency/lottery/start  { amount }
  POST /api/currency/lottery/join   { amount }
  POST /api/trains/crash
  GET  /health
`);
  console.log(`JWT Secret: ${JWT_SECRET}`);
  console.log(`\nTest token for manual requests:\nBearer ${testToken}\n`);
  console.log("Waiting for requests...\n");
});
