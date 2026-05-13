package com.thapsus.cargo.domain.prohibited

import com.thapsus.cargo.data.dto.ProhibitedItemDto
import com.thapsus.cargo.data.dto.ProhibitedSeverity
import com.thapsus.cargo.data.repository.ProhibitedCategoryBody
import com.thapsus.cargo.data.repository.ProhibitedCategorySummary

/**
 * Bundled fallback catalogue of prohibited / restricted / dangerous-goods
 * items for the UK → Kenya consolidation lane. Used by
 * `ProhibitedSearchViewModel` whenever the server's
 * `prohibited_items` table returns empty (it is server-side source of
 * truth, but ships unseeded in many environments).
 *
 * Source of rules:
 *  - **Kenya Revenue Authority (KRA)** import prohibitions + restrictions
 *  - **Kenya Bureau of Standards (KEBS)** standardisation-mark goods
 *  - **Kenya Civil Aviation Authority (KCAA)** + IATA Dangerous Goods
 *    Regulations (66th ed.) — applies to every parcel on the weekly flight
 *  - **Kenya Communications Authority (CA)** for telecoms / radio
 *  - **Kenya Pharmacy and Poisons Board (PPB)** for medicines
 *  - **KEPHIS** (phytosanitary) for plants and seeds
 *  - **UK export controls** (open general / dual-use)
 *  - **CITES** for protected fauna / flora
 *
 * Severity legend:
 *  - PROHIBITED       — will be refused at the Stockport hub and never fly.
 *  - RESTRICTED       — may ship but needs paperwork (permit, licence,
 *                        declaration) and may attract extra duty / fees.
 *  - DANGEROUS_GOODS  — restricted by air-freight rules; some can ship
 *                        with proper UN packaging + IATA DGD, most can't.
 */
object ProhibitedItemsCatalog {

    enum class Severity { PROHIBITED, RESTRICTED, DANGEROUS_GOODS }

    data class Item(
        val term: String,
        val severity: Severity,
        val reason: String,
        val category: String
    )

    /**
     * `riskLevel` strings match what iOS / Android UIs already render:
     *  - "critical" → red badge (PROHIBITED categories)
     *  - "high"     → orange badge (DANGEROUS_GOODS / serious restricted)
     *  - "medium"   → yellow badge (RESTRICTED needing paperwork)
     */
    data class Category(
        val name: String,
        val riskLevel: String,
        val reason: String,
        val items: List<Item>
    )

