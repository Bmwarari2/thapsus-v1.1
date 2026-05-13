package com.thapsus.cargo.domain.prohibited

import com.thapsus.cargo.data.dto.ProhibitedSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProhibitedItemsCatalogTest {

    @Test
    fun catalog_has_meaningful_breadth() {
        val cats = ProhibitedItemsCatalog.categories
        // At least 15 categories so the empty-state never feels sparse.
        assertTrue(cats.size >= 15, "expected ≥ 15 categories, was ${cats.size}")
        // At least 70 individual items across all categories.
        val total = cats.sumOf { it.items.size }
        assertTrue(total >= 70, "expected ≥ 70 items, was $total")
        // Every category populated.
        assertTrue(cats.all { it.items.isNotEmpty() }, "found an empty category")
    }

    @Test
    fun every_severity_class_is_represented() {
        val severities = ProhibitedItemsCatalog.allItems.map { it.severity }.toSet()
        assertEquals(setOf(
            ProhibitedItemsCatalog.Severity.PROHIBITED,
            ProhibitedItemsCatalog.Severity.RESTRICTED,
            ProhibitedItemsCatalog.Severity.DANGEROUS_GOODS
        ), severities)
    }

    @Test
    fun categorisation_round_trip() {
        val summary = ProhibitedItemsCatalog.categoriesSummary()
        // Every summary has a real detail body.
        for (s in summary) {
            val detail = ProhibitedItemsCatalog.categoryDetail(s.category)
            assertNotNull(detail, "no detail for ${s.category}")
            assertEquals(s.itemCount, detail.items.size)
        }
    }

    @Test
    fun search_finds_terms_by_partial_match() {
        // Power bank is high-profile so the search must surface it.
        val results = ProhibitedItemsCatalog.search("power bank")
        assertTrue(results.any { it.term.contains("Power bank", ignoreCase = true) })
        // Severity mapping survives DTO conversion.
        assertTrue(results.any { it.severity == ProhibitedSeverity.DANGEROUS_GOODS })
    }

    @Test
    fun search_finds_by_category_keyword() {
        // "battery" should surface lithium-batteries items even though
        // the user-typed term doesn't appear in every row's term field.
        val results = ProhibitedItemsCatalog.search("battery")
        assertTrue(results.isNotEmpty(), "battery search returned nothing")
    }

    @Test
    fun search_finds_drugs_and_currency_keywords() {
        assertTrue(ProhibitedItemsCatalog.search("cannabis").isNotEmpty())
        assertTrue(ProhibitedItemsCatalog.search("ivory").isNotEmpty())
        assertTrue(ProhibitedItemsCatalog.search("cash").isNotEmpty())
        assertTrue(ProhibitedItemsCatalog.search("drone").isNotEmpty())
    }

    @Test
    fun search_under_two_chars_returns_nothing() {
        assertTrue(ProhibitedItemsCatalog.search("").isEmpty())
        assertTrue(ProhibitedItemsCatalog.search("a").isEmpty())
    }

    @Test
    fun item_ids_are_unique() {
        val dtos = ProhibitedItemsCatalog.allItems.map { item ->
            // Trigger DTO conversion via search hit on the item's term.
            ProhibitedItemsCatalog.search(item.term).first { it.term == item.term }
        }
        val ids = dtos.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids in catalog")
    }

    @Test
    fun risk_levels_are_one_of_three_known_values() {
        val allowed = setOf("critical", "high", "medium")
        for (c in ProhibitedItemsCatalog.categories) {
            assertTrue(c.riskLevel in allowed, "unknown risk level: ${c.riskLevel} on ${c.name}")
        }
    }
}
