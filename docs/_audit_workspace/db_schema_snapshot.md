# Supabase schema snapshot — 2026-04-28

Project: `zzdfxsfuhosuqvsugtfd` (eu-north-1).
Source: `information_schema.columns`, `pg_publication_tables`,
`pg_tables`, `_migrations`.

## Tables (39)

| Table | Cols | Columns (`name:type` — `?` means nullable) |
|---|---|---|
| `_migrations` | 2 | filename:text, applied_at:timestamptz |
| `admin_logs` | 5 | id:text, admin_id:text?, action:text, details:text?, created_at:timestamptz? |
| `agent_invoices` | 11 | id:uuid, agent_id:text, consolidation_id:uuid?, amount_kes_cents:bigint, invoice_no:text?, doc_url:text?, status:enum, paid_at:timestamptz?, created_at:timestamptz, **amount_kes:real?**, notes:text? |
| `aml_flags` | 9 | id:uuid, user_id:text, parcel_id:text?, reason:text, resolved_at:timestamptz?, resolved_by:text?, created_at:timestamptz, status:text?, notes:text? |
| `backups` | 8 | id:text, filename:text, filepath:text, size_bytes:integer, checksum:text, status:text?, created_by:text?, created_at:timestamptz? |
| `buy_for_me_orders` | 13 | id:text, user_id:text, retailer_url:text, item_name:text, size:text?, qty:integer, notes:text?, estimate_gbp:real?, markup_pct:real?, status:text?, parcel_id:text?, created_at:timestamptz?, updated_at:timestamptz? |
| `compliance_trainings` | 9 | id:uuid, user_id:text, course:text, completed_at:timestamptz, expires_at:timestamptz?, certificate_url:text?, cert_url:text?, notes:text?, created_at:timestamptz? |
| `consolidations` | 18 | id:uuid, week_start:date, cutoff_at:timestamptz, departure_at:timestamptz?, arrival_at:timestamptz?, status:enum, total_kg:numeric, total_parcels:integer, master_awb_no:text?, master_awb_pdf_url:text?, tudor_invoice_no:text?, assigned_agent_id:text?, created_at:timestamptz, updated_at:timestamptz, master_awb_pdf:text?, tudor_invoice_pdf:text?, manifest_pdf:text?, notes:text? |
| `customs_entries` | 23 | id:uuid, parcel_id:text, consolidation_id:uuid?, agent_id:text?, idf_no:text?, entry_no:text?, **cif_kes_cents:bigint, duty_kes_cents:bigint, vat_kes_cents:bigint, idf_kes_cents:bigint, rdl_kes_cents:bigint**, status:enum, customer_invoice_id:uuid?, released_at:timestamptz?, created_at:timestamptz, updated_at:timestamptz, **cif_kes:real?, duty_kes:real?, vat_kes:real?, idf_kes:real?, rdl_kes:real?**, admin_fee_kes:real?, notes:text? |
| `dsar_requests` | 9 | id:uuid, user_id:text, type:enum, status:enum, due_at:timestamptz, fulfilled_at:timestamptz?, notes:text?, created_at:timestamptz, export_url:text? |
| `email_logs` | 8 | id:text, user_id:text?, email_to:text, email_type:text, subject:text, status:text?, error_message:text?, created_at:timestamptz? |
| `error_logs` | 11 | id:text, level:text?, source:text, message:text, stack:text?, method:text?, path:text?, status_code:integer?, user_id:text?, meta:text?, created_at:timestamptz? |
| `exchange_rates` | 9 | id:integer, rate:real, updated_by:text?, updated_at:timestamptz?, base:text, quote:text, source:text?, effective_from:timestamptz, currency_pair:text |
| `fees` | 12 | id:text, code:text, label:text, currency:text, **amount_minor:bigint**, is_percentage:boolean, effective_from:timestamptz, effective_to:timestamptz?, **amount:real**, notes:text?, is_active:boolean?, created_at:timestamptz? |
| `insurance_policies` | 13 | id:uuid, parcel_id:text, tier:enum, **declared_value_pence:bigint, premium_pence:bigint**, cert_pdf_url:text?, created_at:timestamptz, user_id:text?, **declared_value_gbp:real?, premium_gbp:real?**, status:text?, claimed_at:timestamptz?, payout_gbp:real? |
| `last_mile_runs` | 10 | id:uuid, rider_id:text, zone:text, run_date:date, status:enum, created_at:timestamptz, total_stops:integer?, completed_stops:integer?, notes:text?, updated_at:timestamptz? |
| `marketing_attributions` | 9 | id:uuid, user_id:text, utm_source:text?, utm_medium:text?, utm_campaign:text?, referrer:text?, landing_page:text?, created_at:timestamptz, landing_path:text? |
| `notifications` | 6 | id:text, user_id:text, type:text, message:text, is_read:boolean?, created_at:timestamptz? |
| `nps_responses` | 6 | id:uuid, user_id:text, parcel_id:text?, score:integer, comment:text?, created_at:timestamptz |
| `orders` | 28 | id:text, user_id:text, tracking_number:text, retailer:text, market:text, status:text?, description:text, weight_kg:real?, dimensions_json:text?, shipping_speed:text?, insurance:boolean?, declared_value:real?, estimated_cost:real?, actual_cost:real?, customs_duty:real?, created_at:timestamptz?, updated_at:timestamptz?, **electronics_item:text?**, order_notes:text?, consolidation_id:uuid?, volumetric_kg:numeric?, chargeable_kg:numeric?, hold_reason:text?, hold_resolved_at:timestamptz?, photographed_at:timestamptz?, insurance_policy_id:uuid?, insurance_tier:text?, insurance_premium_gbp:real? |
| `packages` | 29 | id:text, order_id:text?, user_id:text, description:text?, weight_kg:real?, status:text?, warehouse_location:text?, is_consolidated:boolean?, consolidated_with:text?, received_at:timestamptz?, photo_url:text?, created_at:timestamptz?, updated_at:timestamptz?, barcode:text?, screening_result:text?, insurance_policy_id:text?, tracking_number:text?, retailer:text?, **declared_value_gbp_pence:bigint**, actual_kg:double?, volumetric_kg:double?, chargeable_kg:double?, length_cm:double?, width_cm:double?, height_cm:double?, hold_reason:text?, hold_resolved_at:timestamptz?, photographed_at:timestamptz?, consolidation_id:text? |
| `pallets` | 7 | id:uuid, consolidation_id:uuid, label:text, weight_kg:numeric?, carton_count:integer, photo_url:text?, created_at:timestamptz |
| `parcel_items` | 8 | id:uuid, parcel_id:text, description:text, qty:integer, **unit_value_pence:bigint**, hs_code:text?, created_at:timestamptz, **unit_value_gbp:real?** |
| `password_reset_tokens` | 6 | id:text, user_id:text, token:text, expires_at:timestamptz, used:boolean?, created_at:timestamptz? |
| `pod_events` | 13 | id:uuid, parcel_id:text, run_id:uuid?, captured_at:timestamptz, photo_url:text?, signature_url:text?, otp_used:text?, recipient_name:text?, result:text, rider_id:text?, recipient_phone:text?, notes:text?, created_at:timestamptz? |
| `pricing_tiers` | 10 | id:text, channel:text, min_kg:numeric, max_kg:numeric, effective_from:timestamptz, effective_to:timestamptz?, created_at:timestamptz, **gbp_per_kg:real**, is_active:boolean?, notes:text? — **legacy `gbp_per_kg_pence` already dropped per memory** |
| `prohibited_items` | 9 | id:text, term:text, language:text, severity:text, jurisdiction:text, notes:text?, last_reviewed_at:timestamptz, reason:text?, created_at:timestamptz? |
| `promotions` | 14 | id:uuid, code:text, type:text, **value_minor:bigint**, currency:text, valid_from:timestamptz, valid_to:timestamptz, max_uses:integer?, use_count:integer, created_at:timestamptz, **value:real?**, uses:integer?, is_active:boolean?, description:text? |
| `pvoc_documents` | 9 | id:uuid, consolidation_id:uuid?, parcel_id:text?, type:enum, doc_url:text, issued_by:text?, issued_at:date?, expires_at:date?, created_at:timestamptz |
| `referrals` | 8 | id:text, referrer_id:text, referee_id:text?, referral_code:text, status:text?, reward_amount:real?, completed_at:timestamptz?, created_at:timestamptz? |
| `run_parcels` | 4 | id:uuid, run_id:uuid, parcel_id:text, stop_order:integer |
| `shipping_rates` | 5 | id:uuid, market:varchar, rate_gbp:numeric, updated_by:uuid?, updated_at:timestamptz |
| `ticket_messages` | 5 | id:text, ticket_id:text, sender_id:text, message:text, created_at:timestamptz? |
| `tickets` | 9 | id:text, user_id:text, subject:text, description:text, status:text?, priority:text?, photo_url:text?, created_at:timestamptz?, updated_at:timestamptz? |
| `transactions` | 13 | id:text, user_id:text, type:text, amount:real, currency:text?, payment_method:text?, payment_reference:text?, status:text?, created_at:timestamptz?, idempotency_key:text?, reconciled_at:timestamptz?, **processor_fee_pence:bigint, processor_fee_gbp:real?** |
| `tudor_invoices` | 11 | id:uuid, consolidation_id:uuid, **amount_pence:bigint**, breakdown:jsonb, invoice_no:text?, doc_url:text?, status:enum, paid_at:timestamptz?, created_at:timestamptz, **amount_gbp:real?**, breakdown_json:text? |
| `users` | 28 | id:text, email:text, password:text?, name:text?, phone:text, role:text?, warehouse_id:text, language_pref:text?, referral_code:text, referred_by:text?, wallet_balance:real?, is_active:boolean?, created_at:timestamptz?, updated_at:timestamptz?, delivery_address:text?, admin_notes:text?, country_of_residence:text?, kyc_status:text, kyc_doc_url:text?, marketing_consent_at:timestamptz?, utm_source:text?, utm_medium:text?, utm_campaign:text?, two_fa_enabled:boolean, password_hash:text?, last_login_at:timestamptz?, failed_login_count:integer, full_name:text? |
| `wallet` | 5 | id:text, user_id:text, balance:real?, currency:text?, last_updated:timestamptz? |
| `whatsapp_messages` | 13 | id:uuid, user_id:text?, parcel_id:text?, template:text, locale:text, payload:jsonb, status:text, provider_ref:text?, sent_at:timestamptz?, created_at:timestamptz, payload_json:text?, language:text?, error:text? |

