param(
    [switch]$Modrinth,
    [switch]$CurseForge,
    # sends a Modrinth project still in draft to its moderators
    [switch]$SubmitForReview
)
# Publishes the built jars of the current version to Modrinth and CurseForge, and on a first run creates the Modrinth
# project with its icon and gallery. CurseForge has no way to create a project but its website, so that one must exist.
# Reads MODRINTH_TOKEN, CURSEFORGE_TOKEN and CURSEFORGE_PROJECT_ID from the user's environment; build both jars first.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$repo = "https://github.com/YashjitPal/The-Scarlet-Witch-Mod"
$properties = Get-Content "$root\gradle.properties"
$version = ($properties -match '^version=') -replace '^version=', ''
$minecraft = ($properties -match '^minecraft_version=') -replace '^minecraft_version=', ''
$loaders = [ordered]@{ fabric = "Fabric"; neoforge = "NeoForge" }
$jars = @{}
foreach ($loader in $loaders.Keys) {
    $jars[$loader] = "$root\$loader\build\libs\scarlet-$loader-$minecraft-$version.jar"
    if (-not (Test-Path -LiteralPath $jars[$loader])) {
        throw "No $($jars[$loader]): build it first"
    }
}
$entry = [regex]::Match((Get-Content "$root\CHANGELOG.md" -Raw -Encoding UTF8), "(?ms)^## $([regex]::Escape($version))[ \t]*\r?\n(.*?)(?=^## |\z)")
if (-not $entry.Success) {
    throw "CHANGELOG.md has no entry for $version"
}
$changelog = $entry.Groups[1].Value.Trim()

function Get-Secret([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not $value) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    if (-not $value) {
        throw "$name is not set"
    }
    return $value.Trim()
}

Add-Type -AssemblyName System.Net.Http
$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromMinutes(10)
$http.DefaultRequestHeaders.TryAddWithoutValidation("User-Agent", "YashjitPal/scarlet-witch/$version (github.com/YashjitPal/The-Scarlet-Witch-Mod)") | Out-Null

function Invoke-Api([string]$method, [string]$url, $content, [hashtable]$headers) {
    $request = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::new($method)), $url
    foreach ($key in $headers.Keys) {
        $request.Headers.TryAddWithoutValidation($key, $headers[$key]) | Out-Null
    }
    if ($content) {
        $request.Content = $content
    }
    $response = $http.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "$method $url answered $([int]$response.StatusCode): $text"
    }
    if ($text) {
        return $text | ConvertFrom-Json
    }
}

function New-Json($value) {
    return New-Object System.Net.Http.StringContent (($value | ConvertTo-Json -Depth 10)), ([Text.Encoding]::UTF8), "application/json"
}

function New-File([string]$path, [string]$type) {
    $file = New-Object System.Net.Http.ByteArrayContent (, [IO.File]::ReadAllBytes($path))
    $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new($type)
    return $file
}

