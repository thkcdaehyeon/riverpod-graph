package com.ki960213.riverpodgraph

import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal fun Path.readUtf8Text(): String =
    toFile().readText(StandardCharsets.UTF_8)

internal fun Path.containsText(text: String): Boolean =
    toFile().useLines(StandardCharsets.UTF_8) { lines ->
        lines.any { line -> line.contains(text) }
    }
