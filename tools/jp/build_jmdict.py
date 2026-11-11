#!/usr/bin/env python3
"""JP fork: build the launcher's offline dictionary from a yomitan JMdict zip.

    python3 -I tools/jp/build_jmdict.py ~/Downloads/JMdict_english.zip app/src/main/assets/jp/jmdict.db

Source: https://github.com/yomidevs/jmdict-yomitan (JMdict_english.zip). JMdict is (c) the
Electronic Dictionary Research and Development Group, CC BY-SA 4.0 - see the About text.

One `entry` row per JMdict sequence: headword, reading, part of speech, the first senses
joined, a common flag and a rank (yomitan's score; higher = more common). `key` maps every
written form and reading, kana folded to hiragana, to its entry for exact/prefix lookup.
The output is git-ignored; the app works without it (the search provider stays silent).
"""
import json
import re
import sqlite3
import sys
import zipfile
from collections import defaultdict

MAX_SENSES = 3
MAX_GLOSS_CHARS = 160


def hira(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def glossary_text(node, want=False):
    """The text of every <li> under a data.content == 'glossary' list."""
    out = []
    if isinstance(node, str):
        return [node] if want else []
    if isinstance(node, list):
        for n in node:
            out += glossary_text(n, want)
        return out
    if not isinstance(node, dict):
        return out
    if node.get("type") == "structured-content":
        return glossary_text(node.get("content"), want)
    data = node.get("data") or {}
    if data.get("content") == "glossary":
        items = node.get("content")
        items = items if isinstance(items, list) else [items]
        return ["".join(glossary_text(i, True)) for i in items]
    if want:
        return ["".join(glossary_text(node.get("content"), True))]
    return glossary_text(node.get("content"), False)


POS = {"v1": "ichidan verb", "v5": "godan verb", "vs": "suru verb", "vk": "kuru verb",
       "adj-i": "i-adj", "adj-na": "na-adj", "adj-no": "no-adj", "n": "noun", "adv": "adverb",
       "exp": "expression", "int": "interjection", "prt": "particle", "ctr": "counter",
       "pn": "pronoun", "conj": "conjunction", "suf": "suffix", "pref": "prefix", "vt": "transitive",
       "vi": "intransitive", "uk": "usu. kana"}


def pos_label(tags: str) -> str:
    seen = []
    for t in tags.split():
        if t.isdigit():
            continue
        base = POS.get(t) or (POS["v5"] if t.startswith("v5") else None)
        if base and base not in seen:
            seen.append(base)
    return ", ".join(seen[:3])


def main(src, dst):
    seqs = defaultdict(lambda: {"forms": [], "senses": [], "score": -10**9, "common": False,
                                "head": None, "reading": None, "pos": ""})
    with zipfile.ZipFile(src) as z:
        for name in sorted(n for n in z.namelist() if n.startswith("term_bank_")):
            for term, reading, deftags, _rules, score, glossary, seq, termtags in json.loads(z.read(name)):
                e = seqs[seq]
                for f in (term, reading):
                    if f and f not in e["forms"]:
                        e["forms"].append(f)
                if "⭐" in termtags:
                    e["common"] = True
                if deftags.startswith("forms"):
                    continue
                if score > e["score"] or e["head"] is None:
                    # "uk" (usually kana): the kana form is the headword (コーヒー, not 珈琲).
                    head = (reading or term) if "uk" in deftags.split() else term
                    e["score"], e["head"], e["reading"] = score, head, reading or term
                if not e["pos"]:
                    e["pos"] = pos_label(deftags)
                text = "; ".join(t.strip() for t in glossary_text(glossary) if t.strip())
                if text and text not in [s for _, s in e["senses"]]:
                    e["senses"].append((score, text))

    db = sqlite3.connect(dst)
    db.executescript("""
        DROP TABLE IF EXISTS entry; DROP TABLE IF EXISTS key;
        CREATE TABLE entry(id INTEGER PRIMARY KEY, head TEXT, reading TEXT, pos TEXT,
                           gloss TEXT, common INTEGER, rank INTEGER);
        CREATE TABLE key(k TEXT, id INTEGER);
    """)
    rows, keys = [], []
    for seq, e in seqs.items():
        if not e["senses"]:
            continue
        senses = [s for _, s in sorted(e["senses"], key=lambda x: -x[0])][:MAX_SENSES]
        gloss = " · ".join(senses) if len(senses) == 1 else " ".join(f"{i}. {s}" for i, s in enumerate(senses, 1))
        if len(gloss) > MAX_GLOSS_CHARS:
            gloss = gloss[:MAX_GLOSS_CHARS - 1].rstrip() + "…"
        reading = e["reading"] if e["reading"] != e["head"] else ""
        rows.append((seq, e["head"], reading, e["pos"], gloss, int(e["common"]), e["score"]))
        for f in {hira(re.sub(r"（.*?）", "", f)) for f in e["forms"]}:
            if f:
                keys.append((f, seq))
    db.executemany("INSERT INTO entry VALUES (?,?,?,?,?,?,?)", rows)
    db.executemany("INSERT INTO key VALUES (?,?)", keys)
    db.executescript("CREATE INDEX key_k ON key(k); VACUUM;")
    db.commit()
    print(f"{len(rows)} entries, {len(keys)} keys -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
