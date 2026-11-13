"""Release preflight negative controls (no keystore or Android SDK required)."""
from pathlib import Path
import io
import struct
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_gate import release_ready, verify_apk_native_libraries, verify_elf, version_identity


def fake_elf64(alignment: int) -> bytes:
    data = bytearray(128)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<H", data, 54, 56)
    struct.pack_into("<H", data, 56, 1)
    struct.pack_into("<I", data, 64, 1)  # PT_LOAD
    struct.pack_into("<Q", data, 64 + 48, alignment)
    return bytes(data)


class ReleaseGateTest(unittest.TestCase):
    def test_accepts_current_committed_identity_for_ci(self):
        self.assertEqual((97, "1.0.0"), version_identity("versionCode=97\nversionName=1.0.0\n"))

    def test_rejects_duplicate_and_invalid_version(self):
        for raw in ("versionCode=97\nversionName=1.0.0\nversionCode=98\n",
                    "versionCode=0\nversionName=1.0.0\n",
                    "versionCode=98\nversionName=1.0.0-rc1\n"):
            with self.assertRaises(ValueError):
                version_identity(raw)

    def test_old_candidate_cannot_be_published(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "app").mkdir()
            (root / "app/version.properties").write_text("versionCode=97\nversionName=1.0.0\n")
            with self.assertRaisesRegex(ValueError, "Refusing to replace"):
                release_ready(root)

    def test_new_release_requires_authored_notes(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "app").mkdir()
            (root / "app/version.properties").write_text("versionCode=98\nversionName=1.0.1\n")
            with self.assertRaisesRegex(ValueError, "Missing authored"):
                release_ready(root)
            (root / "docs/releases").mkdir(parents=True)
            (root / "docs/releases/v1.0.1.md").write_text("## 新增\n无\n")
            with self.assertRaisesRegex(ValueError, "missing heading"):
                release_ready(root)

    def test_native_alignment_must_be_16k(self):
        verify_elf(fake_elf64(16384), "lib/arm64-v8a/test.so")
        with self.assertRaisesRegex(ValueError, "below 16 KiB"):
            verify_elf(fake_elf64(4096), "lib/arm64-v8a/test.so")

    def test_apk_must_contain_uncompressed_native_libraries(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "app.apk"
            with zipfile.ZipFile(apk, "w") as out:
                out.writestr("lib/arm64-v8a/test.so", fake_elf64(16384),
                             compress_type=zipfile.ZIP_STORED)
            self.assertEqual(1, verify_apk_native_libraries(apk))
            with zipfile.ZipFile(apk, "w") as out:
                out.writestr("lib/arm64-v8a/test.so", fake_elf64(16384),
                             compress_type=zipfile.ZIP_DEFLATED)
            with self.assertRaisesRegex(ValueError, "compressed"):
                verify_apk_native_libraries(apk)

    def test_no_native_libraries_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "app.apk"
            with zipfile.ZipFile(apk, "w") as out:
                out.writestr("classes.dex", b"test")
            with self.assertRaisesRegex(ValueError, "No native"):
                verify_apk_native_libraries(apk)


if __name__ == "__main__":
    unittest.main()
