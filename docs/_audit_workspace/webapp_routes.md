# Webapp routes inventory — 2026-04-28

Source: walk of `/tmp/sc-fix/Swiftcargo-main/routes/*.js` on branch `JS1` (commit a6302df).
Total: 125 endpoints across 27 files.

## routes/auth.js (mount: `/api/auth`)
- POST `/register` — none — body `{name,email,password,phone,referral_code?}` → `{success,token,supabase_token,supabase_token_expires_at,user}` — INSERT users + wallet (+ referrals if referral_code valid).
- POST `/login` — none — `{email,password}` → `{success,token,supabase_token,supabase_token_expires_at,user}` — SELECT users (requires is_active).
- GET `/me` — auth — → `{success,user{...}}` — SELECT users WHERE id.
- PUT `/profile` — auth — `{name?,phone?,language_pref?}` → `{success,user{...}}` — UPDATE users.
- PUT `/password` — auth — `{current_password,new_password}` → `{success,message}` — UPDATE users.
- POST `/reset-password` — none — `{token,new_password}` → `{success,message}` — UPDATE users + password_reset_tokens.
- POST `/forgot-password` — none — `{email}` → `{success:true,...}` (always 200) — INSERT password_reset_tokens; **email**: sendPasswordResetEmail.
- POST `/supabase-token` — auth — none → `{success,supabase_token,supabase_token_expires_at}`.

## routes/orders.js (mount: `/api/orders`)
- GET `/` — auth — query `{page,limit,status,market}` → `{success,orders[],pagination}` — SELECT COUNT, SELECT orders. **COUNT(*)::int.**
- POST `/` — auth — `{retailer,market,description,weight_kg?,dimensions?,shipping_speed?,insurance?,declared_value?}` → `{success,order{...,cost_breakdown}}` — INSERT orders + packages; UPDATE referrals (if pending) + users wallet + wallet table; INSERT transactions (referral credit ×2). **Push**: `pushToUser(userId,'order_update',{action:'created',order})`, `pushToAdmins('admin_stats',{action:'new_order',order})`. Generates tracking_number. **Referral auto-reward** KES 50 to both parties.
- GET `/:id` — auth — → `{success,order{packages[],cost_breakdown}}`.
- PUT `/:id/status` — auth+isAdmin — `{status,actual_cost?,customs_duty?}` → `{success,order}` — Push: order_update + admin_stats.

## routes/admin.js (mount: `/api/admin`)
- GET `/users` — auth+isAdmin — query `{page,limit,search,role}` → `{success,users[],pagination}`. **COUNT(*)::int**.
- GET `/users/search` — auth+isAdmin — query `q` → `{success,customers[]}` (max 10).
- GET `/users/:id` — auth+isAdmin — → `{success,user{ordersCount,orders[]},recentTransactions[],referralStats{total_referrals,completed_referrals,pending_referrals,total_earned}}`. **SUM(CASE)/COUNT aggregates**.
- PUT `/users/:id` — auth+isAdmin — `{role?,is_active?,delivery_address?,admin_notes?}` → `{success,user}`.
- DELETE `/users/:id` — auth+isAdmin — Cascades through tickets, packages, orders, transactions, wallet, referrals.
- POST `/test-email` — auth+isAdmin — `{to?}` → `{success,email_config{...}}`. Email: sendPasswordResetEmail (test).
- GET `/referrals/stats` — auth+isAdmin — → `{success,stats{total_referrals,completed_referrals,pending_referrals,total_rewards_paid},top_referrers[]}`. **COUNT/SUM aggregates**.
- GET `/referrals` — auth+isAdmin — → `{success,referrals[],pagination}`.
- GET `/orders` — auth+isAdmin — query `{page,limit,status,market,startDate,endDate}` → `{success,orders[],pagination}`.
- PUT `/orders/bulk-update` — auth+isAdmin — `{order_ids[],status}` → `{success,updated_count,orders[]}`. Push per order + admin_stats.
- PUT `/orders/:id/edit` — auth+isAdmin — `{weight_kg?,dimensions?,actual_cost?,customs_duty?,status?,description?,retailer?,electronics_item?,order_notes?}` → `{success,order}`. Email: sendOrderUpdatedEmail. Push: order_update + admin_stats.
- GET `/stats` — auth+isAdmin — → `{success,stats{users{...,new_today},orders{...,new_today,active_orders},markets[],order_statuses[],revenue{...},referrals{...},daily_orders[]}}`. **Multiple COUNT(CASE), AVG, SUM aggregates → must be tolerant.** This is the endpoint behind the /admin/stats deserialise crash.
- GET `/revenue` — auth+isAdmin — → `{success,revenue[],summary[]}`.
- GET `/revenue/export` — auth+isAdmin — → CSV.
- GET `/logs` — auth+isAdmin — → `{success,logs[],pagination}`.
- POST `/users/:id/reset-password` — auth+isAdmin — → `{success,reset_link}`. Email: sendAdminPasswordResetEmail.

