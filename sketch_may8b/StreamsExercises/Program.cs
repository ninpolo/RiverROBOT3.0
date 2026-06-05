using System.Globalization;
using System.IO.Compression;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;

Console.OutputEncoding = Encoding.UTF8;

if (args.Length == 0)
{
    PrintHelp();
    return;
}

try
{
    switch (args[0].ToLowerInvariant())
    {
        case "101-1":
            RequireArgs(args, 2);
            PrintOddLines(args[1]);
            break;
        case "101-2":
            RequireArgs(args, 3);
            WriteNumberedLines(args[1], args[2]);
            break;
        case "101-3":
            RequireArgs(args, 4);
            CountWords(args[1], args[2], args[3]);
            break;
        case "102-1":
            RequireArgs(args, 3);
            CopyBinaryFile(args[1], args[2]);
            break;
        case "102-2-slice":
            RequireArgs(args, 4);
            Slice(args[1], args[2], int.Parse(args[3], CultureInfo.InvariantCulture));
            break;
        case "102-2-assemble":
            RequireArgs(args, 4);
            Assemble(args.Skip(2).ToList(), args[1], compressed: false);
            break;
        case "102-3-slice":
            RequireArgs(args, 4);
            SliceCompressed(args[1], args[2], int.Parse(args[3], CultureInfo.InvariantCulture));
            break;
        case "102-3-assemble":
            RequireArgs(args, 4);
            Assemble(args.Skip(2).ToList(), args[1], compressed: true);
            break;
        case "102-4":
            RequireArgs(args, 2);
            WriteDirectoryReport(args[1], args.Length > 2 ? args[2] : null, recursive: false);
            break;
        case "102-5":
            RequireArgs(args, 2);
            WriteDirectoryReport(args[1], args.Length > 2 ? args[2] : null, recursive: true);
            break;
        case "102-6":
            RequireArgs(args, 2);
            RunHttpServer(int.Parse(args[1], CultureInfo.InvariantCulture));
            break;
        default:
            PrintHelp();
            break;
    }
}
catch (Exception ex)
{
    Console.WriteLine($"Error: {ex.Message}");
}

static void PrintOddLines(string textFile)
{
    using StreamReader reader = new(textFile);
    int lineNumber = 0;

    while (!reader.EndOfStream)
    {
        string? line = reader.ReadLine();
        if (lineNumber % 2 != 0)
        {
            Console.WriteLine(line);
        }

        lineNumber++;
    }
}

static void WriteNumberedLines(string inputFile, string outputFile)
{
    using StreamReader reader = new(inputFile);
    using StreamWriter writer = new(outputFile);
    int lineNumber = 1;

    while (!reader.EndOfStream)
    {
        writer.WriteLine($"Line {lineNumber}: {reader.ReadLine()}");
        lineNumber++;
    }
}

static void CountWords(string wordsFile, string textFile, string resultsFile)
{
    string[] words;
    using (StreamReader reader = new(wordsFile))
    {
        words = Regex.Split(reader.ReadToEnd(), @"\s+")
            .Where(word => !string.IsNullOrWhiteSpace(word))
            .Select(word => word.Trim())
            .Select(w => w.ToLowerInvariant())
            .ToArray();
    }

    string text;
    using (StreamReader reader = new(textFile))
    {
        text = reader.ReadToEnd().ToLowerInvariant();
    }

    Dictionary<string, int> counts = words.ToDictionary(word => word, _ => 0);
    MatchCollection textWords = Regex.Matches(text, @"\b[\p{L}\p{N}']+\b");

    foreach (Match match in textWords)
    {
        if (counts.ContainsKey(match.Value))
        {
            counts[match.Value]++;
        }
    }

    using StreamWriter writer = new(resultsFile);
    foreach (KeyValuePair<string, int> pair in counts.OrderByDescending(p => p.Value).ThenBy(p => p.Key))
    {
        writer.WriteLine($"{pair.Key} - {pair.Value}");
    }
}

