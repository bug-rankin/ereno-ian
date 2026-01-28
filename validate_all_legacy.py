#!/usr/bin/env python3
"""
Comprehensive validation of legacy attacks and datasets.
Validates attack configurations and checks benign datasets exist and are accessible.
"""

import json
import os
from pathlib import Path
from typing import Dict, List, Any, Tuple
import sys
import csv

# Import the validation functions from the main validator
import validate_legacy_attacks as val

# Color codes for terminal output
Colors = val.Colors

def validate_arff_header(filepath: Path) -> Tuple[bool, List[str]]:
    """Validate ARFF file has proper header structure."""
    errors = []
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        if not lines:
            errors.append("File is empty")
            return False, errors
        
        # Check for @relation
        has_relation = False
        has_attributes = False
        has_data = False
        attribute_count = 0
        
        for line in lines[:50]:  # Check first 50 lines for header
            line = line.strip()
            if line.lower().startswith('@relation'):
                has_relation = True
            elif line.lower().startswith('@attribute'):
                has_attributes = True
                attribute_count += 1
            elif line.lower().startswith('@data'):
                has_data = True
                break
        
        if not has_relation:
            errors.append("Missing @relation declaration")
        if not has_attributes:
            errors.append("No @attribute declarations found")
        if not has_data:
            errors.append("Missing @data declaration")
        
        if attribute_count < 5:
            errors.append(f"Too few attributes found: {attribute_count} (expected at least 5)")
        
        return len(errors) == 0, errors
        
    except Exception as e:
        return False, [f"Error reading ARFF file: {str(e)}"]

def validate_csv_header(filepath: Path) -> Tuple[bool, List[str]]:
    """Validate CSV file has proper header structure."""
    errors = []
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.reader(f)
            header = next(reader, None)
            
            if not header:
                errors.append("CSV file has no header row")
                return False, errors
            
            if len(header) < 5:
                errors.append(f"Too few columns: {len(header)} (expected at least 5)")
            
            # Try to read first data row to verify it's not empty
            first_row = next(reader, None)
            if not first_row:
                errors.append("CSV file has no data rows")
        
        return len(errors) == 0, errors
        
    except Exception as e:
        return False, [f"Error reading CSV file: {str(e)}"]

def validate_dataset_file(filepath: Path) -> Tuple[bool, List[str]]:
    """Validate a dataset file based on its extension."""
    if not filepath.exists():
        return False, ["File does not exist"]
    
    if filepath.stat().st_size == 0:
        return False, ["File is empty (0 bytes)"]
    
    if filepath.suffix == '.arff':
        return validate_arff_header(filepath)
    elif filepath.suffix == '.csv':
        return validate_csv_header(filepath)
    else:
        return False, [f"Unknown file type: {filepath.suffix}"]

def scan_datasets(data_dir: Path) -> Dict[str, Tuple[bool, List[str]]]:
    """Scan and validate all dataset files in a directory."""
    results = {}
    
    if not data_dir.exists():
        return results
    
    # Find all ARFF and CSV files
    for filepath in sorted(data_dir.glob('*.arff')):
        valid, errors = validate_dataset_file(filepath)
        results[filepath.name] = (valid, errors, filepath.stat().st_size)
    
    for filepath in sorted(data_dir.glob('*.csv')):
        valid, errors = validate_dataset_file(filepath)
        results[filepath.name] = (valid, errors, filepath.stat().st_size)
    
    return results

def format_size(size_bytes: int) -> str:
    """Format file size in human-readable format."""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.1f} TB"

