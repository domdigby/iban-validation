# IBAN Validator

Command-line tool that validates one or more IBANs and prints human-readable results. Built on [Apache Commons Validator](https://commons.apache.org/proper/commons-validator/) 1.10.1.

## Build

Requires Java 21 and Maven.

```bash
mvn clean package
```

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

## Exit codes

| Code | Meaning |
|------|---------|
| `0`  | All IBANs valid |
| `1`  | No arguments provided |
| `2`  | One or more IBANs invalid |
