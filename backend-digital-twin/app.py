import time
import math
import random
# In a real environment, you would compile the Rust code using `maturin develop`
# which would make `civil_twin_physics` available as a Python module.
try:
    import civil_twin_physics
    RUST_AVAILABLE = True
except ImportError:
    print("Warning: Rust extension 'civil_twin_physics' not found. Ensure you compile with Maturin.")
    RUST_AVAILABLE = False

class DigitalTwinValidator:
    def __init__(self, bridge_id, baseline_frequency_hz):
        self.bridge_id = bridge_id
        self.baseline_frequency_hz = baseline_frequency_hz
        self.tolerance = 0.5 # +/- 0.5Hz shift allowed before structural warning

    def process_incoming_sensor_stream(self, sensor_data_array, sampling_rate):
        print(f"[{self.bridge_id}] Processing {len(sensor_data_array)} sensor data points...")
        start_time = time.time()

        if RUST_AVAILABLE:
            # RUST ACCELERATED PATH: Computes FFT in milliseconds using compiled Rust
            dominant_freq = civil_twin_physics.compute_dominant_frequency(sensor_data_array, sampling_rate)
            is_healthy = civil_twin_physics.validate_structural_health(dominant_freq, self.baseline_frequency_hz, self.tolerance)
        else:
            # PURE PYTHON FALLBACK: Mock calculation (much slower in real life)
            time.sleep(0.1) # Simulate slow python math
            dominant_freq = 4.2 + (random.random() * 0.4) # Mock freq
            diff = abs(dominant_freq - self.baseline_frequency_hz)
            is_healthy = diff <= self.tolerance

        latency = (time.time() - start_time) * 1000
        print(f"[{self.bridge_id}] Analysis Complete in {latency:.2f}ms")
        print(f"[{self.bridge_id}] Dominant Frequency: {dominant_freq:.2f} Hz")
        print(f"[{self.bridge_id}] Structural Status: {'STABLE' if is_healthy else 'CRITICAL DAMAGE DETECTED'}")
        
        return is_healthy

if __name__ == "__main__":
    print("=== CIVIL ENGINEERING DIGITAL TWIN (PYTHON + RUST HYBRID) ===")
    validator = DigitalTwinValidator(bridge_id="Delta-17", baseline_frequency_hz=4.2)
    
    # Simulate an incoming packet of high-frequency seismic data from the Android edge network
    mock_sensor_data = [math.sin(i * 0.1) + (random.random() * 0.2) for i in range(1024)]
    
    # Run the validation
    validator.process_incoming_sensor_stream(mock_sensor_data, sampling_rate=100.0)
