import json
from pathlib import Path
import subprocess

REPO_ROOT = Path(__file__).resolve().parents[1]
BASE = REPO_ROOT / 'config' / 'configparams.json'
OUTCFG = REPO_ROOT / 'target' / 'opt_repro_cfg.json'
OUTDIR = REPO_ROOT / 'target' / 'opt_repro_run'
JAR = REPO_ROOT / 'target' / 'ERENO-1.0-SNAPSHOT-shaded.jar'

with open(BASE, 'r', encoding='utf-8') as f:
    cfg = json.load(f)

# Patch from Optuna trial 2 that failed
patch = {
    'attacksParams': {
        'randomReplay': {
            'enabled': True,
            'count': {'lambda': 1188},
            'windowS': {'min': 6.333844149780402, 'max': 9.183533443047578},
            'delayMs': {'min': 7.062800240922271, 'max': 7.267365091961265},
            'burst': {'min': 39, 'max': 40},
            'reorderProb': 0.4376447928794408,
            'ttlOverride': {'prob': 0.42273172739803044},
            'ethSpoof': {'srcProb': 0.8716482130356028, 'dstProb': 0.9573630203873754}
        }
    }
}

# merge
def merge(d, u):
    for k, v in u.items():
        if isinstance(v, dict) and isinstance(d.get(k), dict):
            merge(d[k], v)
        else:
            d[k] = v

merge(cfg, patch)

OUTCFG.parent.mkdir(parents=True, exist_ok=True)
with open(OUTCFG, 'w', encoding='utf-8') as f:
    json.dump(cfg, f, indent=2)

# run ExperimentRunner
cmd = [
    'java', '-cp', str(JAR),
    'br.ufu.facom.ereno.experiment.ExperimentRunner',
    str(OUTCFG), str(OUTDIR), 'j48', 'random_replay', '123', '0.7'
]
print('Running:', ' '.join(cmd))
proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
print(proc.stdout)

print('Wrote patched config to', OUTCFG)
print('Output dir:', OUTDIR)
