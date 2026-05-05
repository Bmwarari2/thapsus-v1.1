package com.thapsus.cargo.data.remote

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed token store. Replaces the NSUserDefaults v1 implementation
 * after the 2026-04-30 audit (finding §2.4 / S-2): tokens were sitting in the
 * app's plain `<bundle>.plist`, readable on a backed-up or jailbroken device.
 *
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` is the strictest sensible
 * accessibility level: items are unreadable while the device is locked and
 * are NOT included in iCloud / iTunes backups, so a stolen backup can't
 * exfiltrate the token.
 *
 * On first launch after upgrade we migrate any pre-existing values from
 * `NSUserDefaults` into Keychain, clear them from the plist, then set a flag
 * so the migration runs exactly once. The `MIGRATION_FLAG` itself stays in
 * NSUserDefaults — it isn't sensitive and survives uninstall/reinstall, which
 * is the desired behaviour (a fresh install has no legacy values to migrate).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class SecureSettings {
    init { migrateFromUserDefaultsIfNeeded() }

    actual fun getString(key: String): String? = readKeychain(key)

    actual fun putString(key: String, value: String?) {
        if (value == null) deleteKeychain(key) else writeKeychain(key, value)
    }

    actual fun clear() {
        KEYCHAIN_ACCOUNTS.forEach(::deleteKeychain)
    }

    // --- Keychain operations -------------------------------------------------

    private fun readKeychain(key: String): String? = memScoped {
        val serviceCf = CFBridgingRetain(SERVICE)
        val accountCf = CFBridgingRetain(key)
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceCf,
            kSecAttrAccount to accountCf,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )
        try {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
        } finally {
            CFRelease(query)
            CFRelease(serviceCf)
            CFRelease(accountCf)
        }
    }

    private fun writeKeychain(key: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val serviceCf = CFBridgingRetain(SERVICE)
        val accountCf = CFBridgingRetain(key)
        val dataCf = CFBridgingRetain(data)
        val matchQuery = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceCf,
            kSecAttrAccount to accountCf
        )
        val updatePayload = cfDictionaryOf(
            kSecValueData to dataCf
        )
        val updateStatus = SecItemUpdate(matchQuery, updatePayload)
        CFRelease(matchQuery)
        CFRelease(updatePayload)
        if (updateStatus == errSecSuccess) {
            CFRelease(serviceCf); CFRelease(accountCf); CFRelease(dataCf)
            return
        }
        if (updateStatus != errSecItemNotFound) {
            // Recover from a corrupt entry by deleting then re-adding.
            deleteKeychain(key)
        }
        val addQuery = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceCf,
            kSecAttrAccount to accountCf,
            kSecValueData to dataCf,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        )
        SecItemAdd(addQuery, null)
        CFRelease(addQuery)
        CFRelease(serviceCf); CFRelease(accountCf); CFRelease(dataCf)
    }

    private fun deleteKeychain(key: String) {
        val serviceCf = CFBridgingRetain(SERVICE)
        val accountCf = CFBridgingRetain(key)
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceCf,
            kSecAttrAccount to accountCf
        )
        SecItemDelete(query)
        CFRelease(query)
        CFRelease(serviceCf)
        CFRelease(accountCf)
    }

    // --- One-shot migration from NSUserDefaults ------------------------------

    private fun migrateFromUserDefaultsIfNeeded() {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.boolForKey(MIGRATION_FLAG)) return
        KEYCHAIN_ACCOUNTS.forEach { key ->
            val legacy = defaults.stringForKey(key) ?: return@forEach
            writeKeychain(key, legacy)
            defaults.removeObjectForKey(key)
        }
        defaults.setBool(true, forKey = MIGRATION_FLAG)
        defaults.synchronize()
    }

    private companion object {
        const val SERVICE = "com.thapsus.cargo.SecureSettings"
        const val MIGRATION_FLAG = "thapsus.secureSettings.keychainMigrated.v1"

        // Companion-scoped so `init { migrate() }` doesn't read it before the
        // instance property finishes initialising. The previous instance-level
        // `private val keychainAccounts = listOf(...)` was declared AFTER the
        // init block, so on app launch K/N hit it as null and crashed inside
        // `Iterable.iterator()` trampoline before SignInView could render.
        val KEYCHAIN_ACCOUNTS = listOf(
            SecureKeys.SC_TOKEN,
            SecureKeys.SUPABASE_TOKEN,
            SecureKeys.SUPABASE_TOKEN_EXP,
            SecureKeys.USER_PROFILE
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfDictionaryOf(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFMutableDictionaryRef {
    val dict = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        pairs.size.convert(),
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr
    )!!
    pairs.forEach { (k, v) -> CFDictionaryAddValue(dict, k, v) }
    return dict
}
