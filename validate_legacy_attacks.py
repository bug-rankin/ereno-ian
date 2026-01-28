#!/usr/bin/env python3
"""
Validate all legacy attack configurations.
Iterates through attack config files and validates their structure against expected schemas.
"""

import json
import os
from pathlib import Path
from typing import Dict, List, Any, Tuple
import sys

# Color codes for terminal output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def validate_range(data: Dict, key: str, parent: str = "") -> Tuple[bool, List[str]]:
    """Validate a range object has min and max fields."""
    errors = []
    full_key = f"{parent}.{key}" if parent else key
    
    if key not in data:
        return True, []  # Optional field
    
    range_obj = data[key]
    if not isinstance(range_obj, dict):
        errors.append(f"{full_key} should be an object")
        return False, errors
    
    if 'min' not in range_obj or 'max' not in range_obj:
        errors.append(f"{full_key} must have 'min' and 'max' fields")
        return False, errors
    
    min_val = range_obj['min']
    max_val = range_obj['max']
    
    if not isinstance(min_val, (int, float)) or not isinstance(max_val, (int, float)):
        errors.append(f"{full_key} min/max must be numeric")
        return False, errors
    
    if min_val >= max_val:
        errors.append(f"{full_key} min ({min_val}) must be less than max ({max_val})")
        return False, errors
    
    return True, []

def validate_probability(value: Any, field_name: str) -> Tuple[bool, List[str]]:
    """Validate a probability value is between 0 and 1."""
    errors = []
    
    if not isinstance(value, (int, float)):
        errors.append(f"{field_name} must be numeric")
        return False, errors
    
    if value < 0 or value > 1:
        errors.append(f"{field_name} must be between 0 and 1 (got {value})")
        return False, errors
    
    return True, []

