# E-IMZO oynasini kuzatuvchi DOIMIY jarayon (tezlik uchun): bir marta ishga tushadi va stdin'dan buyruq o'qiydi.
# Buyruqlar: "title" (sarlavha), "focus" (oldinga chiqarish), "close" (WM_CLOSE bilan parolsiz yopish).
# Har bir buyruqqa bitta qator javob: oyna sarlavhasi yoki bo'sh qator.
$ErrorActionPreference = 'SilentlyContinue'
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class EimzoSrv {
    public delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    public static string Find(uint[] pids, bool focus, bool close) {
        string found = null;
        EnumWindows((h, p) => {
            uint pid; GetWindowThreadProcessId(h, out pid);
            if (Array.IndexOf(pids, pid) < 0 || !IsWindowVisible(h)) return true;
            var sb = new StringBuilder(512); GetWindowText(h, sb, 512);
            string t = sb.ToString();
            if (t.Length == 0) return true;
            found = t;
            if (focus) { ShowWindow(h, 5); SetForegroundWindow(h); }
            if (close) PostMessage(h, 0x0010, IntPtr.Zero, IntPtr.Zero);
            return false;
        }, IntPtr.Zero);
        return found;
    }
}
'@
$pids = @()
$last = [DateTime]::MinValue
while ($true) {
    $cmd = [Console]::In.ReadLine()
    if ($cmd -eq $null -or $cmd -eq 'exit') { break }
    # E-IMZO jarayonlari ro'yxati 5 soniyada bir yangilanadi
    if ($pids.Count -eq 0 -or ([DateTime]::Now - $last).TotalSeconds -gt 5) {
        $pids = @(Get-Process E-IMZO, javaw -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*E-IMZO*' } | ForEach-Object { [uint32]$_.Id })
        $last = [DateTime]::Now
    }
    $t = $null
    if ($pids.Count -gt 0) { $t = [EimzoSrv]::Find([uint32[]]$pids, ($cmd -eq 'focus'), ($cmd -eq 'close')) }
    if ($t) { [Console]::Out.WriteLine($t) } else { [Console]::Out.WriteLine('') }
    [Console]::Out.Flush()
}