## Realtime publication (`supabase_realtime`)
- `public.consolidations`
- `public.packages`
- `public.transactions`
- `public.wallet`

**Missing per Phase 4 priority #4**: `notifications`, `tickets`, `ticket_messages`, `buy_for_me_orders`, `aml_flags`.

## RLS state (rowsecurity + policy count)

**RLS disabled on every table EXCEPT `exchange_rates`, which has RLS enabled with ZERO policies.** Effectively means anon/authenticated PostgREST reads of `exchange_rates` will return empty results — that's a real gap, since Phase 4 priority #5 (Ops Settings → exchange rates for all four pairs) plus any iOS direct read of rates is broken.

All other tables: RLS off. iOS PostgREST direct reads/writes succeed unconditionally with the API key. This explains why the orders-routing bug (writing `packages` directly) silently works even though it shouldn't.

## `_migrations` table (Supabase-tracked)
- 0001_existing_floor.sql           2026-04-25
- 0002_framework_tables.sql         2026-04-25
- 0003_rls_policies.sql             2026-04-25
- 0003b_fix_exchange_rates.sql      2026-04-25
- 0004_seed.sql                     2026-04-25
- 0005_user_auth.sql                2026-04-25
- 0006_users_align.sql              2026-04-25
- 0007_users_id_default.sql         2026-04-25
- 0008_users_password_nullable.sql  2026-04-25

