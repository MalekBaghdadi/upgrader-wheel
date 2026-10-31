"""Builds the headless test server (the harness) for RLCraft Dregora from scratch, on any OS with Python 3 and Java 8.
Usage: python tools/make_harness.py <folder> <java 8 binary> [CurseForge file id of the pack zip, default v1.1.2b]
Then:  SERVER=<folder>/server JAVA8=<java 8 binary> GRADLE_JDK=<JDK 25> sh tools/test.sh

Downloads the pack zip from the CurseForge CDN, resolves every mod in its manifest through api.cfwidget.com (file names)
and downloads them too (about 600 MB, cached in <folder>/cache), installs Forge 1.12.2-14.23.5.2860 as a server, copies
the mods minus the client-only ones, and the pack's config and scripts. Safe to run again: downloads are cached."""
import json, os, re, shutil, subprocess, sys, time, urllib.parse, urllib.request, zipfile

PACK_PROJECT = 1003933          # RLCraft Dregora
FORGE = '1.12.2-14.23.5.2860'
CLIENT_ONLY = ("OptiFine, OptifineCITPatch, mod-director, RLFoliage, DynamicSurroundingsHuds, fancymenu, konkrete, BHMenu, "
               "EntityCulling, particleculling, ChunkAnimator, MoBends, SoundFilters, FancyBlockParticles, Neat, "
               "LegendaryTooltips, Controlling, MouseTweaks, itlt, LoadingScreens, tlskincape, ResourceLoader, "
               "betterbiomeblend, BlockOverlayFix, Toast Control, GooglyEyes, SpartanHUDBaubles, paintingselgui, "
               "InventoryTweaks").split(', ')

def get(url, tries=5):
    for t in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'upgraderwheel-harness'})
            with urllib.request.urlopen(req, timeout=300) as r:
                return r.read()
        except Exception as e:
            err = e
            time.sleep(2 ** (t + 1))
    raise err

def cdn(file_id, name):
    return f'https://mediafilez.forgecdn.net/files/{file_id // 1000}/{file_id % 1000}/' + urllib.parse.quote(name)

def project_files(project, cache):
    path = os.path.join(cache, f'cfwidget-{project}.json')
    if not os.path.exists(path):
        open(path, 'wb').write(get(f'https://api.cfwidget.com/{project}'))
        time.sleep(0.3)
    return json.load(open(path, encoding='utf-8')).get('files', [])

def fetch(file_id, name, dest):
    if not (os.path.exists(dest) and os.path.getsize(dest) > 0):
        data = get(cdn(file_id, name))
        open(dest + '.part', 'wb').write(data)
        os.replace(dest + '.part', dest)

def main():
    root, java8 = os.path.abspath(sys.argv[1]), sys.argv[2]
    cache = os.path.join(root, 'cache')
    os.makedirs(os.path.join(cache, 'mods'), exist_ok=True)
    pack_files = project_files(PACK_PROJECT, cache)
    pack_id = int(sys.argv[3]) if len(sys.argv) > 3 else next(f['id'] for f in pack_files if f['name'] == 'RLCraft Dregora v1.1.2b.zip')
    pack_name = next(f['name'] for f in pack_files if f['id'] == pack_id)
    pack_zip = os.path.join(cache, pack_name)
    print('pack:', pack_name)
    fetch(pack_id, pack_name, pack_zip)
    z = zipfile.ZipFile(pack_zip)
    manifest = json.loads(z.read('manifest.json'))
    jars, missing = [], []
    for f in manifest['files']:
        hit = [x for x in project_files(f['projectID'], cache) if x['id'] == f['fileID']]
        if not hit:
            missing.append(f)
            continue
        dest = os.path.join(cache, 'mods', hit[0]['name'])
        fetch(f['fileID'], hit[0]['name'], dest)
        jars.append(dest)
    print(f'{len(jars)} mod files, {len(missing)} not found on cfwidget: {missing}')
    server = os.path.join(root, 'server')
    os.makedirs(server, exist_ok=True)
    if not os.path.exists(os.path.join(server, f'forge-{FORGE}.jar')):
        installer = os.path.join(cache, f'forge-{FORGE}-installer.jar')
        if not os.path.exists(installer):
            open(installer, 'wb').write(get(f'https://maven.minecraftforge.net/net/minecraftforge/forge/{FORGE}/forge-{FORGE}-installer.jar'))
        subprocess.run([java8, '-jar', installer, '--installServer'], cwd=server, check=True, stdout=subprocess.DEVNULL)
    mods = os.path.join(server, 'mods')
    os.makedirs(mods, exist_ok=True)
    norm = lambda s: re.sub(r'[^a-z]', '', s.lower())
    copied = 0
    for jar in jars:
        name = os.path.basename(jar)
        if name.endswith('.jar') and not any(norm(name).startswith(norm(c)) for c in CLIENT_ONLY):
            shutil.copy(jar, mods)
            copied += 1
    for name in z.namelist():
        rel = name[len('overrides/'):] if name.startswith('overrides/') else None
        if rel and not name.endswith('/') and rel.startswith(('config/', 'scripts/', 'mods/')):
            out = os.path.join(server, rel)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            open(out, 'wb').write(z.read(name))
    open(os.path.join(server, 'eula.txt'), 'w').write('eula=true\n')
    open(os.path.join(server, 'server.properties'), 'w').write(
        'online-mode=false\nlevel-type=FLAT\nmax-tick-time=-1\nspawn-protection=0\nview-distance=4\n')
    print(f'harness ready in {server} ({copied} server mods)')

if __name__ == '__main__':
    main()