# ---------------------------------------------------------------- Modrinth
if ($Modrinth) {
    $api = "https://api.modrinth.com/v2"
    $auth = @{ Authorization = (Get-Secret 'MODRINTH_TOKEN') }
    $project = $null
    try {
        $project = Invoke-Api GET "$api/project/scarlet-witch" $null $auth
    } catch {
        if ("$_" -notmatch 'answered 404') {
            throw
        }
    }
    if (-not $project) {
        $data = [ordered]@{
            slug = "scarlet-witch"
            title = "Scarlet Witch"
            description = "Chaos magic inspired by the Scarlet Witch: put on the tiara, weave on the costume, master her spells one by one, and bend a whole town into a sitcom inside the Hex."
            # read as a plain string: Get-Content's carries its file's provider along, which ConvertTo-Json chokes on
            body = [IO.File]::ReadAllText("$root\docs\MODPAGE.md", [Text.Encoding]::UTF8)
            categories = @("magic", "adventure", "decoration")
            additional_categories = @("equipment", "game-mechanics", "mobs")
            client_side = "required"
            server_side = "required"
            issues_url = "$repo/issues"
            source_url = $repo
            wiki_url = $null
            discord_url = $null
            donation_urls = @()
            license_id = "LicenseRef-All-Rights-Reserved"
            project_type = "mod"
            initial_versions = @()
            is_draft = $true
            requested_status = "approved"
        }
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add((New-Json $data), "data")
        $form.Add((New-File "$root\docs\media\icon.png" "image/png"), "icon", "icon.png")
        $project = Invoke-Api POST "$api/project" $form $auth
        Write-Output "Modrinth: created the project ($($project.id))"
        $gallery = @(
            @("banner.webp", "Scarlet Witch", "Chaos magic, the Hex and the Darkhold"),
            @("features/suit-up.webp", "Suit up", "Scarlet threads spiral up from her feet and the costume weaves on"),
            @("features/chaos-bolt.webp", "Chaos Bolt", "Blasts of scarlet energy"),
            @("features/chaos-shield.webp", "Chaos Shield", "A disc of energy that stops blows and turns projectiles back"),
            @("features/levitation.webp", "Levitation", "Rising into the air and casting while flying"),
            @("features/telekinesis.webp", "Telekinesis", "A cow lifted in scarlet threads and thrown"),
            @("features/shockwave.webp", "Shockwave", "A burst that throws everything back"),
            @("features/mind-control.webp", "Mind Control", "Looking out through a husk's eyes"),
            @("features/hex-founding.webp", "Raise a Hex", "Your sitcom home builds itself around you"),
            @("features/hex-eras.webp", "Six sitcom eras", "The same street from the 1950s to the present day"),
            @("features/townspeople.webp", "Townspeople", "Hostile mobs become townspeople in era clothes"),
            @("features/rewind.webp", "Rewind the scene", "The last ten seconds running backward on videotape"),
            @("features/hex-falls.webp", "The Hex falls", "Everything goes back the way it was"),
            @("features/darkhold.webp", "The Darkhold", "It floats open and corrupts whoever reads it"),
            @("features/dreamwalking.webp", "Dreamwalking", "Her body rises while her spirit looks out through a cow")
        )
        $order = 0
        foreach ($image in $gallery) {
            $query = "ext=webp&featured=$(if ($order -eq 0) { 'true' } else { 'false' })&title=$([uri]::EscapeDataString($image[1]))&description=$([uri]::EscapeDataString($image[2]))&ordering=$order"
            Invoke-Api POST "$api/project/$($project.id)/gallery?$query" (New-File "$root\docs\media\$($image[0])" "image/webp") $auth | Out-Null
            $order++
        }
        Write-Output "Modrinth: added $order gallery images"
        $project = Invoke-Api GET "$api/project/$($project.id)" $null $auth
    }
    $existing = @(Invoke-Api GET "$api/project/$($project.id)/version" $null $auth)
    foreach ($loader in $loaders.Keys) {
        $number = "$version+$loader"
        if ($existing | Where-Object { $_.version_number -eq $number }) {
            Write-Output "Modrinth: $number is already up"
            continue
        }
        $dependencies = @()
        if ($loader -eq "fabric") {
            # Fabric API
            $dependencies = @(@{ project_id = "P7dR8mSH"; dependency_type = "required" })
        }
        $data = [ordered]@{
            name = "Scarlet Witch $version for $($loaders[$loader])"
            version_number = $number
            changelog = $changelog
            dependencies = $dependencies
            game_versions = @($minecraft)
            version_type = "release"
            loaders = @($loader)
            featured = $true
            project_id = $project.id
            file_parts = @("jar")
            primary_file = "jar"
        }
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add((New-Json $data), "data")
        $form.Add((New-File $jars[$loader] "application/java-archive"), "jar", (Split-Path $jars[$loader] -Leaf))
        $uploaded = Invoke-Api POST "$api/version" $form $auth
        # needed on the server and in every player's game; only Modrinth's v3 API sets it, and a project can't go to
        # review while its environment is unknown
        Invoke-Api PATCH "https://api.modrinth.com/v3/version/$($uploaded.id)" (New-Json @{ environment = "client_and_server" }) $auth | Out-Null
        Write-Output "Modrinth: uploaded $number ($($uploaded.id))"
    }
    if ($SubmitForReview -and $project.status -eq "draft") {
        Invoke-Api PATCH "$api/project/$($project.id)" (New-Json @{ status = "processing" }) $auth | Out-Null
        Write-Output "Modrinth: sent for review"
    }
    Write-Output "Modrinth: https://modrinth.com/mod/$($project.slug) ($((Invoke-Api GET "$api/project/$($project.id)" $null $auth).status))"
}

# ---------------------------------------------------------------- CurseForge
if ($CurseForge) {
    $api = "https://minecraft.curseforge.com/api"
    $auth = @{ "X-Api-Token" = (Get-Secret 'CURSEFORGE_TOKEN') }
    $projectId = Get-Secret 'CURSEFORGE_PROJECT_ID'
    $types = @(Invoke-Api GET "$api/game/version-types" $null $auth)
    $versions = @(Invoke-Api GET "$api/game/versions" $null $auth)
    function Find-Version([string]$typeSlug, [string]$name) {
        $typeIds = @($types | Where-Object { $_.slug -match $typeSlug } | ForEach-Object { $_.id })
        return ($versions | Where-Object { $typeIds -contains $_.gameVersionTypeID -and $_.name -eq $name } | Select-Object -First 1).id
    }
    $minecraftId = Find-Version '^minecraft-' $minecraft
    if (-not $minecraftId) {
        throw "CurseForge doesn't list Minecraft $minecraft yet"
    }
    $shared = @($minecraftId, (Find-Version '^java$' 'Java 25'), (Find-Version '^environment$' 'Client'), (Find-Version '^environment$' 'Server')) | Where-Object { $_ }
    foreach ($loader in $loaders.Keys) {
        $loaderId = Find-Version '^modloader$' $loaders[$loader]
        if (-not $loaderId) {
            throw "CurseForge doesn't list the $($loaders[$loader]) loader"
        }
        $metadata = [ordered]@{
            changelog = $changelog
            changelogType = "markdown"
            displayName = "Scarlet Witch $version for $($loaders[$loader])"
            gameVersions = @($shared) + $loaderId
            releaseType = "release"
        }
        if ($loader -eq "fabric") {
            $metadata.relations = @{ projects = @(@{ slug = "fabric-api"; type = "requiredDependency" }) }
        }
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add((New-Json $metadata), "metadata")
        $form.Add((New-File $jars[$loader] "application/java-archive"), "file", (Split-Path $jars[$loader] -Leaf))
        $uploaded = Invoke-Api POST "$api/projects/$projectId/upload-file" $form $auth
        Write-Output "CurseForge: uploaded $($metadata.displayName) (file $($uploaded.id))"
    }
}