def main():
    """Main validation function."""
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}Legacy Attack & Dataset Validator{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    # ========================================================================
    # Part 1: Validate Attack Configurations
    # ========================================================================
    print(f"{Colors.BOLD}Part 1: Attack Configuration Validation{Colors.RESET}")
    print(f"{Colors.BOLD}{'-'*80}{Colors.RESET}\n")
    
    config_dir = Path('config/attacks')
    if not config_dir.exists():
        print(f"{Colors.RED}Error: config/attacks directory not found{Colors.RESET}")
        return 1
    
    attack_files = sorted(config_dir.glob('uc*.json'))
    
    if not attack_files:
        print(f"{Colors.YELLOW}Warning: No attack configuration files found{Colors.RESET}\n")
        attack_results = {}
    else:
        print(f"Found {len(attack_files)} attack configuration files\n")
        
        attack_results = {}
        for filepath in attack_files:
            print(f"  Validating: {filepath.name}", end=' ')
            valid, errors = val.validate_attack_file(filepath)
            attack_results[filepath.name] = (valid, errors)
            
            if valid:
                print(f"{Colors.GREEN}✓{Colors.RESET}")
            else:
                print(f"{Colors.RED}✗ ({len(errors)} errors){Colors.RESET}")
                for error in errors:
                    print(f"    - {error}")
    
    # ========================================================================
    # Part 2: Validate Benign Datasets
    # ========================================================================
    print(f"\n{Colors.BOLD}Part 2: Benign Dataset Validation{Colors.RESET}")
    print(f"{Colors.BOLD}{'-'*80}{Colors.RESET}\n")
    
    benign_dir = Path('target/benign_data')
    
    if not benign_dir.exists():
        print(f"{Colors.YELLOW}Warning: target/benign_data directory not found{Colors.RESET}")
        print(f"  This directory is created when benign datasets are generated.\n")
        dataset_results = {}
    else:
        print(f"Scanning: {benign_dir}\n")
        dataset_results = scan_datasets(benign_dir)
        
        if not dataset_results:
            print(f"{Colors.YELLOW}No dataset files found{Colors.RESET}\n")
        else:
            print(f"Found {len(dataset_results)} dataset files:\n")
            
            for filename in sorted(dataset_results.keys()):
                valid, errors, size = dataset_results[filename]
                size_str = format_size(size)
                
                if valid:
                    print(f"  {Colors.GREEN}✓{Colors.RESET} {filename:50s} [{size_str:>10s}]")
                else:
                    print(f"  {Colors.RED}✗{Colors.RESET} {filename:50s} [{size_str:>10s}]")
                    for error in errors:
                        print(f"      - {error}")
    
    # ========================================================================
    # Part 3: Summary
    # ========================================================================
    print(f"\n{Colors.BOLD}{'='*80}{Colors.RESET}")
    print(f"{Colors.BOLD}Summary{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    # Attack config summary
    if attack_results:
        valid_attacks = sum(1 for v, _ in attack_results.values() if v)
        invalid_attacks = len(attack_results) - valid_attacks
        total_attack_errors = sum(len(errs) for _, errs in attack_results.values())
        
        print(f"{Colors.BOLD}Attack Configurations:{Colors.RESET}")
        print(f"  Total: {len(attack_results)}")
        print(f"  {Colors.GREEN}Valid: {valid_attacks}{Colors.RESET}")
        if invalid_attacks > 0:
            print(f"  {Colors.RED}Invalid: {invalid_attacks} ({total_attack_errors} errors){Colors.RESET}")
    else:
        print(f"{Colors.BOLD}Attack Configurations:{Colors.RESET} None found")
    
    # Dataset summary
    if dataset_results:
        valid_datasets = sum(1 for v, _, _ in dataset_results.values() if v)
        invalid_datasets = len(dataset_results) - valid_datasets
        total_dataset_errors = sum(len(errs) for _, errs, _ in dataset_results.values())
        total_size = sum(size for _, _, size in dataset_results.values())
        
        print(f"\n{Colors.BOLD}Datasets:{Colors.RESET}")
        print(f"  Total: {len(dataset_results)}")
        print(f"  {Colors.GREEN}Valid: {valid_datasets}{Colors.RESET}")
        if invalid_datasets > 0:
            print(f"  {Colors.RED}Invalid: {invalid_datasets} ({total_dataset_errors} errors){Colors.RESET}")
        print(f"  Total size: {format_size(total_size)}")
    else:
        print(f"\n{Colors.BOLD}Datasets:{Colors.RESET} None found (generate with benign data actions)")
    
    # List validated attack types
    if attack_results:
        print(f"\n{Colors.BOLD}Attack Types:{Colors.RESET}")
        for filename in sorted(attack_results.keys()):
            valid, _ = attack_results[filename]
            status = f"{Colors.GREEN}✓{Colors.RESET}" if valid else f"{Colors.RED}✗{Colors.RESET}"
            attack_name = filename.replace('uc', 'UC').replace('.json', '').replace('_', ' ').title()
            print(f"  {status} {attack_name}")
    
    print(f"\n{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    # Determine exit code
    has_errors = False
    if attack_results:
        has_errors = has_errors or any(not v for v, _ in attack_results.values())
    if dataset_results:
        has_errors = has_errors or any(not v for v, _, _ in dataset_results.values())
    
    return 1 if has_errors else 0

if __name__ == '__main__':
    sys.exit(main())
