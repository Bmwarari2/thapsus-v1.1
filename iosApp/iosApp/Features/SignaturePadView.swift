// SignaturePadView.swift
// SwiftUI signature capture for proof-of-delivery. Captures touch strokes,
// renders them to a PNG, hands the bytes back via `onSubmit`. Used from
// PodCaptureView when a rider needs a signed receipt instead of (or alongside)
// the 4-digit OTP.

import SwiftUI
import UIKit

struct SignaturePadView: View {
    @Environment(\.dismiss) private var dismiss

    let onSubmit: (Data) -> Void

    @State private var strokes: [Stroke] = []
    @State private var current: Stroke = Stroke()

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                instructions
                canvas
                    .frame(maxWidth: .infinity)
                    .frame(height: 320)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.white)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Brand.ink.opacity(0.12), lineWidth: 1)
                    )
                    .padding(.horizontal, 20)

                HStack(spacing: 10) {
                    Button("Clear") {
                        strokes.removeAll()
                        current = Stroke()
                    }
                    .buttonStyle(.bordered)
                    .tint(.secondary)

                    Button("Use signature") { commit() }
                        .buttonStyle(InkButtonStyle())
                        .disabled(strokes.isEmpty)
                }
                .padding(.horizontal, 20)
            }
            .padding(.vertical, 20)
            .scrollContentBackground(.hidden)
            .appBackground()
            .navigationTitle("Signature")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var instructions: some View {
        Text("Have the recipient sign below.")
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .padding(.horizontal, 20)
    }

    private var canvas: some View {
        Canvas { ctx, _ in
            for s in strokes { draw(s, into: &ctx) }
            draw(current, into: &ctx)
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    current.points.append(value.location)
                }
                .onEnded { _ in
                    if !current.points.isEmpty {
                        strokes.append(current)
                        current = Stroke()
                    }
                }
        )
    }

    private func draw(_ stroke: Stroke, into ctx: inout GraphicsContext) {
        guard stroke.points.count > 1 else { return }
        var path = Path()
        path.move(to: stroke.points[0])
        for p in stroke.points.dropFirst() { path.addLine(to: p) }
        ctx.stroke(path, with: .color(Brand.ink), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    private func commit() {
        let size = CGSize(width: 600, height: 320)
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            UIColor.black.setStroke()
            let scaleX = size.width / UIScreen.main.bounds.width
            let scaleY: CGFloat = 1.0
            for stroke in strokes {
                let bezier = UIBezierPath()
                bezier.lineWidth = 3
                bezier.lineCapStyle = .round
                bezier.lineJoinStyle = .round
                guard let first = stroke.points.first else { continue }
                bezier.move(to: CGPoint(x: first.x * scaleX, y: first.y * scaleY))
                for p in stroke.points.dropFirst() {
                    bezier.addLine(to: CGPoint(x: p.x * scaleX, y: p.y * scaleY))
                }
                bezier.stroke()
            }
        }
        guard let png = image.pngData() else { return }
        onSubmit(png)
        dismiss()
    }

    private struct Stroke {
        var points: [CGPoint] = []
    }
}
