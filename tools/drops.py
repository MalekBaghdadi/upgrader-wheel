"""Prices mob drops that nothing else prices, from how hard the mob is and how often it drops them.
Usage: python tools/drops.py <values.csv> <drops.csv> [loot.csv] [--explain]  > src/main/resources/assets/upgraderwheel/pack/rlcraft_dregora_drops.txt
<drops.csv> and <loot.csv> come from the harness (ANALYZE=1 sh tools/test.sh -> config/upgraderwheel-drops.csv, -loot.csv).
With loot.csv, also lists (on stderr) valuable prizes that no recipe, mob or loot table gives and JEI does not hide:
candidates for nowin (some come from mechanics nobody can see from here: dragon forges, parasite evolution).

Model (all numbers are in KNOBS below):
- A kill is worth K = C * EH/20, EH = health x (1 + armor/20) x (1 + attack/20). Above EH 300 it grows with the square
  root instead. C is fitted so that vanilla monsters, whose drops all have trusted values, come out right on average
  (median); bosses get a factor fitted the same way on vanilla bosses (the Wither and its nether star).
  The linear curve was chosen by fitting: with it, gear crafted from mob drops comes out near what its armor and damage
  are worth (about x1.5-1.8 the stat value); with a square curve that gear came out x16 its stats.
- Drops with trusted values (base, pack list, recipes) take their share of K first. What is left (at least
  min_share of K, however rich the mob's other drops are) is split evenly over the mob's unpriced drops, per kill: a drop that comes 1 time in 20 is worth
  20 times its share. Drops every mob has (bandages, global loot) are skipped: the mob is not why you get them.
- An item several mobs drop takes the cheapest mob (the easiest way to farm it), like recipes take the cheapest recipe.
Only items whose value is a guess (source fallback) or a previous estimate (source drop) are written."""
import csv, collections, math, statistics, sys

KNOBS = dict(
    curve_power=1.0,        # kill value grows in step with effective health...
    armor_weight=1.0,       # effective health = health x (1 + armor/20)^armor_weight x (1 + attack/20)^attack_weight
    attack_weight=1.0,
    soft_cap_eh=300.0,      # ...up to this effective health (the Wither), then with the square root
    soft_power=0.5,
    boss_default=16.0,      # boss factor when no vanilla boss can be measured
    min_share=0.5,          # unpriced drops always get at least this fraction of the kill value between them
    global_mobs=0.25,       # dropped by more than this fraction of mob kinds: a global drop, not priced here
    min_rate=0.002,         # rarer than 1 in 500 kills: too few samples to trust
    cross_mod_rate=0.5,     # another mod's item from this mob counts only if it drops this often (designed loot, not a
                            # side lottery like fish bones from a Lycanites mob); pack-made items always count
    min_value=16,
    max_value=524288,       # 4 nether stars
)

def load(values_path, drops_path):
    vals = {}
    for r in csv.DictReader(open(values_path, encoding='utf-8')):
        vals[(r['id'], r['meta'])] = r
    mobs = collections.OrderedDict()
    for r in csv.DictReader(open(drops_path, encoding='utf-8')):
        m = mobs.setdefault(r['entity'], dict(name=r['mob'], h=float(r['health']), a=float(r['armor']),
                                              d=float(r['attack']), boss=r['boss'] == 'true',
                                              monster=r['monster'] == 'true', drops=[]))
        if r['id']:
            m['drops'].append(((r['id'], r['meta']), float(r['per_kill']), float(r['chance']), r['name']))
    return vals, mobs

def effective_health(m, k=KNOBS):
    return max(0.5, m['h']) * (1 + max(0, m['a']) / 20) ** k['armor_weight'] * (1 + max(0, m['d']) / 20) ** k['attack_weight']

def shape(eh, k=KNOBS):
    if eh <= k['soft_cap_eh']:
        return (eh / 20) ** k['curve_power']
    return (k['soft_cap_eh'] / 20) ** k['curve_power'] * (eh / k['soft_cap_eh']) ** k['soft_power']

def candidate(row):
    return row is not None and row['source'] in ('fallback', 'drop') and not row['id'].startswith(('potion:', 'splash:', 'lingering:', 'arrow:'))

def known_per_kill(m, vals, skip):
    total = 0.0
    for key, per_kill, chance, name in m['drops']:
        row = vals.get(key)
        if key in skip or row is None or candidate(row):
            continue
        total += float(row['value']) * per_kill
    return total

