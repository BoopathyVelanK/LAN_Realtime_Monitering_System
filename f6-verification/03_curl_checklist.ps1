<#
F6 Faculty runtime RBAC checklist - PowerShell version.
Same REST coverage as 03_curl_checklist.sh. Run 02_verify.sql first to get
the real Lab B endpoint UUID, then invoke this script with -LabBEndpoint.

Example:
  .\03_curl_checklist.ps1 -LabBEndpoint "11111111-2222-3333-4444-555555555555"
#>

param(
    [Parameter(Mandatory = $true, HelpMessage = "Endpoint B's UUID from 02_verify.sql")]
    [string]$LabBEndpoint,

    [string]$BaseUrl = "http://localhost:8080/api"
)

$LabAEndpoint = "2de8dfec-51c2-452d-95dc-a69033918767"   # Boopathy, known - do not change

function Get-SecurePasswordPlainText {
    <# Prompts securely (no echo) and returns the plaintext only transiently
       in memory - never written to disk, never echoed. #>
    param([string]$PromptLabel)

    $secure = Read-Host -Prompt $PromptLabel -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Invoke-WithStatus {
    <# Runs a request and returns @{ Code = <int>; Body = <string> } even on
       non-2xx responses, matching curl -w "%{http_code}" behavior. #>
    param(
        [string]$Method = "GET",
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    try {
        $params = @{
            Method  = $Method
            Uri     = $Uri
            Headers = $Headers
        }
        if ($Body) {
            $params["Body"] = $Body
            $params["ContentType"] = "application/json"
        }
        $resp = Invoke-WebRequest @params -UseBasicParsing -ErrorAction Stop
        return @{ Code = $resp.StatusCode; Body = $resp.Content }
    } catch {
        $we = $_.Exception
        if ($we.Response) {
            $code = [int]$we.Response.StatusCode
            $stream = $we.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $respBody = $reader.ReadToEnd()
            return @{ Code = $code; Body = $respBody }
        }
        return @{ Code = -1; Body = $we.Message }
    }
}

function Invoke-Login {
    <# Logs in and returns @{ Code = <int>; Token = <string or $null> }. #>
    param([string]$Username, [string]$Password, [string]$BaseUrl)

   $body = @{ usernameOrEmail = $Username; password = $Password } | ConvertTo-Json
    $result = Invoke-WithStatus -Method POST -Uri "$BaseUrl/auth/login" -Body $body
    $token = $null
    if ($result.Code -eq 200) {
        $token = ($result.Body | ConvertFrom-Json).accessToken
    }
    return @{ Code = $result.Code; Token = $token }
}

function Write-Check {
    param([string]$Label, [bool]$Passed, [string]$Detail)
    if ($Passed) {
        Write-Host ("[{0}] PASS: {1}" -f $Label, $Detail) -ForegroundColor Green
    } else {
        Write-Host ("[{0}] FAIL: {1}" -f $Label, $Detail) -ForegroundColor Red
    }
}

# =====================================================================
# F6-G: Faculty login
# =====================================================================
Write-Host "--- G: Faculty login ---"
$facultyPassword = Get-SecurePasswordPlainText -PromptLabel "Enter f6.faculty's password (soc.admin's password, shared via hash reuse)"
$facLogin = Invoke-Login -Username "f6.faculty" -Password $facultyPassword -BaseUrl $BaseUrl
Write-Check -Label "F6-G" -Passed ($facLogin.Code -eq 200 -and $facLogin.Token) -Detail ("login HTTP {0}, token acquired: {1}" -f $facLogin.Code, [bool]$facLogin.Token)

if (-not $facLogin.Token) {
    Write-Host "Cannot continue without a Faculty token - stopping." -ForegroundColor Red
    exit 1
}
$facHeaders = @{ Authorization = "Bearer $($facLogin.Token)" }

# =====================================================================
# F6-H: Faculty endpoint list scope (strengthened - explicit requirements)
# =====================================================================
Write-Host "`n--- H: Faculty endpoint list scope ---"
$endpointsResult = Invoke-WithStatus -Uri "$BaseUrl/endpoints" -Headers $facHeaders

$httpOk = ($endpointsResult.Code -eq 200)
Write-Check -Label "F6-H.1" -Passed $httpOk -Detail ("GET /endpoints -> HTTP {0} (required: 200)" -f $endpointsResult.Code)

$labAPresent = $httpOk -and ($endpointsResult.Body -match [regex]::Escape($LabAEndpoint))
Write-Check -Label "F6-H.2" -Passed $labAPresent -Detail "Lab A endpoint UUID present in Faculty's endpoint list (required)"

$labBAbsent = $httpOk -and (-not ($endpointsResult.Body -match [regex]::Escape($LabBEndpoint)))
Write-Check -Label "F6-H.3" -Passed $labBAbsent -Detail "Lab B endpoint UUID absent from Faculty's endpoint list (required)"

# =====================================================================
# F6-K: explicit endpointId bypass attempts (expect 403)
# =====================================================================
Write-Host "`n--- K: explicit endpointId bypass attempts (expect 403) ---"
$monitoringPaths = @("login", "logout", "usb", "vpn", "idle", "network-usage", "internet-usage", "running-apps")
foreach ($path in $monitoringPaths) {
    $r = Invoke-WithStatus -Uri "$BaseUrl/monitoring/$path`?endpointId=$LabBEndpoint" -Headers $facHeaders
    Write-Check -Label "F6-K" -Passed ($r.Code -eq 403) -Detail ("GET /monitoring/{0}?endpointId=<LabB> -> {1} (required: 403)" -f $path, $r.Code)
}
$r = Invoke-WithStatus -Uri "$BaseUrl/alerts?endpointId=$LabBEndpoint" -Headers $facHeaders
Write-Check -Label "F6-K" -Passed ($r.Code -eq 403) -Detail ("GET /alerts?endpointId=<LabB> -> {0} (required: 403)" -f $r.Code)

$r = Invoke-WithStatus -Uri "$BaseUrl/risk-scores/$LabBEndpoint" -Headers $facHeaders
Write-Check -Label "F6-K" -Passed ($r.Code -eq 403) -Detail ("GET /risk-scores/<LabB> -> {0} (required: 403 - RiskScoreService.getForEndpoint checks scope BEFORE the repository lookup, so an out-of-scope Faculty caller is always denied regardless of whether a risk-score row exists; 404 here would mean the scope check was skipped, not that it passed)" -f $r.Code)

# =====================================================================
# F6-J: omitted endpointId must stay Lab-A-scoped, never fleet-wide
# (strengthened - inspects response body for Lab B leakage)
# =====================================================================
Write-Host "`n--- J: omitted endpointId must stay Lab-A-scoped, never fleet-wide ---"
$r = Invoke-WithStatus -Uri "$BaseUrl/monitoring/login" -Headers $facHeaders

$jHttpOk = ($r.Code -eq 200)
Write-Check -Label "F6-J.1" -Passed $jHttpOk -Detail ("GET /monitoring/login (no endpointId) -> HTTP {0} (required: 200)" -f $r.Code)

$jNoLabBLeak = $jHttpOk -and (-not ($r.Body -match [regex]::Escape($LabBEndpoint)))
Write-Check -Label "F6-J.2" -Passed $jNoLabBLeak -Detail "Lab B endpoint UUID absent from unscoped monitoring response body (required)"

if ($jHttpOk -and ($r.Body -match [regex]::Escape($LabAEndpoint))) {
    Write-Host "[F6-J.3] INFO: Lab A endpoint UUID present in response body (allowed - this is Faculty's assigned scope)"
}

# =====================================================================
# F6-M: Admin regression after Faculty fixture
# =====================================================================
Write-Host "`n--- M: Admin regression after Faculty fixture ---"
$adminPassword = Get-SecurePasswordPlainText -PromptLabel "Enter soc.admin's password"
$adminLogin = Invoke-Login -Username "soc.admin" -Password $adminPassword -BaseUrl $BaseUrl
Write-Check -Label "F6-M.1" -Passed ($adminLogin.Code -eq 200 -and $adminLogin.Token) -Detail ("admin login HTTP {0}, token acquired: {1}" -f $adminLogin.Code, [bool]$adminLogin.Token)

if (-not $adminLogin.Token) {
    Write-Host "Cannot continue Admin regression without an Admin token - stopping." -ForegroundColor Red
    exit 1
}
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.Token)" }

$adminEndpointsResult = Invoke-WithStatus -Uri "$BaseUrl/endpoints" -Headers $adminHeaders
$adminHttpOk = ($adminEndpointsResult.Code -eq 200)
Write-Check -Label "F6-M.2" -Passed $adminHttpOk -Detail ("GET /endpoints as ADMIN -> HTTP {0} (required: 200)" -f $adminEndpointsResult.Code)

$adminSeesLabA = $adminHttpOk -and ($adminEndpointsResult.Body -match [regex]::Escape($LabAEndpoint))
Write-Check -Label "F6-M.3" -Passed $adminSeesLabA -Detail "Lab A endpoint UUID present in Admin's endpoint list (required)"

$adminSeesLabB = $adminHttpOk -and ($adminEndpointsResult.Body -match [regex]::Escape($LabBEndpoint))
Write-Check -Label "F6-M.4" -Passed $adminSeesLabB -Detail "Lab B endpoint UUID present in Admin's endpoint list (required)"

Write-Host "`n--- M: Admin must retain full access to Lab B via the same endpointId checks that denied Faculty ---"
foreach ($path in $monitoringPaths) {
    $r = Invoke-WithStatus -Uri "$BaseUrl/monitoring/$path`?endpointId=$LabBEndpoint" -Headers $adminHeaders
    Write-Check -Label "F6-M" -Passed ($r.Code -eq 200) -Detail ("GET /monitoring/{0}?endpointId=<LabB> as ADMIN -> {1} (required: 200)" -f $path, $r.Code)
}
$r = Invoke-WithStatus -Uri "$BaseUrl/alerts?endpointId=$LabBEndpoint" -Headers $adminHeaders
Write-Check -Label "F6-M" -Passed ($r.Code -eq 200) -Detail ("GET /alerts?endpointId=<LabB> as ADMIN -> {0} (required: 200)" -f $r.Code)

$r = Invoke-WithStatus -Uri "$BaseUrl/risk-scores/$LabBEndpoint" -Headers $adminHeaders
$adminRiskOk = ($r.Code -eq 200 -or $r.Code -eq 404)
Write-Check -Label "F6-M" -Passed $adminRiskOk -Detail ("GET /risk-scores/<LabB> as ADMIN -> {0} (200 or 404 both acceptable - 404 means no risk row exists yet, not a scope failure; 403 would be the real fail)" -f $r.Code)