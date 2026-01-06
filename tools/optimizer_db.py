#!/usr/bin/env python3
"""Python interface to the OptimizerDatabase for tracking optimizer results."""
import csv
import json
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List


class OptimizerDatabasePython:
    """Python interface to read/write optimizer results database."""
    
    def __init__(self, db_dir: str = "target/tracking"):
        self.db_dir = Path(db_dir)
        self.db_path = self.db_dir / "optimizer_results.csv"
        self._ensure_initialized()
    
    def _ensure_initialized(self):
        """Ensure database directory and file exist."""
        self.db_dir.mkdir(parents=True, exist_ok=True)
        if not self.db_path.exists():
            self._write_headers()
    
    def _write_headers(self):
        """Write CSV headers if file doesn't exist."""
        with open(self.db_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([
                'optimizer_id', 'timestamp', 'attack_key', 'attack_combination',
                'optimizer_type', 'num_trials', 'best_metric_f1', 'best_parameters_json',
                'config_base_path', 'notes'
            ])
    
    def save_result(self, attack_key: str, optimizer_type: str, num_trials: int,
                   best_f1: float, best_params: Dict[str, Any], 
                   attack_combination: str = "", config_base_path: str = "",
                   notes: str = "") -> str:
        """Save an optimizer result to the database."""
        optimizer_id = f"OPT_{int(datetime.now().timestamp() * 1000)}_{hash(str(best_params)) % 10000:04d}"
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        params_json = json.dumps(best_params)
        
        with open(self.db_path, 'a', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([
                optimizer_id, timestamp, attack_key, attack_combination,
                optimizer_type, num_trials, f"{best_f1:.6f}", params_json,
                config_base_path, notes
            ])
        
        print(f"Saved optimizer result to database: {optimizer_id}")
        return optimizer_id
    
    def get_best_for_attack(self, attack_key: str) -> Optional[Dict[str, Any]]:
        """Get the best result for a specific attack (lowest F1)."""
        if not self.db_path.exists():
            return None
        
        best = None
        with open(self.db_path, 'r', newline='', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                if row['attack_key'] == attack_key:
                    f1 = float(row['best_metric_f1'])
                    if best is None or f1 < best['best_metric_f1']:
                        best = {
                            'optimizer_id': row['optimizer_id'],
                            'timestamp': row['timestamp'],
                            'attack_key': row['attack_key'],
                            'optimizer_type': row['optimizer_type'],
                            'num_trials': int(row['num_trials']),
                            'best_metric_f1': f1,
                            'best_parameters': json.loads(row['best_parameters_json']) if row['best_parameters_json'] else {},
                            'config_base_path': row['config_base_path'],
                            'notes': row['notes']
                        }
        
        if best:
            print(f"Found previous best for '{attack_key}':")
            print(f"  F1: {best['best_metric_f1']:.6f}")
            print(f"  From: {best['timestamp']}")
            print(f"  Optimizer: {best['optimizer_type']}")
            print(f"  Trials: {best['num_trials']}")
        
        return best
    
    def get_best_for_combination(self, attack_keys: List[str]) -> Optional[Dict[str, Any]]:
        """Get the best result for an attack combination."""
        if not self.db_path.exists():
            return None
        
        combination_key = ",".join(sorted(attack_keys))
        best = None
        
        with open(self.db_path, 'r', newline='', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                row_combo = ",".join(sorted(row['attack_combination'].split(',')))
                if row_combo == combination_key:
                    f1 = float(row['best_metric_f1'])
                    if best is None or f1 < best['best_metric_f1']:
                        best = {
                            'optimizer_id': row['optimizer_id'],
                            'timestamp': row['timestamp'],
                            'attack_combination': row['attack_combination'],
                            'optimizer_type': row['optimizer_type'],
                            'num_trials': int(row['num_trials']),
                            'best_metric_f1': f1,
                            'best_parameters': json.loads(row['best_parameters_json']) if row['best_parameters_json'] else {},
                            'config_base_path': row['config_base_path'],
                            'notes': row['notes']
                        }
        
        if best:
            print(f"Found previous best for combination '{combination_key}':")
            print(f"  F1: {best['best_metric_f1']:.6f}")
            print(f"  From: {best['timestamp']}")
        
        return best
    
    def get_all_results(self) -> List[Dict[str, Any]]:
        """Get all optimizer results."""
        if not self.db_path.exists():
            return []
        
        results = []
        with open(self.db_path, 'r', newline='', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                results.append({
                    'optimizer_id': row['optimizer_id'],
                    'timestamp': row['timestamp'],
                    'attack_key': row['attack_key'],
                    'attack_combination': row['attack_combination'],
                    'optimizer_type': row['optimizer_type'],
                    'num_trials': int(row['num_trials']),
                    'best_metric_f1': float(row['best_metric_f1']),
                    'best_parameters': json.loads(row['best_parameters_json']) if row['best_parameters_json'] else {},
                    'config_base_path': row['config_base_path'],
                    'notes': row['notes']
                })
        
        return results
