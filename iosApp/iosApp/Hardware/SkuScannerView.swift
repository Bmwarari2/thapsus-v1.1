// SkuScannerView.swift
// Operator-only camera scanner that reads STK-XXXXXX warehouse SKUs printed via
// LabelPrinter, plus generic Code 128 / QR / EAN-13 (so the same component can
// be repurposed for retailer barcodes later without a rewrite).
//
// Uses VisionKit's DataScannerViewController on iOS 16+; falls back to a plain
// AVCaptureSession + AVCaptureMetadataOutput on devices/simulators that don't
// support VisionKit (`DataScannerViewController.isSupported == false` covers
// the simulator case automatically).

import SwiftUI
import AVFoundation
import VisionKit

struct SkuScannerView: View {
    let onScan: (String) -> Void
    let onCancel: () -> Void

    @State private var permissionDenied: Bool = false
    @State private var lastDetected: String?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if permissionDenied {
                permissionDeniedView
            } else if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                DataScannerRepresentable(
                    onPayload: handleStablePayload
                )
                .ignoresSafeArea()
            } else {
                AVCaptureBarcodeRepresentable(
                    onPayload: handleStablePayload
                )
                .ignoresSafeArea()
            }

            overlay
        }
        .task {
            await requestCameraPermission()
        }
    }

    /// Debounced scan handler — DataScanner emits per-frame, so require the
    /// same payload to land twice before forwarding upstream.
    private func handleStablePayload(_ payload: String) {
        let cleaned = payload.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !cleaned.isEmpty else { return }
        if lastDetected == cleaned {
            onScan(cleaned)
        } else {
            lastDetected = cleaned
        }
    }

    private func requestCameraPermission() async {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            permissionDenied = false
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            permissionDenied = !granted
        case .denied, .restricted:
            permissionDenied = true
        @unknown default:
            permissionDenied = true
        }
    }

    private var overlay: some View {
        VStack {
            HStack {
                Button {
                    onCancel()
                } label: {
                    Label("Cancel", systemImage: "xmark.circle.fill")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(.black.opacity(0.55), in: Capsule())
                }
                Spacer()
            }
            .padding()
            Spacer()
            VStack(spacing: 6) {
                Text("Align the SKU label inside the frame")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Text("STK-XXXXXX • Code 128 / QR / EAN-13")
                    .font(.caption.monospaced())
                    .foregroundStyle(.white.opacity(0.75))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(.bottom, 36)
        }
    }

    private var permissionDeniedView: some View {
        VStack(spacing: 12) {
            Image(systemName: "camera.fill.badge.ellipsis")
                .font(.system(size: 38))
                .foregroundStyle(.white)
            Text("Camera access denied")
                .font(.headline).foregroundStyle(.white)
            Text("Enable camera access for Thapsus Cargo in Settings to scan SKU labels.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.75))
                .padding(.horizontal, 28)
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                Text("Open Settings").font(.callout.weight(.semibold))
            }
            .buttonStyle(.borderedProminent)
            .tint(Brand.orange)
            Button("Cancel", role: .cancel) { onCancel() }
                .tint(.white.opacity(0.85))
                .padding(.top, 4)
        }
        .padding(.horizontal, 18)
    }
}

private struct DataScannerRepresentable: UIViewControllerRepresentable {
    let onPayload: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.code128, .qr, .ean13, .ean8, .pdf417])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        try? scanner.startScanning()
        return scanner
    }

    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPayload: onPayload)
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onPayload: (String) -> Void
        private var lastFiredAt: Date = .distantPast

        init(onPayload: @escaping (String) -> Void) {
            self.onPayload = onPayload
        }

        func dataScanner(_ dataScanner: DataScannerViewController,
                         didTapOn item: RecognizedItem) {
            forward(item)
        }

        func dataScanner(_ dataScanner: DataScannerViewController,
                         didAdd addedItems: [RecognizedItem],
                         allItems: [RecognizedItem]) {
            addedItems.forEach { forward($0) }
        }

        private func forward(_ item: RecognizedItem) {
            if case let .barcode(barcode) = item, let payload = barcode.payloadStringValue {
                let now = Date()
                guard now.timeIntervalSince(lastFiredAt) > 0.4 else { return }
                lastFiredAt = now
                onPayload(payload)
            }
        }
    }
}

private struct AVCaptureBarcodeRepresentable: UIViewControllerRepresentable {
    let onPayload: (String) -> Void

    func makeUIViewController(context: Context) -> AVCaptureBarcodeViewController {
        let vc = AVCaptureBarcodeViewController()
        vc.onPayload = onPayload
        return vc
    }

    func updateUIViewController(_ uiViewController: AVCaptureBarcodeViewController, context: Context) {}
}

final class AVCaptureBarcodeViewController: UIViewController, @preconcurrency AVCaptureMetadataOutputObjectsDelegate {
    var onPayload: ((String) -> Void)?
    private let session = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer?
    private var lastFiredAt: Date = .distantPast

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        guard
            let device = AVCaptureDevice.default(for: .video),
            let input = try? AVCaptureDeviceInput(device: device),
            session.canAddInput(input)
        else {
            return
        }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.code128, .qr, .ean13, .ean8, .pdf417]

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        self.preview = layer

        let captureSession = session
        DispatchQueue.global(qos: .userInitiated).async {
            captureSession.startRunning()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning { session.stopRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let payload = obj.stringValue,
              !payload.isEmpty else { return }
        let now = Date()
        guard now.timeIntervalSince(lastFiredAt) > 0.4 else { return }
        lastFiredAt = now
        onPayload?(payload)
    }
}
