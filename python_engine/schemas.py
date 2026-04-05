"""
schemas.py  —  TechPulse Spindle R&D Suite  v2.0
Pydantic v2 models for all FastAPI request/response types.

Engineering basis:
  • Target platform : CNC Lathe Spindle
  • OD range        : 40 – 100 mm
  • Max speed       : 15,000 RPM
  • Frequency floor : > 350 Hz  (1.4 × max spindle freq = 1.4 × 250 Hz)
  • Deflection cap  : < 0.05 mm at tool tip
  • Default config  : 4 shaft segments, 3 bearings (front pair + rear single)
"""

from __future__ import annotations
import time
from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field, model_validator


# ══════════════════════════════════════════════════════════════════
#  ENUMERATIONS
# ══════════════════════════════════════════════════════════════════

class MaterialGrade(str, Enum):
    STEEL_42CrMo4      = "STEEL_42CrMo4"       # σ_y=680 MPa, E=210 GPa (default)
    STEEL_42CrMo4_HT   = "STEEL_42CrMo4_HT"    # Heat treated, σ_y=900 MPa
    STEEL_100Cr6       = "STEEL_100Cr6"         # Bearing steel, σ_y=1700 MPa
    TITANIUM_Ti6Al4V   = "TITANIUM_Ti6Al4V"     # σ_y=880 MPa, E=114 GPa
    CARBIDE_TUNGSTEN   = "CARBIDE_TUNGSTEN"     # E=550 GPa

class BearingType(str, Enum):
    ANGULAR_CONTACT_15  = "ANGULAR_CONTACT_15"  # 15° — high speed, low axial
    ANGULAR_CONTACT_25  = "ANGULAR_CONTACT_25"  # 25° — balanced
    ANGULAR_CONTACT_40  = "ANGULAR_CONTACT_40"  # 40° — heavy axial
    DEEP_GROOVE_BALL    = "DEEP_GROOVE_BALL"
    CYLINDRICAL_ROLLER  = "CYLINDRICAL_ROLLER"  # High radial, no axial

class PreloadClass(str, Enum):
    LIGHT   = "LIGHT"    # GA: 50–150 N
    MEDIUM  = "MEDIUM"   # GA: 150–350 N (default)
    HEAVY   = "HEAVY"    # GA: 350–700 N

class MountingArrangement(str, Enum):
    BACK_TO_BACK   = "BACK_TO_BACK"    # DB — stiff against moment loads
    FACE_TO_FACE   = "FACE_TO_FACE"    # DF — compliant
    TANDEM         = "TANDEM"          # DT — max axial in one direction
    SINGLE         = "SINGLE"          # One bearing at this position

class BearingRole(str, Enum):
    LOCATING     = "LOCATING"      # Fixed — carries axial + radial
    NON_LOCATING = "NON_LOCATING"  # Free  — radial only, allows thermal growth

class SimulationMode(str, Enum):
    STATIC         = "STATIC"
    MODAL          = "MODAL"
    MONTE_CARLO    = "MONTE_CARLO"
    FULL_ANALYSIS  = "FULL_ANALYSIS"   # Static + Modal + MC combined
    ML_SURROGATE   = "ML_SURROGATE"
    OPTIMIZATION   = "OPTIMIZATION"

class AnalysisStatus(str, Enum):
    PENDING   = "PENDING"
    RUNNING   = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED    = "FAILED"

class TaguchiArray(str, Enum):
    L9  = "L9"    # 4 factors, 3 levels, 9 runs
    L16 = "L16"   # 5 factors, 4 levels, 16 runs
    L27 = "L27"   # 13 factors, 3 levels, 27 runs


# ══════════════════════════════════════════════════════════════════
#  GEOMETRY — HOLLOW STEPPED SHAFT
# ══════════════════════════════════════════════════════════════════

