"""Progression report for the wheel: reads upgraderwheel-values.csv and prints only what needs a decision.
Usage: python tools/balance.py <values.csv> [--diff <older values.csv>] [--top N]

Nobody reads 7000 rows. Recipes price most items from their ingredients, so a value can only be wrong where it does not
come from a recipe, or where a recipe disagrees with other evidence. This report lists those places, worst first:
  1. ladder      how many prizes sit in each progression tier, per kind of item (the shape of progression)
  2. printers    items worth much more than their cheapest recipe: craft one, bet it, profit (exploits)
  3. gear        gear priced far below or above what its armor/damage is worth next to vanilla iron and diamond gear
  4. jackpots    the most valuable prizes whose value is an estimate (an estimate that is too high is a cheap jackpot)
  5. guesses     unpriced raw items ranked by how many recipes depend on them (fix these first; ignore the long tail)
  6. materials   what gear says its main material is worth (an ingot used in a sword, pickaxe and helmet should cost about
                 what those stats are worth per ingot), next to the material's value: fix ores and metals here
With --diff: which prizes moved the most since the older run, and how many moved between tiers."""
import csv, math, re, sys, collections

TIERS = [  # (name, lower bound, what sits there in vanilla terms)
    ('junk', 0, 'dirt, cobble, planks, sticks'),
    ('stone', 64, 'leather 64, coal 128, bone 144, gunpowder 192'),
    ('iron', 512, 'iron 1024, ender pearl 1024, blaze rod 1536, gold 2048'),
    ('diamond', 4096, 'ghast tear 4096, diamond 8192, potions 8192, emerald 16384'),
    ('dragon', 32768, 'elytra 32768, dragon blood 32768, dragon heart 65536, nether star 139264'),
    ('mythic', 262144, 'dragon egg 262144 and beyond'),
]
GUESS = ('fallback',)
IRON_GEAR = 2048        # stat value of an iron sword: gear weaker than this is not checked against its stats
ESTIMATE = ('fallback', 'gear', 'drop')
KIND_GROUPS = [('material', ('item',)), ('block', ('block',)), ('food', ('food',)), ('weapon', ('weapon', 'bow')),
               ('tool', ('tool',)), ('armor', ('armor_head', 'armor_chest', 'armor_legs', 'armor_feet', 'shield')),
               ('trinket', ('bauble', 'equipment')), ('potion', ('potion',))]

def tier(v):
    name = TIERS[0][0]
    for n, lo, _ in TIERS:
        if v >= lo:
            name = n
    return name

def fnum(v):
    v = float(v)
    if v >= 8192:
        d = v / 8192
        return f"{d:.3g}d"
    if v >= 1024:
        return f"{v / 1024:.3g}i"
    return f"{v:.0f}"

def load(path):
    rows = list(csv.DictReader(open(path, encoding='utf-8')))
    for r in rows:
        r['v'] = float(r['value'])
        r['cost'] = float(r['craftcost']) if r.get('craftcost') else None
        r['stat'] = float(r['statvalue']) if r.get('statvalue') else None
        r['uses_n'] = int(r.get('uses') or 0)
        r['gshare'] = float(r.get('guessshare') or (1 if r['source'] in GUESS else 0))
        r['is_listed'] = r['listed'] == 'true'
        r['group'] = next((g for g, kinds in KIND_GROUPS if r.get('kind') in kinds), 'material')
    return rows

def short(r, n=34):
    name = re.sub('§.', '', r['name'])
    return f"{name[:n]} ({r['id']}:{r['meta']})"

def section(title):
    print(f"\n== {title} ==")

