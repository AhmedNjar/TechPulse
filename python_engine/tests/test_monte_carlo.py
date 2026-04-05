"""
test_monte_carlo.py
Unit and integration tests for the Monte Carlo physics engine.

Run: pytest python_engine/tests/ -v
"""

import pytest
import numpy as np
import sys
import os

# Add parent directory to path so we can import core modules
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))


# ── Minimal config stub ───────────────────────────────────────

class MinimalConfig:
    config_id                     = "test-001"
    project_name                  = "Test Config"
    shaft_diameter_mm             = 50.0
    shaft_length_mm               = 350.0
    overhang_mm                   = 80.0
    spindle_rpm                   = 12_000.0
    axial_load_n                  = 500.0
    radial_load_n                 = 1_500.0
    operating_temp_c              = 25.0
    tol_shaft_diam_mm             = 0.005
    tol_bearing_clearance_mm      = 0.003
    tol_axial_load_pct            = 5.0
    tol_radial_load_pct           = 8.0
    monte_carlo_iterations        = 1_000   # Reduced for test speed
    confidence_level              = 0.95
    material                      = "STEEL_42CrMo4"
    mode                          = "MONTE_CARLO"
    enable_fea                    = False
    youngs_modulus_gpa            = 210.0
    material_yield_strength_mpa   = 680.0
    front_bearing_dynamic_rating_kn = 18.0


# ── MonteCarloRunner tests ────────────────────────────────────

class TestMonteCarloRunner:

    def test_static_deflection_positive(self):
        """Deflection must be a positive finite number."""
        from core.monte_carlo_runner import MonteCarloRunner
        runner = MonteCarloRunner(MinimalConfig())
        result = runner.run_static()
        assert result["static_deflection_mm"] > 0
        assert np.isfinite(result["static_deflection_mm"])

    def test_static_stress_positive(self):
        """Von Mises stress must be positive."""
        from core.monte_carlo_runner import MonteCarloRunner
        result = MonteCarloRunner(MinimalConfig()).run_static()
        assert result["max_von_mises_stress_mpa"] > 0

    def test_static_bearing_life_positive(self):
        """Bearing life hours must be positive."""
        from core.monte_carlo_runner import MonteCarloRunner
        result = MonteCarloRunner(MinimalConfig()).run_static()
        assert result["bearing_life_hours"] > 0

    def test_monte_carlo_pof_range(self):
        """Probability of failure must be in [0, 100]."""
        from core.monte_carlo_runner import MonteCarloRunner
        result = MonteCarloRunner(MinimalConfig()).run()
        assert 0 <= result["probability_of_failure_pct"] <= 100

    def test_monte_carlo_reliability_sums_to_100(self):
        """POF + reliability must equal 100%."""
        from core.monte_carlo_runner import MonteCarloRunner
        result = MonteCarloRunner(MinimalConfig()).run()
        total = result["probability_of_failure_pct"] + result["reliability_pct"]
        assert abs(total - 100.0) < 1e-6

    def test_monte_carlo_distribution_stats(self):
        """Mean deflection should be close to static deflection."""
        from core.monte_carlo_runner import MonteCarloRunner
        runner = MonteCarloRunner(MinimalConfig())
        static = runner.run_static()
        mc     = runner.run()
        # MC mean should be within ±20% of static (sampling noise with small N)
        diff = abs(mc["monte_carlo"]["deflection_mean_mm"] - static["static_deflection_mm"])
        assert diff / static["static_deflection_mm"] < 0.20

    def test_p5_less_than_p95(self):
        """5th percentile deflection must be < 95th percentile."""
        from core.monte_carlo_runner import MonteCarloRunner
        mc = MonteCarloRunner(MinimalConfig()).run()
        assert mc["monte_carlo"]["deflection_p5_mm"] < mc["monte_carlo"]["deflection_p95_mm"]

    def test_natural_frequency_plausible(self):
        """First natural frequency should be in typical spindle range 100–20000 Hz."""
        from core.monte_carlo_runner import MonteCarloRunner
        result = MonteCarloRunner(MinimalConfig()).run_static()
        assert 100 <= result["first_natural_frequency_hz"] <= 20_000