class ShaftSegment(BaseModel):
    """
    One axial segment of the stepped hollow shaft.
    Segments are ordered from tool tip (segment 0) to rear.

    Typical CNC lathe spindle layout (4 segments):
      0: Tool taper      — solid nose, OD 60 mm, L 80 mm
      1: Front journal   — hollow,    OD 80 mm, ID 40 mm, L 120 mm
      2: Body            — hollow,    OD 90 mm, ID 55 mm, L 180 mm
      3: Rear journal    — hollow,    OD 70 mm, ID 40 mm, L 100 mm
    """
    segment_id: int               = Field(ge=0, description="0-indexed, tool→rear order")
    name: str                     = "Segment"
    length_mm: float              = Field(gt=0, le=500, description="Axial length [mm]")
    outer_diameter_mm: float      = Field(gt=0, le=150, description="OD [mm]")
    inner_diameter_mm: float      = Field(ge=0, description="ID [mm]; 0 = solid")
    surface_tolerance_mm: float   = Field(ge=0, default=0.005, description="±IT6 tolerance [mm]")
    material: MaterialGrade       = MaterialGrade.STEEL_42CrMo4

    @model_validator(mode="after")
    def id_less_than_od(self):
        if self.inner_diameter_mm >= self.outer_diameter_mm:
            raise ValueError(
                f"Segment {self.segment_id}: ID ({self.inner_diameter_mm}) must be < OD ({self.outer_diameter_mm})"
            )
        return self

    @property
    def area_mm2(self) -> float:
        import math
        return math.pi / 4 * (self.outer_diameter_mm**2 - self.inner_diameter_mm**2)

    @property
    def second_moment_mm4(self) -> float:
        """Second moment of area I = π(D⁴−d⁴)/64"""
        import math
        return math.pi / 64 * (self.outer_diameter_mm**4 - self.inner_diameter_mm**4)

    @property
    def hollowness_ratio(self) -> float:
        """k = d/D ; 0 = solid, → 1 = thin-walled"""
        if self.outer_diameter_mm == 0:
            return 0.0
        return self.inner_diameter_mm / self.outer_diameter_mm


class ShaftGeometry(BaseModel):
    """Ordered list of shaft segments constituting the full stepped hollow spindle."""
    segments: list[ShaftSegment]  = Field(min_length=1, max_length=10)

    @model_validator(mode="after")
    def validate_ids_sequential(self):
        for i, seg in enumerate(self.segments):
            if seg.segment_id != i:
                raise ValueError(f"Segment IDs must be sequential; got {seg.segment_id} at index {i}")
        return self

    @property
    def total_length_mm(self) -> float:
        return sum(s.length_mm for s in self.segments)

    @property
    def z_boundaries_mm(self) -> list[float]:
        """Cumulative Z positions [0, L0, L0+L1, …, total_L] from tool tip."""
        zs = [0.0]
        for seg in self.segments:
            zs.append(zs[-1] + seg.length_mm)
        return zs

    @classmethod
    def default_cnc_lathe(cls) -> "ShaftGeometry":
        """Standard 4-segment CNC lathe spindle starting geometry."""
        return cls(segments=[
            ShaftSegment(segment_id=0, name="Tool Taper",    length_mm=80,  outer_diameter_mm=60, inner_diameter_mm=0,  surface_tolerance_mm=0.003),
            ShaftSegment(segment_id=1, name="Front Journal", length_mm=120, outer_diameter_mm=80, inner_diameter_mm=40, surface_tolerance_mm=0.005),
            ShaftSegment(segment_id=2, name="Body",          length_mm=180, outer_diameter_mm=90, inner_diameter_mm=55, surface_tolerance_mm=0.010),
            ShaftSegment(segment_id=3, name="Rear Journal",  length_mm=100, outer_diameter_mm=70, inner_diameter_mm=40, surface_tolerance_mm=0.005),
        ])


# ══════════════════════════════════════════════════════════════════
#  GEOMETRY — BEARING SYSTEM
# ══════════════════════════════════════════════════════════════════

class SpacerConfig(BaseModel):
    """Spacer between two adjacent bearings in a set."""
    length_mm: float   = Field(gt=0, description="Spacer axial length [mm]")
    od_mm: float       = Field(gt=0, description="Spacer OD [mm]")
    is_precision: bool = True   # Precision-ground vs standard

