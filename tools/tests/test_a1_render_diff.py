from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).resolve().parents[1] / "a1-render-diff.py"
SPEC = importlib.util.spec_from_file_location("a1_render_diff", MODULE_PATH)
assert SPEC and SPEC.loader
a1_render_diff = importlib.util.module_from_spec(SPEC)
previous_bytecode_setting = sys.dont_write_bytecode
sys.dont_write_bytecode = True
try:
    SPEC.loader.exec_module(a1_render_diff)
finally:
    sys.dont_write_bytecode = previous_bytecode_setting


def write_pgm(path: Path, width: int, height: int, pixels: bytes) -> None:
    assert len(pixels) == width * height
    path.write_bytes(f"P5\n{width} {height}\n255\n".encode("ascii") + pixels)


def make_fake_render(left_pages: Path, right_pages: Path):
    def fake_render(_, pdf: Path, target_dir: Path, __):
        source = left_pages if pdf.name == "L-part.pdf" else right_pages
        target_dir.mkdir(parents=True, exist_ok=True)
        for page in source.glob("*.pgm"):
            (target_dir / page.name).write_bytes(page.read_bytes())
        return sorted(target_dir.glob("*.pgm"))

    return fake_render


class DiffStatsTests(unittest.TestCase):
    def test_reports_exact_and_over_tolerance_counts_separately(self) -> None:
        exact, over_tolerance, maximum, ink_left, ink_right = a1_render_diff.diff_stats(
            bytes([100, 20, 255]),
            bytes([108, 30, 255]),
        )

        self.assertEqual(exact, 2)
        self.assertEqual(over_tolerance, 1)
        self.assertEqual(maximum, 10)
        self.assertEqual((ink_left, ink_right), (2, 2))

    def test_tolerance_difference_is_not_exact_identity(self) -> None:
        exact, over_tolerance, maximum, _, _ = a1_render_diff.diff_stats(
            bytes([100]),
            bytes([108]),
        )

        self.assertEqual(exact, 1)
        self.assertEqual(over_tolerance, 0)
        self.assertEqual(maximum, 8)

    def test_same_pixel_sequence_with_different_dimensions_is_not_pairable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            left = root / "left.pgm"
            right = root / "right.pgm"
            write_pgm(left, 2, 3, bytes([255] * 6))
            write_pgm(right, 3, 2, bytes([255] * 6))

            _, _, left_pixels = a1_render_diff.read_pgm(left)
            _, _, right_pixels = a1_render_diff.read_pgm(right)
            self.assertEqual(left_pixels, right_pixels)
            self.assertEqual(a1_render_diff.read_pgm(left)[:2], (2, 3))
            self.assertEqual(a1_render_diff.read_pgm(right)[:2], (3, 2))

    def test_missing_segment_is_reported_on_the_correct_side(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            left = root / "lenient"
            right = root / "strict"
            left.mkdir()
            right.mkdir()
            (left / "L-shared.pdf").touch()
            (right / "S-shared.pdf").touch()
            (left / "L-strict-missing.pdf").touch()

            pairs, missing_left, missing_right = a1_render_diff.pair_parts(left, right)

            self.assertEqual(len(pairs), 1)
            self.assertEqual(missing_left, [])
            self.assertEqual(missing_right, ["strict-missing.pdf"])

    def test_missing_both_sides_returns_empty_pairs_and_both_missing_lists(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            left = root / "lenient"
            right = root / "strict"
            left.mkdir()
            right.mkdir()
            (left / "L-only.pdf").touch()
            (right / "S-other.pdf").touch()

            pairs, missing_left, missing_right = a1_render_diff.pair_parts(left, right)
            self.assertEqual(pairs, [])
            self.assertEqual(missing_left, ["other.pdf"])
            self.assertEqual(missing_right, ["only.pdf"])

    def test_main_reports_size_mismatch_and_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            left = root / "lenient"
            right = root / "strict"
            output = root / "report"
            left.mkdir()
            right.mkdir()
            (left / "L-part.pdf").touch()
            (right / "S-part.pdf").touch()
            rendered = root / "rendered"
            left_pages = rendered / "left"
            right_pages = rendered / "right"
            left_pages.mkdir(parents=True)
            right_pages.mkdir(parents=True)
            write_pgm(left_pages / "page-1.pgm", 2, 3, bytes([255] * 6))
            write_pgm(right_pages / "page-1.pgm", 3, 2, bytes([255] * 6))

            argv = [
                "a1-render-diff.py",
                "--lenient",
                str(left),
                "--strict",
                str(right),
                "--out",
                str(output),
            ]
            with mock.patch.object(
                a1_render_diff, "render_segment", side_effect=make_fake_render(left_pages, right_pages)
            ), mock.patch.object(
                a1_render_diff, "find_pdftoppm", return_value="pdftoppm"
            ), mock.patch.object(sys, "argv", argv):
                result = a1_render_diff.main()

            self.assertEqual(result, 1)
            self.assertIn("尺寸不符=1", (output / "summary.txt").read_text(encoding="utf-8"))
            self.assertIn("结果=FAIL", (output / "summary.txt").read_text(encoding="utf-8"))

    def test_main_returns_failure_when_no_segment_can_be_paired(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            left = root / "lenient"
            right = root / "strict"
            output = root / "report"
            left.mkdir()
            right.mkdir()
            (left / "L-only.pdf").touch()
            (right / "S-other.pdf").touch()

            argv = [
                "a1-render-diff.py",
                "--lenient",
                str(left),
                "--strict",
                str(right),
                "--out",
                str(output),
            ]
            with mock.patch.object(
                a1_render_diff, "find_pdftoppm", return_value="pdftoppm"
            ), mock.patch.object(sys, "argv", argv):
                result = a1_render_diff.main()

            self.assertEqual(result, 1)
            summary = (output / "summary.txt").read_text(encoding="utf-8")
            self.assertIn("结果=FAIL", summary)
            self.assertIn("lenient 缺少页段=['other.pdf']", summary)
            self.assertIn("strict 缺少页段=['only.pdf']", summary)

    def test_main_returns_two_for_invalid_input(self) -> None:
        argv = [
            "a1-render-diff.py",
            "--lenient",
            "does-not-exist-left",
            "--strict",
            "does-not-exist-right",
            "--out",
            "does-not-matter",
        ]
        with mock.patch.object(
            a1_render_diff, "find_pdftoppm", return_value="pdftoppm"
        ), mock.patch.object(sys, "argv", argv):
            result = a1_render_diff.main()

        self.assertEqual(result, 2)


if __name__ == "__main__":
    unittest.main()
