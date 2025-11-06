IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'derma_onboarding')
BEGIN
    CREATE DATABASE derma_onboarding;
END
GO