## routes/wallet.js (mount: `/api/wallet`)
- GET `/` — auth — → `{success,wallet{...},recent_transactions[]}` (last 5).
- GET `/mpesa-info` — auth — → `{success,mpesa{paybill,account,business_name,instructions[]}}` (static placeholders).
- POST `/mpesa-confirm` — auth — `{mpesa_message,order_id?,amount}` → `{success,transaction_id,payment_reference}`. INSERT transactions(status='pending'); best-effort admin notifications.
- POST `/pay` — auth — `{order_id,amount}` → `{success,transaction_id,amount_paid,order_id,new_balance}`. FOR UPDATE locks.
- GET `/transactions` — auth — query `{page,limit,type,status}` → `{success,transactions[],pagination}`.

## routes/tracking.js (mount: `/api/tracking`)
- GET `/user/packages` — auth — query `{page,limit,status}` → `{success,packages[],pagination}`.
- GET `/:trackingNumber` — optionalAuth — → `{success,tracking{...,packages[]}}`. **Public**.
- PUT `/:id/status` — auth+isAdmin — `{status,warehouse_location?}` → `{success,package}`. sendInAppNotification.

## routes/payment.js (mount: `/api/payment`)
- GET `/:orderId` — none (public) — → `{success,order{id,tracking_number,amount_due,status},mpesa_info{paybill}}`.
- POST `/:orderId/confirm` — none — `{mpesa_message,amount,payer_name?,payer_phone?}` → `{success,transaction_id}`. INSERT transactions+admin_logs+notifications (best-effort).

## routes/referral.js (mount: `/api/referral`)
- GET `/` — auth — → `{success,referral{referral_code,current_balance,statistics{total_referrals,completed_referrals,pending_referrals,total_earned}},referred_users[]}`. **Multiple aggregates**.
- GET `/history` — auth — → `{success,referrals[],pagination}`.
- POST `/validate` — none — `{referral_code}` → `{success:true,valid:bool,referrer_name?}`.

## routes/exchange.js (mount: `/api/exchange`)
- GET `/rates` — none — → `{success,data{USD_KES,GBP_KES,EUR_KES,CNY_KES,KES_USD,...,source,timestamp,last_updated}}`. Falls back to hardcoded DEFAULT_RATES if rates missing.
- POST `/convert` — none — `{amount,from_currency,to_currency}` → `{success,conversion{rate,converted_amount,timestamp}}`.

## routes/tickets.js (mount: `/api/tickets`)
- GET `/` — auth — query `{page,limit,status,priority}` → `{success,tickets[],pagination}` (own only).
- POST `/` — auth — `{subject,description,priority?}` → `{success,ticket}`. Email sendTicketCreatedEmail. Push pushToAdmins.
- GET `/:id` — auth — → `{success,ticket,messages[]}`.
- POST `/:id/message` — auth — `{message}` → `{success,message_id}`. Email sendTicketReplyEmail (admin-side). Push to user or admins.
- PUT `/:id/status` — auth+isAdmin — `{status,admin_message?}` → `{success,ticket}`. Push.
- GET `/admin/all` — auth+isAdmin — → `{success,tickets[],pagination}`.

## routes/pricing.js (mount: `/api/pricing`)
- POST `/calculate` — none — `{weight_kg?,dimensions?,market,shipping_speed?,insurance?,declared_value?,electronics_item?}` → `{success,pricing{total,breakdown}}`.
- GET `/electronics` — none — → `{success,items[{key,label,fee_gbp,min_weight_kg}]}`. Static.
- GET `/rates` — none — → `{success,rates{UK,USA,China}}`. Falls back to defaults.
- PUT `/rates` — auth+isAdmin — `{rates{UK,USA,China}}` → `{success,rates}`. Creates `shipping_rates` if missing.
- PUT `/electronics` — auth+isAdmin — `{fees{phone,laptop,tv_monitor}}` → `{success,fees}`. Creates `electronics_fees` if missing.

