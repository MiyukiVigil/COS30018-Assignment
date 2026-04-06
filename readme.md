# Automated Car Negotiation System

## Setup Instructions

### Prerequisites
- Java 17

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/MiyukiVigil/COS30018-Assignment.git
   ```
2. Navigate to the project directory:
   ```bash
   cd COS30018-Assignment
   ```
3. Install the required dependencies:
   ```bash
   mvn install
   ```
4. If you encountered any problems with Jade, locally install jade and place it into the root folder of the project and run this command:
   For Linux/MacOS:
   ```bash
   mvn install:install-file -Dfile=jade.jar -DgroupId=com.tilab.jade -DartifactId=jade -Dversion=4.6.0 -Dpackaging=jar
   ```
   For Windows:
   ```bash
   mvn install:install-file "-Dfile=jade.jar" "-DgroupId=com.tilab.jade" "-DartifactId=jade" "-Dversion=4.6.0" "-Dpackaging=jar"
   ```

## Dependencies
- **JADE** (Java Agent DEvelopment Framework) – agent platform
- **JavaFX** – GUI toolkit
- **Maven** – project build and dependency management

## Usage

### Running the Application
To start the negotiation system, run:
```bash
mvn javafx:run
```