static void CopyBinaryFile(string sourceFile, string destinationFile)
{
    using FileStream reader = new(sourceFile, FileMode.Open, FileAccess.Read);
    using FileStream writer = new(destinationFile, FileMode.Create, FileAccess.Write);

    byte[] buffer = new byte[4096];
    int bytesRead;
    while ((bytesRead = reader.Read(buffer, 0, buffer.Length)) > 0)
    {
        writer.Write(buffer, 0, bytesRead);
    }
}

static void Slice(string sourceFile, string destinationDirectory, int parts)
{
    Directory.CreateDirectory(destinationDirectory);

    using FileStream reader = new(sourceFile, FileMode.Open, FileAccess.Read);
    long partSize = (long)Math.Ceiling(reader.Length / (double)parts);
    byte[] buffer = new byte[4096];

    for (int part = 1; part <= parts; part++)
    {
        string partPath = Path.Combine(destinationDirectory, $"part-{part:000}.bin");
        using FileStream writer = new(partPath, FileMode.Create, FileAccess.Write);
        CopyLimited(reader, writer, partSize, buffer);
    }
}

static void SliceCompressed(string sourceFile, string destinationDirectory, int parts)
{
    Directory.CreateDirectory(destinationDirectory);

    using FileStream reader = new(sourceFile, FileMode.Open, FileAccess.Read);
    long partSize = (long)Math.Ceiling(reader.Length / (double)parts);
    byte[] buffer = new byte[4096];

    for (int part = 1; part <= parts; part++)
    {
        string partPath = Path.Combine(destinationDirectory, $"part-{part:000}.gz");
        using FileStream fileWriter = new(partPath, FileMode.Create, FileAccess.Write);
        using GZipStream gzipWriter = new(fileWriter, CompressionMode.Compress);
        CopyLimited(reader, gzipWriter, partSize, buffer);
    }
}

static void Assemble(List<string> files, string destinationDirectory, bool compressed)
{
    Directory.CreateDirectory(destinationDirectory);
    string outputPath = Path.Combine(destinationDirectory, compressed ? "assembled-from-gzip.bin" : "assembled.bin");

    using FileStream writer = new(outputPath, FileMode.Create, FileAccess.Write);
    byte[] buffer = new byte[4096];

    foreach (string file in files)
    {
        using FileStream fileReader = new(file, FileMode.Open, FileAccess.Read);

        if (compressed)
        {
            using GZipStream gzipReader = new(fileReader, CompressionMode.Decompress);
            CopyAll(gzipReader, writer, buffer);
        }
        else
        {
            CopyAll(fileReader, writer, buffer);
        }
    }
}

static void CopyLimited(Stream reader, Stream writer, long bytesToCopy, byte[] buffer)
{
    while (bytesToCopy > 0 && reader.Position < reader.Length)
    {
        int bytesToRead = (int)Math.Min(buffer.Length, bytesToCopy);
        int bytesRead = reader.Read(buffer, 0, bytesToRead);
        if (bytesRead == 0)
        {
            break;
        }

        writer.Write(buffer, 0, bytesRead);
        bytesToCopy -= bytesRead;
    }
}

static void CopyAll(Stream reader, Stream writer, byte[] buffer)
{
    int bytesRead;
    while ((bytesRead = reader.Read(buffer, 0, buffer.Length)) > 0)
    {
        writer.Write(buffer, 0, bytesRead);
    }
}