## routes/consolidation.js (mount: `/api/consolidation`)
- GET `/` — auth — → `{success,packages_waiting,total_weight_kg,packages[]}`.
- GET `/requests` — auth — → `{success,requests[{id,packageCount,status,createdAt,completedAt}]}`. **COUNT, MIN, MAX, BOOL_AND aggregates**.
- POST `/request` — auth — `{package_ids[]}` → `{success,consolidation{...}}`.
- GET `/:id` — auth — → `{success,consolidation{...,packages[]}}`.

## routes/prohibited.js (mount: `/api/prohibited`)
- GET `/check` — none — query `item` → `{success,item,check{allowed,reason,category,risk_level}}`.
- GET `/categories` — none — → `{success,categories[]}`.
- GET `/categories/:category` — none — → `{success,category{...}}`.
- GET `/search` — none — query `{q,language?}` → `{success,items[]}`.
- POST `/` — auth+isAdmin — `{term,severity?,jurisdiction?,language?,reason?}` → `{success,id}`.
- PATCH `/:id` — auth+isAdmin — partial → `{success}`.
- DELETE `/:id` — auth+isAdmin — → `{success}`.

## routes/events.js (mount: `/api/events`)
- GET `/` — auth — Server-Sent Events stream. Heartbeat every 25s. Helpers `pushToUser(userId,type,data)` and `pushToAdmins(type,data)` are exported and consumed by other routes. Events fired: `order_update`, `ticket_update`, `notification`, `wallet_update`, `admin_stats`.

## routes/backup.js (mount: `/api/admin/backups`)
- POST `/` — auth+isAdmin — → `{success,backup{...}}`. Records logical backup note (Supabase manages physical backups).
- GET `/` — auth+isAdmin — → `{success,backups[],pagination}`.

## routes/sitemap.js (mount: `/`)
- GET `/sitemap.xml` — none — → XML.
- GET `/robots.txt` — none — → text.

## routes/warehouse.js (mount: `/api/warehouse`)
- GET `/addresses` — auth — → `{success,addresses{UK{...},China{...}},tcCode}`.

## routes/notifications.js (mount: `/api/notifications`)
- GET `/` — auth — query `{limit,offset}` → `{success,notifications[],unread}`. **unread:int**.
- PUT `/:id/read` — auth — → `{success}`.
- PUT `/read-all` — auth — → `{success}`.

## routes/consolidationsV2.js (mount: `/api/consolidations`)
- GET `/current` — none — → `{success,consolidation{id,week_start,cutoff_at,departure_at,total_kg,total_parcels}|null}`.
- GET `/customer/:id` — auth — → `{success,consolidation{...}}` (ownership-checked).
- GET `/` — auth+requireRole('operator') — query `status?` → `{success,consolidations[]}`.
- POST `/` — auth+requireRole('operator') — `{week_start,cutoff_at,departure_at?,notes?}` → `{success,consolidation_id}`.
- GET `/:id` — auth+requireRole('operator') — → `{success,consolidation{...},parcels[],pallets[],manifests[]}`. 🚨 **route read truncated; verify.**

## routes/customs.js (mount: `/api/customs`)
- GET `/agent/consolidations` — auth+requireRole('clearing_agent') — → `{success,consolidations[]}`.
- GET `/agent/consolidations/:id/parcels` — auth+requireRole('clearing_agent') — → `{success,parcels[]}` (with customs_entries).
- POST `/entries` — auth+requireRole('clearing_agent') — `{parcel_id,idf_no?,entry_no?,cif_kes?,duty_kes?,vat_kes?,idf_kes?,rdl_kes?,status?,notes?}` → `{success,entry_id}`.
- PATCH `/entries/:id` — auth+requireRole('clearing_agent') — partial → `{success}` (cascades order to 'released').

## routes/insurance.js (mount: `/api/insurance`)
- POST `/quote` — none — `{tier,declared_value_gbp}` → `{success,quote{premium_gbp,max_cover_gbp,requires_manual_review?}}`.
- POST `/policies` — auth — `{parcel_id,tier,declared_value_gbp?}` → `{success,policy{...}}`.
- GET `/policies` — auth — → `{success,policies[]}` (own).
- POST `/policies/:id/claim` — auth — `{claim_amount_gbp?,notes?}` → `{success}`.

