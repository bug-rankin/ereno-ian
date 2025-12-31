import subprocess
import sys

commands = [[".\mvnw.cmd", "clean", "package", "-DskipTests"],
            ["java", "-jar", "target\ERENO-1.0-SNAPSHOT-uber.jar", "config\pipeline_complete.json"]]


def main():
    try: 
        print("Running commands")
        result = subprocess.run(commands[0], capture_output=True, text=True, check=True)
        print("cmd script done \n")
        for i in range(5):
            result = subprocess.run(commands[1], capture_output=True, text=True, check=True)
        print("Evaluation iterations finished")
    except subprocess.CalledProcessError as e:
        print("Command failed with error code {e.returncode}")
        print(e.stderr)
        sys.exit(e.returncode)
    except FileNotFoundError:
        print("Enable to find file")
        sys.exit(1)

if __name__ == "__main__":
    main()