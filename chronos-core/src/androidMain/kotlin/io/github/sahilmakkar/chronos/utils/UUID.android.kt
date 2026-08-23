package io.github.sahilmakkar.chronos.utils

import java.util.UUID

actual fun randomUUIDString(): String {
    return UUID.randomUUID().toString()
}
