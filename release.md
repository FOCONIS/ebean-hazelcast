# FOCONIS Release

Mit folgendem Befehl kann das Projekt released werden:

```bash
mvn release:prepare release:perform -Darguments="-DskipTests"
```

Wenn das nicht funktioniert, den richtigen Release Commit auschecken und

```bash
mvn clean source:jar deploy -DskipTests
```

ausführen.
