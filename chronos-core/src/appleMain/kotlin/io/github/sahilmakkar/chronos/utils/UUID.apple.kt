package io.github.sahilmakkar.chronos.utils

import platform.Foundation.NSUUID

actual fun randomUUIDString(): String = NSUUID().UUIDString()