def main():
    args = sys.argv[1:]
    top = 15
    if '--top' in args:
        i = args.index('--top'); top = int(args[i + 1]); del args[i:i + 2]
    old = None
    if '--diff' in args:
        i = args.index('--diff'); old = load(args[i + 1]); del args[i:i + 2]
    rows = load(args[0])
    listed = [r for r in rows if r['is_listed']]
    src = collections.Counter(r['source'] for r in listed)
    est = sum(1 for r in listed if r['source'] in ESTIMATE)
    hidden = collections.Counter(r.get('hidden') for r in rows if r.get('hidden'))
    print(f"{len(rows)} items valued, {len(listed)} offered as prizes; kept off the wheel: "
          + (", ".join(f"{k} {v}" for k, v in hidden.most_common()) or "none"))
    print("prize sources: " + ", ".join(f"{k} {v}" for k, v in src.most_common()))
    mostly = sum(1 for r in listed if r['gshare'] >= 0.5)
    print(f"prizes that are mostly (>=50%) rarity guesses: {mostly} ({mostly * 100 // max(1, len(listed))}%); "
          f"any estimate (guess, gear stats, mob drop): {est} ({est * 100 // max(1, len(listed))}%)")

    # 1. ladder
    section("1. ladder: prizes per tier and kind (g = how many of them are mostly rarity guesses)")
    groups = [g for g, _ in KIND_GROUPS]
    print(f"{'tier':8}{'from':>8} " + "".join(f"{g:>12}" for g in groups))
    for name, lo, anchors in TIERS:
        cells = []
        for g in groups:
            xs = [r for r in listed if tier(r['v']) == name and r['group'] == g]
            n_guess = sum(1 for r in xs if r['gshare'] >= 0.5)
            cells.append(f"{len(xs)}" + (f"/{n_guess}g" if n_guess else ""))
        print(f"{name:8}{fnum(lo):>8} " + "".join(f"{c:>12}" for c in cells))
    print("anchors: " + " | ".join(f"{n}: {a}" for n, _, a in TIERS[1:5]))

    # 2. printers
    section("2. printers: worth over 2x their cheapest recipe (craft it, bet it, profit)")
    printers = [r for r in rows if r['cost'] is not None and r['v'] > 2 * r['cost'] and r['v'] - r['cost'] >= 512
                and r.get('hidden') != 'unobtainable']
    potions = [r for r in printers if r['kind'] == 'potion']
    others = sorted((r for r in printers if r['kind'] != 'potion'), key=lambda r: -(r['v'] - r['cost']))
    if potions:
        by_src = collections.Counter(r['source'] for r in potions)
        print(f"{len(potions)} potions (sources {dict(by_src)}): brewedPotionFloor (config) or pack pins sit above brewing cost."
              f" Biggest: " + "; ".join(f"{short(r, 22)} {fnum(r['v'])} vs {fnum(r['cost'])}"
                                         for r in sorted(potions, key=lambda r: -(r['v'] - r['cost']))[:3]))
    print(f"{len(others)} other items. By profit per craft:")
    for r in others[:top]:
        print(f"  {fnum(r['v']):>7} vs cost {fnum(r['cost']):>7} [{r['source']}] {short(r)} <- {r.get('craftrecipe', '')[:55]}")

    # 3. gear vs stats
    section("3. gear at iron strength or better: value vs what its stats are worth. <1/4 = progression skip, >8 = overpriced")
    # Only gear at least as strong as iron gear: below that, cheap materials (cobblestone, leather, gold's softness)
    # make weak gear cheap on purpose, and nothing skips progression
    gear = [r for r in listed if r['stat'] and r['stat'] >= IRON_GEAR]
    low = sorted((r for r in gear if r['v'] < r['stat'] / 4), key=lambda r: r['v'] / r['stat'])
    high = sorted((r for r in gear if r['v'] > r['stat'] * 8), key=lambda r: -r['v'] / r['stat'])
    print(f"{len(gear)} gear prizes with a stat value; {len(low)} far below it, {len(high)} far above it")
    for r in low[:top]:
        print(f"  low  {fnum(r['v']):>7} stats {fnum(r['stat']):>7} [{r['source']}] {short(r)} <- {r['recipe'][:60]}")
    for r in high[:max(5, top // 3)]:
        print(f"  high {fnum(r['v']):>7} stats {fnum(r['stat']):>7} [{r['source']}] {short(r)} <- {r['recipe'][:60]}")

    # 4. jackpots
    if any(r.get('basis') for r in rows):
        for b in ('drop', 'gear'):
            xs = [r['v'] / r['stat'] for r in gear if r.get('basis') == b and r['source'] != 'gear']
            if xs:
                xs.sort()
                print(f"  gear crafted from {b} estimates: median value/stats x{xs[len(xs) // 2]:.2g} over {len(xs)} items"
                      f" (near x1 means the {b} estimates agree with what the gear is worth)")

    section("4. jackpots: most valuable prizes that rest on an estimate somewhere in their recipe tree")
    jack = sorted((r for r in listed if float(r.get('estimateshare') or (1 if r['source'] in ESTIMATE else 0)) >= 0.5),
                  key=lambda r: -r['v'])
    print(f"{len(jack)} prizes get at least half their value from estimates (rarity guesses, gear stats, mob drops)")
    for r in jack[:top]:
        print(f"  {fnum(r['v']):>7} [{r['source']}, {float(r.get('estimateshare') or 0):.0%} estimate] {r['kind']:9} {short(r)}")

    # 5. guesses
    section("5. guesses: unpriced raw items, most depended-on first")
    leaves = [r for r in rows if r['source'] in GUESS and r.get('hidden') != 'unobtainable']
    by_mod = collections.Counter(r['id'].split(':')[0] for r in leaves)
    print(f"{len(leaves)} guessed items, {sum(1 for r in leaves if r['uses_n'] == 0)} used by no recipe. Most: "
          + ", ".join(f"{m} {n}" for m, n in by_mod.most_common(8)))
    for r in sorted(leaves, key=lambda r: -r['uses_n'])[:top]:
        if r['uses_n'] == 0:
            break
        print(f"  {r['uses_n']:4} uses {fnum(r['v']):>7} {r['kind']:9} {short(r)}")

    # 6. materials from gear
    section("6. materials: worth implied by the gear made from them (median over the gear), where it differs 3x or more")
    byname = collections.defaultdict(list)
    for r in rows:
        byname[r['name']].append(r)
    implied = collections.defaultdict(list)
    for r in rows:
        if not r['stat'] or r['stat'] < IRON_GEAR or not r['recipe'] or r['source'] != 'recipe' or r.get('hidden'):
            continue
        parts = []
        for part in r['recipe'].split(' + '):
            m = re.match(r'(\d+) (.*?)( \((makes|brewed|smelted).*)?$', part.strip())
            if not m or m.group(2) not in byname:
                parts = None
                break
            parts.append((int(m.group(1)), byname[m.group(2)][0]))
        if not parts:
            continue
        # the main material: the ingredient that makes up most of the value
        main_n, main = max(parts, key=lambda p: p[0] * p[1]['v'])
        others = sum(n * x['v'] for n, x in parts if x is not main)
        need = (r['stat'] - others) / main_n
        if need > 0:
            implied[(main['id'], main['meta'])].append((need, r))
    flagged = []
    for key, xs in implied.items():
        if len(xs) < 2:
            continue
        mat = next(x for x in rows if (x['id'], x['meta']) == key)
        med = sorted(n for n, _ in xs)[len(xs) // 2]
        ratio = med / max(mat['v'], 0.01)
        if ratio >= 3 or ratio <= 1 / 3:
            flagged.append((ratio, mat, med, xs))
    print(f"{len(implied)} materials make gear; {len(flagged)} are 3x off what their gear says")
    for ratio, mat, med, xs in sorted(flagged, key=lambda f: -abs(math.log(f[0])))[:top]:
        print(f"  x{ratio:<6.3g} {fnum(mat['v']):>7} -> gear says {fnum(med):>7} [{mat['source']}] {short(mat)} ({len(xs)} gear, e.g. {re.sub('§.', '', xs[0][1]['name'])[:22]})")

    if old:
        section("diff against the older run (prizes only)")
        prev = {(r['id'], r['meta']): r for r in old}
        moved = []
        migr = collections.Counter()
        for r in listed:
            p = prev.get((r['id'], r['meta']))
            if p is None or p['v'] <= 0:
                continue
            ratio = r['v'] / p['v']
            if tier(p['v']) != tier(r['v']):
                migr[(tier(p['v']), tier(r['v']))] += 1
            if abs(math.log(ratio)) > math.log(1.5):
                moved.append((ratio, r, p))
        print(f"{len(moved)} prizes changed by more than 1.5x; tier moves: "
              + ", ".join(f"{a}->{b} {n}" for (a, b), n in migr.most_common(10)))
        prev_listed = {(p['id'], p['meta']) for p in old if p['is_listed']}
        now_listed = {(r['id'], r['meta']) for r in listed}
        print(f"newly offered: {len(now_listed - prev_listed)}, no longer offered: {len(prev_listed - now_listed)}")
        for ratio, r, p in sorted(moved, key=lambda x: -abs(math.log(x[0])))[:top]:
            print(f"  x{ratio:<7.3g} {fnum(p['v']):>7} -> {fnum(r['v']):>7} [{p['source']}->{r['source']}] {short(r)}")

if __name__ == '__main__':
    main()
