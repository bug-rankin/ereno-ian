#!/usr/bin/env python3
"""
Test legacy attack configurations by generating small test datasets.
This validates that attack configs work with the Java attack generation system.
"""

import json
import subprocess
import tempfile
from pathlib import Path
import sys
import time
from typing import Dict, List, Tuple

# Color codes for terminal output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

ATTACKS = [
    'uc01_random_replay',
    'uc02_inverse_replay',
    'uc03_masquerade_fault',
    'uc04_masquerade_normal',
    'uc05_injection',
    'uc06_high_stnum_injection',
    'uc07_flooding',
    'uc08_grayhole'
]

def create_test_action_config(attack_name: str, output_dir: Path) -> Path:
    """Create a temporary action config for testing an attack."""
    
    config = {
        "action": "create_attack_dataset",
        "config": {
            "input": {
                "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
                "verifyBenignData": True,
                "useLegacy": True
            },
            "attackSegments": [
                {
                    "attackConfigPath": f"config/attacks/{attack_name}.json",
                    "enabled": True,
                    "randomSeed": 42,
                    "count": 10
                }
            ],
            "output": {
                "directory": str(output_dir),
                "filename": f"test_{attack_name}.arff",
                "format": "arff",
                "enableTracking": False
            }
        }
    }
    
    temp_config = output_dir / f"action_{attack_name}.json"
    with open(temp_config, 'w') as f:
        json.dump(config, f, indent=2)
    
    return temp_config

def test_attack_generation(attack_name: str, timeout: int = 30) -> Tuple[bool, str]:
    """Test generating a dataset with a specific attack."""
    
    try:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            
            # Create action config
            action_config = create_test_action_config(attack_name, temp_path)
            
            # Check if benign data exists
            benign_path = Path("target/benign_data/42_5%fault_benign_data.arff")
            if not benign_path.exists():
                return False, "Benign dataset not found (run benign data generation first)"
            
            # Run Java action
            jar_path = "target/ERENO-1.0-SNAPSHOT-uber.jar"
            if not Path(jar_path).exists():
                return False, f"JAR file not found: {jar_path}"
            
            cmd = ["java", "-jar", jar_path, str(action_config)]
            
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=timeout
            )
            
            # Check if output file was created
            output_file = temp_path / f"test_{attack_name}.arff"
            
            if result.returncode != 0:
                # Try to extract error message
                error_lines = result.stderr.split('\n')
                for line in error_lines:
                    if 'Exception' in line or 'Error' in line:
                        return False, line.strip()
                return False, f"Java process failed with exit code {result.returncode}"
            
            if not output_file.exists():
                return False, "Output file was not created"
            
            if output_file.stat().st_size == 0:
                return False, "Output file is empty"
            
            return True, f"Generated {output_file.stat().st_size} bytes"
            
    except subprocess.TimeoutExpired:
        return False, f"Timed out after {timeout} seconds"
    except Exception as e:
        return False, f"Exception: {str(e)}"

def main():
    """Main test function."""
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}Legacy Attack Generation Test{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    print(f"This script tests each attack by generating a small test dataset.")
    print(f"Note: This requires benign data to exist and can take several minutes.\n")
    
    # Check prerequisites
    print(f"Checking prerequisites...")
    
    jar_path = Path("target/ERENO-1.0-SNAPSHOT-uber.jar")
    if not jar_path.exists():
        print(f"{Colors.RED}✗ JAR file not found: {jar_path}{Colors.RESET}")
        print(f"  Run: mvn clean package")
        return 1
    print(f"{Colors.GREEN}✓ JAR file found{Colors.RESET}")
    
    benign_path = Path("target/benign_data/42_5%fault_benign_data.arff")
    if not benign_path.exists():
        print(f"{Colors.RED}✗ Benign dataset not found{Colors.RESET}")
        print(f"  Generate benign data first using action_create_benign.json")
        return 1
    print(f"{Colors.GREEN}✓ Benign dataset found{Colors.RESET}")
    
    print()
    
    # Test each attack
    results = {}
    
    print(f"{Colors.BOLD}Testing attack configurations:{Colors.RESET}\n")
    
    for i, attack in enumerate(ATTACKS, 1):
        print(f"[{i}/{len(ATTACKS)}] Testing {attack:35s} ", end='', flush=True)
        
        start_time = time.time()
        success, message = test_attack_generation(attack)
        elapsed = time.time() - start_time
        
        results[attack] = (success, message, elapsed)
        
        if success:
            print(f"{Colors.GREEN}✓{Colors.RESET} ({elapsed:.1f}s) - {message}")
        else:
            print(f"{Colors.RED}✗{Colors.RESET} ({elapsed:.1f}s)")
            print(f"      {message}")
    
    # Summary
    print(f"\n{Colors.BOLD}{'='*80}{Colors.RESET}")
    print(f"{Colors.BOLD}Summary{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    successful = sum(1 for s, _, _ in results.values() if s)
    failed = len(results) - successful
    total_time = sum(e for _, _, e in results.values())
    
    print(f"Total tests: {len(results)}")
    print(f"{Colors.GREEN}Successful: {successful}{Colors.RESET}")
    if failed > 0:
        print(f"{Colors.RED}Failed: {failed}{Colors.RESET}")
    print(f"Total time: {total_time:.1f}s")
    
    if failed > 0:
        print(f"\n{Colors.BOLD}Failed attacks:{Colors.RESET}")
        for attack, (success, message, _) in results.items():
            if not success:
                print(f"  {Colors.RED}✗{Colors.RESET} {attack}: {message}")
    
    print(f"\n{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    return 1 if failed > 0 else 0

if __name__ == '__main__':
    sys.exit(main())
