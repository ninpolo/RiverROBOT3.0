# Streams Exercises

Решения на задачите от:

- `10.1. Потоци-упражнения.pdf`
- `10.2. Стандартни потоци-упражнения.pdf`

## Стартиране

От тази папка:

```powershell
dotnet run -- <команда> [аргументи]
```

## 10.1. Потоци

```powershell
dotnet run -- 101-1 samples\text.txt
dotnet run -- 101-2 samples\text.txt output.txt
dotnet run -- 101-3 samples\words.txt samples\text.txt results.txt
```

## 10.2. Стандартни потоци

```powershell
dotnet run -- 102-1 source.bin copy.bin

dotnet run -- 102-2-slice source.bin parts 5
dotnet run -- 102-2-assemble assembled parts\part-001.bin parts\part-002.bin parts\part-003.bin parts\part-004.bin parts\part-005.bin

dotnet run -- 102-3-slice source.bin gzparts 5
dotnet run -- 102-3-assemble assembled-gz gzparts\part-001.gz gzparts\part-002.gz gzparts\part-003.gz gzparts\part-004.gz gzparts\part-005.gz

dotnet run -- 102-4 .
dotnet run -- 102-4 . .cs
dotnet run -- 102-5 .
dotnet run -- 102-5 . .cs

dotnet run -- 102-6 8080
```

Задачи `102-4` и `102-5` записват `report.txt` на Desktop-а на текущия потребител.

