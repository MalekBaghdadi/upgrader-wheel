"""Turns name/mod rules into pack-list lines for guessed (fallback/gear) items only.
Usage: python tools/gen_rules.py <values.csv> [drops.csv]   -> prints lines to paste under '# ---- generated' in the pack list.
First matching rule wins; 'nowin' hides an item from the result list.
Name rules are the weakest evidence there is, so they never pin a mob drop (pass the harness drops.csv: tools/drops.py prices
those from the mob) and never pin anything a recipe can make (NuclearCraft fuels are crafted from isotopes)."""
import csv, re, sys
I, G, D = 1024, 2048, 8192
M = {  # metal / material name -> ingot value
 'copper':512,'tin':512,'lead':768,'zinc':512,'aluminum':768,'aluminium':768,'nickel':1024,'silver':1536,'bronze':768,
 'steel':1536,'boron':1024,'lithium':1024,'magnesium':1024,'manganese':1024,'zirconium':1536,'beryllium':1536,
 'thorium':2048,'uranium':2048,'neptunium':4096,'plutonium':4096,'americium':6144,'curium':6144,'berkelium':8192,
 'californium':10240,'graphite':512,'silicon':768,'tough':3072,'hard carbon':4096,'ferroboron':2048,'lithium manganese':2048,
 'sapphire':4096,'ruby':4096,'amethyst':3072,'topaz':4096,'peridot':3072,'tanzanite':4096,'malachite':3072,'amber':2048,
 'rhodochrosite':3072,'moissanite':4096,'fluorite':2048,'villiaumite':2048,'carobbiite':2048,'obsidian':512,
}
RULES = []
def r(mod, name, val): RULES.append((re.compile(mod), re.compile(name, re.I), val))
# machines / multiblock parts that are only ever crafted: keep out of results
r(r'nuclearcraft', r'_idle$|^$', 'nowin')
for k, v in M.items():
    r(r'nuclearcraft|variedcommodities', rf'^{k} block$', v * 9)
    r(r'nuclearcraft|variedcommodities', rf'^{k} nugget$', v // 9)
    r(r'nuclearcraft|variedcommodities', rf'^{k} (ingot|gem|dust|plate|ore)$', v)
    r(r'nuclearcraft', rf'^(ingot|dust|gem|plate) of {k}$', v)
r(r'nuclearcraft', r'bioplastic', 512)
r(r'nuclearcraft', r'empty cooler|cooler$', 2048)
r(r'nuclearcraft', r'cocoa|chocolate|flour|gelatin|graham|dominos|marshmallow|dry earth', 128)
r(r'variedcommodities', r'^coin ', 256)
r(r'variedcommodities', r'^book$', 128)
r(r'srparasites', r'itemmobspawner|spawner', 'nowin')
r(r'srparasites', r'infested|gore|flesh|bone|remain|rubble|stain|bush|trunk|bloody', 256)
r(r'srparasites', r'drop$|_drop', 2048)
r(r'defiledlands', r'heart|scale|eye|wyrm|scuttler|tooth|claw', 2048)
r(r'defiledlands', r'stone|dirt|grass|sand|log|leaves|sapling|plant', 32)   # ores are priced by hand from their gear
r(r'mod_lavacow', r'hide|jaw|feather|molten|fang|horn|tooth|claw|scale', 1024)
r(r'quark', r'crystal', 512)
r(r'simpledifficulty', r'ice chunk|magma chunk', 256)
r(r'simpledifficulty', r'frost rod', 1024)
r(r'notreepunching', r'flint shard', 64)
r(r'inspirations', r'heartbeet', 192)
r(r'biomesoplenty', r'bamboo|ash|berries|ivy|vine|peach|pear|persimmon|honey', 64)
r(r'betternether', r'reed', 64)
r(r'contenttweaker', r'concrete', 512)
r(r'scalinghealth', r'heart dust', 512)
r(r'aquaculture', r'neptunium', 6144)
r(r'forgottenitems', r'gem$', 4096)
r(r'forgottenitems', r'talisman$', 24576)
r(r'grapplemod', r'upgrade', 4096)
r(r'grapplemod', r'hook$|^grappling hook$|staff|forcefield', 16384)
r(r'.*', r'^$', 'nowin')
rows = [x for x in csv.DictReader(open(sys.argv[1], encoding='utf-8')) if x['source'] in ('fallback', 'gear')]
if len(sys.argv) > 2:
    dropped = {(d['id'], d['meta']) for d in csv.DictReader(open(sys.argv[2], encoding='utf-8')) if d['id']}
    rows = [x for x in rows if (x['id'], x['meta']) not in dropped]
seen = set()
for x in rows:
    mod = x['id'].split(':')[0]
    for mr, nr, v in RULES:
        if mr.fullmatch(mod) or mr.search(mod) and mr.pattern != '.*':
            if nr.search(x['name']):
                key = f"{x['id']}:{x['meta']}" if x['meta'] != '' else x['id']
                if key in seen: break
                seen.add(key)
                print(f"{key} = {v}   # {re.sub('§.', '', x['name'])}")
                break