    val categories: List<Category> = buildList {
        // ── PROHIBITED — never ship ─────────────────────────────────────
        add(
            Category(
                name = "Weapons & ammunition",
                riskLevel = "critical",
                reason = "Kenyan Firearms Act bans private import of firearms, parts, " +
                    "ammunition and certain bladed weapons via commercial cargo. " +
                    "IATA also bans live ammo on passenger / belly-hold aircraft.",
                items = listOf(
                    item("Firearms (rifles, handguns, shotguns)", Severity.PROHIBITED, "Cannot enter Kenya via cargo — only licensed dealers with KPS permits.", "Weapons & ammunition"),
                    item("Air rifles & air pistols", Severity.PROHIBITED, "Treated as firearms under Kenyan law; cargo import refused.", "Weapons & ammunition"),
                    item("Ammunition & gunpowder", Severity.PROHIBITED, "IATA dangerous-goods class 1; not accepted on this lane.", "Weapons & ammunition"),
                    item("Tasers & stun guns", Severity.PROHIBITED, "Banned under Kenyan firearms regulations.", "Weapons & ammunition"),
                    item("Crossbows", Severity.PROHIBITED, "Treated as offensive weapons under Kenyan law.", "Weapons & ammunition"),
                    item("Switchblades & flick knives", Severity.PROHIBITED, "Prohibited weapons under both UK and Kenyan law.", "Weapons & ammunition"),
                    item("Pepper spray & CS gas", Severity.PROHIBITED, "Restricted weapons; civilian import banned.", "Weapons & ammunition"),
                    item("Imitation / replica firearms", Severity.PROHIBITED, "Treated as real firearms by Kenyan customs.", "Weapons & ammunition"),
                    item("Gun parts (barrels, triggers, magazines)", Severity.PROHIBITED, "Same controls as a complete firearm.", "Weapons & ammunition"),
                    item("Body armour / bulletproof vests", Severity.PROHIBITED, "Restricted under Kenyan security regulations.", "Weapons & ammunition")
                )
            )
        )
        add(
            Category(
                name = "Drugs & narcotics",
                riskLevel = "critical",
                reason = "Narcotic Drugs and Psychotropic Substances Control Act — " +
                    "import is a criminal offence regardless of country of origin.",
                items = listOf(
                    item("Cannabis / marijuana (any form)", Severity.PROHIBITED, "Illegal in Kenya regardless of UK legality.", "Drugs & narcotics"),
                    item("CBD oil, CBD edibles, CBD vapes", Severity.PROHIBITED, "Treated as cannabis-derived; banned by PPB.", "Drugs & narcotics"),
                    item("Cocaine, heroin, methamphetamine", Severity.PROHIBITED, "Class A narcotics — criminal offence to import.", "Drugs & narcotics"),
                    item("Khat / miraa products", Severity.PROHIBITED, "Banned for export from the UK under the Misuse of Drugs Act.", "Drugs & narcotics"),
                    item("Kratom", Severity.PROHIBITED, "Controlled substance in Kenya.", "Drugs & narcotics"),
                    item("LSD, MDMA, psilocybin (magic mushrooms)", Severity.PROHIBITED, "Class A controlled substances.", "Drugs & narcotics"),
                    item("Anabolic steroids without prescription", Severity.PROHIBITED, "PPB-controlled; cannot import without doctor's import permit.", "Drugs & narcotics"),
                    item("Drug paraphernalia (bongs, grinders, pipes)", Severity.PROHIBITED, "Prohibited under Kenyan narcotics law.", "Drugs & narcotics"),
                    item("Poppy seeds & opium-derivative tinctures", Severity.PROHIBITED, "Controlled under PPB; import banned.", "Drugs & narcotics"),
                    item("Cathinones / 'legal highs'", Severity.PROHIBITED, "Treated as narcotics analogues.", "Drugs & narcotics")
                )
            )
        )
        add(
            Category(
                name = "Currency, bullion & gemstones",
                riskLevel = "critical",
                reason = "Anti-money-laundering controls (POCAMLA). Cash above " +
                    "USD 10,000 must be declared at customs in person; bulk gold, " +
                    "silver and rough diamonds cannot ship by cargo.",
                items = listOf(
                    item("Banknotes & bulk cash (any currency)", Severity.PROHIBITED, "Cannot ship via courier; declare in person under POCAMLA.", "Currency, bullion & gemstones"),
                    item("Gold bars / bullion", Severity.PROHIBITED, "Requires KRA precious-metals dealer licence.", "Currency, bullion & gemstones"),
                    item("Silver bars / bullion", Severity.PROHIBITED, "Same precious-metals controls as gold.", "Currency, bullion & gemstones"),
                    item("Rough / uncut diamonds", Severity.PROHIBITED, "Kimberley Process Certificate required.", "Currency, bullion & gemstones"),
                    item("Bearer bonds & negotiable instruments", Severity.PROHIBITED, "Treated as cash under POCAMLA.", "Currency, bullion & gemstones"),
                    item("Cheques made out to bearer", Severity.PROHIBITED, "Same as bearer bonds.", "Currency, bullion & gemstones"),
                    item("Loose precious gemstones over £500", Severity.PROHIBITED, "Requires KRA precious-stones licence.", "Currency, bullion & gemstones")
                )
            )
        )
        add(
            Category(
                name = "Live animals, ivory & wildlife products",
                riskLevel = "critical",
                reason = "Wildlife Conservation and Management Act + CITES. Live " +
                    "animals cannot fly on this lane; ivory, raw hides, trophies " +
                    "and any CITES-listed species are banned.",
                items = listOf(
                    item("Live animals (pets, birds, fish, reptiles)", Severity.PROHIBITED, "We don't carry live cargo. Use a specialist pet shipper.", "Live animals, ivory & wildlife products"),
                    item("Ivory & ivory carvings", Severity.PROHIBITED, "CITES Appendix I — possession is a criminal offence.", "Live animals, ivory & wildlife products"),
                    item("Rhino horn", Severity.PROHIBITED, "CITES Appendix I — criminal offence.", "Live animals, ivory & wildlife products"),
                    item("Tortoise shells, coral, sea-shells", Severity.PROHIBITED, "Often CITES-listed; import without certificate is illegal.", "Live animals, ivory & wildlife products"),
                    item("Taxidermy / mounted heads", Severity.PROHIBITED, "Requires CITES permits not issued for commercial cargo.", "Live animals, ivory & wildlife products"),
                    item("Animal furs & exotic skins (snake, croc, leopard)", Severity.PROHIBITED, "CITES-controlled; UK export and Kenya import refused.", "Live animals, ivory & wildlife products"),
                    item("Raw hides & untanned skins", Severity.PROHIBITED, "Veterinary import permit required; not handled by this lane.", "Live animals, ivory & wildlife products"),
                    item("Live insects & arachnids", Severity.PROHIBITED, "KEPHIS phytosanitary refusal.", "Live animals, ivory & wildlife products"),
                    item("Eggs (poultry / wild bird)", Severity.PROHIBITED, "Veterinary import permit required; bird-flu risk.", "Live animals, ivory & wildlife products")
                )
            )
        )
        add(
            Category(
                name = "Counterfeit & infringing goods",
                riskLevel = "critical",
                reason = "Anti-Counterfeit Act 2008. KRA seizes fakes at JKIA and " +
                    "rights-holders can demand destruction.",
                items = listOf(
                    item("Counterfeit branded clothing & shoes", Severity.PROHIBITED, "Fake Nike, adidas, Louis Vuitton etc. seized at JKIA.", "Counterfeit & infringing goods"),
                    item("Counterfeit watches & jewellery", Severity.PROHIBITED, "Fake Rolex, Cartier etc. seized + destroyed.", "Counterfeit & infringing goods"),
                    item("Pirated DVDs, Blu-rays, software", Severity.PROHIBITED, "Copyright infringement; seized + destroyed.", "Counterfeit & infringing goods"),
                    item("Counterfeit electronics (fake AirPods, iPhones, chargers)", Severity.PROHIBITED, "Often unsafe and infringe trademarks.", "Counterfeit & infringing goods"),
                    item("Counterfeit medicines / 'generic' Viagra etc.", Severity.PROHIBITED, "Counterfeit pharma is criminal and dangerous.", "Counterfeit & infringing goods"),
                    item("Counterfeit cosmetics & perfumes", Severity.PROHIBITED, "Often contain banned substances; seized.", "Counterfeit & infringing goods"),
                    item("Replica sports kit with fake club badges", Severity.PROHIBITED, "Trademark infringement.", "Counterfeit & infringing goods")
                )
            )
        )
        add(
            Category(
                name = "Indecent material & extremist content",
                riskLevel = "critical",
                reason = "Films, Stage Plays and Publications Act + Sexual Offences " +
                    "Act. Kenyan customs screens for indecent or hateful media.",
                items = listOf(
                    item("Pornographic films, magazines, books", Severity.PROHIBITED, "Banned under Films, Stage Plays and Publications Act.", "Indecent material & extremist content"),
                    item("Child-protection-offence material", Severity.PROHIBITED, "Criminal offence in both jurisdictions; immediate referral.", "Indecent material & extremist content"),
                    item("Extremist / terrorist propaganda", Severity.PROHIBITED, "POTA Act — possession is an offence.", "Indecent material & extremist content"),
                    item("Hate-speech publications", Severity.PROHIBITED, "NCIC Act — incitement to ethnic hatred is criminal.", "Indecent material & extremist content")
                )
            )
        )
        add(
            Category(
                name = "Human remains & body parts",
                riskLevel = "critical",
                reason = "Repatriation of remains is handled exclusively by " +
                    "registered funeral directors with embassy paperwork — never " +
                    "via courier.",
                items = listOf(
                    item("Cremated ashes", Severity.PROHIBITED, "Specialist undertaker repatriation only.", "Human remains & body parts"),
                    item("Human organs / tissue samples", Severity.PROHIBITED, "Medical-specialist freight only with HTA paperwork.", "Human remains & body parts"),
                    item("Human teeth, bones, hair samples (commercial qty)", Severity.PROHIBITED, "Cannot ship via standard cargo.", "Human remains & body parts")
                )
            )
        )
        add(
            Category(
                name = "Explosives, radioactive & hazardous chemicals",
                riskLevel = "critical",
                reason = "IATA dangerous-goods classes 1, 5, 7 are off-limits on " +
                    "this passenger-aircraft lane regardless of paperwork.",
                items = listOf(
                    item("Fireworks & sparklers", Severity.PROHIBITED, "IATA class 1 — never accepted on belly-hold cargo.", "Explosives, radioactive & hazardous chemicals"),
                    item("Flares & distress signals", Severity.PROHIBITED, "Pyrotechnics — class 1 explosives.", "Explosives, radioactive & hazardous chemicals"),
                    item("Detonators & blasting caps", Severity.PROHIBITED, "Mining explosives — class 1.", "Explosives, radioactive & hazardous chemicals"),
                    item("Asbestos (any form)", Severity.PROHIBITED, "Banned under Kenyan environmental law.", "Explosives, radioactive & hazardous chemicals"),
                    item("Radioactive materials & isotopes", Severity.PROHIBITED, "Class 7 — Radiation Protection Board licence required.", "Explosives, radioactive & hazardous chemicals"),
                    item("Biological agents / clinical waste", Severity.PROHIBITED, "Specialist medical freight only.", "Explosives, radioactive & hazardous chemicals"),
                    item("Mercury thermometers & barometers", Severity.PROHIBITED, "Minamata Convention — phased out worldwide.", "Explosives, radioactive & hazardous chemicals"),
                    item("Lead-acid / wet-cell batteries", Severity.PROHIBITED, "Class 8 corrosive; cannot fly without UN-spec packaging we don't carry.", "Explosives, radioactive & hazardous chemicals")
                )
            )
        )

        // ── DANGEROUS GOODS — IATA-restricted, mostly cannot ship ──────
        add(
            Category(
                name = "Lithium batteries & power banks",
                riskLevel = "high",
                reason = "Lithium-ion fires on aircraft are the airline industry's " +
                    "biggest cargo risk. We accept ONLY batteries installed in a " +
                    "device, under 100 Wh, with terminals taped and the device " +
                    "powered off.",
                items = listOf(
                    item("Loose lithium-ion batteries (any size)", Severity.DANGEROUS_GOODS, "Not accepted loose — must be installed in the device.", "Lithium batteries & power banks"),
                    item("Power banks (any capacity)", Severity.DANGEROUS_GOODS, "Almost always refused by the airline — buy locally in Kenya.", "Lithium batteries & power banks"),
                    item("Spare laptop batteries", Severity.DANGEROUS_GOODS, "Accepted only if installed in the laptop, not as a spare.", "Lithium batteries & power banks"),
                    item("Spare phone batteries", Severity.DANGEROUS_GOODS, "Same rule: installed only, not loose.", "Lithium batteries & power banks"),
                    item("E-bike / e-scooter batteries", Severity.DANGEROUS_GOODS, "Above 100 Wh — never accepted on passenger aircraft.", "Lithium batteries & power banks"),
                    item("Drone batteries (separate)", Severity.DANGEROUS_GOODS, "Must remain installed in the drone; spares refused.", "Lithium batteries & power banks"),
                    item("Vape / e-cigarette batteries (18650 cells)", Severity.DANGEROUS_GOODS, "Loose 18650 cells refused; cells in a device may pass.", "Lithium batteries & power banks")
                )
            )
        )
        add(
            Category(
                name = "Flammable liquids, aerosols & gases",
                riskLevel = "high",
                reason = "IATA dangerous-goods classes 2 & 3. Even small quantities " +
                    "need UN-spec packaging and a dangerous-goods declaration we " +
                    "don't offer on this lane.",
                items = listOf(
                    item("Perfumes & colognes (full-size, > 100 ml)", Severity.DANGEROUS_GOODS, "Most contain > 24% alcohol — class 3 flammable. Sample sizes only.", "Flammable liquids, aerosols & gases"),
                    item("Nail polish & nail polish remover", Severity.DANGEROUS_GOODS, "Acetone-based — class 3 flammable.", "Flammable liquids, aerosols & gases"),
                    item("Aerosol deodorants & hairsprays", Severity.DANGEROUS_GOODS, "Pressurised, flammable propellant.", "Flammable liquids, aerosols & gases"),
                    item("Paints, varnishes, thinners", Severity.DANGEROUS_GOODS, "Flammable liquids — class 3.", "Flammable liquids, aerosols & gases"),
                    item("Lighter fluid / butane refills", Severity.DANGEROUS_GOODS, "Class 2 flammable gas.", "Flammable liquids, aerosols & gases"),
                    item("Lighters & matches", Severity.DANGEROUS_GOODS, "Refused unless empty (no fuel) and declared.", "Flammable liquids, aerosols & gases"),
                    item("CO₂ cartridges (SodaStream, bike pumps)", Severity.DANGEROUS_GOODS, "Compressed gas — class 2.", "Flammable liquids, aerosols & gases"),
                    item("Camping gas canisters", Severity.DANGEROUS_GOODS, "Propane / butane — class 2 flammable gas.", "Flammable liquids, aerosols & gases"),
                    item("Pepper spray / mace", Severity.DANGEROUS_GOODS, "Pressurised + irritant; also banned by Kenyan law.", "Flammable liquids, aerosols & gases"),
                    item("Tear-gas canisters", Severity.DANGEROUS_GOODS, "Class 2 + Kenyan firearms/weapons law.", "Flammable liquids, aerosols & gases")
                )
            )
        )
        add(
            Category(
                name = "Corrosives, oxidisers & toxic chemicals",
                riskLevel = "high",
                reason = "IATA classes 5, 6, 8. We cannot accept household " +
                    "chemicals in any meaningful quantity.",
                items = listOf(
                    item("Bleach (concentrated)", Severity.DANGEROUS_GOODS, "Class 8 corrosive.", "Corrosives, oxidisers & toxic chemicals"),
                    item("Drain cleaner / oven cleaner", Severity.DANGEROUS_GOODS, "Highly corrosive — class 8.", "Corrosives, oxidisers & toxic chemicals"),
                    item("Hydrogen peroxide (> 8%)", Severity.DANGEROUS_GOODS, "Class 5.1 oxidiser.", "Corrosives, oxidisers & toxic chemicals"),
                    item("Pool chlorine tablets", Severity.DANGEROUS_GOODS, "Class 5.1 oxidiser.", "Corrosives, oxidisers & toxic chemicals"),
                    item("Pesticides & insecticides", Severity.DANGEROUS_GOODS, "Class 6.1 toxic + KEPHIS permit.", "Corrosives, oxidisers & toxic chemicals"),
                    item("Weedkillers & herbicides", Severity.DANGEROUS_GOODS, "Class 6.1 toxic + KEPHIS permit.", "Corrosives, oxidisers & toxic chemicals"),
                    item("Acids (sulphuric, hydrochloric, nitric)", Severity.DANGEROUS_GOODS, "Class 8 — never accepted.", "Corrosives, oxidisers & toxic chemicals")
                )
            )
        )
        add(
            Category(
                name = "Magnets, magnetised devices & high-EMF gear",
                riskLevel = "high",
                reason = "Strong magnets interfere with aircraft navigation and " +
                    "are restricted by IATA.",
                items = listOf(
                    item("Rare-earth / neodymium magnets (N52, N50, etc.)", Severity.DANGEROUS_GOODS, "Magnetic field above IATA's 0.418 A/m at 4.5 m limit — refused.", "Magnets, magnetised devices & high-EMF gear"),
                    item("Magnetic toys (e.g. Buckyballs, Geomag)", Severity.DANGEROUS_GOODS, "Refused above neodymium threshold; small ferrite magnets OK.", "Magnets, magnetised devices & high-EMF gear"),
                    item("Large speaker drivers / subwoofer magnets", Severity.DANGEROUS_GOODS, "Often above IATA threshold — packaged shielded only.", "Magnets, magnetised devices & high-EMF gear")
                )
            )
        )

        // ── RESTRICTED — paperwork / extra fees / quantity caps ────────
        add(
            Category(
                name = "Pharmaceuticals & medical devices",
                riskLevel = "medium",
                reason = "Kenya Pharmacy and Poisons Board (PPB) licences every " +
                    "drug imported. Personal-use quantities (≤ 3 months supply) " +
                    "with a doctor's prescription scan pass; larger consignments " +
                    "need an importer permit.",
                items = listOf(
                    item("Prescription medicines (personal supply)", Severity.RESTRICTED, "Bring your prescription scan — up to 3 months supply.", "Pharmaceuticals & medical devices"),
                    item("Insulin, EpiPens & injectables", Severity.RESTRICTED, "Personal-use allowed with prescription; refrigeration risk.", "Pharmaceuticals & medical devices"),
                    item("Vitamins & supplements (commercial qty)", Severity.RESTRICTED, "PPB permit required above personal-use quantities.", "Pharmaceuticals & medical devices"),
                    item("Hearing aids & batteries", Severity.RESTRICTED, "Personal device — declare on commercial invoice.", "Pharmaceuticals & medical devices"),
                    item("Glucose meters, blood-pressure monitors", Severity.RESTRICTED, "Declare as medical device; KEBS standardisation mark may apply.", "Pharmaceuticals & medical devices"),
                    item("Contact lenses & solution", Severity.RESTRICTED, "Personal supply OK; bulk needs PPB permit.", "Pharmaceuticals & medical devices"),
                    item("Surgical instruments", Severity.RESTRICTED, "Importer permit required.", "Pharmaceuticals & medical devices")
                )
            )
        )
        add(
            Category(
                name = "Food, beverages & alcohol",
                riskLevel = "medium",
                reason = "KEBS standardisation mark + KRA excise duty. " +
                    "Personal-quantity packaged food is fine; alcohol attracts " +
                    "high duty and has a 1 L per traveller free allowance " +
                    "(commercial quantities need a wholesaler licence).",
                items = listOf(
                    item("Wine & spirits (over 1 L)", Severity.RESTRICTED, "High excise duty + wholesaler permit for > 1 L per consignee.", "Food, beverages & alcohol"),
                    item("Beer", Severity.RESTRICTED, "Glass-bottle breakage risk; commercial qty needs permit.", "Food, beverages & alcohol"),
                    item("Fresh fruit & vegetables", Severity.RESTRICTED, "KEPHIS phytosanitary certificate required.", "Food, beverages & alcohol"),
                    item("Raw meat & poultry", Severity.RESTRICTED, "Veterinary import permit needed; cold-chain not offered.", "Food, beverages & alcohol"),
                    item("Cheese & dairy", Severity.RESTRICTED, "Veterinary permit + cold chain — limited acceptance.", "Food, beverages & alcohol"),
                    item("Honey", Severity.RESTRICTED, "Veterinary permit required.", "Food, beverages & alcohol"),
                    item("Baby formula (commercial qty)", Severity.RESTRICTED, "KEBS approval; personal qty up to 6 tubs typically passes.", "Food, beverages & alcohol"),
                    item("Protein powders & sports supplements", Severity.RESTRICTED, "KEBS mark required for retail labels; personal use OK.", "Food, beverages & alcohol")
                )
            )
        )
        add(
            Category(
                name = "Cosmetics, skincare & toiletries",
                riskLevel = "medium",
                reason = "KEBS Standardisation Mark + Pre-export Verification of " +
                    "Conformity (PVoC) for commercial quantities. Personal use " +
                    "passes; alcohol-based perfumes go under flammable rules.",
                items = listOf(
                    item("Skin-lightening creams (hydroquinone > 2%)", Severity.PROHIBITED, "PPB-banned; many UK retail creams exceed the Kenyan limit.", "Cosmetics, skincare & toiletries"),
                    item("Cosmetics (commercial quantity)", Severity.RESTRICTED, "KEBS PVoC + standardisation mark.", "Cosmetics, skincare & toiletries"),
                    item("Sunscreens above SPF 50", Severity.RESTRICTED, "KEBS approval for retail labelling.", "Cosmetics, skincare & toiletries"),
                    item("Hair-relaxer / chemical perm kits", Severity.RESTRICTED, "Class 8 corrosive ingredient — check packaging.", "Cosmetics, skincare & toiletries")
                )
            )
        )
        add(
            Category(
                name = "Telecoms, radio & SIM equipment",
                riskLevel = "medium",
                reason = "Communications Authority of Kenya (CA) type-approves " +
                    "radio-emitting devices. Phones need their IMEI registered to " +
                    "stay on Kenyan networks past 60 days.",
                items = listOf(
                    item("Mobile phones (new, with original IMEI)", Severity.RESTRICTED, "OK to ship; register the IMEI with your local network within 60 days.", "Telecoms, radio & SIM equipment"),
                    item("Mobile phones (refurbished without box)", Severity.RESTRICTED, "Higher chance of customs valuation dispute.", "Telecoms, radio & SIM equipment"),
                    item("4G / 5G modems & MiFi routers", Severity.RESTRICTED, "CA type-approval check on entry.", "Telecoms, radio & SIM equipment"),
                    item("Walkie-talkies / two-way radios", Severity.RESTRICTED, "CA licence required to operate.", "Telecoms, radio & SIM equipment"),
                    item("CB / amateur radio gear", Severity.RESTRICTED, "Operator licence required.", "Telecoms, radio & SIM equipment"),
                    item("GPS trackers / vehicle telematics", Severity.RESTRICTED, "CA registration for the SIM module.", "Telecoms, radio & SIM equipment"),
                    item("Signal boosters / repeaters", Severity.PROHIBITED, "Unlicensed boosters are banned by the CA.", "Telecoms, radio & SIM equipment")
                )
            )
        )
        add(
            Category(
                name = "Drones, UAVs & RC aircraft",
                riskLevel = "high",
                reason = "Kenya Civil Aviation Authority (KCAA) requires " +
                    "registration for any drone over 250 g; some models are " +
                    "restricted regardless of weight.",
                items = listOf(
                    item("Camera drones (DJI Mini, Mavic, Phantom)", Severity.RESTRICTED, "KCAA registration required; battery must stay installed.", "Drones, UAVs & RC aircraft"),
                    item("Racing / FPV drones", Severity.RESTRICTED, "KCAA registration; some military-radio-band models refused.", "Drones, UAVs & RC aircraft"),
                    item("RC planes & helicopters with combustion engines", Severity.RESTRICTED, "No fuel residue allowed in the engine.", "Drones, UAVs & RC aircraft"),
                    item("Drones with thermal-imaging payload", Severity.PROHIBITED, "Dual-use export control under UK strategic goods.", "Drones, UAVs & RC aircraft")
                )
            )
        )
        add(
            Category(
                name = "Tobacco, vapes & nicotine",
                riskLevel = "medium",
                reason = "Tobacco Control Act + KRA excise. Personal allowance " +
                    "is 250 g of tobacco / 200 cigarettes per traveller — " +
                    "shipments above need an importer licence.",
                items = listOf(
                    item("Cigarettes (over 200)", Severity.RESTRICTED, "Above personal allowance → excise duty + permit.", "Tobacco, vapes & nicotine"),
                    item("Cigars & pipe tobacco", Severity.RESTRICTED, "Same excise + permit rule.", "Tobacco, vapes & nicotine"),
                    item("Vape devices (with battery installed)", Severity.RESTRICTED, "Battery installed + powered off — DG declaration may be needed.", "Tobacco, vapes & nicotine"),
                    item("Vape devices (loose batteries)", Severity.DANGEROUS_GOODS, "Loose 18650 cells refused under IATA.", "Tobacco, vapes & nicotine"),
                    item("Vape juice / e-liquid", Severity.RESTRICTED, "Contains nicotine — KRA excise + KEBS approval.", "Tobacco, vapes & nicotine"),
                    item("Nicotine pouches (Snus, Zyn, etc.)", Severity.RESTRICTED, "Tobacco-product treatment under Kenyan law.", "Tobacco, vapes & nicotine"),
                    item("Shisha tobacco & molasses", Severity.PROHIBITED, "Banned for public consumption in Kenya since 2017.", "Tobacco, vapes & nicotine")
                )
            )
        )
        add(
            Category(
                name = "Plants, seeds & soil",
                riskLevel = "medium",
                reason = "KEPHIS phytosanitary certification — protects Kenyan " +
                    "agriculture from imported pests.",
                items = listOf(
                    item("Live plants & cuttings", Severity.RESTRICTED, "Phytosanitary certificate from origin country required.", "Plants, seeds & soil"),
                    item("Seeds (any kind)", Severity.RESTRICTED, "Phytosanitary certificate + KEPHIS import permit.", "Plants, seeds & soil"),
                    item("Soil & growing media", Severity.PROHIBITED, "Pest / disease vector — banned import.", "Plants, seeds & soil"),
                    item("Cut flowers", Severity.RESTRICTED, "Phytosanitary certificate.", "Plants, seeds & soil")
                )
            )
        )
        add(
            Category(
                name = "Used clothing & textiles (mitumba)",
                riskLevel = "medium",
                reason = "Mitumba imports go through a special licence and " +
                    "valuation regime — personal-quantity used clothing usually " +
                    "passes, commercial loads need a Mitumba Association " +
                    "Importer permit.",
                items = listOf(
                    item("Bulk used clothing (commercial qty)", Severity.RESTRICTED, "Needs mitumba importer permit.", "Used clothing & textiles (mitumba)"),
                    item("Used shoes (bulk)", Severity.RESTRICTED, "Same mitumba regime.", "Used clothing & textiles (mitumba)"),
                    item("Used bed linen, towels", Severity.RESTRICTED, "Personal qty fine; commercial flagged.", "Used clothing & textiles (mitumba)")
                )
            )
        )
        add(
            Category(
                name = "Items needing trader paperwork",
                riskLevel = "medium",
                reason = "Goods clearly intended for resale — based on quantity " +
                    "or commercial packaging — need a KRA importer PIN, a " +
                    "Certificate of Conformity for KEBS-controlled items, and " +
                    "the appropriate duty.",
                items = listOf(
                    item("Building / construction materials", Severity.RESTRICTED, "Commercial valuation; KEBS standardisation for many lines.", "Items needing trader paperwork"),
                    item("Spare car parts (commercial)", Severity.RESTRICTED, "KEBS + KRA inspector valuation.", "Items needing trader paperwork"),
                    item("Used car tyres", Severity.PROHIBITED, "Banned import since 2018 for safety reasons.", "Items needing trader paperwork"),
                    item("Refrigerators / air conditioners (used)", Severity.RESTRICTED, "Older units with banned refrigerants (R12, R22) refused.", "Items needing trader paperwork"),
                    item("Solar panels (commercial qty)", Severity.RESTRICTED, "EPRA / KEBS approval required for resale.", "Items needing trader paperwork")
                )
            )
        )
        add(
            Category(
                name = "Adult products & lifestyle",
                riskLevel = "medium",
                reason = "Public-morality screening at JKIA + Kenya Films " +
                    "Classification Board rules. Discreet personal items generally " +
                    "pass; printed pornographic material does not.",
                items = listOf(
                    item("Sex toys (discreet, personal)", Severity.RESTRICTED, "Plain packaging only; some shipments are spot-rejected.", "Adult products & lifestyle"),
                    item("Adult lingerie", Severity.RESTRICTED, "No restriction unless packaging is sexually explicit.", "Adult products & lifestyle"),
                    item("Lubricants & intimate-care products", Severity.RESTRICTED, "Cosmetic / pharmaceutical labelling controls.", "Adult products & lifestyle")
                )
            )
        )
    }

