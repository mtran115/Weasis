#!/usr/bin/env python3
"""Summarize the Report Composer interaction log so frequent steps can be turned into macros.

Reads the daily JSON-line files written by InteractionRecorder (one action per line, without patient
identifiers), splits them into reads (one per study), and prints each read's timeline and
summary, then the steps that repeat across reads.

    scripts/interaction-report.py                 # today
    scripts/interaction-report.py --days 3        # today and the two days before
    scripts/interaction-report.py --since 14:30   # today from 14:30
    scripts/interaction-report.py --summary-only  # no timelines
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from pathlib import Path

DATA = Path.home() / ".weasis" / "data" / "report-composer"
DEFAULT_LOG_DIR = DATA / "interaction-log"
SHORTCUTS = DATA / "shortcuts.json"

# A new read starts with each new study (the reader rarely opens comparisons), or after this long idle.
READ_GAP = timedelta(minutes=15)
WINDOW_LEVEL = ("window", "level", "preset", "lutShape")
# W/L changes below this share of the window are slider rounding; real adjustments start near 0.5%.
WL_ROUNDING = 0.001
# Java writes 1 to 3 fraction digits (13:00:00.7); Python before 3.11 only reads 3 or 6.
FRACTION = re.compile(r"\.(\d{1,6})(?=[+-]\d\d:\d\d$)")


@dataclass
class Read:
    events: list[dict] = field(default_factory=list)
    primary: dict | None = None

    @property
    def start(self) -> datetime:
        return self.events[0]["at"]

    @property
    def end(self) -> datetime:
        return self.events[-1]["at"]

    @property
    def title(self) -> str:
        if self.primary is None:
            return "study not identified"
        parts = [self.primary.get("modality"), self.primary.get("study_desc"), self.primary.get("body_part")]
        return " · ".join(str(part) for part in parts if part)

    @property
    def kind(self) -> str:
        """What reads are compared by: the study description, else modality and body part."""
        if self.primary is None:
            return "unknown"
        return str(self.primary.get("study_desc") or f"{self.primary.get('modality')} {self.primary.get('body_part')}")


def parse_time(text: str) -> datetime:
    return datetime.fromisoformat(FRACTION.sub(lambda match: "." + match.group(1).ljust(6, "0"), text))


def load_events(log_dir: Path, days: list[date], since: datetime | None) -> list[dict]:
    events = []
    for day in days:
        path = log_dir / f"{day.isoformat()}.jsonl"
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            try:
                event = json.loads(line)
                event["at"] = parse_time(event["t"])
            except (ValueError, KeyError):
                continue
            if since and event["at"] < since:
                continue
            events.append(event)
    events.sort(key=lambda event: event["at"])
    return without_noise(events)


def rounding_only(event: dict) -> bool:
    """A W/L change too small to be an adjustment: clicking a viewport with the W/L mouse tool
    re-applies the slider's rounded values, a change of a few hundredths of a percent."""
    before, after = event.get("from", {}), event.get("to", {})
    if event["type"] != "display" or not after or not set(after) <= set(WINDOW_LEVEL):
        return False
    window = before.get("window")
    if not isinstance(window, (int, float)) or window <= 0:
        return False
    for name in ("window", "level"):
        old, new = before.get(name), after.get(name, before.get(name))
        if not isinstance(old, (int, float)) or not isinstance(new, (int, float)):
            return False
        if abs(new - old) >= WL_ROUNDING * window:
            return False
    return True


def without_noise(events: list[dict]) -> list[dict]:
    """Drops lines from before the recorder ignored the viewer's own changes: scrolls and display
    changes that changed nothing, and reselecting the viewport that was already selected. Also
    drops W/L changes that are only slider rounding."""
    kept, selected = [], None
    for event in events:
        if event["type"] in ("scroll", "display") and event.get("from") == event.get("to"):
            continue
        if rounding_only(event):
            continue
        if event["type"] == "select_view":
            if event.get("view") == selected:
                continue
            selected = event.get("view")
        kept.append(event)
    return kept


def split_reads(events: list[dict]) -> list[Read]:
    reads: list[Read] = []
    current = Read()
    for event in events:
        gap = current.events and event["at"] - current.end > READ_GAP
        new_study = (
            event["type"] == "study" and current.primary is not None and event["study"] != current.primary["study"]
        )
        if current.events and (gap or new_study):
            reads.append(current)
            current = Read()
        if event["type"] == "study" and current.primary is None:
            current.primary = event
        current.events.append(event)
    if current.events:
        reads.append(current)
    return reads


