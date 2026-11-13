"""Refresh and validate the repository-local Code Wiki."""

from __future__ import annotations

import runpy
from pathlib import Path
import sys


REPO = Path(__file__).resolve().parents[1]
GENERATOR = REPO / "docs/code-wiki/tools/build_reference.py"


if __name__ == "__main__":
    sys.argv[0] = str(GENERATOR)
    runpy.run_path(str(GENERATOR), run_name="__main__")