class BearingMount(BaseModel):
    """
    A single bearing position on the spindle.

    z_position_mm is measured from the tool-tip datum.
    housing_stiffness_n_per_mm represents the combined housing/preload
    spring constant in the radial direction; typical range 50–500 N/mm.
    """
    bearing_id: int                   = Field(ge=0)
    designation: str                  = "7010 CE/P4A"     # ISO catalogue designation
    bearing_type: BearingType         = BearingType.ANGULAR_CONTACT_25
    z_position_mm: float              = Field(ge=0, description="Axial position from tool tip [mm]")
    role: BearingRole                 = BearingRole.LOCATING
    arrangement: MountingArrangement  = MountingArrangement.BACK_TO_BACK
    preload_class: PreloadClass       = PreloadClass.MEDIUM
    preload_force_n: float            = Field(ge=0, default=250.0, description="Applied preload [N]")
    housing_stiffness_n_per_mm: float = Field(gt=0, default=200.0, description="Radial support stiffness [N/mm]")
    contact_angle_deg: float          = Field(ge=0, le=45, default=25.0)
    bore_diameter_mm: float           = Field(gt=0, default=50.0)
    outer_diameter_mm: float          = Field(gt=0, default=80.0)
    width_mm: float                   = Field(gt=0, default=16.0)
    dynamic_load_rating_kn: float     = Field(gt=0, default=25.0, description="ISO 281 C [kN]")
    static_load_rating_kn: float      = Field(gt=0, default=20.0, description="ISO 76 C0 [kN]")
    limiting_speed_rpm: int           = Field(gt=0, default=28_000)
    spacer_after: Optional[SpacerConfig] = None  # Spacer between this and next bearing

class BearingSystem(BaseModel):
    """
    Variable-count bearing system for the spindle.
    Default: 3 bearings — front pair (DB arrangement) + rear single.

    Validation ensures no two bearings share the same Z-position and
    that all Z-positions are within the shaft total length.
    """
    bearings: list[BearingMount] = Field(min_length=1, max_length=8)

    @model_validator(mode="after")
    def validate_unique_positions(self):
        positions = [b.z_position_mm for b in self.bearings]
        if len(positions) != len(set(positions)):
            raise ValueError("Two bearings cannot share the same Z-position")
        return self

    @classmethod
    def default_cnc_lathe(cls) -> "BearingSystem":
        """Front DB pair + rear single — standard lathe spindle layout."""
        return cls(bearings=[
            BearingMount(
                bearing_id=0, designation="7010 CE/P4A",
                bearing_type=BearingType.ANGULAR_CONTACT_25,
                z_position_mm=80.0,  # Front of front journal
                role=BearingRole.LOCATING,
                arrangement=MountingArrangement.BACK_TO_BACK,
                preload_class=PreloadClass.MEDIUM,
                preload_force_n=280.0,
                housing_stiffness_n_per_mm=220.0,
                bore_diameter_mm=50.0, outer_diameter_mm=80.0, width_mm=16.0,
                dynamic_load_rating_kn=25.0, static_load_rating_kn=20.0,
                limiting_speed_rpm=28_000,
                spacer_after=SpacerConfig(length_mm=20.0, od_mm=75.0)
            ),
            BearingMount(
                bearing_id=1, designation="7010 CE/P4A",
                bearing_type=BearingType.ANGULAR_CONTACT_25,
                z_position_mm=116.0, # Second of front pair
                role=BearingRole.LOCATING,
                arrangement=MountingArrangement.BACK_TO_BACK,
                preload_class=PreloadClass.MEDIUM,
                preload_force_n=280.0,
                housing_stiffness_n_per_mm=220.0,
                bore_diameter_mm=50.0, outer_diameter_mm=80.0, width_mm=16.0,
                dynamic_load_rating_kn=25.0, static_load_rating_kn=20.0,
                limiting_speed_rpm=28_000,
            ),
            BearingMount(
                bearing_id=2, designation="NN3010",
                bearing_type=BearingType.CYLINDRICAL_ROLLER,
                z_position_mm=340.0, # Rear — non-locating
                role=BearingRole.NON_LOCATING,
                arrangement=MountingArrangement.SINGLE,
                preload_class=PreloadClass.LIGHT,
                preload_force_n=0.0,
                housing_stiffness_n_per_mm=150.0,
                bore_diameter_mm=50.0, outer_diameter_mm=72.0, width_mm=22.0,
                dynamic_load_rating_kn=48.0, static_load_rating_kn=58.0,
                limiting_speed_rpm=22_000,
            ),
        ])