## routes/lastMile.js (mount: `/api/last-mile`)
- GET `/dispatch` — auth+requireRole('operator') — → `{success,pending[],runs[],zones[]}`.
- POST `/runs` — auth+requireRole('operator') — `{rider_id?,zone,run_date,parcel_ids?}` → `{success,run_id}`.
- PATCH `/runs/:id` — auth+requireRole('operator') — partial → `{success}`.
- GET `/rider/today` — auth+requireRole('rider') — → `{success,runs[]}`.

## routes/kpi.js (mount: `/api/kpi`)
- GET `/` — auth+requireRole('admin') — → `{success,kpi{kg_this_week,kg_last_week,kg_trend_pct,parcels_this_week,on_time_pct,complaints_per_100,nps_avg,nps_responses,wallet_kes,pending_inbound,insurance_claims_gbp,insurance_premiums_gbp}}`. **All loose floats/ints**.
- GET `/marketing` — auth+requireRole('admin') — → `{success,utm[],retention_90d{cohort_size,repeat_in_90d,pct}}`. **Loose**.

## routes/dsar.js (mount: `/api/dsar`)
- POST `/` — auth — `{type,notes?}` → `{success,request_id,due_at,request{...}}`. 503 if table missing.
- GET `/me` — auth — → `{success,requests[]}`.
- GET `/queue` — auth+requireRole('admin') — → `{success,requests[]}`.
- PATCH `/:id` — auth+requireRole('admin') — `{status,notes?,export_url?}` → `{success}`.
- POST `/:id/export` — auth+requireRole('admin') — → `{success,export{generated_at,user{...},orders[],transactions[],tickets[]}}`.

## routes/buyForMe.js (mount: `/api/buy-for-me`)
- POST `/` — auth — `{retailer_url,item_name,size?,qty?,notes?}` → `{success,order_id}`.
- GET `/` — auth — → `{success,orders[]}` (own).
- GET `/queue` — auth+requireRole('operator') — → `{success,orders[]}`.
- GET `/:id` — auth — → `{success,order{...}}`.
- POST `/:id/pay` — auth — → `{success}` or 402. **Reads exchange_rates GBP_KES**, FOR UPDATE locks. 🚨 read truncated; tail of route may include cancel handling.

## routes/ops.js (mount: `/api/ops`)
- GET `/today` — auth+requireRole('operator') — → `{success,today{expected,received,consolidating,in_transit,held}}`. **All COUNT(*)::int**.
- GET `/parcels` — auth+requireRole('operator') — query `{status,q}` → `{success,parcels[]}`.
- POST `/parcels/:id/receive` — auth+requireRole('operator') — `{weight_kg?,dimensions?,photo_url?,barcode?}` → `{success,weight_kg,volumetric_kg,chargeable_kg}`. UPDATE orders + packages.
- POST `/parcels/:id/screen` — auth+requireRole('operator') — `{description}` → `{success,screening_result,screening_details}`. 🚨 read truncated.

## routes/pricingTiers.js (mount: `/api/pricing-tiers`)
- GET `/tiers` — none — → `{success,tiers[]}`. **`gbp_per_kg:real`**.
- POST `/tiers` — auth+requireRole('admin') — `{channel,min_kg,max_kg,gbp_per_kg,notes?}` → `{success,id}`.
- PATCH `/tiers/:id` — auth+requireRole('admin') — partial → `{success}`.
- GET `/fees` — none — → `{success,fees[],degraded?,error?}`. Returns degraded=true if fees table missing.
- PATCH `/fees/:id` — auth+requireRole('admin') — `{amount?,is_percentage?,is_active?,label?,currency?,notes?}` → `{success}`.
- GET `/promotions` — auth+requireRole('admin') — → `{success,promotions[]}` (max 100).
- POST `/promotions` — auth+requireRole('admin') — `{code,type,value,valid_from?,valid_to?,max_uses?,description?}` → 🚨 read truncated.

## routes/nps.js (mount: `/api/nps`)
- POST `/` — auth — `{score,comment?,parcel_id?}` → `{success}`.
- GET `/summary` — auth+requireRole('admin') — → `{success,summary{total,avg_score,promoters,passives,detractors,nps}}`. **Loose**.

