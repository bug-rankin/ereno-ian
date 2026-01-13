import subprocess
import sys
import csv
import os
import json

commands = [[".\mvnw.cmd", "clean", "package", "-DskipTests"],
            ["java", "-jar", "target\ERENO-1.0-SNAPSHOT-uber.jar", "config\pipelines\pipeline_complete.json"]] # potentially change this to run pipeline_loop_random_seeds.json

csv_path = "target/evaluation.csv"

pipeline_path = "config/pipelines/pipeline_complete.json"

header = ["Model", "Accuracy", "Precision", "Recall", "F1 Score", "True Pos", "True Neg", "False Pos", "False Neg", "Eval Time"]

random_seeds = [42, 42, 29, 27, 25, 24, 30, 32, 34, 33, 35, 37, 39, 40, 41, 36, 38, 23, 21, 22, 20]

def main():
    try: 
        print("Running commands")
        result = subprocess.run(commands[0], capture_output=True, text=True, check=True)
        print("cmd script done \n")
        print("Generating CSV file with evaluation metrics")

        if os.path.exists(csv_path): # deletes pre-existing csv file
            os.remove(csv_path)

        with open(csv_path, 'x', newline='') as csvfile: # creates a new csv file
            writer = csv.writer(csvfile)
            writer.writerow(header)
            
        print("CSV file is created")
        for seed in random_seeds:

            with open(pipeline_path, 'r') as json_file: # changes the random seed value in the pipeline_complete.json file
                pipeline_data = json.load(json_file)
            pipeline_data['commonConfig']['randomSeed'] = seed
            with open(pipeline_path, 'w') as json_file:
                json.dump(pipeline_data, json_file, indent=2)
            
            result = subprocess.run(commands[1], capture_output=True, text=True, check=True) # runs the pipeline

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