# Upgrader Wheel installer for Windows.
#
# Run it with one line in PowerShell:
#   irm https://raw.githubusercontent.com/MalekBaghdadi/upgrader-wheel/main/install/install.ps1 | iex
# or double-click Install-UpgraderWheel.bat from the Releases page.
#
# It finds your Minecraft instances (CurseForge, official launcher, TLauncher, Prism, MultiMC, ATLauncher, GDLauncher,
# Modrinth App, Technic, FTB), asks which one, downloads the newest Upgrader Wheel and puts it in that instance's mods
# folder, removing older Upgrader Wheel versions. Nothing else is touched.
#
# Options (when running the file directly):  -ModsFolder "C:\path\to\mods"   install there without asking
#                                            -Jar "C:\path\upgraderwheel.jar" use a jar you already downloaded
#                                            -ListOnly                         only show the instances it finds

param(
    [string]$ModsFolder = $env:UPGRADERWHEEL_MODS,
    [string]$Jar = "",
    [switch]$ListOnly = [bool]$env:UPGRADERWHEEL_LISTONLY
)

$ErrorActionPreference = 'Stop'
$Repo = 'MalekBaghdadi/upgrader-wheel'

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Bad($text) { Write-Host "    $text" -ForegroundColor Yellow }

function Get-GameVersionHint([string]$instanceDir) {
    # Best effort: read the Minecraft version and loader from the launcher's instance file
    try {
        $cf = Join-Path $instanceDir 'minecraftinstance.json'
        if (Test-Path $cf) {
            $j = Get-Content $cf -Raw | ConvertFrom-Json
            $loader = ''
            if ($j.baseModLoader -and $j.baseModLoader.name) { $loader = " " + $j.baseModLoader.name }
            return "$($j.gameVersion)$loader"
        }
        $mmc = Join-Path $instanceDir 'mmc-pack.json'
        if (Test-Path $mmc) {
            $j = Get-Content $mmc -Raw | ConvertFrom-Json
            $mc = ($j.components | Where-Object { $_.uid -eq 'net.minecraft' }).version
            $forge = ($j.components | Where-Object { $_.uid -eq 'net.minecraftforge' }).version
            if ($forge) { return "$mc forge-$forge" }
            return $mc
        }
        $prof = Join-Path $instanceDir 'profile.json'
        if (Test-Path $prof) {
            $j = Get-Content $prof -Raw | ConvertFrom-Json
            if ($j.metadata -and $j.metadata.game_version) { return "$($j.metadata.game_version) $($j.metadata.loader)" }
        }
    } catch { }
    return ''
}