## routes/agentInvoices.js (mount: `/api/agent-invoices`)
- GET `/mine` — auth+requireRole('clearing_agent') — → `{success,invoices[]}`.
- POST `/` — auth+requireRole('clearing_agent') — `{consolidation_id?,invoice_no?,amount_kes,doc_url?,notes?}` → `{success,invoice_id}`.
- GET `/` — auth+isAdmin — query `status?` → `{success,invoices[]}` (max 200).
- PATCH `/:id` — auth+isAdmin — `{status,notes?}` → `{success}` (sets paid_at).

## routes/amlFlags.js (mount: `/api/admin/aml-flags`)
- GET `/` — auth+isAdmin — query `status?` → `{success,flags[]}` (max 200).
- POST `/` — auth+isAdmin — `{user_id,parcel_id?,reason,notes?}` → `{success,id}`.
- PATCH `/:id` — auth+isAdmin — `{status,notes?}` → `{success}`.

---

## Loose-numeric candidates (Phase 2 hardening)

Endpoints that return server-side aggregates (COUNT, SUM, AVG, COUNT FILTER, BOOL_AND), often via pg-node which string-encodes BIGINT/NUMERIC and may emit nulls on empty tables. Each must use `LooseInt`/`LooseLong`/`LooseDouble` in the iOS DTO:

- `GET /api/admin/stats` — heaviest aggregator; **the original crash site**.
- `GET /api/admin/users/:id` — referralStats.{total,completed,pending,total_earned}.
- `GET /api/admin/referrals/stats` — stats + top_referrers.
- `GET /api/admin/users` — pagination.total.
- `GET /api/admin/orders` — pagination.total.
- `GET /api/orders` — pagination.total.
- `GET /api/notifications` — unread.
- `GET /api/referral` — statistics.{total_referrals,completed_referrals,pending_referrals,total_earned}; referred_users[].orders_count.
- `GET /api/referral/history` — pagination.total.
- `GET /api/kpi` — every field on the kpi object (kg/parcels/percentages).
- `GET /api/kpi/marketing` — utm[].signups, retention_90d.{cohort_size,repeat_in_90d,pct}.
- `GET /api/nps/summary` — every field on summary (total, avg_score, promoters, passives, detractors, nps).
- `GET /api/ops/today` — every field on today object.
- `GET /api/ops/parcels/:id/receive` — weight_kg/volumetric_kg/chargeable_kg recomputed.
- `GET /api/consolidation` — total_weight_kg.
- `GET /api/consolidation/requests` — packageCount and aggregated booleans.
- `GET /api/consolidations/current` — total_kg, total_parcels.
- `GET /api/wallet` — balance and recent_transactions[].amount.
- `GET /api/wallet/transactions` — pagination.total.
- `GET /api/tracking/user/packages` — pagination.total.
- `GET /api/admin/error-logs` — pagination.total + stats endpoint.

## Side-effects worth surfacing to iOS

Every push helper is `pushToUser(userId, type, data)` or `pushToAdmins(type, data)` over the SSE stream at `GET /api/events`:
- `order_update` — fired on order create, status change, edit, cancel.
- `ticket_update` — fired on ticket create, message, status change.
- `notification` — generic, currently unused by routes (memory says iOS only polls).
- `wallet_update` — fired on referral auto-credit.
- `admin_stats` — fired on order create, status change, edit, bulk update.

Email fan-outs:
- sendPasswordResetEmail (forgot + admin reset)
- sendOrderUpdatedEmail (admin order edit)
- sendTicketCreatedEmail (ticket create, to SUPPORT_EMAIL)
- sendTicketReplyEmail (admin reply)
- sendInAppNotification (tracking status update)
- sendOrderCreatedEmail — staged in `server-patches/routes/orders_create_email.patch.md`, **not yet applied** to JS1 main.

## 🚨 Source-read truncations

Four route handlers were not fully captured by the agent — verify before relying on the inventory:
1. `POST /api/ops/parcels/:id/screen`
2. `POST /api/buy-for-me/:id/pay`
3. `POST /api/pricing-tiers/promotions`
4. `GET /api/consolidations/:id`

I'll re-read these directly during synthesis, before writing the final audit.
