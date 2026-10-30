-- Ветеринарна клиника „Дария“ — Microsoft SQL Server (T-SQL)
-- Изпълнете скрипта в избраната база данни.

CREATE TABLE dbo.Owners (
    OwnerID       INT IDENTITY(1,1) CONSTRAINT PK_Owners PRIMARY KEY,
    FirstName     NVARCHAR(80) NOT NULL,
    LastName      NVARCHAR(80) NOT NULL,
    Email         NVARCHAR(254) NOT NULL CONSTRAINT UQ_Owners_Email UNIQUE,
    Phone         NVARCHAR(32) NULL,
    CreatedAt     DATETIME2(0) NOT NULL CONSTRAINT DF_Owners_CreatedAt DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.Species (
    SpeciesID     INT IDENTITY(1,1) CONSTRAINT PK_Species PRIMARY KEY,
    Name          NVARCHAR(80) NOT NULL CONSTRAINT UQ_Species_Name UNIQUE
);

CREATE TABLE dbo.Pets (
    PetID         INT IDENTITY(1,1) CONSTRAINT PK_Pets PRIMARY KEY,
    OwnerID       INT NOT NULL,
    SpeciesID     INT NOT NULL,
    Name          NVARCHAR(100) NOT NULL,
    Breed         NVARCHAR(100) NULL,
    DateOfBirth   DATE NULL,
    Sex           NVARCHAR(20) NULL,
    MicrochipNo   NVARCHAR(50) NULL CONSTRAINT UQ_Pets_Microchip UNIQUE,
    Notes         NVARCHAR(MAX) NULL,
    CreatedAt     DATETIME2(0) NOT NULL CONSTRAINT DF_Pets_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_Pets_Owners FOREIGN KEY (OwnerID) REFERENCES dbo.Owners(OwnerID),
    CONSTRAINT FK_Pets_Species FOREIGN KEY (SpeciesID) REFERENCES dbo.Species(SpeciesID)
);

-- Users са потребителските акаунти на ветеринарните лекари.
-- Съхранявайте само сигурен хеш на паролата, никога парола в чист текст.
CREATE TABLE dbo.Vets (
    VetID          INT IDENTITY(1,1) CONSTRAINT PK_Vets PRIMARY KEY,
    FirstName      NVARCHAR(80) NOT NULL,
    LastName       NVARCHAR(80) NOT NULL,
    Email          NVARCHAR(254) NOT NULL CONSTRAINT UQ_Vets_Email UNIQUE,
    PasswordHash   NVARCHAR(500) NOT NULL,
    IsActive       BIT NOT NULL CONSTRAINT DF_Vets_IsActive DEFAULT 1,
    CreatedAt      DATETIME2(0) NOT NULL CONSTRAINT DF_Vets_CreatedAt DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.Treatments (
    TreatmentID       INT IDENTITY(1,1) CONSTRAINT PK_Treatments PRIMARY KEY,
    Name              NVARCHAR(150) NOT NULL CONSTRAINT UQ_Treatments_Name UNIQUE,
    Description       NVARCHAR(MAX) NULL,
    ProcedureType     NVARCHAR(30) NOT NULL CONSTRAINT DF_Treatments_Type DEFAULT N'procedure',
    -- При ваксините vaccine_code идентифицира препарата/вида доза.
    VaccineCode       NVARCHAR(80) NULL,
    MinIntervalDays   INT NULL,
    DefaultPrice      DECIMAL(10,2) NOT NULL,
    IsActive          BIT NOT NULL CONSTRAINT DF_Treatments_IsActive DEFAULT 1,
    CONSTRAINT CK_Treatments_Type CHECK (ProcedureType IN (N'procedure', N'vaccine', N'other')),
    CONSTRAINT CK_Treatments_Price CHECK (DefaultPrice >= 0),
    CONSTRAINT CK_Treatments_Vaccine CHECK (
        (ProcedureType = N'vaccine' AND VaccineCode IS NOT NULL
            AND MinIntervalDays IS NOT NULL AND MinIntervalDays > 0)
        OR
        (ProcedureType <> N'vaccine' AND VaccineCode IS NULL AND MinIntervalDays IS NULL)
    ),
    CONSTRAINT UQ_Treatments_VaccineCode UNIQUE (VaccineCode)
);

CREATE TABLE dbo.Visits (
    VisitID       INT IDENTITY(1,1) CONSTRAINT PK_Visits PRIMARY KEY,
    PetID         INT NOT NULL,
    VetID         INT NOT NULL,
    StartsAt      DATETIME2(0) NOT NULL,
    EndsAt        DATETIME2(0) NOT NULL,
    Status        NVARCHAR(20) NOT NULL CONSTRAINT DF_Visits_Status DEFAULT N'scheduled',
    Reason        NVARCHAR(MAX) NULL,
    Diagnosis     NVARCHAR(MAX) NULL,
    ClinicalNotes NVARCHAR(MAX) NULL,
    CreatedAt     DATETIME2(0) NOT NULL CONSTRAINT DF_Visits_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_Visits_Pets FOREIGN KEY (PetID) REFERENCES dbo.Pets(PetID),
    CONSTRAINT FK_Visits_Vets FOREIGN KEY (VetID) REFERENCES dbo.Vets(VetID),
    CONSTRAINT CK_Visits_Times CHECK (EndsAt > StartsAt),
    CONSTRAINT CK_Visits_Status CHECK (Status IN (N'scheduled', N'completed', N'cancelled', N'no_show'))
);

CREATE TABLE dbo.VisitTreatments (
    VisitTreatmentID INT IDENTITY(1,1) CONSTRAINT PK_VisitTreatments PRIMARY KEY,
    VisitID          INT NOT NULL,
    TreatmentID      INT NOT NULL,
    Quantity         DECIMAL(8,2) NOT NULL CONSTRAINT DF_VisitTreatments_Quantity DEFAULT 1,
    UnitPrice        DECIMAL(10,2) NOT NULL,
    Notes            NVARCHAR(MAX) NULL,
    AdministeredAt   DATETIME2(0) NULL,
    CONSTRAINT FK_VisitTreatments_Visits FOREIGN KEY (VisitID) REFERENCES dbo.Visits(VisitID) ON DELETE CASCADE,
    CONSTRAINT FK_VisitTreatments_Treatments FOREIGN KEY (TreatmentID) REFERENCES dbo.Treatments(TreatmentID),
    CONSTRAINT CK_VisitTreatments_Quantity CHECK (Quantity > 0),
    CONSTRAINT CK_VisitTreatments_UnitPrice CHECK (UnitPrice >= 0),
    CONSTRAINT UQ_VisitTreatments UNIQUE (VisitID, TreatmentID)
);

CREATE INDEX IX_Pets_OwnerID ON dbo.Pets(OwnerID);
CREATE INDEX IX_Visits_Pet_StartsAt ON dbo.Visits(PetID, StartsAt DESC);
CREATE INDEX IX_Visits_Vet_StartsAt ON dbo.Visits(VetID, StartsAt);
CREATE INDEX IX_VisitTreatments_TreatmentID ON dbo.VisitTreatments(TreatmentID);

GO

-- SQL Server няма exclusion constraint за времеви интервали.
-- Тригерът блокира застъпващи се посещения за един ветеринар, включително
-- при много редове в INSERT/UPDATE. Съседни часове са разрешени.
CREATE TRIGGER dbo.TR_Visits_NoVetOverlap
ON dbo.Visits
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS i
        JOIN dbo.Visits AS v WITH (UPDLOCK, HOLDLOCK)
          ON v.VetID = i.VetID
         AND v.VisitID <> i.VisitID
         AND i.StartsAt < v.EndsAt
         AND v.StartsAt < i.EndsAt
        WHERE i.Status <> N'cancelled'
          AND v.Status <> N'cancelled'
    )
    BEGIN
        THROW 50001, N'Ветеринарят вече има посещение в този часови интервал.', 1;
    END;
END;

GO

-- Ваксинацията се счита за поставена, когато посещението е завършено
-- и AdministeredAt е попълнено. Проверява се последната доза от същия
-- VaccineCode за същия любимец. Тригерът обработва и много реда наведнъж.
CREATE TRIGGER dbo.TR_VisitTreatments_VaccineInterval
ON dbo.VisitTreatments
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS i
        JOIN dbo.Visits AS currentVisit ON currentVisit.VisitID = i.VisitID
        JOIN dbo.Treatments AS currentTreatment ON currentTreatment.TreatmentID = i.TreatmentID
        JOIN dbo.VisitTreatments AS previousDose ON previousDose.VisitTreatmentID <> i.VisitTreatmentID
        JOIN dbo.Visits AS previousVisit ON previousVisit.VisitID = previousDose.VisitID
        JOIN dbo.Treatments AS previousTreatment ON previousTreatment.TreatmentID = previousDose.TreatmentID
        WHERE currentTreatment.ProcedureType = N'vaccine'
          AND currentVisit.Status = N'completed'
          AND i.AdministeredAt IS NOT NULL
          AND previousVisit.PetID = currentVisit.PetID
          AND previousVisit.Status = N'completed'
          AND previousDose.AdministeredAt IS NOT NULL
          AND previousTreatment.VaccineCode = currentTreatment.VaccineCode
          AND i.AdministeredAt < DATEADD(DAY, currentTreatment.MinIntervalDays, previousDose.AdministeredAt)
    )
    BEGIN
        THROW 50002, N'Минималният интервал между дозите на тази ваксина не е изтекъл.', 1;
    END;
