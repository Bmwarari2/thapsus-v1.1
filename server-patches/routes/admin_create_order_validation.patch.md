# `routes/admin.js` — surface the offending field on order-create 23514s

## Why

Webapp PostgreSQL log:

```
error: new row for relation "orders" violates check constraint
detail: 'Failing row contains (… , Bra, 3, pending, null, f, …).'
code: '23514'
routine: 'ExecConstraints'
```

The constraint name isn't echoed, the failing column isn't named, and
the iOS banner only shows "Failed to create order for client". Cause is
something on the request body that doesn't satisfy the table CHECKs —
in this incident, `market = '3'` looked numeric where the column expects
`'UK'` or `'China'`. The handler should reject these *before* the INSERT
with a message that names the field.

## 1. Replace the existing validators in `POST /admin/orders/create-for-client`

Find the block that currently reads:

```js
    if (!retailer || !market || !description)
      return res.status(400).json({ success: false, message: 'Missing required fields' });
    if (!['UK','China'].includes(market))
      return res.status(400).json({ success: false, message: 'Invalid market. Must be UK or China' });
    const speed = shipping_speed || 'economy';
    if (!['economy','express'].includes(speed))
      return res.status(400).json({ success: false, message: 'Invalid shipping speed' });
```

Replace with:

```js
    const trimmedRetailer    = typeof retailer    === 'string' ? retailer.trim()    : '';
    const trimmedDescription = typeof description === 'string' ? description.trim() : '';
    const trimmedMarket      = typeof market      === 'string' ? market.trim()      : '';
    const speed              = (typeof shipping_speed === 'string' && shipping_speed.trim()) || 'economy';

    if (!trimmedRetailer)
      return res.status(400).json({ success: false, message: 'Retailer is required.' });
    if (!trimmedDescription)
      return res.status(400).json({ success: false, message: 'Description is required.' });
    if (!['UK','China'].includes(trimmedMarket))
      return res.status(400).json({
        success: false,
        message: `Invalid market "${market}" — expected one of: UK, China.`
      });
    if (!['economy','express'].includes(speed))
      return res.status(400).json({
        success: false,
        message: `Invalid shipping_speed "${shipping_speed}" — expected one of: economy, express.`
      });
    if (weight_kg !== undefined && weight_kg !== null && (Number.isNaN(Number(weight_kg)) || Number(weight_kg) < 0))
      return res.status(400).json({
        success: false,
        message: `Invalid weight_kg "${weight_kg}" — must be a non-negative number.`
      });
    if (declared_value !== undefined && declared_value !== null && (Number.isNaN(Number(declared_value)) || Number(declared_value) < 0))
      return res.status(400).json({
        success: false,
        message: `Invalid declared_value "${declared_value}" — must be a non-negative number.`
      });

    // From here on, prefer the trimmed copies.
    const requestRetailer    = trimmedRetailer;
    const requestMarket      = trimmedMarket;
    const requestDescription = trimmedDescription;
```

Then in the `INSERT INTO orders (…)` and `sendOrderCreatedEmail(…)` calls
further down, swap the original `retailer`, `market`, `description`
identifiers for `requestRetailer`, `requestMarket`, `requestDescription`.

## 2. Surface the constraint name when an INSERT does fail

The catch block at the bottom of the handler currently logs with no
context. Replace:

```js
  } catch (error) {
    console.error('Create order for client error:', error);
    res.status(500).json({ success: false, message: 'Failed to create order for client' });
  }
```

with:

```js
  } catch (error) {
    console.error('Create order for client error:', error);
    if (error?.code === '23514') {
      return res.status(400).json({
        success: false,
        message: `Order rejected by DB CHECK constraint ${error.constraint || '(unnamed)'} — ${error.detail || error.message}`
      });
    }
    if (error?.code === '23502') {
      return res.status(400).json({
        success: false,
        message: `Order rejected — required column ${error.column || '(unknown)'} was null.`
      });
    }
    res.status(500).json({ success: false, message: 'Failed to create order for client' });
  }
```

## 3. Make the GET /exchange-rates handler degrade instead of 500

Find:

```js
router.get('/exchange-rates', authMiddleware, isAdmin, async (req, res) => {
  try {
    const db = req.db;
    const rates = await db.query('SELECT currency_pair, rate, updated_at FROM exchange_rates');
    …
  } catch (error) {
    console.error('Get exchange rates error:', error);
    res.status(500).json({ success: false, message: 'Failed to fetch exchange rates' });
  }
});
```

Replace with:

```js
router.get('/exchange-rates', authMiddleware, isAdmin, async (req, res) => {
  try {
    const db = req.db;
    const exists = await db.query(
      `SELECT to_regclass('public.exchange_rates') AS reg`
    );
    if (!exists.rows[0]?.reg) {
      return res.status(503).json({
        success: false,
        message: 'exchange_rates table not provisioned — run database/migrations/006_exchange_rates_alignment.sql.',
        rates: { USD_KES: 130.5, GBP_KES: 164.2, EUR_KES: 142.8, CNY_KES: 18.2 },
      });
    }
    const colCheck = await db.query(
      `SELECT 1 FROM information_schema.columns
        WHERE table_schema='public' AND table_name='exchange_rates' AND column_name='currency_pair'`
    );
    if (colCheck.rowCount === 0) {
      return res.status(503).json({
        success: false,
        message: 'exchange_rates is missing the currency_pair column — run database/migrations/006_exchange_rates_alignment.sql.',
        rates: { USD_KES: 130.5, GBP_KES: 164.2, EUR_KES: 142.8, CNY_KES: 18.2 },
      });
    }
    const rates = await db.query(
      'SELECT currency_pair, rate, updated_at FROM exchange_rates'
    );
    const ratesObj = {};
    let latestUpdate = null;
    rates.rows.forEach(r => {
      ratesObj[r.currency_pair] = parseFloat(r.rate);
      if (!latestUpdate || r.updated_at > latestUpdate) latestUpdate = r.updated_at;
    });
    if (rates.rows.length === 0) {
      Object.assign(ratesObj, { USD_KES: 130.5, GBP_KES: 164.2, EUR_KES: 142.8, CNY_KES: 18.2 });
    }
    res.json({ success: true, rates: ratesObj, updated_at: latestUpdate });
  } catch (error) {
    console.error('Get exchange rates error:', error);
    res.status(500).json({ success: false, message: 'Failed to fetch exchange rates' });
  }
});
```

After deploy, also run `database/migrations/006_exchange_rates_alignment.sql`
in the Supabase SQL Editor. The handler will then succeed for real.
