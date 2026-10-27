"""Token-cheap value triage: reads upgraderwheel-values.csv, prints only what needs a human decision.
Usage: python tools/triage.py <values.csv> [top_n]
Leaf guesses (fallback/gear) are ranked by how many priced recipes depend on them; crafted items are
checked with arithmetic invariants instead of being read one by one."""
import csv, re, sys, collections
rows = list(csv.DictReader(open(sys.argv[1], encoding='utf-8')))
top = int(sys.argv[2]) if len(sys.argv) > 2 else 60
byname = collections.defaultdict(list)
for r in rows: byname[r['name']].append(r)
dep = collections.Counter()
for r in rows:
    if r['source'] in ('recipe', 'brewing', 'smelting'):
        for part in r['recipe'].split(' + '):
            m = re.match(r'\d+ (.*)', part.strip())
            if m: dep[m.group(1)] += 1
mod = lambda r: r['id'].split(':')[0]
leaf = [r for r in rows if r['source'] in ('fallback', 'gear')]
print(f"{len(rows)} rows; {len(leaf)} guessed leaves ({len(leaf)*100//len(rows)}%) are the only values needing review\n")
print("== guessed leaves by mod (count, distinct values) ==")
g = collections.defaultdict(list)
for r in leaf: g[mod(r)].append(int(r['value']))
for m, v in sorted(g.items(), key=lambda x: -len(x[1]))[:25]:
    print(f"{m:22}{len(v):5}  {sorted(set(v))[:6]}")
print(f"\n== top {top} guessed leaves by dependents (fix these, ignore the long tail) ==")
ranked = sorted(leaf, key=lambda r: -dep[r['name']])
for r in ranked[:top]:
    if dep[r['name']] == 0: break
    print(f"{dep[r['name']]:4} {r['id']}:{r['meta']} {r['name']} = {r['value']} [{r['source']}]")
print("\n== invariant violations in crafted items ==")
for r in rows:
    if r['source'] != 'recipe': continue
    tot = 0; ok = True
    for part in r['recipe'].split(' + '):
        m = re.match(r'(\d+) (.*)', part.strip())
        if not m or m.group(2) not in byname: ok = False; break
        tot += int(m.group(1)) * min(float(x['value']) for x in byname[m.group(2)])
    if ok and tot and float(r['value']) < tot * 0.3:
        print(f"cheap-vs-ingredients {r['id']} {r['name']} = {r['value']} (ingredients {tot})")
