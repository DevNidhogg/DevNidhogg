# Java Applied Systems

Three standalone Java 21 utilities for cryptographic password generation, digital forensics, and metadata privacy. No build system or Java library dependencies.

## Quick start

Install JDK 21 or later. On Windows, double-click `run.bat` in the desired project directory. On Linux/macOS, run `sh run.sh` in that directory. The launchers run Java source directly unless a compiled app.jar is present. Source files can also be opened directly in IntelliJ IDEA.

| Project | Implementation |
|---|---|
| password-generation | SecureRandom, independent uniform code-point sampling, 94 printable non-space ASCII characters, selectable Unicode alphabets, custom characters, theoretical entropy. |
| file-carving | Read-only image scanning, PNG CRC validation, JPEG/PDF signature recovery, ZIP central-directory checks, SHA-256 and TSV report. |
| metadata-score | Field-by-field privacy inventory, heuristic score, selective editing/removal, copy-only exports, output re-inspection. |

## Metadata Score requirements

DOCX/XLSX/PPTX core properties work with Java alone. For image EXIF/GPS and other supported metadata, install ExifTool from https://exiftool.org/ and add it to PATH or set the EXIFTOOL environment variable to its full executable path. Image tags must be supported/writable by ExifTool. Empty values request removal; choose an existing field and type a replacement such as google.com. PDF and other non-image formats are inspection-only (PDF incremental writes can leave recoverable metadata). Office support is limited to core properties, not embedded images, comments, revision histories or custom property parts. Modifying signed documents may invalidate signatures. Re-inspect and verify each edited field; the score is heuristic, not a guarantee of anonymity.

## Forensic limitations

Carving supports contiguous candidates, with a maximum candidate size of 64 MiB, 1,000 recovered files and 10,000 signature attempts. JPEG and PDF output is signature-based and may be incomplete or false-positive; PNG CRC and ZIP central-directory validation do not prove content integrity. ZIP64, fragmented files and incremental PDF versions are not fully supported. Only scan images you own or are authorized to inspect. The original is never written. Recovered files are not automatically opened.

## Tests

From this directory (JDK 21):

    java password-generation/PasswordGeneration.java --self-test
    java file-carving/FileCarving.java --self-test
    java metadata-score/MetadataScore.java --self-test

The metadata test exercises Office core properties without ExifTool. ExifTool integration requires the external binary; its image-editing path is not covered by the built-in test.

Generated passwords are not written to disk or sent over a network by the program. The terminal can retain displayed secrets. Unicode may be rejected or normalized by some services; ASCII is the compatibility default. Each symbol is sampled independently; character categories are not forcibly inserted.