END;

GO

-- Предстоящи ваксинации: последната поставена доза плюс минималния интервал.
CREATE VIEW dbo.UpcomingVaccinations
AS
    WITH LastDoses AS (
        SELECT v.PetID,
               t.TreatmentID,
               t.Name AS VaccineName,
               t.VaccineCode,
               t.MinIntervalDays,
               MAX(vt.AdministeredAt) AS LastAdministeredAt
        FROM dbo.VisitTreatments AS vt
        JOIN dbo.Visits AS v ON v.VisitID = vt.VisitID
        JOIN dbo.Treatments AS t ON t.TreatmentID = vt.TreatmentID
        WHERE t.ProcedureType = N'vaccine'
          AND v.Status = N'completed'
          AND vt.AdministeredAt IS NOT NULL
        GROUP BY v.PetID, t.TreatmentID, t.Name, t.VaccineCode, t.MinIntervalDays
    )
    SELECT p.PetID,
           p.Name AS PetName,
           o.OwnerID,
           o.FirstName AS OwnerFirstName,
           o.LastName AS OwnerLastName,
           ld.TreatmentID,
           ld.VaccineName,
           ld.VaccineCode,
           ld.LastAdministeredAt,
           CONVERT(DATE, DATEADD(DAY, ld.MinIntervalDays, ld.LastAdministeredAt)) AS DueDate
    FROM LastDoses AS ld
    JOIN dbo.Pets AS p ON p.PetID = ld.PetID
    JOIN dbo.Owners AS o ON o.OwnerID = p.OwnerID
    WHERE CONVERT(DATE, DATEADD(DAY, ld.MinIntervalDays, ld.LastAdministeredAt)) >= CONVERT(DATE, GETDATE());