def validate_random_replay(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC01 - Random Replay attack configuration."""
    errors = []
    
    # Check required fields
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'random_replay':
        errors.append(f"attackType should be 'random_replay', got '{config['attackType']}'")
    
    # Validate count
    if 'count' in config:
        count = config['count']
        if not isinstance(count, dict) or 'lambda' not in count:
            errors.append("count must have 'lambda' field")
        elif not isinstance(count['lambda'], (int, float)) or count['lambda'] <= 0:
            errors.append(f"count.lambda must be positive (got {count.get('lambda')})")
    
    # Validate ranges
    valid, errs = validate_range(config, 'windowS')
    errors.extend(errs)
    
    valid, errs = validate_range(config, 'delayMs')
    errors.extend(errs)
    
    # Validate burst configuration
    if 'burst' in config:
        burst = config['burst']
        if not isinstance(burst, dict):
            errors.append("burst must be an object")
        else:
            if 'prob' in burst:
                valid, errs = validate_probability(burst['prob'], 'burst.prob')
                errors.extend(errs)
            
            # Check if burst has min/max directly (not as a range object)
            if 'min' in burst and 'max' in burst:
                min_val = burst['min']
                max_val = burst['max']
                if not isinstance(min_val, (int, float)) or not isinstance(max_val, (int, float)):
                    errors.append("burst.min/max must be numeric")
                elif min_val >= max_val:
                    errors.append(f"burst.min ({min_val}) must be less than burst.max ({max_val})")
            
            valid, errs = validate_range(burst, 'gapMs', 'burst')
            errors.extend(errs)
    
    # Validate reorderProb
    if 'reorderProb' in config:
        valid, errs = validate_probability(config['reorderProb'], 'reorderProb')
        errors.extend(errs)
    
    # Validate ttlOverride
    if 'ttlOverride' in config:
        ttl = config['ttlOverride']
        if not isinstance(ttl, dict):
            errors.append("ttlOverride must be an object")
        else:
            if 'valuesMs' in ttl:
                if not isinstance(ttl['valuesMs'], list):
                    errors.append("ttlOverride.valuesMs must be an array")
            if 'prob' in ttl:
                valid, errs = validate_probability(ttl['prob'], 'ttlOverride.prob')
                errors.extend(errs)
    
    # Validate ethSpoof
    if 'ethSpoof' in config:
        eth = config['ethSpoof']
        if not isinstance(eth, dict):
            errors.append("ethSpoof must be an object")
        else:
            if 'srcProb' in eth:
                valid, errs = validate_probability(eth['srcProb'], 'ethSpoof.srcProb')
                errors.extend(errs)
            if 'dstProb' in eth:
                valid, errs = validate_probability(eth['dstProb'], 'ethSpoof.dstProb')
                errors.extend(errs)
    
    return len(errors) == 0, errors

def validate_inverse_replay(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC02 - Inverse Replay attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'inverse_replay':
        errors.append(f"attackType should be 'inverse_replay', got '{config['attackType']}'")
    
    # Validate count
    if 'count' in config:
        count = config['count']
        if not isinstance(count, dict) or 'lambda' not in count:
            errors.append("count must have 'lambda' field")
        elif not isinstance(count['lambda'], (int, float)) or count['lambda'] <= 0:
            errors.append(f"count.lambda must be positive (got {count.get('lambda')})")
    
    # Validate blockLen
    valid, errs = validate_range(config, 'blockLen')
    errors.extend(errs)
    
    # Validate delayMs
    valid, errs = validate_range(config, 'delayMs')
    errors.extend(errs)
    
    # Validate burst configuration
    if 'burst' in config:
        burst = config['burst']
        if not isinstance(burst, dict):
            errors.append("burst must be an object")
        else:
            if 'prob' in burst:
                valid, errs = validate_probability(burst['prob'], 'burst.prob')
                errors.extend(errs)
            
            # Check if burst has min/max directly (not as a range object)
            if 'min' in burst and 'max' in burst:
                min_val = burst['min']
                max_val = burst['max']
                if not isinstance(min_val, (int, float)) or not isinstance(max_val, (int, float)):
                    errors.append("burst.min/max must be numeric")
                elif min_val >= max_val:
                    errors.append(f"burst.min ({min_val}) must be less than burst.max ({max_val})")
            
            valid, errs = validate_range(burst, 'gapMs', 'burst')
            errors.extend(errs)
    
    # Validate ttlOverride
    if 'ttlOverride' in config:
        ttl = config['ttlOverride']
        if not isinstance(ttl, dict):
            errors.append("ttlOverride must be an object")
        else:
            if 'valuesMs' in ttl:
                if not isinstance(ttl['valuesMs'], list):
                    errors.append("ttlOverride.valuesMs must be an array")
            if 'prob' in ttl:
                valid, errs = validate_probability(ttl['prob'], 'ttlOverride.prob')
                errors.extend(errs)
    
    return len(errors) == 0, errors

def validate_masquerade_fault(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC03 - Masquerade Fault attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'masquerade_fault':
        errors.append(f"attackType should be 'masquerade_fault', got '{config['attackType']}'")
    
    # Validate fault configuration
    if 'fault' in config:
        fault = config['fault']
        if not isinstance(fault, dict):
            errors.append("fault must be an object")
        else:
            if 'prob' in fault:
                valid, errs = validate_probability(fault['prob'], 'fault.prob')
                errors.extend(errs)
            
            valid, errs = validate_range(fault, 'durationMs', 'fault')
            errors.extend(errs)
    
    # Validate cbStatus
    if 'cbStatus' in config:
        cb = config['cbStatus']
        if not isinstance(cb, int) or cb < 0:
            errors.append(f"cbStatus must be non-negative integer (got {cb})")
    
    # Validate boolean flags
    if 'incrementStNumOnFault' in config:
        if not isinstance(config['incrementStNumOnFault'], bool):
            errors.append("incrementStNumOnFault must be boolean")
    
    # Validate sqnumMode
    if 'sqnumMode' in config:
        mode = config['sqnumMode']
        valid_modes = ['fast', 'slow', 'random']
        if mode not in valid_modes:
            errors.append(f"sqnumMode must be one of {valid_modes} (got '{mode}')")
    
    # Validate ttlMsValues
    if 'ttlMsValues' in config:
        if not isinstance(config['ttlMsValues'], list):
            errors.append("ttlMsValues must be an array")
        else:
            for i, val in enumerate(config['ttlMsValues']):
                if not isinstance(val, (int, float)) or val < 0:
                    errors.append(f"ttlMsValues[{i}] must be non-negative number (got {val})")
    
    # Validate analog configuration
    if 'analog' in config:
        analog = config['analog']
        if not isinstance(analog, dict):
            errors.append("analog must be an object")
        else:
            valid, errs = validate_range(analog, 'deltaAbs', 'analog')
            errors.extend(errs)
    
    # Validate trapArea configuration
    if 'trapArea' in config:
        trap = config['trapArea']
        if not isinstance(trap, dict):
            errors.append("trapArea must be an object")
        else:
            valid, errs = validate_range(trap, 'multiplier', 'trapArea')
            errors.extend(errs)
            
            if 'spikeProb' in trap:
                valid, errs = validate_probability(trap['spikeProb'], 'trapArea.spikeProb')
                errors.extend(errs)
    
    return len(errors) == 0, errors

def validate_masquerade_normal(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC04 - Masquerade Normal attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'masquerade_normal':
        errors.append(f"attackType should be 'masquerade_normal', got '{config['attackType']}'")
    
    # Similar structure to masquerade_fault but without fault injection
    # Validate cbStatus
    if 'cbStatus' in config:
        cb = config['cbStatus']
        if not isinstance(cb, int) or cb < 0:
            errors.append(f"cbStatus must be non-negative integer (got {cb})")
    
    # Validate sqnumMode
    if 'sqnumMode' in config:
        mode = config['sqnumMode']
        valid_modes = ['fast', 'slow', 'random']
        if mode not in valid_modes:
            errors.append(f"sqnumMode must be one of {valid_modes} (got '{mode}')")
    
    # Validate ttlMsValues
    if 'ttlMsValues' in config:
        if not isinstance(config['ttlMsValues'], list):
            errors.append("ttlMsValues must be an array")
        else:
            for i, val in enumerate(config['ttlMsValues']):
                if not isinstance(val, (int, float)) or val < 0:
                    errors.append(f"ttlMsValues[{i}] must be non-negative number (got {val})")
    
    return len(errors) == 0, errors

def validate_injection(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC05 - Injection attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'injection':
        errors.append(f"attackType should be 'injection', got '{config['attackType']}'")
    
    # Validate numInjectedMessages
    if 'numInjectedMessages' in config:
        num = config['numInjectedMessages']
        if not isinstance(num, int) or num <= 0:
            errors.append(f"numInjectedMessages must be positive integer (got {num})")
    
    # Validate randomSeed
    if 'randomSeed' in config:
        seed = config['randomSeed']
        if not isinstance(seed, int):
            errors.append(f"randomSeed must be integer (got {seed})")
    
    # Validate injectionPattern
    if 'injectionPattern' in config:
        pattern = config['injectionPattern']
        valid_patterns = ['random', 'uniform', 'burst']
        if pattern not in valid_patterns:
            errors.append(f"injectionPattern must be one of {valid_patterns} (got '{pattern}')")
    
    return len(errors) == 0, errors

def validate_high_stnum_injection(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC06 - High StNum Injection attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'high_stnum_injection':
        errors.append(f"attackType should be 'high_stnum_injection', got '{config['attackType']}'")
    
    # Similar to injection but with stNum-specific parameters
    if 'numInjectedMessages' in config:
        num = config['numInjectedMessages']
        if not isinstance(num, int) or num <= 0:
            errors.append(f"numInjectedMessages must be positive integer (got {num})")
    
    if 'stNumMin' in config:
        if not isinstance(config['stNumMin'], int) or config['stNumMin'] < 0:
            errors.append(f"stNumMin must be non-negative integer (got {config['stNumMin']})")
    
    if 'stNumMax' in config:
        if not isinstance(config['stNumMax'], int) or config['stNumMax'] < 0:
            errors.append(f"stNumMax must be non-negative integer (got {config['stNumMax']})")
    
    if 'stNumMin' in config and 'stNumMax' in config:
        if config['stNumMin'] >= config['stNumMax']:
            errors.append(f"stNumMin must be less than stNumMax")
    
    return len(errors) == 0, errors

def validate_flooding(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC07 - Flooding attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'flooding':
        errors.append(f"attackType should be 'flooding', got '{config['attackType']}'")
    
    # Validate burst
    valid, errs = validate_range(config, 'burst')
    errors.extend(errs)
    
    # Validate gapMs
    valid, errs = validate_range(config, 'gapMs')
    errors.extend(errs)
    
    # Validate boolean flags
    if 'stnumEveryPacket' in config:
        if not isinstance(config['stnumEveryPacket'], bool):
            errors.append("stnumEveryPacket must be boolean")
    
    # Validate sqnumStrideValues
    if 'sqnumStrideValues' in config:
        if not isinstance(config['sqnumStrideValues'], list):
            errors.append("sqnumStrideValues must be an array")
        else:
            for i, val in enumerate(config['sqnumStrideValues']):
                if not isinstance(val, int) or val <= 0:
                    errors.append(f"sqnumStrideValues[{i}] must be positive integer (got {val})")
    
    # Validate ttlMs
    if 'ttlMs' in config:
        ttl = config['ttlMs']
        if not isinstance(ttl, (int, float)) or ttl <= 0:
            errors.append(f"ttlMs must be positive number (got {ttl})")
    
    # Validate ethSrcSpoofProb
    if 'ethSrcSpoofProb' in config:
        valid, errs = validate_probability(config['ethSrcSpoofProb'], 'ethSrcSpoofProb')
        errors.extend(errs)
    
    return len(errors) == 0, errors

def validate_grayhole(config: Dict) -> Tuple[bool, List[str]]:
    """Validate UC08 - Grayhole attack configuration."""
    errors = []
    
    # Check attack type
    if 'attackType' not in config:
        errors.append("Missing 'attackType' field")
    elif config['attackType'] != 'grayhole':
        errors.append(f"attackType should be 'grayhole', got '{config['attackType']}'")
    
    # Validate dropProbability
    if 'dropProbability' in config:
        valid, errs = validate_probability(config['dropProbability'], 'dropProbability')
        errors.extend(errs)
    
    # Validate selectiveDropping
    if 'selectiveDropping' in config:
        if not isinstance(config['selectiveDropping'], bool):
            errors.append("selectiveDropping must be boolean")
    
    # Validate randomSeed
    if 'randomSeed' in config:
        seed = config['randomSeed']
        if not isinstance(seed, int):
            errors.append(f"randomSeed must be integer (got {seed})")
    
    return len(errors) == 0, errors

VALIDATORS = {
    'uc01_random_replay.json': validate_random_replay,
    'uc02_inverse_replay.json': validate_inverse_replay,
    'uc03_masquerade_fault.json': validate_masquerade_fault,
    'uc04_masquerade_normal.json': validate_masquerade_normal,
    'uc05_injection.json': validate_injection,
    'uc06_high_stnum_injection.json': validate_high_stnum_injection,
    'uc07_flooding.json': validate_flooding,
    'uc08_grayhole.json': validate_grayhole,
}

def validate_attack_file(filepath: Path) -> Tuple[bool, List[str]]:
    """Validate a single attack configuration file."""
    try:
        with open(filepath, 'r') as f:
            config = json.load(f)
        
        filename = filepath.name
        if filename in VALIDATORS:
            return VALIDATORS[filename](config)
        else:
            return True, [f"No validator found for {filename}"]
    
    except json.JSONDecodeError as e:
        return False, [f"JSON parse error: {str(e)}"]
    except Exception as e:
        return False, [f"Error reading file: {str(e)}"]

def main():
    """Main validation function."""
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}Legacy Attack Configuration Validator{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    # Find attack config files
    config_dir = Path('config/attacks')
    if not config_dir.exists():
        print(f"{Colors.RED}Error: config/attacks directory not found{Colors.RESET}")
        return 1
    
    attack_files = sorted(config_dir.glob('uc*.json'))
    
    if not attack_files:
        print(f"{Colors.YELLOW}Warning: No attack configuration files found{Colors.RESET}")
        return 0
    
    print(f"Found {len(attack_files)} attack configuration files\n")
    
    # Validate each file
    results = {}
    total_errors = 0
    
    for filepath in attack_files:
        print(f"{Colors.BOLD}Validating: {filepath.name}{Colors.RESET}")
        valid, errors = validate_attack_file(filepath)
        results[filepath.name] = (valid, errors)
        
        if valid:
            print(f"  {Colors.GREEN}✓ Valid{Colors.RESET}")
        else:
            print(f"  {Colors.RED}✗ Invalid ({len(errors)} errors){Colors.RESET}")
            for error in errors:
                print(f"    - {error}")
            total_errors += len(errors)
        print()
    
    # Summary
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}")
    print(f"{Colors.BOLD}Summary:{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*80}{Colors.RESET}")
    
    valid_count = sum(1 for v, _ in results.values() if v)
    invalid_count = len(results) - valid_count
    
    print(f"Total files validated: {len(results)}")
    print(f"{Colors.GREEN}Valid configurations: {valid_count}{Colors.RESET}")
    if invalid_count > 0:
        print(f"{Colors.RED}Invalid configurations: {invalid_count}{Colors.RESET}")
        print(f"{Colors.RED}Total errors: {total_errors}{Colors.RESET}")
    
    # List of validated attacks
    print(f"\n{Colors.BOLD}Attack Types:{Colors.RESET}")
    for filename in sorted(results.keys()):
        valid, _ = results[filename]
        status = f"{Colors.GREEN}✓{Colors.RESET}" if valid else f"{Colors.RED}✗{Colors.RESET}"
        attack_name = filename.replace('uc', 'UC').replace('.json', '')
        print(f"  {status} {attack_name}")
    
    print(f"\n{Colors.BOLD}{'='*80}{Colors.RESET}\n")
    
    return 0 if invalid_count == 0 else 1

if __name__ == '__main__':
    sys.exit(main())