# ══════════════════════════════════════════════════════════════════
#  STOCHASTIC VARIABLES — Monte Carlo inputs
# ══════════════════════════════════════════════════════════════════

class StochasticVariables(BaseModel):
    """
    Manufacturing and operational uncertainties for Monte Carlo sampling.
    All values are 1-σ standard deviations unless noted.

    Physics basis:
      runout_1sigma_mm     : Radial runout (ISO 230-7 grade) → adds rotating eccentric force
      eccentricity_1sigma_mm: Mass eccentricity → F_ec = m_unbalance × ω² × e
      bearing_preload_cv   : Coefficient of variation on preload force (typ. 0.10–0.20)
      angular_misalign_rad : Bearing ring misalignment (ISO 492) → induces moment load
      od_tolerance_mm      : Shaft OD manufacturing tolerance (IT6 for journals)
      bearing_clearance_mm : Bearing radial clearance variation (Group C2/C3)
      axial_load_cv        : Cutting force axial component variation
      radial_load_cv       : Cutting force radial component variation
      temp_delta_c         : Operating temperature variation → affects preload and clearance
    """
    runout_1sigma_mm: float       = Field(ge=0, default=0.003,
        description="Shaft radial runout 1σ [mm] per ISO 230-7 grade P4")
    eccentricity_1sigma_mm: float = Field(ge=0, default=0.002,
        description="Unbalance mass eccentricity 1σ [mm]")
    bearing_preload_cv: float     = Field(ge=0, le=0.5, default=0.12,
        description="Preload coefficient of variation (σ/µ)")
    angular_misalign_rad: float   = Field(ge=0, default=2e-4,
        description="Bearing ring angular misalignment 1σ [rad] per ISO 492")
    od_tolerance_mm: float        = Field(ge=0, default=0.005,
        description="OD manufacturing tolerance ±IT6 [mm]")
    bearing_clearance_mm: float   = Field(ge=0, default=0.003,
        description="Bearing radial clearance ±C3 group [mm]")
    axial_load_cv: float          = Field(ge=0, le=0.5, default=0.08,
        description="Axial cutting force coefficient of variation")
    radial_load_cv: float         = Field(ge=0, le=0.5, default=0.10,
        description="Radial cutting force coefficient of variation")
    temp_delta_c: float           = Field(ge=0, default=15.0,
        description="Operating temperature rise 1σ [°C]")
    n_iterations: int             = Field(ge=100, le=500_000, default=10_000)
    confidence_level: float       = Field(gt=0, lt=1, default=0.95)


# ══════════════════════════════════════════════════════════════════
#  TAGUCHI ROBUST DESIGN
# ══════════════════════════════════════════════════════════════════

class TaguchiControlFactor(BaseModel):
    name: str
    unit: str
    level_1: float
    level_2: float
    level_3: float

class TaguchiRun(BaseModel):
    run_number: int
    factor_levels: dict[str, int]      # factor_name → level (1,2,3)
    response_deflection_mm: float
    response_freq_hz: float
    response_stress_mpa: float
    sn_ratio_deflection: float         # η = -10 log₁₀(MSD), smaller-is-better
    sn_ratio_freq: float               # larger-is-better
    sn_ratio_stress: float             # smaller-is-better

class TaguchiResponseTable(BaseModel):
    factor_name: str
    level_1_effect: float
    level_2_effect: float
    level_3_effect: float
    delta: float                       # max − min effect
    rank: int                          # Influence ranking