function Find-ModsFolders {
    $found = New-Object System.Collections.Generic.List[object]
    $seen = @{}
    function Add-Candidate([string]$mods, [string]$launcher, [string]$name, [string]$instanceDir) {
        if (-not $mods) { return }
        $full = [System.IO.Path]::GetFullPath($mods)
        if ($seen.ContainsKey($full.ToLower())) { return }
        if (-not (Test-Path $full -PathType Container)) { return }
        $seen[$full.ToLower()] = $true
        $jars = @(Get-ChildItem -Path $full -Filter *.jar -File -ErrorAction SilentlyContinue)
        $hint = Get-GameVersionHint $instanceDir
        $dregora = @($jars | Where-Object { $_.Name -like 'DregoraRL*' }).Count -gt 0
        $rlcraft = @($jars | Where-Object { $_.Name -like 'RLTweaker*' -or $_.Name -like 'RLMixins*' }).Count -gt 0
        $has112 = ($hint -like '1.12.2*') -or (@($jars | Where-Object { $_.Name -match '1\.12' }).Count -gt 0)
        $installed = @($jars | Where-Object { $_.Name -like 'upgraderwheel-*' } | ForEach-Object { $_.Name }) -join ', '
        $found.Add([pscustomobject]@{
            Launcher = $launcher; Name = $name; Mods = $full; Hint = $hint; Jars = $jars.Count
            Dregora = $dregora; RLCraft = $rlcraft; Is112 = $has112; Installed = $installed
        })
    }
    function Add-Instances([string]$root, [string]$launcher, [string]$modsRelative) {
        if (-not (Test-Path $root -PathType Container)) { return }
        foreach ($dir in Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue) {
            foreach ($rel in $modsRelative.Split('|')) {
                $mods = Join-Path $dir.FullName $rel
                if (Test-Path $mods -PathType Container) {
                    Add-Candidate $mods $launcher $dir.Name $dir.FullName
                    break
                }
            }
        }
    }

    $appdata = $env:APPDATA
    $local = $env:LOCALAPPDATA
    $userHome = $env:USERPROFILE

    # CurseForge app (default and older locations, plus a custom folder from its settings)
    Add-Instances (Join-Path $userHome 'curseforge\minecraft\Instances') 'CurseForge' 'mods'
    Add-Instances (Join-Path $userHome 'Documents\Curse\Minecraft\Instances') 'CurseForge' 'mods'
    try {
        $cfSettings = Join-Path $appdata 'CurseForge\storage\settings.json'
        if (Test-Path $cfSettings) {
            $raw = Get-Content $cfSettings -Raw
            foreach ($m in [regex]::Matches($raw, '"([A-Za-z]:\\\\[^"]*?[Mm]inecraft[^"]*?)"')) {
                $p = $m.Groups[1].Value -replace '\\\\', '\'
                Add-Instances (Join-Path $p 'Instances') 'CurseForge' 'mods'
            }
        }
    } catch { }

    # Official launcher and TLauncher (shared folder, and TLauncher's separate folder per version)
    Add-Candidate (Join-Path $appdata '.minecraft\mods') 'Official launcher / TLauncher' '.minecraft' (Join-Path $appdata '.minecraft')
    Add-Instances (Join-Path $appdata '.minecraft\versions') 'TLauncher' 'mods'

    # Prism Launcher, PolyMC, MultiMC-style launchers
    foreach ($l in @('PrismLauncher', 'PolyMC', 'MultiMC')) {
        Add-Instances (Join-Path $appdata "$l\instances") $l '.minecraft\mods|minecraft\mods'
    }
    foreach ($p in @("$userHome\MultiMC\instances", "$userHome\Desktop\MultiMC\instances", "$userHome\Downloads\MultiMC\instances", 'C:\MultiMC\instances')) {
        Add-Instances $p 'MultiMC' '.minecraft\mods|minecraft\mods'
    }

    # ATLauncher, GDLauncher, Modrinth App, Technic, FTB App
    Add-Instances (Join-Path $appdata 'ATLauncher\instances') 'ATLauncher' 'mods'
    Add-Instances (Join-Path $userHome 'ATLauncher\instances') 'ATLauncher' 'mods'
    Add-Instances (Join-Path $appdata 'gdlauncher_next\instances') 'GDLauncher' 'mods'
    Add-Instances (Join-Path $appdata 'gdlauncher_carbon\data\instances') 'GDLauncher' 'instance\mods'
    Add-Instances (Join-Path $appdata 'com.modrinth.theseus\profiles') 'Modrinth App' 'mods'
    Add-Instances (Join-Path $appdata 'ModrinthApp\profiles') 'Modrinth App' 'mods'
    Add-Instances (Join-Path $appdata '.technic\modpacks') 'Technic' 'mods'
    Add-Instances (Join-Path $local '.ftba\instances') 'FTB App' 'mods'

    return $found
}

function Get-LatestJar {
    Write-Step "Downloading the newest Upgrader Wheel from GitHub"
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -Headers @{ 'User-Agent' = 'upgraderwheel-installer' }
    $asset = $release.assets | Where-Object { $_.name -like 'upgraderwheel-*.jar' -and $_.name -notlike '*-dev*' -and $_.name -notlike '*-sources*' } | Select-Object -First 1
    if (-not $asset) { throw "The latest release ($($release.tag_name)) has no jar attached." }
    $target = Join-Path ([System.IO.Path]::GetTempPath()) $asset.name
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $target -UseBasicParsing -Headers @{ 'User-Agent' = 'upgraderwheel-installer' }
    Write-Host "    Got $($asset.name) ($($release.tag_name))"
    return $target
}

