package net.squanchy.support.time

import org.threeten.bp.format.DateTimeFormatter

fun shortTimeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun shortDateFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
