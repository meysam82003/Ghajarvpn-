#!/usr/bin/env python3
"""Prepare phone-sized artwork without resizing or recompressing existing JPEGs.

PNG photos are converted to high-quality 4:4:4 JPEG. Transparent branding stays
PNG. The report records dimensions, hashes, byte savings and pixel PSNR; it does
not pretend a JPEG conversion is mathematically lossless.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps


def optimize(source: Path, destination: Path) -> dict:
    original = source.read_bytes()
    with Image.open(io.BytesIO(original)) as image:
        image.load()
        dimensions = image.size
        if image.format == "JPEG" and image.getexif().get(274, 1) == 1:
            # These uploaded photos are already small. Keeping their exact
            # encoded bytes prevents cumulative JPEG generation loss.
            result, quality, psnr = original, "original JPEG", None
        else:
            oriented = ImageOps.exif_transpose(image)
            if "A" in oriented.getbands() and oriented.getchannel("A").getextrema()[0] < 255:
                raise ValueError(f"Transparent artwork must remain PNG: {source.name}")
            rgb = oriented.convert("RGB")
            pixels = np.asarray(rgb, dtype=np.float32)
            for quality in (92, 94, 96, 98):
                encoded = io.BytesIO()
                options = dict(format="JPEG", quality=quality, subsampling=0, optimize=True, progressive=True)
                if image.info.get("icc_profile"):
                    options["icc_profile"] = image.info["icc_profile"]
                rgb.save(encoded, **options)
                result = encoded.getvalue()
                decoded = np.asarray(Image.open(io.BytesIO(result)).convert("RGB"), dtype=np.float32)
                mse = float(np.mean((pixels - decoded) ** 2))
                psnr = 99.0 if mse == 0 else 10 * math.log10(255 ** 2 / mse)
                if psnr >= 42:
                    break
            if psnr < 40:
                raise ValueError(f"Unexpected conversion loss: {source.name}: {psnr:.2f} dB")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(result)
        with Image.open(io.BytesIO(result)) as output:
            assert output.size == dimensions, "Artwork must not be resized"
    return dict(name=destination.name, width=dimensions[0], height=dimensions[1],
                original_bytes=len(original), bytes=len(result), quality=quality,
                psnr_db=round(psnr, 2) if psnr is not None else None,
                sha256=hashlib.sha256(result).hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="JSON array of {source, destination}")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    report = [optimize(Path(row["source"]), Path(row["destination"]))
              for row in json.loads(args.manifest.read_text())]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(dict(images=len(report), before_bytes=sum(r["original_bytes"] for r in report),
                         after_bytes=sum(r["bytes"] for r in report))))
