.\mvnw.cmd clean package -DskipTests

for ($i = 0; $i -lt 100; $i++) {
    java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\pipeline_complete.json
}