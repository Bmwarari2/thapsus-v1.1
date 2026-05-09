// NpsSurveyView.swift
// One-tap post-delivery survey. The host (CustomerDashboardView, ParcelDetailView)
// observes the parcel status and presents this sheet once when status flips to
// "delivered". Submits to POST /api/nps via NpsSurveyViewModel.

import SwiftUI
import ThapsusShared

struct NpsSurveyView: View {
    let parcelId: String?
    let onDone: () -> Void

    @State private var vm: NpsSurveyViewModel?
    @State private var observer: StateFlowObserver<NpsSurveyViewModelState>?
    @State private var score: Int = 9
    @State private var comment: String = ""

    @ScaledMetric(relativeTo: .largeTitle) private var npsScoreSize: CGFloat = 36

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EditorialHeader(
                    eyebrow: "Quick check-in",
                    title: "How was the delivery?",
                    subtitle: "0 means awful, 10 means you'd recommend us. Takes 5 seconds."
                )

                CrystalCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Score")
                                .font(.headline)
                                .foregroundStyle(Brand.ink)
                            Spacer()
                            Text("\(score)")
                                .font(.system(size: npsScoreSize, weight: .bold, design: .rounded))
                                .foregroundStyle(scoreTint)
                                .contentTransition(.numericText())
                        }
                        HStack(spacing: 6) {
                            ForEach(0...10, id: \.self) { n in
                                Button {
                                    score = n
                                } label: {
                                    Text("\(n)")
                                        .font(.system(.callout, design: .rounded).weight(.semibold))
                                        .frame(maxWidth: .infinity, minHeight: 36)
                                        .background(
                                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                .fill(score == n ? Brand.orange.opacity(0.85) : Brand.cream.opacity(0.5))
                                        )
                                        .foregroundStyle(score == n ? .white : Brand.ink)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Text(scoreLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                CrystalCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Anything to add?").font(.headline).foregroundStyle(Brand.ink)
                        TextEditor(text: $comment)
                            .frame(minHeight: 100)
                            .padding(8)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(Brand.cream.opacity(0.6))
                            )
                            .scrollContentBackground(.hidden)
                    }
                }

                statusBanner

                Button("Submit") {
                    vm?.submit(score: Int32(score), comment: comment.isEmpty ? nil : comment)
                }
                .buttonStyle(InkButtonStyle())
                .disabled((observer?.value as? NpsSurveyViewModelStateSubmitting) != nil)

                Button("Maybe later") { onDone() }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .appBackground()
        .task {
            guard vm == nil else { return }
            let model = ThapsusSdk.shared.npsSurveyViewModel(parcelId: parcelId)
            vm = model
            observer = StateFlowObserver(initial: model.state.value) { model.state }
        }
        .onDisappear { vm?.clear(); vm = nil; observer = nil }
        .onChange(of: observer?.value as? NpsSurveyViewModelStateSent != nil) { _, sent in
            if sent { onDone() }
        }
    }

    private var scoreTint: Color {
        switch score {
        case 0...6: return .red
        case 7...8: return .orange
        default: return .green
        }
    }

    private var scoreLabel: String {
        switch score {
        case 0...6: return "Detractor — what would have made it better?"
        case 7...8: return "Passive — close to a recommendation."
        default: return "Promoter — thank you!"
        }
    }

    @ViewBuilder
    private var statusBanner: some View {
        if let err = observer?.value as? NpsSurveyViewModelStateError {
            ErrorBanner(title: "Couldn't submit", message: err.message)
        }
    }
}
