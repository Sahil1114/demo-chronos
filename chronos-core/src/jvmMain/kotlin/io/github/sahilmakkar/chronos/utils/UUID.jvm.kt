package io.github.sahilmakkar.chronos.utils

import java.util.UUID


actual fun randomUUIDString(): String = UUID.randomUUID().toString()