Note: the `server-patches/database/migrations/` files (000–006) in the iOS repo are NOT logged here (they were run via the SQL editor). The schema reflects all of them: `gbp_per_kg_pence` is gone, `currency_pair` is on `exchange_rates`, realtime publication exists, etc.

## Cross-cutting observations

1. **Dual currency columns everywhere.** Pence/cents (bigint) AND major-unit (real) live side-by-side on: `agent_invoices`, `customs_entries`, `fees`, `insurance_policies`, `packages` (just `_pence`, no major-unit twin), `parcel_items`, `promotions`, `transactions`, `tudor_invoices`. Memory says: DB stores GBP doubles, iOS Money stores pence, QuoteEngine bridges. Whichever column the webapp reads/writes from is the one that drives the iOS DTO.

2. **`exchange_rates` is publicly empty under RLS.** Either the webapp talks to it via service-role key (server-side only) or the page silently shows nothing for non-server-side fetches. Worth confirming when we cross-reference with the route inventory.

3. **`wallet` has only `balance:real?` and one row per user** — no `wallet_entries`/ledger table observed. Reconciliation is probably synthesised from `transactions`.

4. **`packages.declared_value_gbp_pence:bigint` is NOT NULL?** The query output shows no `?` after that column. If true, the iOS direct-write path must always include it — and the DTO must always supply it. Worth confirming.

5. **`users.password` AND `users.password_hash` both exist.** Probably auth migration in flight; leave alone unless Auth domain audit flags it.

6. **`shipping_rates` (5 cols) vs `pricing_tiers` (10 cols).** Two separate rate tables. The webapp likely uses one for the calculator and one for some kind of tier override. To resolve in synthesis.

7. **`whatsapp_messages` table exists.** No iOS WhatsApp surface; webapp must have an outbound channel. Parity row needed.