GO

-- Приходи по процедури по календарен месец за завършени посещения.
CREATE VIEW dbo.ProcedureRevenueByMonth
AS
    SELECT DATEFROMPARTS(YEAR(v.StartsAt), MONTH(v.StartsAt), 1) AS [Month],
           t.TreatmentID,
           t.Name AS TreatmentName,
           CAST(SUM(vt.Quantity * vt.UnitPrice) AS DECIMAL(12,2)) AS Revenue,
           CAST(SUM(vt.Quantity) AS DECIMAL(12,2)) AS QuantityPerformed
    FROM dbo.VisitTreatments AS vt
    JOIN dbo.Visits AS v ON v.VisitID = vt.VisitID
    JOIN dbo.Treatments AS t ON t.TreatmentID = vt.TreatmentID
    WHERE v.Status = N'completed'
    GROUP BY DATEFROMPARTS(YEAR(v.StartsAt), MONTH(v.StartsAt), 1), t.TreatmentID, t.Name;

GO

-- История на любимеца в уеб приложението.
-- @AuthenticatedOwnerID трябва да идва от проверената потребителска сесия,
-- а не от произволна стойност, подадена от браузъра.
-- DECLARE @AuthenticatedOwnerID INT = ...;
-- DECLARE @PetID INT = ...;
-- SELECT v.VisitID, v.StartsAt, v.Status, v.Diagnosis, v.ClinicalNotes,
--        t.Name AS Treatment, vt.Quantity, vt.UnitPrice, vt.AdministeredAt
-- FROM dbo.Pets AS p
-- JOIN dbo.Visits AS v ON v.PetID = p.PetID
-- LEFT JOIN dbo.VisitTreatments AS vt ON vt.VisitID = v.VisitID
-- LEFT JOIN dbo.Treatments AS t ON t.TreatmentID = vt.TreatmentID
-- WHERE p.OwnerID = @AuthenticatedOwnerID AND p.PetID = @PetID
-- ORDER BY v.StartsAt DESC;

-- Примерни справки:
-- SELECT * FROM dbo.UpcomingVaccinations ORDER BY DueDate;
-- SELECT * FROM dbo.ProcedureRevenueByMonth ORDER BY [Month] DESC, TreatmentName;

-- Примерни видове животни:
-- INSERT INTO dbo.Species (Name) VALUES (N'Куче'), (N'Котка'), (N'Заек');
