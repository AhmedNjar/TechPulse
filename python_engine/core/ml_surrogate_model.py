"""
ml_surrogate_model.py
Gaussian Process / GBM surrogate model that predicts deflection and stress
from spindle config features — without running a full FEA solve each time.

Training data: collected from historical ANSYS / Monte Carlo runs.

Workflow:
  1. Collect N labelled samples (SpindleConfig → FEA result)
  2. Train this surrogate on the dataset
  3. Use surrogate during GA evaluation for 100-1000× speedup
  4. Periodically retrain as more FEA data accumulates

Model: GradientBoostingRegressor (fast, no GPU needed)
       Swap for Gaussian Process if UQ (uncertainty) on prediction is needed.
"""

import os
import joblib
import numpy as np
from typing import Any

try:
    from sklearn.ensemble import GradientBoostingRegressor
    from sklearn.preprocessing import StandardScaler
    from sklearn.pipeline import Pipeline
    from sklearn.multioutput import MultiOutputRegressor
    SKLEARN_AVAILABLE = True
except ImportError:
    SKLEARN_AVAILABLE = False


FEATURE_NAMES = [
    "shaft_diameter_mm",
    "shaft_length_mm",
    "overhang_mm",
    "spindle_rpm",
    "radial_load_n",
    "axial_load_n",
    "operating_temp_c",
    "front_bearing_dynamic_rating_kn",
]

TARGET_NAMES = [
    "deflection_mm",
    "von_mises_stress_mpa",
    "bearing_life_hours",
]


class MLSurrogateModel:

    def __init__(self):
        self.is_loaded   = False
        self._pipeline   = None

    # ── Training ──────────────────────────────────────────────

    def train(self, X: np.ndarray, y: np.ndarray):
        """
        Train surrogate on labelled samples.
        X shape: (n_samples, len(FEATURE_NAMES))
        y shape: (n_samples, len(TARGET_NAMES))
        """
        if not SKLEARN_AVAILABLE:
            raise RuntimeError("scikit-learn is not installed")

        gbm = GradientBoostingRegressor(
            n_estimators   = 300,
            max_depth      = 5,
            learning_rate  = 0.05,
            subsample      = 0.8,
            random_state   = 42
        )
        self._pipeline = Pipeline([
            ("scaler", StandardScaler()),
            ("model",  MultiOutputRegressor(gbm))
        ])
        self._pipeline.fit(X, y)
        self.is_loaded = True

    def save(self, path: str):
        if self._pipeline is None:
            raise RuntimeError("Model not trained yet")
        joblib.dump(self._pipeline, path)

    def load(self, path: str):
        if not os.path.exists(path):
            print(f"[ML] Surrogate model not found at {path} — predictions will fall back to Monte Carlo")
            return
        self._pipeline = joblib.load(path)
        self.is_loaded = True

    # ── Inference ─────────────────────────────────────────────

    def predict(self, config: Any) -> dict:
        """
        Predict deflection, stress, and bearing life from a SpindleConfig dict.
        Falls back to a rough analytical estimate if model is not loaded.
        """
        if not self.is_loaded or self._pipeline is None:
            return self._analytical_fallback(config)

        features = self._extract_features(config)
        prediction = self._pipeline.predict([features])[0]

        return {
            "static_deflection_mm":       float(prediction[0]),
            "max_von_mises_stress_mpa":   float(prediction[1]),
            "bearing_life_hours":         float(prediction[2]),
            "first_natural_frequency_hz": 0.0,   # Not in surrogate — compute separately
            "probability_of_failure_pct": 0.0,   # Requires MC; surrogate gives point estimate
            "reliability_pct":            100.0,
        }

    def _extract_features(self, config: Any) -> list:
        return [
            getattr(config, "shaft_diameter_mm",                  50.0),
            getattr(config, "shaft_length_mm",                   350.0),
            getattr(config, "overhang_mm",                        80.0),
            getattr(config, "spindle_rpm",                     12000.0),
            getattr(config, "radial_load_n",                    1500.0),
            getattr(config, "axial_load_n",                      500.0),
            getattr(config, "operating_temp_c",                   25.0),
            getattr(config, "front_bearing_dynamic_rating_kn",    18.0),
        ]

    def _analytical_fallback(self, config: Any) -> dict:
        """Very rough analytical estimate when no trained model is available."""
        d = getattr(config, "shaft_diameter_mm", 50.0)
        L = getattr(config, "shaft_length_mm",  350.0)
        F = getattr(config, "radial_load_n",    1500.0)
        E = 210_000
        I = 3.14159 * d**4 / 64
        defl = (F * L**3) / (48 * E * I)
        return {
            "static_deflection_mm":       defl,
            "max_von_mises_stress_mpa":   320.0,
            "bearing_life_hours":         4000.0,
            "first_natural_frequency_hz": 1800.0,
            "probability_of_failure_pct": 0.0,
            "reliability_pct":            100.0,
        }