class TaguchiResult(BaseModel):
    array_type: TaguchiArray           = TaguchiArray.L9
    control_factors: list[TaguchiControlFactor]
    experiment_runs: list[TaguchiRun]
    response_table_deflection: list[TaguchiResponseTable]
    response_table_freq: list[TaguchiResponseTable]
    optimal_factor_levels: dict[str, float]
    predicted_optimum_deflection_mm: float
    predicted_optimum_freq_hz: float
    confidence_interval_pct: float     = 95.0


# ══════════════════════════════════════════════════════════════════
#  MODAL ANALYSIS RESULTS
# ══════════════════════════════════════════════════════════════════

class ModeShape(BaseModel):
    mode_number: int                   # 1-indexed
    natural_frequency_hz: float
    damping_ratio: float               = 0.0
    mode_type: str                     # e.g. "1st Bending XY", "Torsional"
    max_displacement_mm: float         # Normalised max amplitude
    critical_speed_rpm: float          # ω_n × 60 / (2π)
    safety_margin_pct: float           # (f_n − f_spindle) / f_spindle × 100

class ModalResult(BaseModel):
    solver: str                        = "ANSYS Block Lanczos"
    n_modes_extracted: int
    mode_shapes: list[ModeShape]
    spindle_frequency_hz: float        # Operating freq = RPM / 60
    first_critical_speed_rpm: float    # RPM at 1st natural freq
    safety_factor_to_first_critical: float   # f_n1 / f_operating
    campbell_diagram_data: Optional[list[dict]] = None  # [{rpm, freqs}]


# ══════════════════════════════════════════════════════════════════
#  MONTE CARLO RESULTS
# ══════════════════════════════════════════════════════════════════

class ISO281BearingLife(BaseModel):
    """ISO 281 probabilistic L10 life calculation per bearing."""
    bearing_id: int
    designation: str
    dynamic_load_rating_kn: float
    equivalent_dynamic_load_mean_n: float
    equivalent_dynamic_load_std_n: float
    l10_life_mean_hours: float
    l10_life_std_hours: float
    l10_life_p5_hours: float
    l10_life_p95_hours: float
    failure_probability_pct: float     # P(life < 20,000 h)

class MonteCarloResult(BaseModel):
    n_iterations: int
    probability_of_failure_pct: float
    reliability_pct: float

    # Tool-tip deflection distribution
    deflection_mean_mm: float
    deflection_std_mm: float
    deflection_p5_mm: float
    deflection_p95_mm: float

    # Von Mises stress at most-loaded section
    stress_mean_mpa: float
    stress_std_mpa: float
    stress_p95_mpa: float

    # First natural frequency distribution (Monte Carlo on geometry)
    freq_mean_hz: float
    freq_std_hz: float
    freq_p5_hz: float

    # ISO 281 bearing life per bearing
    bearing_lives: list[ISO281BearingLife]

    # Sensitivity indices (Sobol first-order)
    sensitivity: dict[str, float]      # variable_name → S1 index

    # Stochastic variable statistics
    runout_effect_mean_n: float        # Mean additional force due to runout
    eccentricity_effect_mean_n: float
    misalignment_moment_mean_nmm: float


# ══════════════════════════════════════════════════════════════════
#  FEA STATIC RESULT
# ══════════════════════════════════════════════════════════════════

class StaticFEAResult(BaseModel):
    solver_version: str
    element_type: str                  = "BEAM188"
    n_elements: int
    n_nodes: int
    max_deflection_mm: float
    max_deflection_location_mm: float  # Z-position of max deflection
    tool_tip_deflection_mm: float
    max_bending_stress_mpa: float
    max_bending_stress_location_mm: float
    max_von_mises_mpa: float
    safety_factor_yield: float
    safety_factor_fatigue: float
    render_image_b64: Optional[str]    = None   # PyVista PNG, base64-encoded


# ══════════════════════════════════════════════════════════════════
#  FULL SIMULATION RESULT ENVELOPE
# ══════════════════════════════════════════════════════════════════

