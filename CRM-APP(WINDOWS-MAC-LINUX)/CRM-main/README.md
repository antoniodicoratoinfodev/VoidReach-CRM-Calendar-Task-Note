# CRM Management System (JavaFX)

Questo è un gestionale CRM sviluppato in Java utilizzando JavaFX, progettato per avere un'interfaccia moderna e pulita.

## Caratteristiche
- **Sidebar di Navigazione**: Accesso rapido a Dashboard, Contatti, Calendario, Lead, ecc.
- **Gestione Contatti**: Tabella dettagliata con ricerca, paginazione e tag colorati.
- **Pannello Laterale**: Mini calendario e lista delle prossime attività/task.
- **Stile Moderno**: Design basato su CSS con font chiari e colori professionali.

## Requisiti
- Java JDK 17 o superiore(io uso il 25)
- Maven

## Come Eseguire
1. Assicurati di avere Maven installato.
2. Dalla cartella principale del progetto, esegui:
   ```bash
   mvn clean javafx:run
   ```
   Oppure usa lo script fornito:
   ```bash
   ./run.sh
   ```
   Io eseguo da IntelliJ.
## Struttura del Progetto
- `src/main/java`: Contiene il codice sorgente Java (App, Controller, Model).
- `src/main/resources`: Contiene i file FXML per il layout e il file CSS per lo stile.