    /** Cached flat list of every item across categories. */
    val allItems: List<Item> by lazy { categories.flatMap { it.items } }

    fun categoriesSummary(): List<ProhibitedCategorySummary> = categories.map {
        ProhibitedCategorySummary(
            category = it.name,
            riskLevel = it.riskLevel,
            itemCount = it.items.size,
            reason = it.reason
        )
    }

    fun categoryDetail(name: String): ProhibitedCategoryBody? {
        val cat = categories.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        return ProhibitedCategoryBody(
            category = cat.name,
            riskLevel = cat.riskLevel,
            reason = cat.reason,
            items = cat.items.map { it.term }
        )
    }

    /**
     * Free-text search across term, category and reason. Returns up to
     * 50 matches sorted with exact-prefix term matches first.
     */
    fun search(query: String): List<ProhibitedItemDto> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return allItems
            .asSequence()
            .filter { it.matches(q) }
            .sortedWith(compareByDescending { it.term.lowercase().startsWith(q) })
            .take(50)
            .map { it.toDto() }
            .toList()
    }

    private fun Item.matches(q: String): Boolean =
        term.lowercase().contains(q) ||
            category.lowercase().contains(q) ||
            reason.lowercase().contains(q)

    private fun Item.toDto(): ProhibitedItemDto = ProhibitedItemDto(
        id = "catalog:" + category.lowercase()
            .replace(" ", "_")
            .replace("&", "and")
            .replace(",", "")
            .replace("/", "_") + ":" + term.lowercase()
            .replace(" ", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("/", "_"),
        term = term,
        severity = when (severity) {
            Severity.PROHIBITED -> ProhibitedSeverity.PROHIBITED
            Severity.RESTRICTED -> ProhibitedSeverity.RESTRICTED
            Severity.DANGEROUS_GOODS -> ProhibitedSeverity.DANGEROUS_GOODS
        },
        jurisdiction = "KE",
        language = "en",
        reason = reason,
        lastReviewedAt = null
    )

    private fun item(term: String, severity: Severity, reason: String, category: String): Item =
        Item(term = term, severity = severity, reason = reason, category = category)
}
