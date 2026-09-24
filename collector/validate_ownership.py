import csv
import sys
from pathlib import Path

REQUIRED = {
    "date",
    "share_code",
    "investor_name",
    "total_holding_shares",
    "percentage",
}

def validate(path):
    path = Path(path)

    if not path.exists():
        raise SystemExit(f"ERROR: file tidak ditemukan: {path}")

    if path.stat().st_size == 0:
        raise SystemExit("ERROR: file kosong")

    rows = []

    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)

        if not reader.fieldnames:
            raise SystemExit("ERROR: header CSV tidak ditemukan")

        missing = REQUIRED - set(reader.fieldnames)
        if missing:
            raise SystemExit(
                "ERROR: kolom wajib tidak ada: "
                + ", ".join(sorted(missing))
            )

        for row in reader:
            ticker = (row.get("share_code") or "").strip()
            holder = (row.get("investor_name") or "").strip()

            try:
                percentage = float(
                    (row.get("percentage") or "").strip()
                )
            except ValueError:
                continue

            if not ticker or not holder:
                continue

            if percentage < 1.0:
                continue

            rows.append(row)

    if not rows:
        raise SystemExit("ERROR: tidak ada data kepemilikan >=1%")

    tickers = {r["share_code"].strip().upper() for r in rows}

    print("VALID")
    print(f"rows >=1% : {len(rows)}")
    print(f"emiten     : {len(tickers)}")
    print(f"tanggal    : {rows[0]['date']}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(
            "Usage: python3 collector/validate_ownership.py FILE.csv"
        )

    validate(sys.argv[1])
