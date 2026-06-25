# IBAN Validator

Command-line tool that validates one or more IBANs and prints human-readable results. Built on [Apache Commons Validator](https://commons.apache.org/proper/commons-validator/) 1.10.1.

## Build

Requires Java 21 and Maven.

```bash
mvn clean package
```

This produces a single self-contained jar at `target/iban-validator-1.0.0.jar` with all dependencies bundled in — copy it anywhere and run it with just a JRE installed, no other files needed.

## Usage

```bash
java -jar target/iban-validator-1.0.0.jar <IBAN> [IBAN2 ...]
```

Input is case-insensitive and whitespace is stripped, so pretty-printed IBANs (e.g. `GB82 WEST 1234 5698 7654 32`) are accepted.

### Example output

```
GB82WEST12345698765432                    true
DE89370400440532013000                    true
GB82WEST12345698765499                    false: Checksum validation failed (MOD-97) -- check digits '82' are incorrect for this account number; correct check digits would be 82
XX123                                     false: Unknown or unsupported country code 'XX'
```

## Batch mode (file in, CSV out)

Validate a list of IBANs from a text file (one per line, blank lines are skipped) and write the results to a CSV file:

```bash
java -jar target/iban-validator-1.0.0.jar --file ibans.txt
java -jar target/iban-validator-1.0.0.jar --file ibans.txt --output results.csv
```

If `--output` is omitted, the CSV is written next to the input file using its base name (e.g. `ibans.txt` → `ibans.csv`). The CSV has a header row and one row per IBAN:

```csv
IBAN,Result
GB82WEST12345698765432,true
DE89370400440532013099,false: Checksum validation failed (MOD-97) -- check digits '89' are incorrect for this account number; correct check digits would be 35
```

The console prints a short summary instead of per-IBAN output, e.g. `Processed 4 IBANs, 2 invalid -> wrote ibans.csv`.

## Exit codes

| Code | Meaning |
|------|---------|
| `0`  | All IBANs valid |
| `1`  | No arguments provided, or a usage/IO error (e.g. `--file` path not found) |
| `2`  | One or more IBANs invalid |