def phrase_labels() -> dict[str, dict]:
    try:
        library = json.loads(SHORTCUTS.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    return {item["id"]: item for item in library.get("shortcuts", []) if "id" in item}


def number(value) -> str:
    if isinstance(value, float):
        return f"{value:g}"
    return str(value)


def view(event: dict) -> str:
    return f"v{event['view']} " if "view" in event else ""


def series(event: dict) -> str:
    text = event.get("series_desc") or "?"
    return f"{text} (#{event['series_no']})" if "series_no" in event else text


def run_suffix(event: dict) -> str:
    count, ms = event.get("count", 1), event.get("ms", 0)
    return f"  ({count} steps, {ms / 1000:.1f} s)" if count > 1 else ""


def describe_display(event: dict) -> str:
    before, after = event.get("from", {}), event.get("to", {})
    if set(after) <= set(WINDOW_LEVEL) and ("window" in after or "level" in after):
        old = f"{number(before.get('window', '?'))}/{number(before.get('level', '?'))}"
        new = f"{number(after.get('window', '?'))}/{number(after.get('level', '?'))}"
        preset = f" preset {after['preset']}" if "preset" in after else ""
        return f"{view(event)}W/L {old} → {new}{preset}"
    changes = ", ".join(f"{name} {number(before.get(name))} → {number(after[name])}" for name in after)
    return f"{view(event)}{changes}"


def describe(event: dict, labels: dict[str, dict]) -> str:
    kind = event["type"]
    if kind == "study":
        details = " · ".join(str(event[name]) for name in ("modality", "study_desc", "body_part") if name in event)
        return f"study {event['study']}  {details}"
    if kind == "series_shown":
        return f"{view(event)}← {series(event)}"
    if kind == "select_view":
        return f"select {view(event)}{series(event) if 'series_desc' in event else ''}".rstrip()
    if kind == "layout":
        return f"layout {event['layout']}"
    if kind == "mouse_tool":
        return f"mouse tool → {event['tool']}"
    if kind == "display":
        return describe_display(event) + run_suffix(event)
    if kind == "scroll":
        return (
            f"{view(event)}scroll {series(event)} slice {event['from'].get('slice')} → {event['to'].get('slice')}"
            + run_suffix(event)
        )
    if kind == "key":
        commands = f"  [{', '.join(event['commands'])}]" if event.get("commands") else ""
        where = f" in {event['field']}" if "field" in event else f" ({event['context']})" if "context" in event else ""
        return f"{view(event)}key {event['key']}{where}{commands}"
    if kind == "typing":
        return f"typed {event.get('count', 1)} chars in {event['field']}  ({event.get('ms', 0) / 1000:.0f} s)"
    if kind == "phrase":
        item = labels.get(event["id"], {})
        label = item.get("label") or event["id"]
        return f"phrase “{label}”  ({event.get('category', '')})"
    if kind == "composer_exam":
        return f"composer exam {event['exam']}"
    if kind == "report_copied":
        return f"report copied ({event.get('chars', '?')} chars)"
    if kind == "key_image":
        return "key image captured"
    if kind == "sequence_key":
        return f"{view(event)}{event.get('direction', 'next')} sequence key"
    if kind == "study_key":
        return f"{event.get('direction', 'next')} study key"
    if kind == "auto_window_level":
        source = "on open" if event.get("source") == "series_open" else "by key"
        if event.get("applied"):
            return f"{view(event)}Auto W/L {source} → {number(event.get('window'))}/{number(event.get('level'))}"
        return f"{view(event)}Auto W/L {source} skipped ({event.get('reason', '?')})"
    if kind == "macro":
        zoom = f" zoom {number(event['zoom'])}" if "zoom" in event else ""
        auto = " + Auto W/L" if event.get("auto_window_level") else ""
        return f"{view(event)}macro “{event['macro']}” on {series(event)}{zoom}{auto}"
    extras = ", ".join(f"{name} {value}" for name, value in event.items() if name not in ("t", "at", "type"))
    return f"{kind.replace('_', ' ')}  {extras}".rstrip()


def step(event: dict, labels: dict[str, dict]) -> str | None:
    """A comparable label for one action, for finding repeated sequences. None for actions a macro
    would not repeat, such as scrolling and selecting a viewport (it is only logged when it changes,
    so whether it appears depends on the previous read)."""
    kind = event["type"]
    if kind == "series_shown":
        return f"show {view(event)}{event.get('series_desc', '?')}"
    if kind == "layout":
        return f"layout {event['layout']}"
    if kind == "key":
        return f"cmd {'+'.join(event['commands'])}" if event.get("commands") else f"key {event['key']}"
    if kind == "display":
        after = event.get("to", {})
        if "preset" in after:
            return f"{view(event)}preset {after['preset']}"
        if set(after) <= set(WINDOW_LEVEL):
            return f"{view(event)}W/L drag"
        return f"{view(event)}{','.join(after)} → {','.join(number(value) for value in after.values())}"
    if kind == "phrase":
        return f"phrase {labels.get(event['id'], {}).get('label') or event['id']}"
    if kind == "mouse_tool":
        return f"tool {event['tool']}"
    if kind == "sequence_key":
        return f"{view(event)}{event.get('direction', 'next')} sequence"
    if kind == "study_key":
        return f"{event.get('direction', 'next')} study"
    if kind == "composer_exam":
        return f"composer exam {event['exam']}"
    if kind in ("key_image", "report_copied", "case_export"):
        return kind.replace("_", " ")
    return None


def summarize(read: Read, labels: dict[str, dict]) -> list[str]:
    events = read.events
    finished = next((event["at"] for event in events if event["type"] in ("report_copied", "case_export")), read.end)
    lines = [f"  time to report: {(finished - read.start).total_seconds() / 60:.1f} min"]
    layouts = [event["layout"] for event in events if event["type"] == "layout"]
    if layouts:
        lines.append(f"  layouts: {' → '.join(layouts)}")
    placed: dict[str, str] = {}
    for event in events:
        if event["type"] == "series_shown" and "view" in event:
            placed[f"v{event['view']}"] = event.get("series_desc", "?")
    if placed:
        lines.append("  last series per viewport: " + ", ".join(f"{name} {desc}" for name, desc in sorted(placed.items())))
    scroll_time: Counter[str] = Counter()
    for event in events:
        if event["type"] == "scroll":
            scroll_time[event.get("series_desc", "?")] += event.get("ms", 0)
    if scroll_time:
        top = ", ".join(f"{desc} {ms / 1000:.0f} s" for desc, ms in scroll_time.most_common(5))
        lines.append(f"  scrolling time by series: {top}")
    window_level = [event for event in events if event["type"] == "display" and set(event.get("to", {})) <= set(WINDOW_LEVEL)]
    if window_level:
        lines.append(f"  W/L changes: {len(window_level)}")
    commands = Counter(command for event in events if event["type"] == "key" for command in event.get("commands", []))
    if commands:
        lines.append("  shortcut commands: " + ", ".join(f"{name} ×{count}" for name, count in commands.most_common(8)))
    phrases = [labels.get(event["id"], {}).get("label") or event["id"] for event in events if event["type"] == "phrase"]
    if phrases:
        lines.append(f"  phrases in order: {' | '.join(phrases)}")
    typed: Counter[str] = Counter()
    for event in events:
        if event["type"] == "typing":
            typed[event["field"]] += event.get("count", 1)
    if typed:
        lines.append("  typed chars by field: " + ", ".join(f"{name} {count}" for name, count in typed.most_common()))
    images = sum(1 for event in events if event["type"] == "key_image")
    if images:
        lines.append(f"  key images: {images}")
    runs = sum(1 for event in events if event["type"] == "macro")
    if runs:
        fixes = macro_corrections(events)
        lines.append(f"  macro runs: {runs}, followed by a correction: {len(fixes)}")
        lines.extend(f"    {fix}" for fix in fixes)
    return lines


def macro_corrections(events: list[dict]) -> list[str]:
    """What the reader changed in a viewport after a macro set it up, until another series opened
    there: the zoom they settled on, W/L drags, and Auto W/L pressed again."""
    open_runs: dict[object, dict] = {}
    found: list[dict] = []
    for event in events:
        where = event.get("view")
        kind = event["type"]
        if kind == "macro":
            run = {"macro": event, "zoom": None, "wl": 0, "auto": 0, "skipped": ""}
            open_runs[where] = run
            found.append(run)
        elif kind == "series_shown":
            open_runs.pop(where, None)
        elif (run := open_runs.get(where)) is None:
            continue
        elif kind == "display" and "zoom" in event.get("to", {}):
            run["zoom"] = event["to"]["zoom"]
        elif kind == "display" and set(event.get("to", {})) <= set(WINDOW_LEVEL):
            run["wl"] += 1
        elif kind == "key" and "dicom.windowLevelAuto" in event.get("commands", []):
            run["auto"] += 1
        elif kind == "auto_window_level" and event.get("source") == "series_open" and not event.get("applied"):
            run["skipped"] = event.get("reason", "?")
    fixes = []
    for run in found:
        macro = run["macro"]
        changes = []
        if run["zoom"] is not None and "zoom" in macro:
            changes.append(f"zoom {number(macro['zoom'])} → {number(run['zoom'])} (×{run['zoom'] / macro['zoom']:.2f})")
        if run["wl"]:
            changes.append(f"W/L changed ×{run['wl']}")
        if run["skipped"]:
            changes.append(f"its Auto W/L was skipped ({run['skipped']})")
        if run["auto"]:
            changes.append(f"Auto W/L pressed ×{run['auto']}")
        if changes:
            fixes.append(f"{view(macro)}{series(macro)}: {', '.join(changes)}")
    return fixes


def contains(sequence: tuple[str, ...], part: tuple[str, ...]) -> bool:
    return any(sequence[start : start + len(part)] == part for start in range(len(sequence) - len(part) + 1))


def repeats(reads: list[Read], labels: dict[str, dict]) -> list[str]:
    lines = []
    by_kind: dict[str, list[Read]] = defaultdict(list)
    for read in reads:
        by_kind[read.kind].append(read)
    for kind, group in by_kind.items():
        steps = [[label for event in read.events if (label := step(event, labels))] for read in group]
        lines.append(f"\n{kind}: {len(group)} read{'s' if len(group) != 1 else ''}")
        for index, sequence in enumerate(steps, start=1):
            opening = sequence[:10]
            lines.append(f"  opening of read {index}: {' → '.join(opening) or '(none)'}")
        counts: Counter[tuple[str, ...]] = Counter()
        for sequence in steps:
            seen = set()
            for size in range(2, 16):
                for start in range(len(sequence) - size + 1):
                    gram = tuple(sequence[start : start + size])
                    if len(set(gram)) > 1 and gram not in seen:
                        seen.add(gram)
                        counts[gram] += 1
        if len(group) > 1:
            shared = [(gram, count) for gram, count in counts.items() if count >= 2]
            # A sequence inside a longer one found as often adds nothing.
            shared = [
                (gram, count)
                for gram, count in shared
                if not any(
                    count == other_count and len(other) > len(gram) and contains(other, gram)
                    for other, other_count in shared
                )
            ]
            shared.sort(key=lambda item: (-len(item[0]) * item[1], item[0]))
            lines.append("  sequences in more than one read:")
            lines.extend(f"    {count}× {' → '.join(gram)}" for gram, count in shared[:12])
            if not shared:
                lines.append("    (none yet)")
    return lines


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--days", type=int, default=1, help="days to read, ending today (default 1)")
    parser.add_argument("--since", help="today from this time, HH:MM")
    parser.add_argument("--summary-only", action="store_true", help="skip the timelines")
    parser.add_argument("--log-dir", type=Path, default=DEFAULT_LOG_DIR, help=argparse.SUPPRESS)
    args = parser.parse_args()

    today = date.today()
    days = [today - timedelta(days=offset) for offset in range(args.days - 1, -1, -1)]
    since = None
    if args.since:
        since = datetime.combine(today, datetime.strptime(args.since, "%H:%M").time()).astimezone()
    events = load_events(args.log_dir, days, since)
    if not events:
        print(f"No recorded actions in {args.log_dir} for {', '.join(day.isoformat() for day in days)}.")
        return 1
    labels = phrase_labels()
    reads = split_reads(events)
    for number_, read in enumerate(reads, start=1):
        print(f"\n=== Read {number_}: {read.title}  ({read.start:%H:%M}–{read.end:%H:%M}) ===")
        if not args.summary_only:
            for event in read.events:
                offset = (event["at"] - read.start).total_seconds()
                print(f"  {int(offset // 60):2d}:{offset % 60:04.1f}  {describe(event, labels)}")
        print("  -- summary --")
        print("\n".join(summarize(read, labels)))
    print("\n=== Repeats ===")
    print("\n".join(repeats(reads, labels)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
