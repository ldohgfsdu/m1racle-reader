# R9 Human-Feel Device Acceptance

- Date: 2026-07-19
- Result: PASS / DEVICE_ACCEPTED
- Device: 25098PN5AC, Android 16, 1220x2656, 120 Hz capable
- Endpoint: 198.18.0.1:37173
- Package: com.markleaf.app.qa
- Activity: real com.markleaf.app.MainActivity
- Document: Large Search Document
- Entry: frozen QA APK reinstalled with data retained, process cold-started, document opened from the real Home card
- Human protocol: normal reading; slow drag; micro-jitter; clear reversal; immediate reversal after fling; continued-use feel
- Human outcome: no stickiness, unintended jump, twitch, delayed direction switch, visible snap artifact, or feel drift reported

## Frozen hashes

- Installed and candidate APK: 7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf
- Chrome Perfetto: 38451ae9a93d331713b6850f633f9c1e1e52739dd5dee94220cae8aa660de467
- First-entry follow-up Perfetto: d0fdac1c120de1e6259d6df447f6af1259da05dc3aa74d2d2f0844784700cf2d
- Human-reject reference Perfetto: 510695ec069798a988da859a2c9dac5a913da4fee2a9493ce668a043b9ddd1a0

## Evidence caveat

The post-pass gfxinfo dump is invalid because the app process was no longer dumpable. It is retained for audit and is not treated as positive performance evidence. The crash buffer was empty, and the saved logcat contains no MarkLeaf FATAL or ANR marker. Human feel is the authoritative result for this gate; prior automated ADB, gfxinfo, and Perfetto results remain the automated evidence.
