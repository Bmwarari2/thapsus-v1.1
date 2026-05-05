package com.thapsus.cargo.android.hardware

/**
 * SKU minter for operator-printed warehouse labels. Mirrors the iOS
 * `WarehouseSku.mint()` exactly — same alphabet (Crockford-style without
 * I/O/0/1 to avoid handwriting confusion), same `STK-XXXXXX` shape so SKUs
 * minted on either platform are interchangeable.
 */
object WarehouseSku {
    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray()

    fun mint(): String {
        val suffix = (0 until 6).map { alphabet.random() }.joinToString("")
        return "STK-$suffix"
    }
}

object LabelWelcome {
    private val messages = listOf(
        "Karibu! Thanks for shipping with us — we'll see you on the next one.",
        "Asante sana — your parcel is in good hands.",
        "Welcome aboard. Here's to many more parcels together!",
        "Hujambo! Glad you chose Thapsus. We've got it from here.",
        "Cheers from Stockport — onward to Nairobi!",
        "Thanks for trusting us with your parcel — enjoy!",
        "Mambo vipi! Your shipment is one of many we hope to handle.",
        "From the UK, with love. Karibu Thapsus Cargo.",
        "Asante for being part of the Thapsus family.",
        "Safe travels, little parcel. Thanks for choosing us!"
    )

    fun random(): String = messages.random()
}
