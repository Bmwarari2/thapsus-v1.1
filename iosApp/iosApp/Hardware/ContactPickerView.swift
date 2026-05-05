// ContactPickerView.swift
// Wraps CNContactPickerViewController so the rider can grab a recipient's phone
// number without retyping it. Used in PodCaptureView and on the customer parcel
// pre-registration form.

import ContactsUI
import SwiftUI

struct ContactPickerView: UIViewControllerRepresentable {
    let onPicked: (_ name: String?, _ phone: String?) -> Void

    func makeUIViewController(context: Context) -> CNContactPickerViewController {
        let vc = CNContactPickerViewController()
        vc.delegate = context.coordinator
        vc.predicateForEnablingContact = NSPredicate(format: "phoneNumbers.@count > 0")
        vc.displayedPropertyKeys = [
            CNContactGivenNameKey,
            CNContactFamilyNameKey,
            CNContactPhoneNumbersKey
        ]
        return vc
    }

    func updateUIViewController(_ uiViewController: CNContactPickerViewController, context: Context) { }

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, CNContactPickerDelegate {
        let onPicked: (_ name: String?, _ phone: String?) -> Void
        init(onPicked: @escaping (_ name: String?, _ phone: String?) -> Void) {
            self.onPicked = onPicked
        }

        func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
            let name = [contact.givenName, contact.familyName]
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            let phone = contact.phoneNumbers.first?.value.stringValue
            onPicked(name.isEmpty ? nil : name, phone)
        }

        func contactPicker(
            _ picker: CNContactPickerViewController,
            didSelect contactProperty: CNContactProperty
        ) {
            let name = [contactProperty.contact.givenName, contactProperty.contact.familyName]
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            let phone = (contactProperty.value as? CNPhoneNumber)?.stringValue
                ?? contactProperty.contact.phoneNumbers.first?.value.stringValue
            onPicked(name.isEmpty ? nil : name, phone)
        }

        func contactPickerDidCancel(_ picker: CNContactPickerViewController) {
            onPicked(nil, nil)
        }
    }
}
