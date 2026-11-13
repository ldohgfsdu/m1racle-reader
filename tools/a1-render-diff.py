#!/usr/bin/env python3
"""A1 逐页渲染比对（独立渲染器：poppler / pdftoppm）。

用法：
    python tools/a1-render-diff.py --lenient <L 产物目录> --strict <S 产物目录> \
        --out <报告目录> [--dpi 100] [--top 50]

做的事：
 1. 把 L-/S- 两套产物按页段配对（文件名去掉 L-/S- 前缀后相同即配对）；
 2. 每段用 pdftoppm 渲染成灰度 PGM（独立于 PDFBox 的渲染器）；
 3. 逐页做字节比较：完全相同 → 记 identical；不同 → 分别计算精确差异、
    超过容差的差异、最大通道差、尺寸和两侧墨量；
 4. 输出 CSV（逐页）+ summary.txt（汇总）+ diff/ 下差异页的并排 PGM。

 退出码：0 表示全部页面字节一致；1 表示存在任何差异、尺寸不符或缺页；
 2 表示工具或输入错误。容差只分类报告，不会把差异判成通过。
"""
from __future__ import annotations

import argparse
import csv
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

POPPLER_NAMES = ("pdftoppm", "pdftoppm.exe")


class RenderDiffError(Exception):
    pass


def find_pdftoppm() -> str:
    for name in POPPLER_NAMES:
        found = shutil.which(name)
        if found:
            return found
    raise RenderDiffError("找不到 pdftoppm（poppler）。请把它加入 PATH。")


