# ROM images

Original Yamaha DMP9/16 firmware EPROM dumps.

**ROM binaries are NOT stored in this repository.**

To participate in analysis, obtain your own dumps from physical hardware
and verify them against the hashes in `hashes/sha256.txt`.

## Known images

| Filename | Part number | Version | Date | Size |
|----------|-------------|---------|------|------|
| `TMS27C240-10-DMP9-16-XN349E0-v1.02-10.11.1993.bin` | XN349E0 | 1.02 | 1993-11-10 | 524288 bytes |
| `TMS27C240-10-DMP9-16-XN349F0-v1.10-20.01.1994.bin` | XN349F0 | 1.10 | 1994-01-20 | 524288 bytes |
| `TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin` | XN349G0 | 1.11 | 1994-03-10 | 524288 bytes |

## Canonical analysis base

Use **XN349G0 v1.11** as the primary analysis target.
Older versions are analysed via diff against v1.11.

## Verifying your dumps

```bash
sha256sum TMS27C240-10-DMP9-16-XN349E0-v1.02-10.11.1993.bin
sha256sum TMS27C240-10-DMP9-16-XN349F0-v1.10-20.01.1994.bin
sha256sum TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin
```

Compare output against `hashes/sha256.txt`.