static void WriteDirectoryReport(string directoryPath, string? extensionFilter, bool recursive)
{
    DirectoryInfo directory = new(directoryPath);
    if (!directory.Exists)
    {
        throw new DirectoryNotFoundException(directoryPath);
    }

    SearchOption option = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
    string? normalizedExtension = NormalizeExtension(extensionFilter);

    IEnumerable<FileInfo> files = directory
        .EnumerateFiles("*", option)
        .Where(file => normalizedExtension == null || file.Extension.Equals(normalizedExtension, StringComparison.OrdinalIgnoreCase));

    var groups = files
        .GroupBy(file => file.Extension.ToLowerInvariant())
        .OrderByDescending(group => group.Count())
        .ThenBy(group => group.Key);

    string desktopPath = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
    string reportPath = Path.Combine(desktopPath, "report.txt");

    using StreamWriter writer = new(reportPath);
    foreach (var group in groups)
    {
        writer.WriteLine(group.Key);
        foreach (FileInfo file in group.OrderBy(file => file.Length).ThenBy(file => file.Name))
        {
            writer.WriteLine($"--{file.Name} - {file.Length / 1024.0:F3}kb");
        }
    }

    Console.WriteLine($"Report written to: {reportPath}");
}

static string? NormalizeExtension(string? extension)
{
    if (string.IsNullOrWhiteSpace(extension))
    {
        return null;
    }

    return extension.StartsWith('.') ? extension : "." + extension;
}

static void RunHttpServer(int port)
{
    using TcpListener listener = new(IPAddress.Loopback, port);
    listener.Start();
    Console.WriteLine($"Server started at http://localhost:{port}/");

    while (true)
    {
        using TcpClient client = listener.AcceptTcpClient();
        using NetworkStream stream = client.GetStream();
        using StreamReader reader = new(stream);

        string requestLine = reader.ReadLine() ?? "";
        string path = requestLine.Split(' ', StringSplitOptions.RemoveEmptyEntries).ElementAtOrDefault(1) ?? "/";

        string body = path switch
        {
            "/" => "<h1>Welcome!</h1><p><a href=\"/info\">Info page</a></p>",
            "/info" => $"<h1>Info</h1><p>Current time: {DateTime.Now}</p><p>Logical processors: {Environment.ProcessorCount}</p>",
            _ => "<h1>404 Not Found</h1><p>The requested page does not exist.</p>"
        };

        int statusCode = path is "/" or "/info" ? 200 : 404;
        string statusText = statusCode == 200 ? "OK" : "Not Found";
        byte[] bodyBytes = Encoding.UTF8.GetBytes($"<!doctype html><html><head><meta charset=\"utf-8\"><title>{statusText}</title></head><body>{body}</body></html>");
        string header = $"HTTP/1.1 {statusCode} {statusText}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {bodyBytes.Length}\r\n\r\n";

        stream.Write(Encoding.ASCII.GetBytes(header));
        stream.Write(bodyBytes);
    }
}

static void RequireArgs(string[] args, int count)
{
    if (args.Length < count)
    {
        throw new ArgumentException("Missing arguments. Run without arguments to see help.");
    }
}

static void PrintHelp()
{
    Console.WriteLine("Usage:");
    Console.WriteLine("  dotnet run -- 101-1 <textFile>");
    Console.WriteLine("  dotnet run -- 101-2 <textFile> <outputFile>");
    Console.WriteLine("  dotnet run -- 101-3 <wordsFile> <textFile> <resultsFile>");
    Console.WriteLine("  dotnet run -- 102-1 <sourceFile> <destinationFile>");
    Console.WriteLine("  dotnet run -- 102-2-slice <sourceFile> <destinationDirectory> <parts>");
    Console.WriteLine("  dotnet run -- 102-2-assemble <destinationDirectory> <part1> <part2> ...");
    Console.WriteLine("  dotnet run -- 102-3-slice <sourceFile> <destinationDirectory> <parts>");
    Console.WriteLine("  dotnet run -- 102-3-assemble <destinationDirectory> <part1.gz> <part2.gz> ...");
    Console.WriteLine("  dotnet run -- 102-4 <directory> [extension]");
    Console.WriteLine("  dotnet run -- 102-5 <directory> [extension]");
    Console.WriteLine("  dotnet run -- 102-6 <port>");
}
