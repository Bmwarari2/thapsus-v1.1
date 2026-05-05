// LabelPrinter.swift
// Phase C — operator-printed warehouse SKU labels via AirPrint. Customers no
// longer print anything; operators receive parcels by description, mint a SKU,
// stick the printed label on the box, and post the receive call to the server.
//
// SKU format: STK-XXXXXX (Stockport hub + 6-char Crockford-style alphabet
// without I/O/0/1 to avoid handwriting confusion). 10 characters total fits
// comfortably in a Code 128 barcode at 4×6" thermal density.

import SwiftUI
import UIKit
import CoreImage.CIFilterBuiltins
import ThapsusShared

enum WarehouseSku {
    private static let alphabet = Array("23456789ABCDEFGHJKLMNPQRSTUVWXYZ")

    static func mint() -> String {
        let suffix = (0..<6).map { _ in alphabet.randomElement()! }
        return "STK-" + String(suffix)
    }
}

/// Random thank-you / welcome line printed on the bottom of every label so
/// every customer gets a small touch of warmth in the warehouse pipeline.
/// The list is intentionally short so the operator sees variety without
/// the messages becoming background noise.
enum LabelWelcome {
    private static let messages: [String] = [
        "Karibu! Thanks for shipping with us — we'll see you on the next one.",
        "Asante sana — your parcel is in good hands.",
        "Welcome aboard. Here's to many more parcels together!",
        "Hujambo! Glad you chose Thapsus. We've got it from here.",
        "Cheers from Stockport — onward to Nairobi!",
        "Thanks for trusting us with your parcel — enjoy!",
        "Mambo vipi! Your shipment is one of many we hope to handle.",
        "From the UK, with love. Karibu Thapsus Cargo.",
        "Asante for being part of the Thapsus family.",
        "Safe travels, little parcel. Thanks for choosing us!",
    ]

    static func random() -> String {
        messages.randomElement() ?? messages[0]
    }
}

enum LabelPrinter {
    /// Renders the SwiftUI label to a print-ready PNG and presents the
    /// AirPrint dialog. `onComplete(true)` fires when the print job was
    /// dispatched (or the operator hit "Print" on a saved-to-PDF picker).
    @MainActor
    static func print(_ label: WarehouseLabel, onComplete: @escaping (Bool) -> Void) {
        let renderer = ImageRenderer(content: label)
        renderer.scale = 3 // 300 dpi-ish on a 4×6" label
        guard let image = renderer.uiImage else {
            onComplete(false)
            return
        }

        let info = UIPrintInfo.printInfo()
        info.outputType = .general
        info.jobName = "Thapsus Cargo \(label.sku)"

        let controller = UIPrintInteractionController.shared
        controller.printInfo = info
        controller.printingItem = image
        controller.present(animated: true) { _, ok, _ in
            onComplete(ok)
        }
    }
}

/// 4×6" warehouse label rendered as a SwiftUI view so we can both preview it
/// in the print sheet and snapshot it for AirPrint.
struct WarehouseLabel: View {
    let sku: String
    let customerName: String
    let customerWarehouseCode: String?  // TC-XXXX, the per-user shelf identifier
    let warehouseCode: String           // STK-01, the hub identifier
    let retailer: String?
    let descriptionText: String?
    let welcomeMessage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                Image("Logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 28, height: 28)
                Text("THAPSUS CARGO")
                    .font(.system(size: 16, weight: .heavy)).tracking(3)
                Spacer()
                Text(warehouseCode)
                    .font(.system(.subheadline, design: .monospaced).weight(.heavy))
            }
            Divider()

            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text("CUSTOMER").font(.caption2.weight(.heavy)).tracking(2)
                    Spacer()
                    if let code = customerWarehouseCode, !code.isEmpty {
                        Text(code)
                            .font(.system(.caption, design: .monospaced).weight(.heavy))
                            .foregroundStyle(.black.opacity(0.6))
                    }
                }
                Text(customerName.uppercased())
                    .font(.system(size: 22, weight: .heavy))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }

            if let r = retailer, !r.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("RETAILER").font(.caption2.weight(.heavy)).tracking(2)
                    Text(r).font(.callout.weight(.semibold))
                }
            }

            if let d = descriptionText, !d.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("DESCRIPTION").font(.caption2.weight(.heavy)).tracking(2)
                    Text(d).font(.subheadline).lineLimit(2)
                }
            }

            Spacer(minLength: 4)

            if let barcode = Self.barcode(from: sku) {
                Image(uiImage: barcode)
                    .resizable()
                    .interpolation(.none)
                    .aspectRatio(contentMode: .fit)
                    .frame(height: 70)
                    .frame(maxWidth: .infinity)
            }
            Text(sku)
                .font(.system(.title2, design: .monospaced).weight(.heavy))
                .frame(maxWidth: .infinity)

            Text(welcomeMessage)
                .font(.system(size: 9.5, weight: .medium))
                .italic()
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .foregroundStyle(.black.opacity(0.65))
                .padding(.top, 4)
        }
        .padding(20)
        .frame(width: 384, height: 576) // 4×6" at 96 dpi base; ImageRenderer scales up
        .background(Color.white)
        .foregroundStyle(.black)
    }

    private static func barcode(from text: String) -> UIImage? {
        let filter = CIFilter.code128BarcodeGenerator()
        filter.message = Data(text.utf8)
        filter.quietSpace = 8
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 3, y: 3))
        let context = CIContext()
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
