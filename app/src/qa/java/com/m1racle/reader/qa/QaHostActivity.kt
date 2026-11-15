package com.m1racle.reader.qa

/**
 * Isolated Compose host for Robolectric UI regression tests.
 *
 * Lives outside every feature package on purpose: it has to outlive the readers
 * it hosts, so deleting ui/epub or ui/viewer must not take the test host with
 * them.
 */
class QaHostActivity : androidx.activity.ComponentActivity()
