// CameraPickerView.swift
// UIImagePickerController-backed camera/library picker. Used for POD capture
// and for parcel-condition photos at intake.

import SwiftUI
import UIKit
import AVFoundation
import PhotosUI

/// Distinct failure modes the picker can surface to its caller, so the call
/// site can render the right UI (e.g. a "Camera access denied — open Settings"
/// banner vs. a silent dismiss for a user-cancelled pick).
enum CameraPickerFailure: Equatable {
    case cameraAccessDenied
    case cameraAccessRestricted
}

/// SwiftUI wrapper that presents either the device camera or the photo library
/// and returns a JPEG-encoded `Data` blob ready to upload to Supabase Storage.
///
/// `onPicked(nil, nil)` means the user cancelled — show nothing.
/// `onPicked(nil, .cameraAccessDenied)` means the user previously denied access;
/// the caller should surface a callout linking to Settings.
struct CameraPickerView: UIViewControllerRepresentable {
    enum Source { case camera, library }

    let source: Source
    let onPicked: (Data?, CameraPickerFailure?) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        switch source {
        case .camera:
            // Pre-flight the camera authorisation status.  AVCaptureDevice
            // gives us .denied / .restricted / .notDetermined / .authorized;
            // .notDetermined triggers the system prompt automatically when
            // UIImagePickerController is presented, so we forward.  For a
            // hard denial we surface a typed failure so the caller can link
            // the user to Settings instead of presenting an empty picker.
            let status = AVCaptureDevice.authorizationStatus(for: .video)
            switch status {
            case .denied:
                DispatchQueue.main.async { self.onPicked(nil, .cameraAccessDenied) }
                return UIViewController()
            case .restricted:
                DispatchQueue.main.async { self.onPicked(nil, .cameraAccessRestricted) }
                return UIViewController()
            case .notDetermined, .authorized:
                break
            @unknown default:
                break
            }
            let picker = UIImagePickerController()
            picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
            picker.cameraCaptureMode = .photo
            picker.allowsEditing = false
            picker.delegate = context.coordinator
            return picker
        case .library:
            var config = PHPickerConfiguration()
            config.filter = .images
            config.selectionLimit = 1
            let picker = PHPickerViewController(configuration: config)
            picker.delegate = context.coordinator
            return picker
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) { }

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, PHPickerViewControllerDelegate {
        let onPicked: (Data?, CameraPickerFailure?) -> Void
        init(onPicked: @escaping (Data?, CameraPickerFailure?) -> Void) { self.onPicked = onPicked }

        // NOTE: do NOT call `picker.dismiss(animated:)` from these
        // callbacks. The parent SwiftUI sheet's `onPicked` closure flips
        // its own `@State` binding to false to dismiss the camera; calling
        // dismiss on the picker simultaneously races SwiftUI's own
        // dismissal in the presentation chain. On iOS 17+ that race
        // intermittently collapses the OUTER PodCaptureView sheet too —
        // the rider takes a photo and ends up back on the run stop list.
        // Letting SwiftUI own the dismissal keeps both layers in sync.

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let img = info[.originalImage] as? UIImage
            onPicked(img?.jpegData(compressionQuality: 0.85), nil)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onPicked(nil, nil)
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else {
                onPicked(nil, nil); return
            }
            provider.loadObject(ofClass: UIImage.self) { obj, _ in
                let data = (obj as? UIImage)?.jpegData(compressionQuality: 0.85)
                DispatchQueue.main.async { self.onPicked(data, nil) }
            }
        }
    }
}