class SimulationResult(BaseModel):
    result_id: str
    config_id: str
    status: AnalysisStatus             = AnalysisStatus.PENDING
    mode: SimulationMode
    timestamp_ms: int                  = Field(default_factory=lambda: int(time.time() * 1000))
    duration_sec: float                = 0.0

    static: Optional[StaticFEAResult]  = None
    modal: Optional[ModalResult]       = None
    monte_carlo: Optional[MonteCarloResult] = None
    taguchi: Optional[TaguchiResult]   = None

    warnings: list[str]                = []
    error_message: Optional[str]       = None


# ══════════════════════════════════════════════════════════════════
#  PROJECT FILE — .spindle save/load
# ══════════════════════════════════════════════════════════════════

class ProjectMetadata(BaseModel):
    project_id: str
    name: str
    description: str                   = ""
    author: str                        = ""
    institution: str                   = ""
    created_at: str                    = ""
    last_modified_at: str              = ""
    version: str                       = "2.0"

class SpindleProject(BaseModel):
    """
    Complete session state saved to / loaded from a .spindle JSON file.
    All simulation results are embedded so the project is fully self-contained.
    """
    metadata: ProjectMetadata
    geometry: ShaftGeometry
    bearing_system: BearingSystem
    stochastic: StochasticVariables
    operating_speed_rpm: float         = Field(gt=0, le=15_000, default=12_000)
    axial_load_n: float                = Field(ge=0, default=500.0)
    radial_load_n: float               = Field(ge=0, default=1_500.0)
    material: MaterialGrade            = MaterialGrade.STEEL_42CrMo4
    results: list[SimulationResult]    = []
    taguchi_data: Optional[TaguchiResult] = None

    @classmethod
    def default_cnc_lathe(cls, name: str = "New CNC Lathe Spindle") -> "SpindleProject":
        import uuid, datetime
        return cls(
            metadata=ProjectMetadata(
                project_id=str(uuid.uuid4()),
                name=name,
                created_at=datetime.datetime.now().isoformat(),
                last_modified_at=datetime.datetime.now().isoformat(),
            ),
            geometry=ShaftGeometry.default_cnc_lathe(),
            bearing_system=BearingSystem.default_cnc_lathe(),
            stochastic=StochasticVariables(),
        )


# ══════════════════════════════════════════════════════════════════
#  FASTAPI REQUEST / RESPONSE DTOs
# ══════════════════════════════════════════════════════════════════

class SimulateRequest(BaseModel):
    """Posted by the Kotlin app to start any simulation mode."""
    config_id: str
    project: SpindleProject
    mode: SimulationMode               = SimulationMode.FULL_ANALYSIS
    n_modal_modes: int                 = Field(ge=2, le=12, default=6)

class SimulateResponse(BaseModel):
    result_id: str
    status: str
    message: str                       = ""
    estimated_duration_sec: int        = 0

class ResultResponse(BaseModel):
    result_id: str
    config_id: str
    status: str
    result: Optional[SimulationResult] = None
    error_message: Optional[str]       = None

class RenderRequest(BaseModel):
    """Request a PyVista rendered image of the shaft geometry + results."""
    config_id: str
    result_id: Optional[str]           = None
    render_mode: str                   = "STRESS"  # "STRESS" | "DEFLECTION" | "MODAL" | "GEOMETRY"
    modal_mode_number: int             = 1         # Which mode shape to show
    width_px: int                      = Field(ge=400, le=3840, default=1200)
    height_px: int                     = Field(ge=300, le=2160, default=600)

class RenderResponse(BaseModel):
    image_b64: str                     # Base64-encoded PNG
    width_px: int
    height_px: int
    colorbar_min: float
    colorbar_max: float
    colorbar_unit: str

class HealthResponse(BaseModel):
    status: str
    ansys_available: bool
    pyvista_available: bool
    ml_model_loaded: bool
    version: str                       = "2.0"

class OptimisationStreamEvent(BaseModel):
    event_type: str                    # "GENERATION" | "COMPLETE" | "ERROR"
    generation: int
    best_fitness: float
    mean_fitness: float
    best_deflection_mm: float
    best_freq_hz: float
    best_weight_kg: float
    pareto_points: list[dict]          # [{deflection, freq, weight}]
    constraint_violations: int         = 0
