# E-IMZO dasturining ko'rinib turgan oynasini topadi, uni oldinga chiqaradi va sarlavhasini qaytaradi.
# Oyna bo'lmasa hech narsa chiqarmaydi.
$ErrorActionPreference = 'SilentlyContinue'
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class EimzoWin {
    public delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    public static string Find(uint[] pids, bool focus) { return Find(pids, focus, false); }
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
            if (close) PostMessage(h, 0x0010, IntPtr.Zero, IntPtr.Zero); // WM_CLOSE: oyna parolsiz yopiladi
            return false;
        }, IntPtr.Zero);
        return found;
    }
}
'@
$pids = @(Get-Process E-IMZO, javaw -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*E-IMZO*' } | ForEach-Object { [uint32]$_.Id })
if ($pids.Count -eq 0) { exit 0 }
$focus = $args -contains 'focus'
$close = $args -contains 'close'
$t = [EimzoWin]::Find([uint32[]]$pids, $focus, $close)
if ($t) { Write-Output $t }
