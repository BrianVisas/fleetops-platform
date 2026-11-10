"""Edit docs/engineering-log.md in place: the EL-017 follow-up, and EL-020.

Refuses to write anything if the file is not in the expected state.
"""
import sys
from pathlib import Path

log_path, here = Path(sys.argv[1]), Path(sys.argv[2])

ROW_019 = "| [EL-019](#el-019-ci-and-the-image-built-with-different-maven-versions)"
ROW_020 = ("| [EL-020](#el-020-a-file-nobody-had-touched-showed-as-modified) "
           "| A file nobody had touched showed as modified | build | fixed |")
EL_017_LAST_LINE = "history it reads."


def fail(msg):
    print(f"update-log: {msg}. Nothing was changed.", file=sys.stderr)
    sys.exit(1)


lines = log_path.read_text(encoding="utf-8").splitlines()
if any(line.startswith("## EL-020") for line in lines):
    fail("EL-020 is already in the log")

rows = [i for i, l in enumerate(lines) if l.startswith(ROW_019)]
ends = [i for i, l in enumerate(lines) if l == EL_017_LAST_LINE]
if len(rows) != 1 or len(ends) != 1:
    fail("expected exactly one EL-019 index row and one end of EL-017")

followup = (here / "el-017-followup.md").read_text(encoding="utf-8").rstrip("\n").splitlines()
el_020 = (here / "el-020.md").read_text(encoding="utf-8").rstrip("\n").splitlines()

# Insert from the bottom up, so earlier line numbers stay valid.
lines[ends[0] + 1:ends[0] + 1] = followup
lines[rows[0] + 1:rows[0] + 1] = [ROW_020]
lines += el_020

log_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("update-log: EL-017 follow-up and EL-020 added")
