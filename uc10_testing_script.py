import subprocess
import sys
import csv

commands = [[".\mvnw.cmd", "clean", "package", "-DskipTests"],
            ["java", "-jar", "target\ERENO-1.0-SNAPSHOT-uber.jar", "config\pipelines\pipeline_complete.json"]]

path = "target/evaluation.csv"

header = ["Model", "Accuracy", "Precision", "Recall", "F1 Score", "True Pos", "True Neg", "False Pos", "False Neg", "Eval Time"]

def main():
    try: 
        print("Running commands")
        result = subprocess.run(commands[0], capture_output=True, text=True, check=True)
        print("cmd script done \n")
        print("Generating CSV file with evaluation metrics")
        with open(path, 'x', newline='') as csvfile:
            writer = csv.writer(csvfile)
            writer.writerow(header)
        print("CSV file is created")
        for i in range(5):
            result = subprocess.run(commands[1], capture_output=True, text=True, check=True)
        print("Evaluation iterations finished")
    except subprocess.CalledProcessError as e:
        print("Command failed with error code {e.returncode}")
        print(e.stderr)
        sys.exit(e.returncode)
    except FileNotFoundError:
        print("Unable to find file")
        sys.exit(1)

if __name__ == "__main__":
    main()