# ── Deflection formula unit test ──────────────────────────────

class TestDeflectionFormula:
    """
    Manual verification of the beam deflection formula.
    δ_max = F L³ / (48 E I)
    Reference: shaft Ø=50 mm, L=350 mm, F=1500 N, E=210000 N/mm²
    I = π × 50⁴ / 64 = 306796 mm⁴
    δ = 1500 × 350³ / (48 × 210000 × 306796) ≈ 0.0272 mm
    """

    def test_reference_deflection(self):
        import math
        d = 50.0; L = 350.0; F = 1500.0; E = 210_000.0
        I = math.pi * d**4 / 64
        delta = (F * L**3) / (48 * E * I)
        assert abs(delta - 0.0272) < 0.002, f"Expected ~0.0272 mm, got {delta:.4f} mm"


# ── ML Surrogate tests ────────────────────────────────────────

class TestMLSurrogate:

    def test_analytical_fallback_returns_dict(self):
        """Surrogate should fall back gracefully without a trained model."""
        from core.ml_surrogate_model import MLSurrogateModel
        model = MLSurrogateModel()   # Not loaded
        result = model.predict(MinimalConfig())
        assert "static_deflection_mm" in result
        assert result["static_deflection_mm"] > 0

    def test_train_predict_roundtrip(self):
        """After training on synthetic data, prediction should be finite."""
        from core.ml_surrogate_model import MLSurrogateModel
        import numpy as np
        model = MLSurrogateModel()
        rng = np.random.default_rng(42)
        X = rng.uniform([30,200,40,500,0,0,-10,5], [80,500,120,40_000,10_000,5_000,120,50], (200,8))
        y = rng.uniform([0.01,100,500], [0.1,500,50_000], (200,3))
        model.train(X, y)
        result = model.predict(MinimalConfig())
        assert np.isfinite(result["static_deflection_mm"])
        assert np.isfinite(result["max_von_mises_stress_mpa"])


# ── Schemas validation tests ──────────────────────────────────

class TestSchemas:

    def test_simulate_request_valid(self):
        from schemas import SimulateRequest
        req = SimulateRequest(
            config_id="test", project_name="Test",
            shaft_diameter_mm=50, shaft_length_mm=350, overhang_mm=80,
            front_bearing_designation="7008 CE/P4A", rear_bearing_designation="7010 CE/P4A",
            spindle_rpm=12000, axial_load_n=500, radial_load_n=1500,
            tol_shaft_diam_mm=0.005, tol_bearing_clearance_mm=0.003,
            tol_axial_load_pct=5.0, tol_radial_load_pct=8.0,
            monte_carlo_iterations=1000, confidence_level=0.95, material="STEEL_42CrMo4"
        )
        assert req.youngs_modulus_gpa == 210.0
        assert req.material_yield_strength_mpa == 680.0

    def test_simulate_request_invalid_shaft(self):
        from schemas import SimulateRequest
        from pydantic import ValidationError
        with pytest.raises(ValidationError):
            SimulateRequest(
                config_id="test", project_name="Test",
                shaft_diameter_mm=-10,   # Invalid — must be > 0
                shaft_length_mm=350, overhang_mm=80,
                front_bearing_designation="7008 CE/P4A",
                rear_bearing_designation="7010 CE/P4A",
                spindle_rpm=12000, axial_load_n=500, radial_load_n=1500,
                tol_shaft_diam_mm=0.005, tol_bearing_clearance_mm=0.003,
                tol_axial_load_pct=5.0, tol_radial_load_pct=8.0,
                monte_carlo_iterations=1000, confidence_level=0.95, material="STEEL_42CrMo4"
            )


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
