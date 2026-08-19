using System;

internal static class AskPass
{
    private static int Main()
    {
        Console.Write(Environment.GetEnvironmentVariable("CODEX_SSH_PASSWORD") ?? string.Empty);
        return 0;
    }
}
