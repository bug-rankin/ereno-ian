import subprocess
import sys
import csv
import os
import json

commands = [["mvnw.cmd", "clean", "package", "-DskipTests"], # array of commands, can either run the full pipeline or run each step individually
            ["java", "-jar", "target/ERENO-1.0-SNAPSHOT-uber.jar", "config/pipelines/pipeline_complete.json"],
            ["java", "-jar", "target/ERENO-1.0-SNAPSHOT-uber.jar", "config/actions/action_create_benign.json"],
            ["java", "-jar", "target/ERENO-1.0-SNAPSHOT-uber.jar", "config/actions/action_create_attack_dataset.json"],
            ["java", "-jar", "target/ERENO-1.0-SNAPSHOT-uber.jar", "config/actions/action_create_test_dataset.json"],
            ["java", "-jar", "target/ERENO-1.0-SNAPSHOT-uber.jar", "config/actions/action_evaluate.json"]] 

csv_path = "target/evaluation.csv"

pipeline_path = "config/pipelines/pipeline_complete.json"

header = ["Model", "Accuracy", "Precision", "Recall", "F1 Score", "True Pos", "True Neg", "False Pos", "False Neg", "Eval Time"] # csv file header

random_seeds = [42, 29, 27, 25, 24, 30, 32, 34, 33, 35, 37, 39, 40, 41, 36, 38, 23, 21, 22, 20] # list of random seeds to run tests on. 42 run twice since it doesn't write parameter values on first run for some reason

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

        # result = subprocess.run(commands[2], capture_output=True, text=True, check=True) # creates benign dataset

        for seed in random_seeds:

            with open(pipeline_path, 'r') as json_file: # changes the random seed value in the pipeline_complete.json file
                pipeline_data = json.load(json_file)
            pipeline_data['commonConfig']['randomSeed'] = seed
            with open(pipeline_path, 'w') as json_file:
                json.dump(pipeline_data, json_file, indent=2)

            result = subprocess.run(commands[1], capture_output=True, text=True, check=True) # runs the entire pipeline (really do not want to do this)
            
            # result = subprocess.run(commands[3], capture_output=True, text=True, check=True) # creates training dataset
            # result = subprocess.run(commands[4], capture_output=True, text=True, check=True) # creates test dataset
            # result = subprocess.run(commands[5], capture_output=True, text=True, check=True) # runs evaluation


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