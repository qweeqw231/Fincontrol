# Count total tests from surefire-reports
$total = 0
$failures = 0
$errors = 0
$passed = 0
$suites = 0
Get-ChildItem 'c:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend\target\surefire-reports\TEST-*.xml' | ForEach-Object {
    $content = [xml](Get-Content $_.FullName)
    $total += [int]$content.testsuite.tests
    $failures += [int]$content.testsuite.failures
    $errors += [int]$content.testsuite.errors
    $suites++
}
$passed = $total - $failures - $errors
Write-Host ("suites=" + $suites + " total=" + $total + " passed=" + $passed + " failures=" + $failures + " errors=" + $errors)