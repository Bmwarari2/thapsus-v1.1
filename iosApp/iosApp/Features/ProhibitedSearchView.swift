// ProhibitedSearchView.swift
// Mirrors the webapp's `/prohibited` page: a category list of restricted /
// dangerous goods served from `/api/prohibited/categories`. Tap a category
// to expand its items in a sheet. Free-text search (against the seeded
// `prohibited_items` table) stays as a secondary path for power users.

import SwiftUI
import ThapsusShared

struct ProhibitedSearchView: View {
    @State private var vm: ProhibitedSearchViewModel? = nil
    @State private var stateObs: StateFlowObserver<ProhibitedSearchViewModelUiState>? = nil
    @State private var detailObs: StateFlowObserver<ProhibitedCategoryBody?>? = nil
    @State private var query: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Compliance", systemImage: "exclamationmark.shield")
                EditorialHeader(title: "Prohibited &\nrestricted",
                                subtitle: "Check before you ship. Some items can't fly, others need extra paperwork.")

                searchBar
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Prohibited items")
        .glassNavigationBar()
        .sheet(item: detailItem()) { detail in
            CategoryDetailSheet(detail: detail) { vm?.closeCategory() }
        }
        .task { bootstrap() }
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("Search lithium battery, perfume…", text: $query)
                .textFieldStyle(.plain)
                .submitLabel(.search)
                .onSubmit { vm?.search(query: query, language: "en") }
            if !query.isEmpty {
                Button(action: { query = ""; vm?.reset() }) {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(.white.opacity(0.5), lineWidth: 1)
        )
    }

    @ViewBuilder
    private var content: some View {
        switch stateObs?.value {
        case let cats as ProhibitedSearchViewModelUiStateCategoriesLoaded:
            if cats.categories.isEmpty {
                CrystalCard {
                    Text("No categories returned. Try a search above.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(cats.categories, id: \.category) { c in
                    Button(action: { vm?.openCategory(name: c.category) }) {
                        categoryRow(c)
                    }.buttonStyle(.plain)
                }
            }
        case let results as ProhibitedSearchViewModelUiStateSearchResults:
            if results.items.isEmpty {
                CrystalCard {
                    Text("Nothing matches “\(query)”. If unsure, raise a support ticket.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            } else {
                ForEach(results.items, id: \.id) { item in
                    searchRow(item)
                }
            }
        case is ProhibitedSearchViewModelUiStateSearching:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 24)
        case let err as ProhibitedSearchViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 24)
        }
    }

    private func categoryRow(_ c: ProhibitedCategorySummary) -> some View {
        CrystalCard {
            HStack(alignment: .top, spacing: 12) {
                riskBadge(c.riskLevel ?? "")
                VStack(alignment: .leading, spacing: 4) {
                    Text(c.category).font(.headline).foregroundStyle(Brand.ink)
                    if let reason = c.reason {
                        Text(reason).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
                    }
                    Text("\(c.itemCount) item\(c.itemCount == 1 ? "" : "s")")
                        .font(.caption.weight(.semibold)).foregroundStyle(Brand.orange)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.secondary)
            }
        }
    }

    private func riskBadge(_ risk: String) -> some View {
        let color: Color
        switch risk.lowercased() {
        case "critical": color = .red
        case "high": color = .orange
        case "medium": color = .yellow
        default: color = .gray
        }
        return Image(systemName: "exclamationmark.triangle.fill")
            .font(.title3)
            .foregroundStyle(color)
            .frame(width: 36, height: 36)
            .background(Circle().fill(color.opacity(0.15)))
    }

    private func searchRow(_ item: ProhibitedItemDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(item.term.capitalized).font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text(String(describing: item.severity).uppercased())
                        .font(.system(size: 9, weight: .heavy)).tracking(2)
                        .foregroundStyle(.red)
                }
                if let reason = item.reason, !reason.isEmpty {
                    Text(reason).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func detailItem() -> Binding<ProhibitedCategoryBody?> {
        Binding(
            get: { detailObs?.value },
            set: { newValue in if newValue == nil { vm?.closeCategory() } }
        )
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.prohibitedSearchViewModel()
        vm = model
        model.loadCategories()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
        detailObs = StateFlowObserver(initial: model.categoryDetail.value) { model.categoryDetail }
    }
}

extension ProhibitedCategoryBody: @retroactive Identifiable {
    public var id: String { category }
}

private struct CategoryDetailSheet: View {
    let detail: ProhibitedCategoryBody
    let onDismiss: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let reason = detail.reason {
                        ErrorBanner(title: detail.riskLevel?.capitalized ?? "Restricted", message: reason)
                    }
                    SectionHeader(title: "Items in this category")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], spacing: 8) {
                        ForEach(detail.items, id: \.self) { item in
                            Text(item.capitalized)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Brand.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(.ultraThinMaterial)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(.white.opacity(0.4), lineWidth: 1)
                                )
                        }
                    }
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .liquidBackdrop()
            .navigationTitle(detail.category)
            .glassNavigationBar()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { onDismiss(); dismiss() }
                }
            }
        }
    }
}
