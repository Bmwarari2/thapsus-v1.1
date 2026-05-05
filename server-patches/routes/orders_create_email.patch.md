# `routes/orders.js` — fire `sendOrderCreatedEmail` on customer order create

Two small additions to `Swiftcargo-main/routes/orders.js`. The admin
`create-for-client` endpoint already does this; this brings the customer
endpoint in line so every order — however it was created — produces a
confirmation email.

## 1. Top of file — add the import

Find the existing imports block at the top and add:

```js
import { sendOrderCreatedEmail } from '../utils/email.js';
```

## 2. `POST /` handler — fire the email after the success response prep

In `router.post('/', authMiddleware, async (req, res) => { … })`, locate
the block right before the `res.status(201).json(…)` call:

```js
    // Push to the customer who placed the order + all admins
    pushToUser(userId, 'order_update', { action: 'created', order });
    pushToAdmins('admin_stats', { action: 'new_order', order });

    res.status(201).json({ success: true, message: 'Order created successfully', order });
```

Insert this block immediately above the `res.status(201).json(…)` line:

```js
    // Auto-generated confirmation email — same template the admin
    // create-for-client flow uses, so customer-initiated orders get the
    // same receipt. Non-fatal: if Gmail/SMTP isn't configured we log and
    // continue so the order still succeeds.
    try {
      const userRow = await db.query('SELECT email, name FROM users WHERE id = $1', [userId]);
      const customer = userRow.rows[0];
      if (customer?.email) {
        const appUrl = process.env.APP_URL || 'https://www.thapsus.uk';
        const orderForEmail = {
          user_id: userId,
          shipping_cost: costBreakdown.breakdown?.base_shipping?.amount || 0,
          handling_fee:
            (costBreakdown.breakdown?.electronics_handling?.amount || 0) +
            (costBreakdown.breakdown?.handling_fee?.amount || 0),
          insurance_fee: costBreakdown.breakdown?.insurance?.amount || 0,
          customs_duty: costBreakdown.breakdown?.customs_estimate?.amount || 0,
          estimated_cost: costBreakdown.total || 0,
          actual_cost: null,
        };
        sendOrderCreatedEmail(
          customer.email, customer.name || 'Customer',
          trackingNumber, retailer, market, description,
          speed, `${appUrl}/orders`, orderForEmail
        ).catch((err) => console.warn('Order created email failed (non-fatal):', err.message));
      }
    } catch (mailErr) {
      console.warn('Order created email lookup failed (non-fatal):', mailErr.message);
    }
```

## Verifying the email actually sends

After deploying, the iOS Admin Console → Email service card should still
show three green ✓s with non-zero `len` values. If it does and you still
don't get a receipt:

1. Tap **Test send** in the iOS Admin dashboard. If that succeeds, the
   problem is in the order path, not the SMTP credentials.
2. Check Railway logs for `Order created email failed` — that will print
   the underlying Gmail rejection reason (rate-limit, sender-mismatch,
   etc.).
3. Confirm the customer record has a non-empty `email` column —
   `sendOrderCreatedEmail` early-returns when `toEmail` is falsy.
