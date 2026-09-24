import csv
import sys
from pathlib import Path

if len(sys.argv) < 3:
    print("Usage: python3 collector/convert_ownership.py INPUT.csv OUTPUT.csv [THRESHOLD]")
    sys.exit(1)

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
threshold = sys.argv[3] if len(sys.argv) > 3 else ">=1%"

with src.open("r", encoding="utf-8-sig", newline="") as f:
    reader = csv.DictReader(f)

    required = {
        "date",
        "share_code",
        "investor_name",
        "total_holding_shares",
        "percentage",
    }

    if not reader.fieldnames:
        raise SystemExit("Header CSV tidak ditemukan")

    missing = required - set(reader.fieldnames)
    if missing:
        raise SystemExit(
            "Kolom wajib tidak ada: " + ", ".join(sorted(missing))
        )

    rows = []

    for r in reader:
        ticker = (r.get("share_code") or "").strip().upper()
        holder = (r.get("investor_name") or "").strip()
        date = (r.get("date") or "").strip()

        try:
            percentage = float(r["percentage"])
        except:
            continue

        try:
            shares = int(float(r["total_holding_shares"]))
        except:
            shares = ""

        if not ticker or not holder:
            continue

        rows.append({
            "date": date,
            "share_code": ticker,
            "investor_name": holder,
            "total_holding_shares": shares,
            "percentage": percentage,
            "threshold": threshold,
        })

with dst.open("w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(
        f,
        fieldnames=[
            "date",
            "share_code",
            "investor_name",
            "total_holding_shares",
            "percentage",
            "threshold",
        ],
    )
    writer.writeheader()
    writer.writerows(rows)

print("OK")
print("INPUT :", src)
print("OUTPUT:", dst)
print("ROWS  :", len(rows))
print("THRESHOLD:", threshold)
