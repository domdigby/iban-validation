package com.affinis;

import org.apache.commons.validator.routines.IBANValidator;
import org.apache.commons.validator.routines.IBANValidatorStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * IBAN validator with human-readable failure reasons.
 *
 * Delegates all validation (format, country-specific length, and MOD-97
 * checksum) to commons-validator's IBANValidator.validate(), then maps
 * each IBANValidatorStatus to a clear explanation.
 *
 * Usage:
 *   java -jar iban-validator-1.0.0.jar <IBAN> [IBAN2 ...]
 *   java -jar iban-validator-1.0.0.jar --file <input.txt> [--output <output.csv>]
 *
 * Exit codes:  0 = all IBANs valid,  2 = one or more invalid,  1 = usage/IO error
 */
public class Main {

    // ── entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        if (args[0].equals("--file")) {
            runBatchMode(args);
            return;
        }

        boolean allValid = true;
        for (String iban : args) {
            String result = validate(iban);
            System.out.printf("%-40s  %s%n", iban, result);
            if (!result.equals("true")) allValid = false;
        }

        System.exit(allValid ? 0 : 2);
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar iban-validator-1.0.0.jar <IBAN> [IBAN2 ...]");
        System.err.println("   or: java -jar iban-validator-1.0.0.jar --file <input.txt> [--output <output.csv>]");
        System.err.println("Examples:");
        System.err.println("  java -jar iban-validator-1.0.0.jar GB82WEST12345698765432 DE89370400440532013000");
        System.err.println("  java -jar iban-validator-1.0.0.jar --file ibans.txt");
    }

    // ── batch mode ───────────────────────────────────────────────────────────

    /**
     * Reads one IBAN per line from {@code --file <input.txt>}, validates each,
     * and writes a two-column CSV ("IBAN,Result") to {@code --output <output.csv>}
     * (or "<input-basename>.csv" alongside the input file if omitted).
     * Blank lines are skipped. Exits 0 if all valid, 2 if any invalid, 1 on usage/IO error.
     */
    private static void runBatchMode(String[] args) {
        String inputArg = null;
        String outputArg = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file" -> {
                    if (i + 1 >= args.length) {
                        printUsage();
                        System.exit(1);
                    }
                    inputArg = args[++i];
                }
                case "--output" -> {
                    if (i + 1 >= args.length) {
                        printUsage();
                        System.exit(1);
                    }
                    outputArg = args[++i];
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (inputArg == null) {
            printUsage();
            System.exit(1);
        }

        Path inputPath = Path.of(inputArg);
        List<String> lines;
        try {
            lines = Files.readAllLines(inputPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not read input file '" + inputArg + "': " + e.getMessage());
            System.exit(1);
            return;
        }

        Path outputPath = outputArg != null ? Path.of(outputArg) : resolveDefaultOutputPath(inputPath);

        StringBuilder csv = new StringBuilder();
        csv.append("IBAN,Result").append(System.lineSeparator());

        int processedCount = 0;
        int invalidCount = 0;
        for (String line : lines) {
            String iban = line.trim();
            if (iban.isEmpty()) continue;

            String result = validate(iban);
            processedCount++;
            if (!result.equals("true")) invalidCount++;

            csv.append(csvEscape(iban)).append(',').append(csvEscape(result)).append(System.lineSeparator());
        }

        try {
            Files.writeString(outputPath, csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write output file '" + outputPath + "': " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.printf("Processed %d IBANs, %d invalid -> wrote %s%n", processedCount, invalidCount, outputPath);
        System.exit(invalidCount == 0 ? 0 : 2);
    }

    /** Defaults to the input file's base name with a ".csv" extension, in the same directory. */
    private static Path resolveDefaultOutputPath(Path inputPath) {
        String fileName = inputPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        Path parent = inputPath.getParent();
        return parent != null ? parent.resolve(baseName + ".csv") : Path.of(baseName + ".csv");
    }

    /** Quotes a CSV field (doubling embedded quotes) if it contains a comma, quote, or newline. */
    private static String csvEscape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    // ── validation ───────────────────────────────────────────────────────────

    /**
     * Validates a single IBAN (whitespace is stripped, case is ignored).
     *
     * @return {@code "true"} if valid, otherwise a {@code "false: <reason>"} string
     */
    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "false: IBAN is null or empty";
        }

        // Normalise: strip internal whitespace (e.g. pretty-printed "GB82 WEST …"), upper-case
        String iban = raw.replaceAll("\\s+", "").toUpperCase();

        IBANValidatorStatus status = IBANValidator.DEFAULT_IBAN_VALIDATOR.validate(iban);

        return switch (status) {
            case VALID -> "true";
            case UNKNOWN_COUNTRY -> "false: Unknown or unsupported country code '"
                    + safeCountryCode(iban) + "'";
            case INVALID_LENGTH  -> "false: Incorrect length " + iban.length()
                    + " for country '" + safeCountryCode(iban) + "'"
                    + " (or overall length out of range 15-34)";
            case INVALID_PATTERN -> "false: BBAN does not match the expected format for country '"
                    + safeCountryCode(iban) + "' (invalid characters or structure)";
            case INVALID_CHECKSUM -> "false: Checksum validation failed (MOD-97)"
                    + " -- check digits '" + safeCheckDigits(iban)
                    + "' are incorrect for this account number"
                    + "; correct check digits would be " + computeCorrectCheckDigits(iban);
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String safeCountryCode(String iban) {
        return iban.length() >= 2 ? iban.substring(0, 2) : iban;
    }

    private static String safeCheckDigits(String iban) {
        return iban.length() >= 4 ? iban.substring(2, 4) : "??";
    }

    /**
     * Computes the two check digits that would make this IBAN pass MOD-97,
     * assuming the country code and BBAN are correct.
     * Returns "??" if the IBAN is too short to attempt the calculation.
     */
    private static String computeCorrectCheckDigits(String iban) {
        if (iban.length() < 5) return "??";
        String country = iban.substring(0, 2);
        String bban    = iban.substring(4);
        // Replace check digits with "00", rearrange, then compute 98 - MOD-97
        int remainder  = mod97(toNumeric(bban + country + "00"));
        return String.format("%02d", 98 - remainder);
    }

    /** Replace each letter with its numeric equivalent: A=10 … Z=35. */
    private static String toNumeric(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) sb.append(c - 'A' + 10);
            else                       sb.append(c);
        }
        return sb.toString();
    }

    /** MOD-97 over a large decimal string (no BigInteger needed). */
    private static int mod97(String numeric) {
        int remainder = 0;
        for (char c : numeric.toCharArray()) {
            remainder = (remainder * 10 + (c - '0')) % 97;
        }
        return remainder;
    }
}