def estimate(vals, mobs, k=KNOBS):
    """Returns (C, ratios used for calibration, global drops, {item key: (value, mob, per kill, name)})."""
    droppers = collections.Counter(key for m in mobs.values() for key in {d[0] for d in m['drops']})
    global_drops = {key for key, n in droppers.items() if n > KNOBS['global_mobs'] * len(mobs)}
    # Calibrate on vanilla monsters with no unpriced drops
    ratios = []
    for e, m in mobs.items():
        if not e.startswith('minecraft:') or not m['monster'] or m['boss']:
            continue
        if any(candidate(vals.get(d[0])) for d in m['drops'] if d[0] not in global_drops):
            continue
        kv = known_per_kill(m, vals, global_drops)
        if kv > 0:
            ratios.append(kv / shape(effective_health(m, k), k))
    C = statistics.median(ratios) if ratios else 512.0
    boss_ratios = []
    for e, m in mobs.items():
        if e.startswith('minecraft:') and m['boss']:
            kv = known_per_kill(m, vals, global_drops)
            if kv > 0:
                boss_ratios.append(kv / (C * shape(effective_health(m, k), k)))
    boss = statistics.median(boss_ratios) if boss_ratios else k['boss_default']
    k['_boss'] = boss
    best = {}  # key -> (value, mob, rate)
    for e, m in mobs.items():
        ns = e.split(':')[0]
        unpriced = [d for d in m['drops'] if d[0] not in global_drops and candidate(vals.get(d[0])) and d[1] >= k['min_rate']
                    and (ns in ('minecraft', d[0][0].split(':')[0]) or d[0][0].startswith('contenttweaker:') or d[1] >= k['cross_mod_rate'])]
        if not unpriced:
            continue
        kill = C * shape(effective_health(m, k), k) * (boss if m['boss'] else 1)
        kv = known_per_kill(m, vals, global_drops)
        residual = max(kill - kv, k['min_share'] * kill)
        share = residual / len(unpriced)
        for key, per_kill, chance, name in unpriced:
            v = min(k['max_value'], max(k['min_value'], share / per_kill))
            if key not in best or v < best[key][0]:
                best[key] = (v, e, per_kill, name)
    return C, ratios, global_drops, best

def main():
    explain = '--explain' in sys.argv
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    vals, mobs = load(args[0], args[1])
    C, ratios, global_drops, best = estimate(vals, mobs, KNOBS)
    print("# Upgrader Wheel - mob drop values for RLCraft Dregora, made by tools/drops.py from a test-server run that killed")
    print(f"# every mob kind {max(1, 200)} times. Do not edit by hand: pin a value in rlcraft_dregora.txt instead (pins win).")
    print(f"# A kill is worth {C:.0f} x effective health / 20 (fitted on vanilla monsters; square root above {KNOBS['soft_cap_eh']:.0f};"
          f" bosses x{KNOBS.get('_boss', 0):.0f}, fitted on the Wither).")
    print("# These are estimates: a cheaper recipe still lowers them. Comment: the mob it is cheapest from, drops per kill.")
    for key in sorted(best, key=lambda k: (k[0], int(k[1]))):
        v, e, per_kill, name = best[key]
        rounded = int(round(v)) if v < 1024 else int(round(v / 64) * 64)
        print(f"{key[0]}:{key[1]} = {rounded}   # {name.replace('§', '')[:40]}: {e} {per_kill:.3g}/kill")
    if len(args) > 2:
        looted = {(r['id'], r['meta']) for r in csv.DictReader(open(args[2], encoding='utf-8'))}
        dropped = {d[0] for m in mobs.values() for d in m['drops']}
        orphans = [r for r in vals.values() if r['listed'] == 'true' and r['source'] in ('fallback', 'gear')
                   and not r['recipe'] and float(r['value']) >= 4096 and (r['id'], r['meta']) not in looted | dropped]
        by_mod = collections.defaultdict(list)
        for r in orphans:
            by_mod[r['id'].split(':')[0]].append(r)
        print(f"# {len(orphans)} prizes worth 4096+ that no recipe, mob or loot table gives (nowin candidates):", file=sys.stderr)
        for mod, rs in sorted(by_mod.items(), key=lambda x: -len(x[1])):
            print(f"#  {mod} {len(rs)}: " + ", ".join(f"{r['name'][:24]} {int(float(r['value']))}" for r in rs[:5]), file=sys.stderr)
    if explain:
        print(f"\n# calibration C={C:.1f} from {len(ratios)} vanilla mobs; {len(global_drops)} global drops skipped", file=sys.stderr)
        for key in sorted(global_drops):
            print('#  global:', key, vals.get(key, {}).get('name', '?'), file=sys.stderr)

if __name__ == '__main__':
    main()