def read_pgm(path: Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if not data.startswith(b"P5"):
        raise ValueError(f"{path} 不是二进制 PGM")
    # 解析 header: P5 <w> <h> <maxval>，允许 # 注释
    index = 2
    fields: list[int] = []
    while len(fields) < 3:
        while index < len(data) and data[index : index + 1].isspace():
            index += 1
        if data[index : index + 1] == b"#":
            while index < len(data) and data[index] not in (10, 13):
                index += 1
            continue
        start = index
        while index < len(data) and not data[index : index + 1].isspace():
            index += 1
        fields.append(int(data[start:index]))
    index += 1  # 单个空白分隔符
    width, height, maxval = fields
    if maxval != 255:
        raise ValueError(f"{path} maxval={maxval}，只支持 255")
    pixels = data[index:]
    if len(pixels) != width * height:
        raise ValueError(
            f"{path} 像素载荷长度={len(pixels)}，预期 {width * height}"
        )
    return width, height, pixels


def diff_stats(left: bytes, right: bytes) -> tuple[int, int, int, int, int]:
    """返回 (精确差异, 容差差异, 最大差, 左墨量, 右墨量)；阈值 8 与设备侧一致。"""
    if len(left) != len(right):
        raise ValueError("两页像素数不同")
    exact_diff_pixels = 0
    diff_pixels = 0
    max_diff = 0
    ink_left = 0
    ink_right = 0
    for a, b in zip(left, right):
        delta = a - b
        if delta < 0:
            delta = -delta
        if delta:
            exact_diff_pixels += 1
            if delta > 8:
                diff_pixels += 1
            if delta > max_diff:
                max_diff = delta
        if a < 245:
            ink_left += 1
        if b < 245:
            ink_right += 1
    return exact_diff_pixels, diff_pixels, max_diff, ink_left, ink_right


def pair_parts(
    lenient_dir: Path, strict_dir: Path
) -> tuple[list[tuple[Path, Path]], list[str], list[str]]:
    def index(directory: Path, prefix: str) -> dict[str, Path]:
        table: dict[str, Path] = {}
        for path in sorted(directory.glob("*.pdf")):
            name = path.name
            if name.startswith(prefix):
                name = name[len(prefix) :]
            table[name] = path
        return table

    left = index(lenient_dir, "L-")
    right = index(strict_dir, "S-")
    common = sorted(set(left) & set(right))
    missing_left = sorted(set(right) - set(left))
    missing_right = sorted(set(left) - set(right))
    if missing_left or missing_right:
        print(
            f"[失败] 缺页段：lenient={missing_left}, strict={missing_right}",
            file=sys.stderr,
        )
    return (
        [(left[name], right[name]) for name in common],
        missing_left,
        missing_right,
    )


def render_segment(pdftoppm: str, pdf: Path, target_dir: Path, dpi: int) -> list[Path]:
    target_dir.mkdir(parents=True, exist_ok=True)
    prefix = target_dir / "page"
    try:
        subprocess.run(
            [pdftoppm, "-gray", "-r", str(dpi), str(pdf), str(prefix)],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        stderr = getattr(error, "stderr", None)
        detail = stderr.decode("utf-8", errors="replace") if stderr else ""
        raise RenderDiffError(f"渲染失败：{pdf}\n{detail}".rstrip()) from error
    return sorted(target_dir.glob("page*.pgm"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lenient", required=True, type=Path)
    parser.add_argument("--strict", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--dpi", type=int, default=100)
    parser.add_argument("--top", type=int, default=50, help="报表里列出的差异页数量")
    args = parser.parse_args()

    if args.dpi <= 0:
        print("--dpi 必须大于 0", file=sys.stderr)
        return 2
    if args.top < 0:
        print("--top 不能小于 0", file=sys.stderr)
        return 2
    if not args.lenient.is_dir():
        print(f"lenient 目录不存在：{args.lenient}", file=sys.stderr)
        return 2
    if not args.strict.is_dir():
        print(f"strict 目录不存在：{args.strict}", file=sys.stderr)
        return 2
    try:
        pdftoppm = find_pdftoppm()
        args.out.mkdir(parents=True, exist_ok=True)
        pairs, missing_left, missing_right = pair_parts(args.lenient, args.strict)
    except (OSError, RenderDiffError) as error:
        print(error, file=sys.stderr)
        return 2
    rows: list[dict[str, object]] = []
    page_number = 0

    try:
        with tempfile.TemporaryDirectory(prefix="a1-render-") as temp:
            temp_dir = Path(temp)
            for part_index, (left_pdf, right_pdf) in enumerate(pairs, start=1):
                left_pages = render_segment(pdftoppm, left_pdf, temp_dir / f"L{part_index}", args.dpi)
                right_pages = render_segment(pdftoppm, right_pdf, temp_dir / f"S{part_index}", args.dpi)
                if len(left_pages) != len(right_pages):
                    rows.append(
                        {
                            "page": f"{left_pdf.name}",
                            "lenient_file": left_pdf.name,
                            "strict_file": right_pdf.name,
                            "segment": part_index,
                            "status": "page-count-mismatch",
                            "lenient_pages": len(left_pages),
                            "strict_pages": len(right_pages),
                            "lenient_page": "",
                            "strict_page": "",
                            "size_lenient": "",
                            "size_strict": "",
                            "exact_diff_pixels": "",
                            "diff_pixels": "",
                            "max_diff": "",
                            "ink_lenient": "",
                            "ink_strict": "",
                        }
                    )
                    continue
                for offset, (left_page, right_page) in enumerate(zip(left_pages, right_pages)):
                    page_number += 1
                    left_bytes = left_page.read_bytes()
                    right_bytes = right_page.read_bytes()
                    lenient_width, lenient_height, left_pixels = read_pgm(left_page)
                    strict_width, strict_height, right_pixels = read_pgm(right_page)
                    size_lenient = f"{lenient_width}x{lenient_height}"
                    size_strict = f"{strict_width}x{strict_height}"
                    page_fields = {
                        "page": page_number,
                        "lenient_file": left_pdf.name,
                        "strict_file": right_pdf.name,
                        "segment": part_index,
                        "lenient_page": left_page.name,
                        "strict_page": right_page.name,
                        "size_lenient": size_lenient,
                        "size_strict": size_strict,
                    }
                    if (lenient_width, lenient_height) != (strict_width, strict_height):
                        rows.append(
                            {
                                **page_fields,
                                "status": "size-mismatch",
                                "lenient_pages": len(left_pages),
                                "strict_pages": len(right_pages),
                                "exact_diff_pixels": "",
                                "diff_pixels": "",
                                "max_diff": "",
                                "ink_lenient": "",
                                "ink_strict": "",
                            }
                        )
                        continue
                    if left_bytes == right_bytes:
                        rows.append(
                            {
                                **page_fields,
                                "status": "identical",
                                "lenient_pages": len(left_pages),
                                "strict_pages": len(right_pages),
                                "exact_diff_pixels": 0,
                                "diff_pixels": 0,
                                "max_diff": 0,
                                "ink_lenient": "",
                                "ink_strict": "",
                            }
                        )
                        continue
                    exact_diff_pixels, diff_pixels, max_diff, ink_left, ink_right = diff_stats(
                        left_pixels, right_pixels
                    )
                    rows.append(
                        {
                            **page_fields,
                            "status": "diff",
                            "lenient_pages": len(left_pages),
                            "strict_pages": len(right_pages),
                            "exact_diff_pixels": exact_diff_pixels,
                            "diff_pixels": diff_pixels,
                            "max_diff": max_diff,
                            "ink_lenient": ink_left,
                            "ink_strict": ink_right,
                        }
                    )
                    if exact_diff_pixels:
                        diff_dir = args.out / "diff"
                        diff_dir.mkdir(exist_ok=True)
                        shutil.copy(left_page, diff_dir / f"p{page_number:04d}-L.pgm")
                        shutil.copy(right_page, diff_dir / f"p{page_number:04d}-S.pgm")
                print(
                    f"[{part_index}/{len(pairs)}] {left_pdf.name}: {len(left_pages)} 页",
                    flush=True,
                )
    except (OSError, ValueError, RenderDiffError) as error:
        print(error, file=sys.stderr)
        return 2

    csv_path = args.out / "render-diff.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "page",
                "lenient_file",
                "strict_file",
                "segment",
                "status",
                "lenient_page",
                "strict_page",
                "size_lenient",
                "size_strict",
                "exact_diff_pixels",
                "diff_pixels",
                "max_diff",
                "ink_lenient",
                "ink_strict",
                "lenient_pages",
                "strict_pages",
                "lenient_only_segments",
                "strict_only_segments",
            ],
        )
        writer.writeheader()
        for row in rows:
            row["lenient_only_segments"] = "|".join(missing_left)
            row["strict_only_segments"] = "|".join(missing_right)
            writer.writerow(row)
        if not rows:
            writer.writerow(
                {
                    "status": "segment-missing",
                    "lenient_only_segments": "|".join(missing_left),
                    "strict_only_segments": "|".join(missing_right),
                }
            )

    identical = sum(1 for row in rows if row["status"] == "identical")
    differing = [row for row in rows if row["status"] == "diff"]
    exact_differing = [
        row for row in differing if int(row.get("exact_diff_pixels") or 0) > 0
    ]
    tolerance_only = [
        row for row in differing if int(row.get("diff_pixels") or 0) == 0
    ]
    size_mismatches = [row for row in rows if row["status"] == "size-mismatch"]
    page_count_mismatches = [
        row for row in rows if row["status"] == "page-count-mismatch"
    ]
    failed = bool(
        differing or size_mismatches or page_count_mismatches or missing_left or missing_right
    )
    summary = [
        f"dpi={args.dpi}",
        f"段数={len(pairs)}",
        f"页数={page_number}",
        f"字节级完全一致={identical}",
        f"精确像素有差异={len(exact_differing)}",
        f"仅容差内差异={len(tolerance_only)}",
        f"尺寸不符={len(size_mismatches)}",
        f"页数不符={len(page_count_mismatches)}",
        f"lenient 缺少页段={missing_left}",
        f"strict 缺少页段={missing_right}",
        f"结果={'PASS' if not failed else 'FAIL'}",
        "",
        f"前 {args.top} 个差异页：",
    ]
    for row in differing[: args.top]:
        summary.append(
            f"  第 {row['page']} 页 段{row['segment']} diffPx={row['diff_pixels']} "
            f"exactDiffPx={row['exact_diff_pixels']} maxDiff={row['max_diff']} "
            f"ink={row['ink_lenient']}/{row['ink_strict']}"
        )
    for row in size_mismatches[: args.top]:
        summary.append(
            f"  尺寸不符 段{row['segment']} {row['lenient_file']}:"
            f"{row['lenient_page']} {row['size_lenient']} vs "
            f"{row['strict_file']}:{row['strict_page']} {row['size_strict']}"
        )
    for row in page_count_mismatches[: args.top]:
        summary.append(
            f"  页数不符 段{row['segment']} {row['lenient_file']} "
            f"{row['lenient_pages']} vs {row['strict_file']} {row['strict_pages']}"
        )
    for name in missing_left[: args.top]:
        summary.append(f"  lenient 缺少页段 {name}")
    for name in missing_right[: args.top]:
        summary.append(f"  strict 缺少页段 {name}")
    text = "\n".join(summary)
    (args.out / "summary.txt").write_text(text, encoding="utf-8")
    print(text)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
