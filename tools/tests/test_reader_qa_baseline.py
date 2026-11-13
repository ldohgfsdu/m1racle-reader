"""Negative controls for Reader QA startup and UI hierarchy verification."""
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from reader_qa_baseline import inspect_hierarchy, launch_time


class BaselineDetectorTest(unittest.TestCase):
    def test_am_start_reports_positive_time(self):
        self.assertEqual(258, launch_time("Status: ok\nTotalTime: 258\nWaitTime: 280\n"))

    def test_missing_or_zero_time_fails(self):
        for raw in ("", "TotalTime: 0\n", "ThisTime: 12\n"):
            with self.assertRaises(AssertionError):
                launch_time(raw)

    def test_real_qa_package_nodes_required(self):
        good = '<hierarchy><node package="com.m1racle.reader.qa" bounds="[10,60][100,120]"/></hierarchy>'
        self.assertEqual(1, inspect_hierarchy(good))
        wrong = '<hierarchy><node package="com.android.launcher" bounds="[10,60][100,120]"/></hierarchy>'
        with self.assertRaises(AssertionError):
            inspect_hierarchy(wrong)

    def test_malformed_hierarchy_is_not_accepted(self):
        import xml.etree.ElementTree as ET
        with self.assertRaises(ET.ParseError):
            inspect_hierarchy("<hierarchy>")


if __name__ == "__main__":
    unittest.main()