function Install-Into([string]$mods, [string]$jarPath) {
    $old = @(Get-ChildItem -Path $mods -Filter 'upgraderwheel-*.jar' -File -ErrorAction SilentlyContinue)
    foreach ($f in $old) {
        try {
            Remove-Item -LiteralPath $f.FullName -Force
            Write-Host "    Removed old $($f.Name)"
        } catch {
            throw "Could not remove $($f.Name). Close Minecraft (and the launcher's game window) first, then run this again."
        }
    }
    Copy-Item -LiteralPath $jarPath -Destination $mods -Force
    Write-Host "    Installed $(Split-Path $jarPath -Leaf) into $mods" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Upgrader Wheel installer' -ForegroundColor White
Write-Host 'Needs Minecraft 1.12.2 with Forge (RLCraft, RLCraft Dregora and most 1.12.2 modpacks already have it).'
Write-Host ''

try {
    if (-not $ModsFolder) {
        Write-Step 'Looking for Minecraft instances'
        $candidates = @(Find-ModsFolders)
        # Most likely first: RLCraft Dregora, then RLCraft, then 1.12.2 packs, then the rest
        $candidates = @($candidates | Sort-Object @{ e = { -not $_.Dregora } }, @{ e = { -not $_.RLCraft } }, @{ e = { -not $_.Is112 } }, Launcher, Name)
        if ($candidates.Count -gt 0) {
            for ($i = 0; $i -lt $candidates.Count; $i++) {
                $c = $candidates[$i]
                $tags = @()
                if ($c.Dregora) { $tags += 'RLCraft Dregora' } elseif ($c.RLCraft) { $tags += 'RLCraft' }
                if ($c.Hint) { $tags += $c.Hint } elseif ($c.Is112) { $tags += 'looks like 1.12.2' }
                $tags += "$($c.Jars) mods"
                if ($c.Installed) { $tags += "has $($c.Installed)" }
                Write-Host ("  [{0}] {1}: {2}  ({3})" -f ($i + 1), $c.Launcher, $c.Name, ($tags -join ', '))
                Write-Host ("      {0}" -f $c.Mods) -ForegroundColor DarkGray
            }
        } else {
            Write-Bad 'No Minecraft instances found automatically.'
        }
        if ($ListOnly) { return }
        Write-Host '  [P] Paste the path of a mods folder myself'
        Write-Host ''
        $answer = Read-Host 'Install into which one? (number, or P)'
        if ($answer -match '^[Pp]$') {
            $ModsFolder = (Read-Host 'Path of the mods folder').Trim('"', ' ')
        } elseif ($answer -match '^\d+$' -and [int]$answer -ge 1 -and [int]$answer -le $candidates.Count) {
            $ModsFolder = $candidates[[int]$answer - 1].Mods
        } else {
            throw 'Nothing chosen.'
        }
    }
    if (-not (Test-Path $ModsFolder -PathType Container)) {
        $leaf = Split-Path $ModsFolder -Leaf
        $parent = Split-Path $ModsFolder -Parent
        if ($leaf -eq 'mods' -and $parent -and (Test-Path $parent -PathType Container)) {
            New-Item -ItemType Directory -Path $ModsFolder | Out-Null
        } else {
            throw "That folder does not exist: $ModsFolder"
        }
    }
    if (-not $Jar) { $Jar = Get-LatestJar }
    Write-Step "Installing"
    Install-Into $ModsFolder $Jar
    Write-Host ''
    Write-Host 'Done! Start Minecraft and press U in game to open the wheel (change the key in Options > Controls).' -ForegroundColor Green
    Write-Host 'Playing on a server or LAN with friends? Everyone needs the mod, and so does the server.'
} catch {
    Write-Host ''
    Write-Host "Install failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Manual install: download the jar from https://github.com/MalekBaghdadi/upgrader-wheel/releases/latest and drop it in your instance''s mods folder.'
}
Write-Host ''
if ($Host.Name -eq 'ConsoleHost' -and -not $env:UPGRADERWHEEL_NOPAUSE) { Read-Host 'Press Enter to close' | Out-Null }
